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

async function sendToTokens(tokens: string[], title: string, body: string, data?: Record<string, string>) {
  const unique = [...new Set(tokens.filter(Boolean))];
  if (!unique.length) return;
  await admin.messaging().sendEachForMulticast({
    tokens: unique,
    notification: { title, body },
    data: data ?? {},
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

async function driverToken(fleetId: string, driverId: string): Promise<string[]> {
  const driver = await db.collection("fleets").doc(fleetId).collection("drivers").doc(driverId).get();
  const deviceId = driver.get("deviceId") as string | undefined;
  if (!deviceId) return [];
  const device = await db.collection("fleets").doc(fleetId).collection("devices").doc(deviceId).get();
  const token = device.get("fcmToken") as string | undefined;
  return token ? [token] : [];
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
  await sendToTokens(tokens, "New delivery assigned", "A nearby request was assigned to you.", {
    deliveryId: deliveryRef.id,
    fleetId,
  });
  return true;
}

/** Notify dispatcher when a customer creates a delivery request. */
export const onDeliveryRequestCreated = onDocumentCreated(
  "fleets/{fleetId}/deliveryRequests/{requestId}",
  async (event) => {
    const fleetId = event.params.fleetId;
    const requestId = event.params.requestId;
    const tokens = await fleetDispatcherTokens(fleetId);
    await sendToTokens(tokens, "New delivery request", "A customer scheduled a delivery.", {
      requestId,
      fleetId,
    });
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
      await sendToTokens(tokens, "Delivery assigned", "Open the driver app to accept.", { deliveryId, fleetId });
    }
    if (next === STATUS.ARRIVED) {
      const tokens = await fleetDispatcherTokens(fleetId);
      await sendToTokens(tokens, "Driver arrived", `Delivery ${deliveryId.slice(0, 8)} arrived.`, {
        deliveryId,
        fleetId,
      });
      if (customerUid) {
        const cust = await db.collection("customerProfiles").doc(customerUid).get();
        const t = cust.get("fcmToken") as string | undefined;
        if (t) await sendToTokens([t], "Driver arrived", "Your truck has arrived.", { deliveryId });
      }
    }
    if (next === STATUS.PAYMENT_ACCEPTED) {
      const tokens = await fleetDispatcherTokens(fleetId);
      await sendToTokens(tokens, "Payment accepted", "A trip payment was confirmed.", { deliveryId, fleetId });
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
