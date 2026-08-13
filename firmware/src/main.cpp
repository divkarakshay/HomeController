#include <Arduino.h>
#include <NimBLEDevice.h>
#include <Preferences.h>

#include "config.h"

#define SERVICE_UUID     "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define WRITE_CHAR_UUID  "4fafc202-1fb5-459e-8fcc-c5c9c331914b"
#define STATUS_CHAR_UUID "4fafc203-1fb5-459e-8fcc-c5c9c331914b"

// ---- Protocol codes (kept in sync with the Android app) ----
// No authentication: any connected phone can control the relays.
// Command packets written to WRITE_CHAR:
//   [0x01, channel, 0x00/0x01]      -> set single relay
//   [0x02]                          -> request all states
// Response packets notified on STATUS_CHAR:
//   [0xA2, channel, state]          -> set command applied
//   [0xA4, s0..s7]                  -> get-all response (also pushed on subscribe)
//   [0xA5, channel, state]          -> manual switch changed the relay
static const uint8_t CMD_SET       = 0x01;
static const uint8_t CMD_GET       = 0x02;
static const uint8_t RSP_SET_OK    = 0xA2;
static const uint8_t RSP_GET_ALL   = 0xA4;
static const uint8_t RSP_STATE_CHANGED = 0xA5;
static const uint8_t RSP_ERR_CMD   = 0xE1;
static const uint8_t RSP_ERR_CH    = 0xE2;

static uint8_t g_states[RELAY_COUNT] = {0};
static BLECharacteristic* g_statusChar = nullptr;

static bool g_switchStable[RELAY_COUNT] = {false};
static bool g_switchPending[RELAY_COUNT] = {false};
static uint32_t g_switchSince[RELAY_COUNT] = {0};

static Preferences g_prefs;
static const char NVS_NAMESPACE[] = "relay";
static const char NVS_KEY[] = "states";

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
      setRelay(ch, raw);
      saveStatesToNvs();
      uint8_t out[3] = {RSP_STATE_CHANGED, ch, g_states[ch]};
      notify(out, 3);
      Serial.printf("[SWITCH] ch%u -> %s\n", ch, raw ? "ON" : "OFF");
    }
  }
}

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* server, ble_gap_conn_desc* desc) override {
    Serial.printf("[BLE] client connected (%u)\n", server->getConnectedCount());
    NimBLEDevice::startAdvertising();
  }

  void onDisconnect(NimBLEServer* server, ble_gap_conn_desc* desc) override {
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

    switch (d[0]) {
      case CMD_SET:
        if (n != 3 || d[1] >= RELAY_COUNT) { out[0] = RSP_ERR_CH; outLen = 1; break; }
        setRelay(d[1], d[2] != 0);
        saveStatesToNvs();
        out[0] = RSP_SET_OK;
        out[1] = d[1];
        out[2] = g_states[d[1]];
        outLen = 3;
        break;

      case CMD_GET:
        respondStates(RSP_GET_ALL, out, outLen);
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

  g_prefs.begin(NVS_NAMESPACE, false);

  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    pinMode(RELAY_PINS[i], OUTPUT);
    setRelay(i, false);
  }

  // Restore the last saved (app or switch) states from flash memory.
  restoreStatesFromNvs();

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
}

void loop() {
  handleSwitches();
  delay(10);
}