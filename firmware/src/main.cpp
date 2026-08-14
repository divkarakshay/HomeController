#include <Arduino.h>
#include <NimBLEDevice.h>
#include <Preferences.h>
#include <Update.h>

#include "config.h"

#define SERVICE_UUID     "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define WRITE_CHAR_UUID  "4fafc202-1fb5-459e-8fcc-c5c9c331914b"
#define STATUS_CHAR_UUID "4fafc203-1fb5-459e-8fcc-c5c9c331914b"

// ---- Protocol codes (kept in sync with the Android app) ----
// No authentication: any connected phone can control the relays.
// Command packets written to WRITE_CHAR:
//   [0x01, channel, 0x00/0x01]      -> set single relay
//   [0x02]                          -> request all states
//   [0x03, mode]                    -> set power-restore mode (0=last state, 1=all off)
//   [0x04]                          -> request power-restore mode
//   [0x05, ch, minHi, minLo, on]    -> start (on=1) or cancel (on=0) an off-timer
//   [0x06, b0..b3 LSB first]        -> sync wall-clock epoch seconds (for later uses)
//   [0x07, sz0..sz3 LE]             -> begin OTA upload (target app slot size)
//   [0x08, seq0, seq1, data...]     -> OTA chunk (16 data bytes, seq from 0)
//   [0x09, crc0..crc3 LE]           -> finish OTA, verify CRC-32, reboot on success
//   [0x0A, level]                   -> set fan speed level 1-6 (6 = boost)
//   [0x0B]                          -> request current fan speed level
// Response packets notified on STATUS_CHAR:
//   [0xA2, channel, state]          -> set command applied
//   [0xA4, s0..s7]                  -> get-all response (also pushed on subscribe)
//   [0xA5, channel, state]          -> manual switch changed the relay
//   [0xA6, mode]                    -> power-restore mode
//   [0xA7, ch, minHi, minLo, on]    -> timer accepted / cancelled
//   [0xA8, sz0..sz3]                -> OTA upload started
//   [0xA9, seq0, seq1]              -> OTA chunk acked (every 16th chunk, and seq 0)
//   [0xAA]                          -> OTA finished, rebooting
//   [0xAB]                          -> OTA CRC mismatch, no reboot
//   [0xAC, seq0, seq1]              -> OTA gap: resend from this seq
//   [0xAD, seq0, seq1]              -> OTA chunk acked (seq 0, every 16th, last)
//   [0xAE, level]                   -> fan speed level (echo when cycling completes / on request)
//   [0xAF, 0/1]                     -> fan speed cycling busy state changed
static const uint8_t CMD_SET       = 0x01;
static const uint8_t CMD_GET       = 0x02;
static const uint8_t CMD_SET_MODE  = 0x03;
static const uint8_t CMD_GET_MODE  = 0x04;
static const uint8_t CMD_SET_TIMER = 0x05;
static const uint8_t CMD_SET_EPOCH = 0x06;
static const uint8_t CMD_OTA_BEGIN = 0x07;
static const uint8_t CMD_OTA_WRITE = 0x08;
static const uint8_t CMD_OTA_END   = 0x09;
static const uint8_t CMD_SET_FAN_SPEED = 0x0A;
static const uint8_t CMD_GET_FAN_SPEED = 0x0B;
static const uint8_t RSP_SET_OK    = 0xA2;
static const uint8_t RSP_GET_ALL   = 0xA4;
static const uint8_t RSP_STATE_CHANGED = 0xA5;
static const uint8_t RSP_MODE_OK   = 0xA6;
static const uint8_t RSP_TIMER_OK  = 0xA7;
static const uint8_t RSP_OTA_BEGIN = 0xA8;
static const uint8_t RSP_OTA_WRITE = 0xA9;
static const uint8_t RSP_OTA_END   = 0xAA;
static const uint8_t RSP_OTA_CRC_ERR = 0xAB;
static const uint8_t RSP_OTA_RESEND = 0xAC;
static const uint8_t RSP_OTA_ACK   = 0xAD;
static const uint8_t RSP_FAN_SPEED = 0xAE;
static const uint8_t RSP_FAN_BUSY  = 0xAF;
static const uint8_t RSP_ERR_CMD   = 0xE1;
static const uint8_t RSP_ERR_CH    = 0xE2;

