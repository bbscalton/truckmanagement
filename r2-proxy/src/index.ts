type Env = {
  MEDIA_BUCKET: R2Bucket;
  DB: D1Database;
  EDGE_CACHE: KVNamespace;
  FIREBASE_PROJECT_ID?: string;
  FIREBASE_API_KEY?: string;
  FIREBASE_AUTH_DOMAIN?: string;
  WENT_DARK_AFTER_MS?: string;
};

type Status = "ok" | "warn" | "fail";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,HEAD,PUT,POST,DELETE,OPTIONS",
      "access-control-allow-headers": "content-type,authorization,x-fleet-id",
      "cache-control": "no-store",
    },
  });
}

function wentDarkMs(env: Env): number {
  const raw = Number(env.WENT_DARK_AFTER_MS ?? 300000);
  return Number.isFinite(raw) && raw > 0 ? raw : 300000;
}

async function probeFirebase(env: Env): Promise<{ status: Status; message: string; latencyMs: number | null }> {
  const projectId = env.FIREBASE_PROJECT_ID?.trim();
  const apiKey = env.FIREBASE_API_KEY?.trim();
  const authDomain = env.FIREBASE_AUTH_DOMAIN?.trim();
  if (!projectId && !apiKey && !authDomain) {
    return { status: "warn", message: "Firebase probe vars not set on Worker.", latencyMs: null };
  }
  const started = Date.now();
  try {
    const host = authDomain || `${projectId}.firebaseapp.com`;
    const res = await fetch(`https://${host}/__/firebase/init.json`, { method: "GET" });
    const latencyMs = Date.now() - started;
    if (res.ok || res.status === 404) {
      return { status: "ok", message: `Firebase domain reachable (${host}).`, latencyMs };
    }
    return { status: "fail", message: `Firebase probe HTTP ${res.status}.`, latencyMs };
  } catch (error) {
    return {
      status: "fail",
      message: error instanceof Error ? error.message : "Firebase probe failed.",
      latencyMs: Date.now() - started,
    };
  }
}

async function probeD1(env: Env): Promise<{ status: Status; message: string; latencyMs: number }> {
  const started = Date.now();
  try {
    await env.DB.prepare("SELECT 1 AS ok").first();
    return { status: "ok", message: "D1 ops database is reachable.", latencyMs: Date.now() - started };
  } catch (error) {
    return {
      status: "fail",
      message: error instanceof Error ? error.message : "D1 probe failed.",
      latencyMs: Date.now() - started,
    };
  }
}

async function probeKv(env: Env): Promise<{ status: Status; message: string; latencyMs: number }> {
  const started = Date.now();
  try {
    const existing = await env.EDGE_CACHE.get("__health_probe");
    if (existing != null) {
      return { status: "ok", message: "KV edge cache is reachable.", latencyMs: Date.now() - started };
    }
    await env.EDGE_CACHE.put("__health_probe", "1", { expirationTtl: 86400 });
    return { status: "ok", message: "KV edge cache is reachable.", latencyMs: Date.now() - started };
  } catch (error) {
    return {
      status: "fail",
      message: error instanceof Error ? error.message : "KV probe failed.",
      latencyMs: Date.now() - started,
    };
  }
}

