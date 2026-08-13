#pragma once

#include <stdint.h>

// BLE name the phone app scans for. Must match Protocol.DEVICE_NAME in the app.
#define BLE_DEVICE_NAME "HomeController"

// App-layer PIN (0-65535). The phone must send this once per connection.
// Keep the same value in the app's PIN field. 2468 is the default.
#define BLE_PIN 2468

// Number of relay channels wired to the 8-channel relay module.
#define RELAY_COUNT 8

// Channel 0 is the fan, channels 1-7 are the lights.
#define RELAY_CH_FAN 0

// Most 8-channel relay modules are LOW-trigger (optocoupler input: LOW = relay ON).
// Set to true if your module is HIGH-trigger.
#define RELAY_ACTIVE_HIGH false

// GPIOs driving the relay module inputs (IN1..IN8), one per channel.
// ESP32 devkit-safe outputs (avoids strapping pins 0, 2, 5, 12, 15).
static const uint8_t RELAY_PINS[RELAY_COUNT] = {4, 16, 17, 5, 18, 19, 21, 22};