static const uint8_t RESTORE_LAST   = 0;
static const uint8_t RESTORE_ALL_OFF = 1;
static const char FW_VERSION[] = "v2.1-fan";

enum FanPhase {
  FAN_IDLE = 0,
  FAN_PHASE_BOOT,   // fan just powered on, spinning up to its memory speed
  FAN_PHASE_OFF,    // relay OFF pulse in progress
  FAN_PHASE_ON      // relay ON, fan registering the new level before the next pulse
};

static uint8_t g_states[RELAY_COUNT] = {0};
static BLECharacteristic* g_statusChar = nullptr;

static bool g_switchStable[RELAY_COUNT] = {false};
static bool g_switchPending[RELAY_COUNT] = {false};
static uint32_t g_switchSince[RELAY_COUNT] = {0};

// Off-timers (RAM only, not persisted across reboots): 0 = inactive,
// otherwise millis() deadline when the relay must turn off.
static uint32_t g_timerEnd[RELAY_COUNT] = {0};

static uint32_t g_epoch = 0;

// --- BLE OTA state ---
static const uint16_t OTA_CHUNK_SIZE = 16;
static const uint32_t OTA_STALL_TIMEOUT_MS = 20000;
static bool g_otaActive = false;
static uint32_t g_otaExpected = 0;
static uint16_t g_otaSeq = 0;
static uint32_t g_otaCrc = 0;
static uint32_t g_otaLastChunkMs = 0;
static uint32_t g_otaLastResendMs = 0;

static uint32_t crc32Update(uint32_t crc, const uint8_t* data, size_t len) {
  for (size_t i = 0; i < len; i++) {
    crc ^= data[i];
    for (int b = 0; b < 8; b++) {
      crc = (crc >> 1) ^ (0xEDB88320UL & -(crc & 1));
    }
  }
  return crc;
}

static uint32_t crc32Finish(uint32_t crc) { return crc ^ 0xFFFFFFFFUL; }

static Preferences g_prefs;
static const char NVS_NAMESPACE[] = "relay";
static const char NVS_KEY[] = "states";
static const char NVS_MODE_KEY[] = "restore";
static const char NVS_FAN_KEY[] = "fanlevel";

// --- Atomberg fan speed state ---
// g_fanLevel is the last level this controller commanded (1-5 plain, 6 boost).
// g_fanTarget/0 = no cycling in progress; g_fanPendingTarget queues a new
// request made while a cycle is still running.
static uint8_t g_fanLevel = FAN_LEVEL_MIN;
static uint8_t g_fanTarget = 0;
static uint8_t g_fanPendingTarget = 0;
static uint8_t g_fanStep = 0;
static uint8_t g_fanPhase = FAN_IDLE;
static uint32_t g_fanPhaseSince = 0;

void fanSetRelay(bool on);
void fanNotifySpeed();
void fanNotifyBusy(bool busy);
void fanStartCycling(uint8_t target);
void fanAbort();
void fanTick();

void setRelay(uint8_t ch, bool on) {
  if (ch >= RELAY_COUNT) return;
  g_states[ch] = on ? 1 : 0;
  bool level = RELAY_ACTIVE_HIGH ? on : !on;
  digitalWrite(RELAY_PINS[ch], level ? HIGH : LOW);
}

uint8_t statesBitmap() {
  uint8_t b = 0;
  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    if (g_states[i]) b |= (1 << i);
  }
  return b;
}

void saveStatesToNvs() {
  g_prefs.putUChar(NVS_KEY, statesBitmap());
}

void restoreStatesFromNvs() {
  uint8_t saved = g_prefs.getUChar(NVS_KEY, 0);
  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    setRelay(i, saved & (1 << i));
  }
  Serial.printf("[NVS] restored states 0x%02X\n", saved);
}

