# HomeController

Wi-Fi-less home automation: an ESP32 + 8-channel relay module (1 fan + 7 lights in the
living room) controlled over **Bluetooth Low Energy (BLE)** — no pairing, no router needed.

## Architecture

```
┌──────────────────────────┐      BLE (no pairing)       ┌─────────────────────────────┐
│ Android app (Kotlin)     │  ────────────────────────>  │ ESP32 (NimBLE peripheral)   │
│ scans, connects, writes  │                            │ 8 GPIOs -> 8-channel relay  │
│ relay on/off commands    │  <────────────────────────  │ CH0 = fan, CH1-7 = lights   │
└──────────────────────────┘   status notifications      └─────────────────────────────┘
```

- BLE connections need **no pairing** on the phone — the app connects to the ESP32 and
  exchanges commands over GATT characteristics directly.
- **No app-layer PIN either**: any connected phone can control the relays. Multi-phone
  safe — each phone that subscribes receives the current states automatically. (Anyone
  within ~10–30 m with the app can control your lights; for a locked-down setup, re-add
  the PIN auth.)
- If the BLE link drops, the app silently auto-reconnects (up to 8 attempts, 2 s apart).

## Directories

| Path        | Contents                                        |
|-------------|-------------------------------------------------|
| `firmware/` | ESP32 BLE relay controller (PlatformIO + Arduino + NimBLE) |
| `android/`  | Android controller app (Kotlin + Jetpack Compose)          |

## Wiring (firmware)

| Relay channel | Device     | ESP32 GPIO |
|---------------|------------|------------|
| CH0           | Fan        | GPIO2  (IN1) |
| CH1           | Light 1    | GPIO16 (IN2) |
| CH2           | Light 2    | GPIO17 (IN3) |
| CH3           | Light 3    | GPIO5  (IN4) |
| CH4           | Light 4    | GPIO18 (IN5) |
| CH5           | Light 5    | GPIO19 (IN6) |
| CH6           | Light 6    | GPIO21 (IN7) |
| CH7           | Light 7    | GPIO22 (IN8) |

- Most 8-channel relay modules are **low-trigger** (IN pulled low = relay ON). If yours is
  high-trigger, set `RELAY_ACTIVE_HIGH true` in `firmware/include/config.h`.
- Power the relay board from a **separate 5 V supply** (screw terminals), never from the
  ESP32's 3.3 V. Connect the grounds (ESP32 GND ↔ relay GND).
- Fan and lights run on relay contacts only (on/off). Fan speed control would need a
  triac/dimmer module instead of a relay.

## Manual toggle switches

Each channel also has a physical SPST toggle switch (works even without the phone).
Wire one terminal of each switch to GND, the other to the GPIO below. Internal pull-ups
are used, so a switch needs no extra parts.

| Channel | Switch GPIO |
|---------|-------------|
| CH0 Fan     | GPIO13 |
| CH1 Light 1 | GPIO14 |
| CH2 Light 2 | GPIO15 |
| CH3 Light 3 | GPIO23 |
| CH4 Light 4 | GPIO25 |
| CH5 Light 5 | GPIO26 |
| CH6 Light 6 | GPIO27 |
| CH7 Light 7 | GPIO32 |

- Switch closed (to GND) = relay ON, open = OFF. Debounced in firmware (40 ms).
- Flipping a switch updates its relay **and** notifies the phone app, so the UI stays
  in sync (response code `A5`).
- At boot, relays are set to match the physical switches.
- Switches and the app both control the relays — last one to change wins.

## State persistence (power cut safe)

Relay states are saved to the ESP32's NVS flash on every change (from the app or a
switch) and restored at boot, so app-set states survive power cuts:

| Light was ON via…        | Power returns →                                  |
|--------------------------|--------------------------------------------------|
| Toggle switch (physically ON) | ON (physical switch always wins)            |
| App only (switch OFF)    | ON (restored from NVS flash memory)              |

A physically-ON switch always overrides the saved state at boot. NVS writes happen only
when a relay actually changes, so flash wear is negligible.

## Power restore mode

What the ESP32 does at boot after a power cut is configurable from the app
(**⋮ → Settings → Power Restore**):

| Mode      | Power returns →                                  |
|-----------|--------------------------------------------------|
| Last state (default) | App-set states restored from flash (ON ones come back ON) |
| All off   | All relays start OFF regardless of saved states   |

- The setting itself is saved in NVS and survives power cuts.
- A physically-ON toggle switch still always wins at boot, in both modes.

## BLE protocol

