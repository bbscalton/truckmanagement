import * as admin from "firebase-admin";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";

admin.initializeApp();
const db = admin.firestore();

const STATUS = {
  DISPATCHER_REVIEW: "dispatcher_review",
  AUTO_NEAREST: "auto_nearest",
  ASSIGNED: "assigned",
  ACCEPTED: "accepted_by_driver",
  ARRIVED: "arrived",
  PAYMENT_PENDING: "payment_pending",
  PAYMENT_ACCEPTED: "payment_accepted",
} as const;

const dedupe = new Map<string, number>();
const DEDUPE_MS = 30_000;

function shouldSend(key: string): boolean {
  const now = Date.now();
  const prev = dedupe.get(key) ?? 0;
  if (now - prev < DEDUPE_MS) return false;
  dedupe.set(key, now);
  if (dedupe.size > 500) {
    for (const [k, t] of dedupe) {
      if (now - t > DEDUPE_MS) dedupe.delete(k);
    }
  }
  return true;
}

function haversineMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

async function sendToTokens(
  tokens: string[],
  title: string,
  body: string,
  data: Record<string, string> = {},
  dedupeKey?: string,
) {
  if (dedupeKey && !shouldSend(dedupeKey)) return;
  const unique = [...new Set(tokens.filter(Boolean))];
  if (!unique.length) return;
  await admin.messaging().sendEachForMulticast({
    tokens: unique,
    notification: { title, body },
    data: { ...data, title, body },
    android: {
      priority: "high",
      notification: {
        channelId: data.type === "chat" ? "truckmgmt_chat" : data.type === "new_request" ? "truckmgmt_alerts" : "truckmgmt_jobs",
        sound: "default",
        priority: "high" as const,
      },
    },
    apns: {
      headers: { "apns-priority": "10" },
      payload: { aps: { sound: "default" } },
    },
  });
}

async function fleetDispatcherTokens(fleetId: string): Promise<string[]> {
  const fleet = await db.collection("fleets").doc(fleetId).get();
  const ownerUid = fleet.get("ownerUid") as string | undefined;
  if (!ownerUid) return [];
  const profile = await db.collection("dispatcherProfiles").doc(ownerUid).get();
  const token = profile.get("fcmToken") as string | undefined;
  return token ? [token] : [];
}

async function customerToken(customerUid: string): Promise<string[]> {
  if (!customerUid) return [];
  const cust = await db.collection("customerProfiles").doc(customerUid).get();
  const t = cust.get("fcmToken") as string | undefined;
  return t ? [t] : [];
}

async function driverToken(fleetId: string, driverId: string): Promise<string[]> {
  const driver = await db.collection("fleets").doc(fleetId).collection("drivers").doc(driverId).get();
  const deviceId = driver.get("deviceId") as string | undefined;
  if (!deviceId) return [];
  const device = await db.collection("fleets").doc(fleetId).collection("devices").doc(deviceId).get();
  const token = device.get("fcmToken") as string | undefined;
  return token ? [token] : [];
}

async function nearestOnlineDriverTokens(fleetId: string, pickupLat?: number, pickupLng?: number): Promise<string[]> {
  const driversSnap = await db
    .collection("fleets")
    .doc(fleetId)
    .collection("drivers")
    .where("online", "==", true)
    .get();
  const now = Date.now();
  type Cand = { id: string; dist: number };
  const candidates: Cand[] = [];
  for (const d of driversSnap.docs) {
    const hb = (d.get("lastHeartbeatAt") as number | undefined) ?? 0;
    if (hb && now - hb > 5 * 60_000) continue;
    const lat = d.get("lastLat") as number | undefined;
    const lng = d.get("lastLng") as number | undefined;
    let dist = Number.MAX_SAFE_INTEGER;
    if (pickupLat != null && pickupLng != null && lat != null && lng != null) {
      dist = haversineMeters(pickupLat, pickupLng, lat, lng);
    }
    candidates.push({ id: d.id, dist });
  }
  candidates.sort((a, b) => a.dist - b.dist);
  const tokens: string[] = [];
  for (const c of candidates.slice(0, 5)) {
    tokens.push(...(await driverToken(fleetId, c.id)));
  }
  return tokens;
}