void notify(const uint8_t* data, size_t len) {
  if (g_statusChar && g_statusChar->getSubscribedCount() > 0) {
    g_statusChar->setValue(data, len);
    g_statusChar->notify();
  }
}

void respondStates(uint8_t code, uint8_t* out, size_t& len) {
  out[0] = code;
  memcpy(out + 1, g_states, RELAY_COUNT);
  len = 1 + RELAY_COUNT;
}

bool readSwitch(uint8_t ch) {
  return digitalRead(SWITCH_PINS[ch]) == LOW;
}

void handleSwitches() {
  static uint32_t lastPoll = 0;
  uint32_t now = millis();
  if (now - lastPoll < 10) return;
  lastPoll = now;

  for (uint8_t ch = 0; ch < RELAY_COUNT; ch++) {
    bool raw = readSwitch(ch);
    if (raw != g_switchPending[ch]) {
      g_switchPending[ch] = raw;
      g_switchSince[ch] = now;
    } else if (raw != g_switchStable[ch] && now - g_switchSince[ch] >= SWITCH_DEBOUNCE_MS) {
      g_switchStable[ch] = raw;
      if (ch == RELAY_CH_FAN) fanAbort();  // manual override stops any speed cycle
      setRelay(ch, raw);
      saveStatesToNvs();
      uint8_t out[3] = {RSP_STATE_CHANGED, ch, g_states[ch]};
      notify(out, 3);
      Serial.printf("[SWITCH] ch%u -> %s\n", ch, raw ? "ON" : "OFF");
    }
  }
}

void handleTimers() {
  uint32_t now = millis();
  for (uint8_t ch = 0; ch < RELAY_COUNT; ch++) {
    if (g_timerEnd[ch] == 0) continue;
    if ((int32_t)(g_timerEnd[ch] - now) > 0) continue;
    g_timerEnd[ch] = 0;
    if (g_states[ch]) {
      if (ch == RELAY_CH_FAN) fanAbort();
      setRelay(ch, false);
      saveStatesToNvs();
      uint8_t out[3] = {RSP_STATE_CHANGED, ch, 0};
      notify(out, 3);
      Serial.printf("[TIMER] ch%u -> OFF\n", ch);
    }
  }
}

void startTimer(uint8_t ch, uint16_t minutes) {
  if (ch >= RELAY_COUNT || minutes == 0) {
    g_timerEnd[ch] = 0;
    return;
  }
  g_timerEnd[ch] = millis() + (uint32_t)minutes * 60000UL;
}

bool timerActive(uint8_t ch) {
  return ch < RELAY_COUNT && g_timerEnd[ch] != 0;
}

uint16_t timerMinutesLeft(uint8_t ch) {
  if (!timerActive(ch)) return 0;
  int32_t ms = (int32_t)(g_timerEnd[ch] - millis());
  return ms > 0 ? (uint16_t)((ms + 59999) / 60000) : 0;
}

// ---- Atomberg-style fan speed cycling ----
// The fan runs off the channel-0 relay, so the only speed control it knows is
// the wall-switch pattern: OFF ~1 s, then ON; each OFF-ON cycle steps the fan
// up one level. At max speed the pattern loops back to Speed 1. This mirrors
// the manual procedure exactly, with millisecond timing the phone can't match.
// The sequence is driven from loop() so BLE callbacks keep working.

void fanSetRelay(bool on) {
  setRelay(RELAY_CH_FAN, on);
  saveStatesToNvs();
  uint8_t out[3] = {RSP_STATE_CHANGED, RELAY_CH_FAN, g_states[RELAY_CH_FAN]};
  notify(out, 3);
  Serial.printf("[FAN] relay %s\n", on ? "ON" : "OFF");
}

void fanNotifySpeed() {
  uint8_t out[2] = {RSP_FAN_SPEED, g_fanLevel};
  notify(out, 2);
}

