#include <Arduino.h>
#include <NimBLEDevice.h>

#include "config.h"

#define SERVICE_UUID     "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define WRITE_CHAR_UUID  "4fafc202-1fb5-459e-8fcc-c5c9c331914b"
#define STATUS_CHAR_UUID "4fafc203-1fb5-459e-8fcc-c5c9c331914b"

// ---- Protocol codes (kept in sync with the Android app) ----
// Command packets written to WRITE_CHAR:
//   [0xFF, pinHi, pinLo]            -> authenticate
//   [0x01, channel, 0x00/0x01]      -> set single relay
//   [0x02]                          -> request all states
// Response packets notified on STATUS_CHAR:
//   [0xA1, s0..s7]                  -> auth OK + current states
//   [0xA2, channel, state]          -> set command applied
//   [0xA4, s0..s7]                  -> get-all response
//   [0xE3]                          -> not authenticated
//   [0xE4]                          -> wrong PIN
static const uint8_t CMD_AUTH      = 0xFF;
static const uint8_t CMD_SET       = 0x01;
static const uint8_t CMD_GET       = 0x02;
static const uint8_t RSP_AUTH_OK   = 0xA1;
static const uint8_t RSP_SET_OK    = 0xA2;
static const uint8_t RSP_GET_ALL   = 0xA4;
static const uint8_t RSP_ERR_CMD   = 0xE1;
static const uint8_t RSP_ERR_CH    = 0xE2;
static const uint8_t RSP_ERR_UAUTH = 0xE3;
static const uint8_t RSP_ERR_PIN   = 0xE4;

static bool g_authed = false;
static uint8_t g_states[RELAY_COUNT] = {0};
static BLECharacteristic* g_statusChar = nullptr;

void setRelay(uint8_t ch, bool on) {
  if (ch >= RELAY_COUNT) return;
  g_states[ch] = on ? 1 : 0;
  bool level = RELAY_ACTIVE_HIGH ? on : !on;
  digitalWrite(RELAY_PINS[ch], level ? HIGH : LOW);
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

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* server, ble_gap_conn_desc* desc) override {
    g_authed = false;
    Serial.println("[BLE] connected, waiting for PIN");
    NimBLEDevice::startAdvertising();
  }

  void onDisconnect(NimBLEServer* server, ble_gap_conn_desc* desc) override {
    g_authed = false;
    Serial.println("[BLE] disconnected, advertising again");
    NimBLEDevice::startAdvertising();
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
      case CMD_AUTH:
        if (n != 3) { out[0] = RSP_ERR_CMD; outLen = 1; break; }
        if (((uint16_t)d[1] << 8) | d[2] == BLE_PIN) {
          g_authed = true;
          respondStates(RSP_AUTH_OK, out, outLen);
          Serial.println("[BLE] auth OK");
        } else {
          out[0] = RSP_ERR_PIN;
          outLen = 1;
          Serial.println("[BLE] wrong PIN");
        }
        break;

      case CMD_SET:
        if (!g_authed) { out[0] = RSP_ERR_UAUTH; outLen = 1; break; }
        if (n != 3 || d[1] >= RELAY_COUNT) { out[0] = RSP_ERR_CH; outLen = 1; break; }
        setRelay(d[1], d[2] != 0);
        out[0] = RSP_SET_OK;
        out[1] = d[1];
        out[2] = g_states[d[1]];
        outLen = 3;
        break;

      case CMD_GET:
        if (!g_authed) { out[0] = RSP_ERR_UAUTH; outLen = 1; break; }
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

  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    pinMode(RELAY_PINS[i], OUTPUT);
    setRelay(i, false);
  }

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

  service->start();

  NimBLEAdvertisementData adv;
  adv.setName(BLE_DEVICE_NAME);
  adv.setCompleteServices(NimBLEUUID(SERVICE_UUID));
  NimBLEDevice::getAdvertising()->setAdvertisementData(adv);
  NimBLEDevice::startAdvertising();

  Serial.println("[BLE] advertising as " BLE_DEVICE_NAME);
}

void loop() {
  delay(100);
}