async function allOnlineDriverTokens(fleetId: string): Promise<string[]> {
  const driversSnap = await db
    .collection("fleets")
    .doc(fleetId)
    .collection("drivers")
    .where("online", "==", true)
    .limit(20)
    .get();
  const tokens: string[] = [];
  for (const d of driversSnap.docs) {
    tokens.push(...(await driverToken(fleetId, d.id)));
  }
  return tokens;
}

async function assignNearestDriver(fleetId: string, requestId: string): Promise<boolean> {
  const reqRef = db.collection("fleets").doc(fleetId).collection("deliveryRequests").doc(requestId);
  const req = await reqRef.get();
  if (!req.exists) return false;
  const status = req.get("status") as string;
  if (status !== STATUS.DISPATCHER_REVIEW && status !== STATUS.AUTO_NEAREST) return false;

  const pickupLat = req.get("pickupLat") as number | undefined;
  const pickupLng = req.get("pickupLng") as number | undefined;
  const driversSnap = await db
    .collection("fleets")
    .doc(fleetId)
    .collection("drivers")
    .where("online", "==", true)
    .get();

  const now = Date.now();
  type Cand = { id: string; dist: number; truckId?: string };
  const candidates: Cand[] = [];
  for (const d of driversSnap.docs) {
    const hb = (d.get("lastHeartbeatAt") as number | undefined) ?? 0;
    if (hb && now - hb > 5 * 60_000) continue;
    const lat = d.get("lastLat") as number | undefined;
    const lng = d.get("lastLng") as number | undefined;
    let dist = Number.MAX_SAFE_INTEGER;
    if (pickupLat != null && pickupLng != null && lat != null && lng != null) {
      dist = haversineMeters(pickupLat, pickupLng, lat, lng);
    }
    candidates.push({ id: d.id, dist, truckId: d.get("truckId") as string | undefined });
  }
  candidates.sort((a, b) => a.dist - b.dist);
  const best = candidates[0];
  if (!best) {
    await reqRef.update({ status: STATUS.AUTO_NEAREST, nearestAttemptedAt: admin.firestore.FieldValue.serverTimestamp() });
    return false;
  }

  const data = { ...(req.data() ?? {}) } as Record<string, unknown>;
  data.status = STATUS.ASSIGNED;
  data.assignedDriverId = best.id;
  data.assignedTruckId = best.truckId ?? null;
  data.requestId = requestId;
  data.updatedAt = admin.firestore.FieldValue.serverTimestamp();
  data.assignmentSource = "nearest_driver";

  const deliveryRef = db.collection("fleets").doc(fleetId).collection("deliveries").doc();
  await deliveryRef.set(data);
  await reqRef.update({
    status: STATUS.ASSIGNED,
    deliveryId: deliveryRef.id,
    assignedDriverId: best.id,
    assignmentSource: "nearest_driver",
  });

  const tokens = await driverToken(fleetId, best.id);
  await sendToTokens(
    tokens,
    "New delivery assigned",
    "A nearby request was assigned to you.",
    { type: "delivery", deliveryId: deliveryRef.id, fleetId },
    `assign:${deliveryRef.id}`,
  );
  return true;
}

/** Notify dispatcher + nearest drivers when a customer creates a delivery request. */
export const onDeliveryRequestCreated = onDocumentCreated(
  "fleets/{fleetId}/deliveryRequests/{requestId}",
  async (event) => {
    const fleetId = event.params.fleetId;
    const requestId = event.params.requestId;
    const data = event.data?.data() ?? {};
    const pickup = String(data.pickupAddress ?? "Pickup");
    const dropoff = String(data.dropoffAddress ?? "Dropoff");

    const dispatcherTokens = await fleetDispatcherTokens(fleetId);
    await sendToTokens(
      dispatcherTokens,
      "New delivery request",
      `${pickup} → ${dropoff}`,
      { type: "new_request", requestId, fleetId, pickup, dropoff },
      `req:dispatcher:${requestId}`,
    );

    const driverTokens = await nearestOnlineDriverTokens(
      fleetId,
      data.pickupLat as number | undefined,
      data.pickupLng as number | undefined,
    );
    await sendToTokens(
      driverTokens,
      "Delivery request nearby",
      `${pickup} → ${dropoff}`,
      { type: "new_request", requestId, fleetId },
      `req:drivers:${requestId}`,
    );
  },
);