void fanNotifyBusy(bool busy) {
  uint8_t out[2] = {RSP_FAN_BUSY, busy ? (uint8_t)1 : (uint8_t)0};
  notify(out, 2);
}

// Number of OFF-ON cycles to go from level a to level b (Boost wraps to 1).
uint8_t fanStepsBetween(uint8_t from, uint8_t to) {
  return (uint8_t)((to + FAN_LEVEL_MAX - from) % FAN_LEVEL_MAX);
}

void fanStartCycling(uint8_t target) {
  if (target < FAN_LEVEL_MIN) target = FAN_LEVEL_MIN;
  if (target > FAN_LEVEL_MAX) target = FAN_LEVEL_MAX;

  if (g_fanTarget != 0) {
    // A cycle is already running: remember the newest desired level and let
    // the running cycle finish before starting the next one.
    g_fanPendingTarget = target;
    fanNotifyBusy(true);
    return;
  }
  if (target == g_fanLevel) {
    // Already there: just make sure the fan is powered on.
    if (!g_states[RELAY_CH_FAN]) fanSetRelay(true);
    fanNotifyBusy(false);
    fanNotifySpeed();
    return;
  }

  g_fanStep = fanStepsBetween(g_fanLevel, target);
  g_fanTarget = target;
  g_fanPhaseSince = millis();
  if (g_states[RELAY_CH_FAN]) {
    g_fanPhase = FAN_PHASE_OFF;
    fanSetRelay(false);
  } else {
    g_fanPhase = FAN_PHASE_BOOT;
    fanSetRelay(true);
  }
  fanNotifyBusy(true);
  Serial.printf("[FAN] cycling %u -> %u (%u steps)\n",
                g_fanLevel, g_fanTarget, g_fanStep);
}

void fanAbort() {
  if (g_fanTarget == 0) return;
  g_fanTarget = 0;
  g_fanPendingTarget = 0;
  g_fanPhase = FAN_IDLE;
  fanNotifyBusy(false);
  Serial.println("[FAN] cycling aborted");
}

void fanTick() {
  if (g_fanTarget == 0) return;
  uint32_t now = millis();
  uint32_t elapsed = now - g_fanPhaseSince;

  switch (g_fanPhase) {
    case FAN_PHASE_BOOT:
      // Power was just applied; wait for the fan to reach its memory speed
      // before the first OFF pulse.
      if (elapsed < FAN_BOOT_SETTLE_MS) break;
      g_fanPhase = FAN_PHASE_OFF;
      g_fanPhaseSince = now;
      fanSetRelay(false);
      break;

    case FAN_PHASE_OFF:
      if (elapsed < FAN_OFF_PULSE_MS) break;
      g_fanPhase = FAN_PHASE_ON;
      g_fanPhaseSince = now;
      fanSetRelay(true);  // power back on = the fan steps up one level
      break;

    case FAN_PHASE_ON:
      if (elapsed < FAN_ON_SETTLE_MS) break;
      g_fanStep--;
      if (g_fanStep == 0) {
        g_fanLevel = g_fanTarget;
        g_prefs.putUChar(NVS_FAN_KEY, g_fanLevel);
        g_fanTarget = 0;
        g_fanPhase = FAN_IDLE;
        fanNotifyBusy(false);
        fanNotifySpeed();
        Serial.printf("[FAN] level is now %u\n", g_fanLevel);
        if (g_fanPendingTarget != 0) {
          uint8_t next = g_fanPendingTarget;
          g_fanPendingTarget = 0;
          fanStartCycling(next);
        }
      } else {
        g_fanPhase = FAN_PHASE_OFF;
        g_fanPhaseSince = now;
        fanSetRelay(false);
      }
      break;

    default:
      break;
  }
}