async function probeR2(env: Env): Promise<{ status: Status; message: string; latencyMs: number }> {
  const started = Date.now();
  try {
    await env.MEDIA_BUCKET.head("__health_probe");
    return { status: "ok", message: "R2 media bucket is reachable.", latencyMs: Date.now() - started };
  } catch {
    try {
      await env.MEDIA_BUCKET.put("__health_probe", "1");
      return { status: "ok", message: "R2 media bucket is reachable.", latencyMs: Date.now() - started };
    } catch (error) {
      return {
        status: "fail",
        message: error instanceof Error ? error.message : "R2 probe failed.",
        latencyMs: Date.now() - started,
      };
    }
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return json({ ok: true });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    if (path === "/health" || path === "/platform-health") {
      const [firebase, d1, kv, r2] = await Promise.all([
        probeFirebase(env),
        probeD1(env),
        probeKv(env),
        probeR2(env),
      ]);
      const statuses = [firebase.status, d1.status, kv.status, r2.status];
      const overall: Status = statuses.includes("fail")
        ? "fail"
        : statuses.includes("warn")
          ? "warn"
          : "ok";
      return json({
        ok: overall === "ok",
        status: overall,
        product: "TruckMgmt",
        checks: { firebase, d1, kv, r2 },
        generatedAtMs: Date.now(),
      });
    }

    if (path.startsWith("/edge/fleet/") && request.method === "GET") {
      const fleetId = path.split("/").pop()!;
      const cached = await env.EDGE_CACHE.get(`fleet:${fleetId}`, "json");
      if (cached) return json({ source: "kv", fleet: cached });

      const snap = await env.DB.prepare(
        "SELECT * FROM fleet_snapshots WHERE fleet_id = ?1",
      )
        .bind(fleetId)
        .first();
      const devices = await env.DB.prepare(
        "SELECT * FROM device_heartbeats WHERE fleet_id = ?1 ORDER BY last_heartbeat_ms DESC LIMIT 100",
      )
        .bind(fleetId)
        .all();
      return json({ source: "d1", snapshot: snap, devices: devices.results ?? [] });
    }

    if (path === "/edge/sync/device" && request.method === "POST") {
      const body = (await request.json()) as {
        fleetId?: string;
        deviceId?: string;
        driverName?: string;
        lastHeartbeatMs?: number;
        lat?: number;
        lng?: number;
        online?: boolean;
        monitoringActive?: boolean;
        batteryPercent?: number;
      };
      if (!body.fleetId || !body.deviceId) {
        return json({ error: "fleetId and deviceId required" }, 400);
      }
      const now = Date.now();
      const hb = body.lastHeartbeatMs ?? now;
      await env.DB.prepare(
        `INSERT INTO device_heartbeats (
          fleet_id, device_id, driver_name, last_heartbeat_ms, lat, lng,
          battery_percent, monitoring_active, online, updated_at_ms
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
        ON CONFLICT(fleet_id, device_id) DO UPDATE SET
          driver_name=excluded.driver_name,
          last_heartbeat_ms=excluded.last_heartbeat_ms,
          lat=excluded.lat,
          lng=excluded.lng,
          battery_percent=excluded.battery_percent,
          monitoring_active=excluded.monitoring_active,
          online=excluded.online,
          updated_at_ms=excluded.updated_at_ms`,
      )
        .bind(
          body.fleetId,
          body.deviceId,
          body.driverName ?? null,
          hb,
          body.lat ?? null,
          body.lng ?? null,
          body.batteryPercent ?? null,
          body.monitoringActive ? 1 : 0,
          body.online ? 1 : 0,
          now,
        )
        .run();

      const darkAfter = wentDarkMs(env);
      const devices = await env.DB.prepare(
        "SELECT online, last_heartbeat_ms FROM device_heartbeats WHERE fleet_id = ?1",
      )
        .bind(body.fleetId)
        .all<{ online: number; last_heartbeat_ms: number }>();
      const rows = devices.results ?? [];
      const online = rows.filter((r) => r.online && now - r.last_heartbeat_ms < darkAfter).length;
      await env.DB.prepare(
        `INSERT INTO fleet_snapshots (
          fleet_id, registered_devices, online_devices, offline_devices,
          pending_requests, active_deliveries, latest_heartbeat_ms, source, updated_at_ms
        ) VALUES (?1, ?2, ?3, ?4, 0, 0, ?5, 'edge', ?6)
        ON CONFLICT(fleet_id) DO UPDATE SET
          registered_devices=excluded.registered_devices,
          online_devices=excluded.online_devices,
          offline_devices=excluded.offline_devices,
          latest_heartbeat_ms=excluded.latest_heartbeat_ms,
          source=excluded.source,
          updated_at_ms=excluded.updated_at_ms`,
      )
        .bind(body.fleetId, rows.length, online, Math.max(0, rows.length - online), hb, now)
        .run();

      const fleetCache = { fleetId: body.fleetId, onlineDevices: online, registeredDevices: rows.length, updatedAtMs: now };
      await env.EDGE_CACHE.put(`fleet:${body.fleetId}`, JSON.stringify(fleetCache), { expirationTtl: 120 });
      return json({ ok: true, fleet: fleetCache });
    }

    if (path.startsWith("/upload/") && request.method === "PUT") {
      const key = path.replace(/^\/upload\//, "");
      await env.MEDIA_BUCKET.put(key, request.body, {
        httpMetadata: { contentType: request.headers.get("content-type") ?? "application/octet-stream" },
      });
      return json({ ok: true, key });
    }

    if (path.startsWith("/media/") && request.method === "GET") {
      const key = path.replace(/^\/media\//, "");
      const obj = await env.MEDIA_BUCKET.get(key);
      if (!obj) return json({ error: "not found" }, 404);
      const headers = new Headers();
      obj.writeHttpMetadata(headers);
      headers.set("access-control-allow-origin", "*");
      return new Response(obj.body, { headers });
    }

    if (path.startsWith("/downloads/") && request.method === "GET") {
      const key = path.replace(/^\//, "");
      const obj = await env.MEDIA_BUCKET.get(key);
      if (!obj) return json({ error: "APK not uploaded yet" }, 404);
      return new Response(obj.body, {
        headers: {
          "content-type": "application/vnd.android.package-archive",
          "content-disposition": `attachment; filename="${key.split("/").pop()}"`,
          "access-control-allow-origin": "*",
        },
      });
    }

    return json({ error: "not found", path }, 404);
  },
};
