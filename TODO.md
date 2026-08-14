# TODO

## In progress / pending

- [x] Channel rename + icons (Channels card in Settings, 8 icon catalog, persisted per-channel) — impl + test done.
- [x] Timers (firmware CMD_SET_TIMER 0x05 / CMD_SET_EPOCH 0x06, A7 echo, fire -> A5; app dialog + chips) — impl + E2E tested.
- [ ] Commit pending changes: animated background + theme + channels + timers (ask user).
- [ ] Home-screen widget — requires RelayService foreground BLE refactor first (single owner of the GATT link).
- [ ] BLE OTA — firmware partition table (ota_0) + chunked write + verification; app update dialog.
- [ ] Adaptive app icon / logo — replace `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
      (design a 432x432 px symbol, keep it inside the center 264 px safe zone; see README).

## Backlog

### High value
- [ ] Multi-controller support — scan for several ESP32s (e.g., one per room), pick a device,
      remember per-device names + state.
- [ ] Scenes — one-tap presets ("Movie", "Good night") = set a group of relays.
- [ ] Relay usage history — simple on-times/event log stored in NVS, shown with a small chart.

### Nice to have
- [ ] Optional app PIN lock — keep BLE open (multi-phone) but protect the phone app.
- [ ] ESP-NOW sensor node support — motion/temperature nodes trigger relays (lights on, fan auto).
- [ ] Web UI via ESP32 SoftAP — tiny captive-portal HTML to control relays without the app.
- [ ] Diagnostic screen — signal strength, connection params, packet counters, last NVS writes.
- [ ] Localization — store strings in `res/values-*` files.

## Done

- Power-restore mode (0x03/0x04, NVS, boot apply) — commit `2f0e94d`.
- Settings screen via ⋮ menu; animated background; system dark/light theme.
- Timers + channels verified E2E against the live board.