static uint16_t g_lastConnHandle = BLE_HS_CONN_HANDLE_NONE;

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* server, ble_gap_conn_desc* desc) override {
    if (desc->conn_handle == g_lastConnHandle) return;
    g_lastConnHandle = desc->conn_handle;
    Serial.printf("[BLE] client connected (%u)\n", server->getConnectedCount());
    NimBLEDevice::startAdvertising();
  }

  void onDisconnect(NimBLEServer* server, ble_gap_conn_desc* desc) override {
    g_lastConnHandle = BLE_HS_CONN_HANDLE_NONE;
    if (g_otaActive) {
      g_otaActive = false;
      Update.abort();
      Serial.println("[OTA] aborted: client disconnected");
    }
    Serial.printf("[BLE] client disconnected (%u left)\n", server->getConnectedCount());
    NimBLEDevice::startAdvertising();
  }
};

class StatusCallbacks : public NimBLECharacteristicCallbacks {
  void onSubscribe(NimBLECharacteristic* chr, ble_gap_conn_desc* desc, uint16_t subValue) override {
    if (subValue & 0x0001) {
      uint8_t out[1 + RELAY_COUNT];
      size_t len;
      respondStates(RSP_GET_ALL, out, len);
      notify(out, len);
      Serial.println("[BLE] pushed states to new subscriber");
    }
  }
};

class WriteCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* chr, ble_gap_conn_desc* desc) override {
    std::string value = chr->getValue();
    if (value.size() < 1) return;

    const uint8_t* d = (const uint8_t*)value.data();
    size_t n = value.size();
    uint8_t out[1 + RELAY_COUNT] = {0};
    size_t outLen = 0;

    // Throttle console chatter: only log control commands and rare events.
    // Per-chunk logging at 115200 baud starves the BLE host task and drops
    // packets, so chunk writes are silent (progress prints every 128).
    if (d[0] != CMD_OTA_WRITE) {
      Serial.printf("[BLE] write: %02X len=%u\n", d[0], (unsigned)n);
    }

