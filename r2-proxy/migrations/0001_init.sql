-- TruckMgmt edge ops database (Cloudflare D1)

CREATE TABLE IF NOT EXISTS fleet_snapshots (
  fleet_id TEXT NOT NULL,
  registered_devices INTEGER NOT NULL DEFAULT 0,
  online_devices INTEGER NOT NULL DEFAULT 0,
  offline_devices INTEGER NOT NULL DEFAULT 0,
  pending_requests INTEGER NOT NULL DEFAULT 0,
  active_deliveries INTEGER NOT NULL DEFAULT 0,
  latest_heartbeat_ms INTEGER NOT NULL DEFAULT 0,
  source TEXT NOT NULL DEFAULT 'firebase',
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (fleet_id)
);

CREATE TABLE IF NOT EXISTS device_heartbeats (
  fleet_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
  driver_name TEXT,
  last_heartbeat_ms INTEGER NOT NULL,
  lat REAL,
  lng REAL,
  battery_percent INTEGER,
  monitoring_active INTEGER NOT NULL DEFAULT 0,
  online INTEGER NOT NULL DEFAULT 0,
  updated_at_ms INTEGER NOT NULL,
  PRIMARY KEY (fleet_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_device_heartbeats_fleet_hb
  ON device_heartbeats (fleet_id, last_heartbeat_ms DESC);

CREATE TABLE IF NOT EXISTS health_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  generated_at_ms INTEGER NOT NULL,
  ok INTEGER NOT NULL,
  r2_status TEXT,
  d1_status TEXT,
  kv_status TEXT,
  firebase_status TEXT,
  latency_ms INTEGER,
  detail_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_health_events_generated
  ON health_events (generated_at_ms DESC);