/** When delivery status changes, notify relevant parties. */
export const onDeliveryUpdated = onDocumentUpdated(
  "fleets/{fleetId}/deliveries/{deliveryId}",
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    if (!before || !after) return;
    const prev = before.get("status") as string;
    const next = after.get("status") as string;
    if (prev === next) return;

    const fleetId = event.params.fleetId;
    const deliveryId = event.params.deliveryId;
    const driverId = after.get("assignedDriverId") as string | undefined;
    const customerUid = after.get("customerUid") as string | undefined;

    if (next === STATUS.ASSIGNED && driverId) {
      const tokens = await driverToken(fleetId, driverId);
      await sendToTokens(
        tokens,
        "Delivery assigned",
        "Open the driver app to accept.",
        { type: "delivery", deliveryId, fleetId },
        `status:assigned:${deliveryId}`,
      );
      const custTokens = await customerToken(customerUid ?? "");
      await sendToTokens(
        custTokens,
        "Driver assigned",
        "A driver has been assigned to your delivery.",
        { type: "delivery", deliveryId, fleetId },
        `status:assigned:customer:${deliveryId}`,
      );
      const dispTokens = await fleetDispatcherTokens(fleetId);
      await sendToTokens(
        dispTokens,
        "Request assigned",
        `Delivery ${deliveryId.slice(0, 8)} assigned.`,
        { type: "delivery", deliveryId, fleetId },
        `status:assigned:dispatcher:${deliveryId}`,
      );
    }
    if (next === STATUS.ACCEPTED) {
      const dispTokens = await fleetDispatcherTokens(fleetId);
      await sendToTokens(
        dispTokens,
        "Driver accepted",
        `Delivery ${deliveryId.slice(0, 8)} accepted.`,
        { type: "delivery", deliveryId, fleetId },
        `status:accepted:${deliveryId}`,
      );
      const custTokens = await customerToken(customerUid ?? "");
      await sendToTokens(
        custTokens,
        "On the way",
        "Your driver accepted and is heading to pickup.",
        { type: "delivery", deliveryId, fleetId },
        `status:accepted:customer:${deliveryId}`,
      );
    }
    if (next === STATUS.ARRIVED) {
      const tokens = await fleetDispatcherTokens(fleetId);
      await sendToTokens(
        tokens,
        "Driver arrived",
        `Delivery ${deliveryId.slice(0, 8)} arrived.`,
        { type: "delivery", deliveryId, fleetId },
        `status:arrived:${deliveryId}`,
      );
      const custTokens = await customerToken(customerUid ?? "");
      await sendToTokens(
        custTokens,
        "Driver arrived",
        "Your truck has arrived.",
        { type: "delivery", deliveryId, fleetId },
        `status:arrived:customer:${deliveryId}`,
      );
    }
    if (next === STATUS.PAYMENT_ACCEPTED) {
      const tokens = await fleetDispatcherTokens(fleetId);
      await sendToTokens(
        tokens,
        "Payment accepted",
        "A trip payment was confirmed.",
        { type: "delivery", deliveryId, fleetId },
        `status:paid:${deliveryId}`,
      );
    }
  },
);

/** Fleet chat — notify other fleet members. */
export const onFleetChatCreated = onDocumentCreated(
  "fleets/{fleetId}/fleetChat/{messageId}",
  async (event) => {
    const fleetId = event.params.fleetId;
    const msg = event.data?.data() ?? {};
    const senderRole = String(msg.senderRole ?? "");
    const type = String(msg.type ?? "text");
    const preview =
      type === "image" ? "📷 Image" : type === "audio" ? "🎤 Voice note" : String(msg.text ?? "New message");

    const driverTokens = await allOnlineDriverTokens(fleetId);
    const dispatcherTokens = await fleetDispatcherTokens(fleetId);

    if (senderRole !== "dispatcher") {
      await sendToTokens(
        dispatcherTokens,
        "Fleet chat",
        preview,
        { type: "chat", chatScope: "fleet", fleetId },
        `fleetchat:dispatcher:${event.params.messageId}`,
      );
    }
    if (senderRole !== "driver") {
      await sendToTokens(
        driverTokens,
        "Fleet chat",
        preview,
        { type: "chat", chatScope: "fleet", fleetId },
        `fleetchat:drivers:${event.params.messageId}`,
      );
    }
  },
);