    switch (d[0]) {
      case CMD_SET:
        if (n != 3 || d[1] >= RELAY_COUNT) { out[0] = RSP_ERR_CH; outLen = 1; break; }
        if (d[1] == RELAY_CH_FAN) fanAbort();  // explicit relay control wins over cycling
        setRelay(d[1], d[2] != 0);
        saveStatesToNvs();
        out[0] = RSP_SET_OK;
        out[1] = d[1];
        out[2] = g_states[d[1]];
        outLen = 3;
        {
          uint8_t stateChanged[3] = {RSP_STATE_CHANGED, d[1], g_states[d[1]]};
          notify(stateChanged, 3);
        }
        break;

      case CMD_GET:
        respondStates(RSP_GET_ALL, out, outLen);
        break;

      case CMD_SET_MODE:
        if (n != 2 || d[1] > RESTORE_ALL_OFF) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
        g_prefs.putUChar(NVS_MODE_KEY, d[1]);
        out[0] = RSP_MODE_OK;
        out[1] = d[1];
        outLen = 2;
        Serial.printf("[NVS] restore mode = %s\n", d[1] == RESTORE_ALL_OFF ? "all off" : "last state");
        break;

      case CMD_GET_MODE:
        out[0] = RSP_MODE_OK;
        out[1] = g_prefs.getUChar(NVS_MODE_KEY, RESTORE_LAST);
        outLen = 2;
        break;

      case CMD_SET_TIMER:
        if (n != 5 || d[1] >= RELAY_COUNT || d[4] > 1) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
        {
          uint16_t minutes = (uint16_t)(d[2] << 8) | d[3];
          if (d[4] == 1) {
            if (minutes == 0) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
            startTimer(d[1], minutes);
          } else {
            g_timerEnd[d[1]] = 0;
          }
          out[0] = RSP_TIMER_OK;
          out[1] = d[1];
          out[2] = d[2];
          out[3] = d[3];
          out[4] = d[4];
          outLen = 5;
          Serial.printf("[TIMER] ch%u %s (min=%u)\n", d[1], d[4] ? "started" : "cancelled", minutes);
        }
        break;

      case CMD_SET_EPOCH:
        if (n != 5) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
        g_epoch = (uint32_t)d[1] | ((uint32_t)d[2] << 8) | ((uint32_t)d[3] << 16) | ((uint32_t)d[4] << 24);
        g_prefs.putUInt("epoch", g_epoch);
        out[0] = RSP_SET_OK;
        out[1] = 0xFF;
        out[2] = 0;
        outLen = 3;
        Serial.printf("[TIME] epoch synced to %lu\n", (unsigned long)g_epoch);
        break;

      case CMD_SET_FAN_SPEED:
        if (n != 2 || d[1] < FAN_LEVEL_MIN || d[1] > FAN_LEVEL_MAX) {
          out[0] = RSP_ERR_CMD; outLen = 1; break;
        }
        fanStartCycling(d[1]);
        out[0] = RSP_FAN_BUSY;
        out[1] = g_fanTarget != 0 ? (uint8_t)1 : (uint8_t)0;
        outLen = 2;
        break;

      case CMD_GET_FAN_SPEED:
        out[0] = RSP_FAN_SPEED;
        out[1] = g_fanLevel;
        outLen = 2;
        break;

      case CMD_OTA_BEGIN:
        if (n != 5 || g_otaActive) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
        {
          uint32_t size = (uint32_t)d[1] | ((uint32_t)d[2] << 8) |
                          ((uint32_t)d[3] << 16) | ((uint32_t)d[4] << 24);
          if (size < 0x1000 || size > 0x1C0000) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
          if (!Update.begin(size)) {
            Serial.printf("[OTA] begin FAILED: %s\n", Update.errorString());
            out[0] = RSP_ERR_CMD; outLen = 1; break;
          }
          g_otaActive = true;
          g_otaExpected = size;
          g_otaSeq = 0;
          g_otaCrc = 0xFFFFFFFFUL;
          g_otaLastChunkMs = millis();
          out[0] = RSP_OTA_BEGIN;
          memcpy(out + 1, d + 1, 4);
          outLen = 5;
          Serial.printf("[OTA] begin size=%lu\n", (unsigned long)size);
        }
        break;

      case CMD_OTA_WRITE:
        if (!g_otaActive || n < 4) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
        {
          uint16_t seq = (uint16_t)(d[1] << 8) | d[2];
          if (seq != g_otaSeq) {
            // Out-of-order or duplicate: do not abort, ask the phone to
            // resend from where we are. In-flight duplicates are ignored.
            // At most one resend notice per 50 ms so the phone can catch up.
            if (seq > g_otaSeq && millis() - g_otaLastResendMs > 50) {
              g_otaLastResendMs = millis();
              out[0] = RSP_OTA_RESEND;
              out[1] = g_otaSeq >> 8;
              out[2] = g_otaSeq & 0xFF;
              outLen = 3;
              notify(out, outLen);
            }
            break;
          }
          size_t chunk = n - 3;
          if (chunk > OTA_CHUNK_SIZE) chunk = OTA_CHUNK_SIZE;
          if (Update.write((uint8_t*)d + 3, chunk) != chunk) {
            g_otaActive = false;
            Update.abort();
            out[0] = RSP_ERR_CMD;
            outLen = 1;
            Serial.println("[OTA] aborted: flash write failed");
            break;
          }
          g_otaCrc = crc32Update(g_otaCrc, d + 3, chunk);
          uint32_t written = (uint32_t)g_otaSeq * OTA_CHUNK_SIZE + chunk;
          if (written > g_otaExpected) written = g_otaExpected;
          g_otaSeq++;
          g_otaLastChunkMs = millis();
          // Ack the first chunk, then every 16th, and the final one so the
          // phone can pace itself and know when the stream is complete.
          if (written == g_otaExpected ||
              g_otaSeq == 1 || g_otaSeq % 16 == 0) {
            out[0] = RSP_OTA_ACK;
            out[1] = (g_otaSeq - 1) >> 8;
            out[2] = (g_otaSeq - 1) & 0xFF;
            outLen = 3;
            notify(out, outLen);
          }
          if (g_otaSeq % 128 == 0) {
            Serial.printf("[OTA] %lu/%lu bytes\n",
                          (unsigned long)written, (unsigned long)g_otaExpected);
          }
        }
        break;

      case CMD_OTA_END:
        if (!g_otaActive || n != 5) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
        {
          uint32_t sentCrc = (uint32_t)d[1] | ((uint32_t)d[2] << 8) |
                             ((uint32_t)d[3] << 16) | ((uint32_t)d[4] << 24);
          uint32_t calcCrc = crc32Finish(g_otaCrc);
          g_otaActive = false;
          if (sentCrc != calcCrc || !Update.end()) {
            Update.abort();
            out[0] = RSP_OTA_CRC_ERR;
            outLen = 1;
            Serial.printf("[OTA] CRC mismatch: sent %08X calc %08X\n", sentCrc, calcCrc);
            break;
          }
          out[0] = RSP_OTA_END;
          outLen = 1;
          Serial.println("[OTA] success, rebooting...");
          notify(out, outLen);
          delay(300);
          ESP.restart();
        }
        break;

      default:
        out[0] = RSP_ERR_CMD;
        outLen = 1;
        break;
    }

