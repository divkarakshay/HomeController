# Wiring & Soldering Guide

Hardware: **DOIT ESP32 DevKit V1** (30-pin, 15 per side) + 8-channel relay module +
8 SPST toggle switches.

## Board orientation

USB port at the top, ESP32-WROOM-32 module in the center. All relay pins are on the
**right column**, all switch pins on the **left column**.

```
                    ┌────────────────────────────┐
                    │            USB             │
                    └────────────────────────────┘
LEFT COLUMN (switches)          RIGHT COLUMN (relay module)
   ┌─────────────────────┐        ┌─────────────────────┐
   │ EN                  │        │ 3V3                 │
   │ VP   (D36)          │        │ GND  ◄─── shared ground ──► relay GND
   │ VN   (D39)          │        │ D15  ◄── SW2 (Light 2)
   │ D34                 │        │ D2   ◄── IN1 (CH0 Fan)
   │ D35                 │        │ D0                │
   │ D32  ◄── SW7        │        │ D4                 │
   │ D33                 │        │ D16  ◄── IN2 (CH1 Light 1)
   │ D25  ◄── SW4        │        │ D17  ◄── IN3 (CH2 Light 2)
   │ D26  ◄── SW5        │        │ D5   ◄── IN4 (CH3 Light 3)
   │ D27  ◄── SW6        │        │ D18  ◄── IN5 (CH4 Light 4)
   │ D14  ◄── SW1        │        │ D19  ◄── IN6 (CH5 Light 5)
   │ D12  (leave free)   │        │ D21  ◄── IN7 (CH6 Light 6)
   │ D13  ◄── SW0        │        │ RX0  (D3)
   │ GND  ◄── switch     │        │ TX0  (D1)
   │       common rail   │        │ D22  ◄── IN8 (CH7 Light 7)
   │ D23  ◄── SW3        │        │                     │
   └─────────────────────┘        └─────────────────────┘
   ```

On this board the Arduino pin number equals the GPIO number (D2 = GPIO2), so the
firmware's `RELAY_PINS`/`SWITCH_PINS` values work directly in `digitalWrite`/`digitalRead`.

## Relay connections (right column → relay module IN1..IN8)

| Channel | Device     | Pin        |
|---------|------------|------------|
| CH0     | Fan        | D2  = GPIO2  (IN1) |
| CH1     | Light 1    | D16 = GPIO16 (IN2) |
| CH2     | Light 2    | D17 = GPIO17 (IN3) |
| CH3     | Light 3    | D5  = GPIO5  (IN4) |
| CH4     | Light 4    | D18 = GPIO18 (IN5) |
| CH5     | Light 5    | D19 = GPIO19 (IN6) |
| CH6     | Light 6    | D21 = GPIO21 (IN7) |
| CH7     | Light 7    | D22 = GPIO22 (IN8) |

## Switch connections (left column → SPST switches)

One terminal of each switch to GND, the other to the GPIO. Internal pull-ups handle
the rest; closed switch = relay ON.

| Channel | Pin            |
|---------|----------------|
| CH0 Fan     | D13 = GPIO13 |
| CH1 Light 1 | D14 = GPIO14 |
| CH2 Light 2 | D15 = GPIO15 |
| CH3 Light 3 | D23 = GPIO23 |
| CH4 Light 4 | D25 = GPIO25 |
| CH5 Light 5 | D26 = GPIO26 |
| CH6 Light 6 | D27 = GPIO27 |
| CH7 Light 7 | D32 = GPIO32 |

## Critical rules

1. Power the relay board from a **separate 5 V supply** (screw terminals), never from
   the ESP32's 3.3 V.
2. Connect grounds: ESP32 GND ↔ relay module GND, plus a GND wire to the switch rail.
3. Mains wiring (220 V) goes only to the relay contacts (COM/NO), never near the ESP32.
4. Relay module is low-trigger (IN LOW = ON), matching `RELAY_ACTIVE_HIGH false` in
   `firmware/include/config.h`. Flip to true if your module is high-trigger.
5. GPIO2 (fan CH0) also drives the onboard LED — normal here (LED off = relay on).
6. GPIO12 is a strapping pin — leave unwired, as the firmware does.

## Soldering order & tips

1. Solder the GND rail first (relay GND + switch common rail).
2. Relay wires in one color (e.g., red): GPIO2→IN1 … GPIO22→IN8.
3. Switch wires in another color (e.g., blue): GPIO13→SW0 … GPIO32→SW7.
4. Use 22 AWG stranded wire; tin both pads, keep joints under ~2 s.
5. Count pins physically: the DOIT v1 silkscreen is often blank (no GPIO labels).
   Verify with a multimeter/beep test against GND before trusting a joint.
6. After soldering, test with the app before closing anything up.

Reference: pinout per mischianti.org DOIT ESP32 DevKit V1 pinout; firmware pins in
`firmware/include/config.h`.