- Device name: `HomeController`
- Service: `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- Write characteristic: `4fafc202-…` (commands)
- Notify characteristic: `4fafc203-…` (responses)

| Write packet                    | Meaning                          | Response                     |
|---------------------------------|----------------------------------|------------------------------|
| `01 <ch> <00/01>`               | Set relay channel 0–7            | `A2 <ch> <state>`            |
| `02`                            | Request all states               | `A4 <s0..s7>`                |
| `03 <mode>`                     | Set power-restore mode (0 = last state, 1 = all off) | `A6 <mode>` |
| `04`                            | Request power-restore mode       | `A6 <mode>`                  |
| `0A <level>`                    | Set fan speed level 1–6 (6 = boost) | `AF <0/1>` + `AE <level>` |
| `0B`                            | Request fan speed level          | `AE <level>`                 |

Response codes: `A2` set OK, `A4` states (also pushed automatically to any phone when it
subscribes), `A5` channel changed by a manual switch, `A6` restore mode, `A7` timer,
`AE` fan speed level (also pushed when a speed change finishes), `AF` fan cycling busy
state (1 = cycling, 0 = idle), `E1` bad command, `E2` bad channel.

## Atomberg-style fan speed control

The fan runs off relay CH0 (on/off only), so the only speed control it understands is the
wall-switch pattern that Atomberg BLDC fans respond to: **turn the relay OFF ~1 s, then ON;
each OFF-ON cycle steps the fan up one level** (Speed 1→2→…→5→Boost, and Boost wraps back
to Speed 1). The app's fan card — a rotary dial merged with the power button —
automates exactly that pattern with millisecond precision in the firmware:

| Setting | Value |
|---------|-------|
| Levels | 1–5 + Boost (6 = max) |
| Relay OFF pulse | 1.0 s per step |
| Relay ON between steps | 2.5 s (the fan must register each level) |
| Power-on warm-up | 8.0 s before the first pulse when the fan was OFF |

- **The fan remembers its speed across power cuts** — turning it on is just a
  relay ON; the fan resumes at its own remembered speed and no cycling happens
  until you pick a new speed on the dial.
- The dial shows the fan's indicator disk — 1–5 green LEDs lit for the speed, all 6 amber
  for Boost — and snaps with haptic feedback. The hub is the fan's on/off button.
- Starting from a dead stop, the BLDC needs a few seconds to spin up before the
  OFF–ON pulses register (the 1 s pulse works mid-spin because the motor's
  momentum keeps the fan's controller alive) — the 8 s warm-up covers that, so
  speed changes initiated from standstill take a bit longer.
- While cycling, the dial shows an orange progress ring, the status line reads
  "Setting Speed N…" and the dial locks; new requests made mid-cycle are queued
  and applied when the current cycle finishes.
- The level is saved in the ESP32's NVS (like the relay states) and restored at boot, so
  the dial is correct after a power cut.
- Anything that takes direct control of the fan relay aborts the cycle: toggling the
  switch in the app, flipping the physical wall switch, or the off-timer firing.
- Limitation: the fan's own IR remote (and the physical-switch trick) can change
  the speed without the controller knowing, and a standstill fan with the relay
  ON is invisible to us too (e.g. the remote's own off button). The dial assumes
  the last level the app set, so it can drift out of sync — if the speed was
  changed by hand since the last dial commit, the next cycle may land on the
  wrong speed.
- Add a triac/dimmer on CH0 instead of a relay to get instant, silent speed changes
  (then drop the cycling timing constants in `firmware/include/config.h`).

## Build & flash the ESP32

1. Install PlatformIO (one time): `python3 -m pip install platformio`
2. Connect the ESP32 via USB, then flash:

```sh
cd ~/MyWorkspace/HomeController/firmware
python3 -m platformio run -t upload --upload-port /dev/cu.usbserial-0001
```

(`/dev/cu.usbserial-0001` is the port for this Mac; find yours with `ls /dev/cu.*`.)

3. Watch the log: `python3 -m platformio device monitor -p /dev/cu.usbserial-0001 -b 115200`
   — you should see `[BLE] advertising as HomeController`

**Extra firmware commands**

```sh
# Just compile (no upload) — catches errors fast
python3 -m platformio run

# Erase the whole flash (fixes boot loops / corrupted settings), then flash again
python3 -m platformio run -t erase --upload-port /dev/cu.usbserial-0001
python3 -m platformio run -t upload --upload-port /dev/cu.usbserial-0001
```

## Build, pack & sideload the Android app

```sh
cd ~/MyWorkspace/HomeController/android

# 1. Compile + package the APK (app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# 2. Sideload it onto the phone (phone must have USB debugging enabled and be plugged in)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or everything in one go:
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app auto-connects on launch. If it can't find the device, tap **Connect** to retry;
the app also auto-reconnects silently after a dropped link.

**Other useful phone commands**

```sh
adb devices                                   # check the phone is connected
adb install -r app/build/outputs/apk/debug/app-debug.apk   # reinstall keeping app data
adb uninstall com.homecontroller.app          # remove the app completely
adb logcat -d | grep -E "BleRelayManager"     # view BLE logs from the app
```

## App usage

- The app auto-connects on launch; status shows **Connected / Connecting… / Offline**.
- When connected: control the fan with the dial card up top, toggle the Light 1–7
  tiles, **Refresh**, **Disconnect**.
- The fan card at the top has a rotary dial that sets the fan speed 1–5 + Boost by
  stepping the fan relay like the wall-switch trick. Drag or tap a notch to pick a
  speed; tap the hub to turn the fan on/off (the fan is turned on automatically
  when you pick a speed while it's off).
- **⋮ → Settings** opens the settings screen with the **Power Restore** mode selector.
- Bluetooth + location (Android ≤ 10) permissions are requested on first launch.
- The fan channel currently uses GPIO2 (which also drives the onboard LED — the LED is a
  pin indicator and relays are low-trigger, so LED off = relay on). Move the fan back to
  GPIO4 if you want a clean line: change `RELAY_PINS[0]` in `firmware/include/config.h`.

## Security notes

- No BLE pairing and no app PIN: anyone within ~10–30 m using this app can control the
  relays. Fine for a home living room; for a security-critical setup, add authentication.
- Don't use this to control anything safety-critical (no 220 V relay switching without a
  proper enclosure, fused supply, and certified relay module).