    notify(out, outLen);
  }
};

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("[BLE] HomeController starting");
  Serial.printf("[FW] version %s\n", FW_VERSION);

  g_prefs.begin(NVS_NAMESPACE, false);

  // The fan remembers its speed internally; mirror that in NVS so the app's
  // dial shows the right level after a power cut.
  g_fanLevel = g_prefs.getUChar(NVS_FAN_KEY, FAN_LEVEL_MIN);
  if (g_fanLevel < FAN_LEVEL_MIN || g_fanLevel > FAN_LEVEL_MAX) {
    g_fanLevel = FAN_LEVEL_MIN;
  }
  Serial.printf("[FAN] saved level %u\n", g_fanLevel);

  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    pinMode(RELAY_PINS[i], OUTPUT);
    setRelay(i, false);
  }

  // Restore the last saved (app or switch) states from flash memory —
  // unless the configured mode is "all off" after a power restore.
  uint8_t restoreMode = g_prefs.getUChar(NVS_MODE_KEY, RESTORE_LAST);
  Serial.printf("[NVS] restore mode: %s\n",
                restoreMode == RESTORE_ALL_OFF ? "all off" : "last state");
  if (restoreMode == RESTORE_LAST) {
    restoreStatesFromNvs();
  }

  // A switch that is physically ON always wins over the saved state.
  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    pinMode(SWITCH_PINS[i], INPUT_PULLUP);
    g_switchStable[i] = readSwitch(i);
    g_switchPending[i] = g_switchStable[i];
    if (g_switchStable[i]) setRelay(i, true);
  }
  saveStatesToNvs();
  Serial.println("[SWITCH] manual switches initialized");

  NimBLEDevice::init(BLE_DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P7);

  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  NimBLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* writeChar =
      service->createCharacteristic(WRITE_CHAR_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  writeChar->setCallbacks(new WriteCallbacks());

  g_statusChar =
      service->createCharacteristic(STATUS_CHAR_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  g_statusChar->setCallbacks(new StatusCallbacks());

  service->start();

  NimBLEAdvertisementData adv;
  adv.setName(BLE_DEVICE_NAME);
  adv.setCompleteServices(NimBLEUUID(SERVICE_UUID));
  NimBLEDevice::getAdvertising()->setAdvertisementData(adv);
  NimBLEDevice::startAdvertising();

  Serial.println("[BLE] advertising as " BLE_DEVICE_NAME);
  Serial.printf("[OTA] ready (app slot max 0x1C0000 bytes)\n");
}

void loop() {
  handleSwitches();
  handleTimers();
  fanTick();
  if (g_otaActive && millis() - g_otaLastChunkMs > OTA_STALL_TIMEOUT_MS) {
    g_otaActive = false;
    Update.abort();
    Serial.println("[OTA] stale upload aborted (no chunks for 20s)");
  }
  delay(10);
}