/** Trip chat — notify customer, driver, dispatcher. */
export const onTripChatCreated = onDocumentCreated(
  "fleets/{fleetId}/tripChat/{deliveryId}/messages/{messageId}",
  async (event) => {
    const fleetId = event.params.fleetId;
    const deliveryId = event.params.deliveryId;
    const msg = event.data?.data() ?? {};
    const senderRole = String(msg.senderRole ?? "");
    const type = String(msg.type ?? "text");
    const preview =
      type === "image" ? "📷 Image" : type === "audio" ? "🎤 Voice note" : String(msg.text ?? "New message");

    const delivery = await db.collection("fleets").doc(fleetId).collection("deliveries").doc(deliveryId).get();
    const customerUid = delivery.get("customerUid") as string | undefined;
    const driverId = delivery.get("assignedDriverId") as string | undefined;

    const data = { type: "chat", chatScope: "trip", fleetId, deliveryId };

    if (senderRole !== "customer") {
      const custTokens = await customerToken(customerUid ?? "");
      await sendToTokens(custTokens, "Trip chat", preview, data, `tripchat:customer:${event.params.messageId}`);
    }
    if (senderRole !== "driver" && driverId) {
      const driverTokens = await driverToken(fleetId, driverId);
      await sendToTokens(driverTokens, "Trip chat", preview, data, `tripchat:driver:${event.params.messageId}`);
    }
    if (senderRole !== "dispatcher") {
      const dispTokens = await fleetDispatcherTokens(fleetId);
      await sendToTokens(dispTokens, "Trip chat", preview, data, `tripchat:dispatcher:${event.params.messageId}`);
    }
  },
);

/** Server-side trip rollup when payment becomes visible to dispatcher. */
export const onPaymentAccepted = onDocumentUpdated(
  "fleets/{fleetId}/payments/{paymentId}",
  async (event) => {
    const before = event.data?.before;
    const after = event.data?.after;
    if (!before || !after) return;
    if (before.get("visibleToDispatcher") === true) return;
    if (after.get("visibleToDispatcher") !== true) return;

    const fleetId = event.params.fleetId;
    const amount = (after.get("amount") as number | undefined) ?? 0;
    const deliveryId = after.get("deliveryId") as string | undefined;
    const driverId = after.get("driverId") as string | undefined;

    const fleetRef = db.collection("fleets").doc(fleetId);
    await fleetRef.update({
      tripCount: admin.firestore.FieldValue.increment(1),
      totalRevenue: admin.firestore.FieldValue.increment(amount),
    });

    let pointCount = 0;
    if (deliveryId) {
      const trail = await fleetRef.collection("locationTrail").where("deliveryId", "==", deliveryId).get();
      pointCount = trail.size;
    }
    await fleetRef.collection("trips").add({
      deliveryId: deliveryId ?? null,
      driverId: driverId ?? null,
      paymentId: event.params.paymentId,
      cost: amount,
      pointCount,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      source: "cloud_function",
    });
    logger.info("Payment rollup complete", { fleetId, amount, deliveryId });
  },
);

/** Every minute: auto-assign nearest driver for timed-out dispatcher_review requests. */
export const assignNearestDriverScheduled = onSchedule("every 1 minutes", async () => {
  const now = Date.now();
  const fleets = await db.collection("fleets").limit(50).get();
  for (const fleet of fleets.docs) {
    const requests = await fleet.ref
      .collection("deliveryRequests")
      .where("status", "==", STATUS.DISPATCHER_REVIEW)
      .limit(20)
      .get();
    for (const req of requests.docs) {
      const timeoutAt = (req.get("dispatcherTimeoutAt") as number | undefined) ?? 0;
      if (timeoutAt > 0 && now >= timeoutAt) {
        await assignNearestDriver(fleet.id, req.id);
      }
    }
  }
});

export const purgeOldLocationTrail = onSchedule("every 24 hours", async () => {
  const cutoff = Date.now() - 14 * 24 * 60 * 60 * 1000;
  const fleets = await db.collection("fleets").limit(50).get();
  for (const fleet of fleets.docs) {
    const old = await fleet.ref.collection("locationTrail").where("ts", "<", cutoff).limit(400).get();
    const batch = db.batch();
    old.docs.forEach((d) => batch.delete(d.ref));
    if (!old.empty) await batch.commit();
  }
});
