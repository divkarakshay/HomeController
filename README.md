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
- An app-layer PIN is still required once per connection so strangers in range cannot
  flip your lights (range ~10–30 m).

## Directories

| Path        | Contents                                        |
|-------------|-------------------------------------------------|
| `firmware/` | ESP32 BLE relay controller (PlatformIO + Arduino + NimBLE) |
| `android/`  | Android controller app (Kotlin + Jetpack Compose)          |

## Wiring (firmware)

| Relay channel | Device     | ESP32 GPIO |
|---------------|------------|------------|
| CH0           | Fan        | GPIO4  (IN1) |
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

## BLE protocol

- Device name: `HomeController`
- Service: `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- Write characteristic: `4fafc202-…` (commands)
- Notify characteristic: `4fafc203-…` (responses)

| Write packet                    | Meaning                          | Response                     |
|---------------------------------|----------------------------------|------------------------------|
| `FF <pinHi> <pinLo>`            | Authenticate (PIN from config.h) | `A1 <s0..s7>` auth OK + states, or `E4` wrong PIN |
| `01 <ch> <00/01>`               | Set relay channel 0–7            | `A2 <ch> <state>`            |
| `02`                            | Request all states               | `A4 <s0..s7>`                |

Response codes: `A1` auth OK, `A2` set OK, `A4` states, `E1` bad command, `E2` bad channel,
`E3` not authenticated, `E4` wrong PIN.

## Build & flash the ESP32

1. Install PlatformIO: `python3 -m pip install platformio`
2. `cd firmware && pio run` (first run downloads the toolchain)
3. Connect the ESP32 via USB, then `pio run -t upload`
4. Watch the log: `pio device monitor` — you should see `[BLE] advertising as HomeController`

## Build & install the Android app

```sh
cd android
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

- Enter the PIN (default `2468`, keep in sync with `firmware/include/config.h` BLE_PIN) and
  tap **Scan & Connect**.
- Bluetooth + location (Android ≤ 10) permissions are requested on first launch.
- The app auto-reconnects when the device comes back in range.

## Security notes

- No BLE encryption is enforced (this pairs nothing), so the PIN is the only barrier.
  Anyone within BLE range who knows the PIN can control the relays.
- Change `BLE_PIN` in `firmware/include/config.h` and in the app PIN field.
- Don't use this to control anything safety-critical (no 220 V relay switching without a
  proper enclosure, fused supply, and certified relay module).