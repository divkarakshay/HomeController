#pragma once

#include <stdint.h>

// BLE name the phone app scans for. Must match Protocol.DEVICE_NAME in the app.
#define BLE_DEVICE_NAME "HomeController"

// Number of relay channels wired to the 8-channel relay module.
#define RELAY_COUNT 8

// Channel 0 is the fan, channels 1-7 are the lights.
#define RELAY_CH_FAN 0

// Most 8-channel relay modules are LOW-trigger (optocoupler input: LOW = relay ON).
// Set to true if your module is HIGH-trigger.
#define RELAY_ACTIVE_HIGH false

// GPIOs driving the relay module inputs (IN1..IN8), one per channel.
// ESP32 devkit-safe outputs (avoids strapping pins 0, 2, 5, 12, 15).
static const uint8_t RELAY_PINS[RELAY_COUNT] = {2, 16, 17, 5, 18, 19, 21, 22};

// Manual toggle switches (one per channel, SPST). Wire one terminal to GND and
// the other to the GPIO below. The internal pull-up keeps the line HIGH (switch
// OFF) when open; closing the switch pulls it LOW (relay ON). Flipping a switch
// drives its relay and notifies the phone app.
static const uint8_t SWITCH_PINS[RELAY_COUNT] = {13, 14, 15, 23, 25, 26, 27, 32};

// Debounce time for the manual switches, in milliseconds.
#define SWITCH_DEBOUNCE_MS 40

// ---- Atomberg-style BLDC fan speed control (channel 0) ----
// The fan is connected through a plain relay, so the only way to change its
// speed is the wall-switch pattern the fan responds to: OFF ~1 s, then ON,
// each OFF-ON cycle steps the fan up one level (Boost -> Speed 1 wrap).
// The firmware automates this pattern with millisecond precision.
#define FAN_LEVEL_MIN 1
#define FAN_LEVEL_MAX 6            // 1..5 + boost mode
#define FAN_OFF_PULSE_MS 1000      // relay OFF time per step (~1 s is the magic number)
#define FAN_ON_SETTLE_MS 2500      // relay ON time between steps (fan must register each level)
#define FAN_BOOT_SETTLE_MS 3000    // wait for the fan to spin up to its memory speed before step 1