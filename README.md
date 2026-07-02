# AutoTach

**AutoTach** is an Android Auto app that turns your car's infotainment screen into a live, color-coded tachometer. It connects to an OBD-II Bluetooth adapter, reads engine RPM directly off the CAN bus, and renders a full-screen tach on the dashboard display that shifts color in real time as the revs climb.  Green at idle, amber through the powerband, red near redline.

Built and tested against the **Carista EVO** adapter (ELM327 v2.2 compatible), but the parser is generic and works with any standards-compliant ELM327 device.

---

## Features

- **Android Auto integration** via `CarAppService` + `Session` + `Screen` with a `NavigationTemplate`, so the app renders full-screen on the head unit while the driver is moving — hosted through the Android for Cars App Library (`androidx.car.app`).
- **Custom Canvas rendering to the car's `Surface`** driven by a `SurfaceCallback` — segmented RPM and speed bars with per-segment HSV interpolation (green → amber → red)
- **MVVM** — an `ObdRepository` acts as the apps 'Model' or dat layer,  exposing `StateFlow`s that can be read directly on the car service side (since it's not an activity), and read on the phone side through a Viewmodel class that wraps the repository state and methods in an activity-safe manner. Finally the phone `Fragment`s and the Android Auto `Screen` act as views that simply observe.
- **Single-Activity UI** with `Fragment`s (`MainFragment`, `ConnectFragment`), hosted by a single main activity
- **Live OBD-II data** parsed from an ELM327 BLE adapter (engine RPM `010C`, speed `010D`), with multi-frame byte reassembly and full ELM327 error-string handling — see [Architecture](#architecture) and [The Protocol Stack](#the-protocol-stack) below.

---

## Architecture

The app follows **MVVM** with a clean three-layer separation underneath the ViewModel. Each layer owns one protocol concern and knows nothing about the layers above it. The phone UI (for setup / pairing) and the Android Auto `Screen` (for the in-car tach display) are both thin observers of the same ViewModel state:

```
Head unit ◀── renders ─── CarAppService / Screen ┐
                                                 │  observes
Phone UI ──── observes ───────────────────────── ▶ ViewModel ── owns ──▶ ObdReader ── owns ──▶ BluetoothCommunicator
                                                                       OBD-II / PIDs        BLE GATT + ELM327
```

- **`BluetoothCommunicator`** (transport): owns the `BluetoothGatt`, runs the GATT callbacks, enables CCCD notifications, sends raw AT command strings, and exposes a `suspend fun sendCommand(cmd: String): String`. It has no idea what a PID is.
- **`ObdReader`** (domain): wraps the communicator and exposes `suspend fun getRpm()` / `getSpeed()`. All hex parsing, error detection, and PID formulas live here. It has no idea what GATT is.
- **`ObdViewModel`** (presentation): owns the `ObdReader`, exposes `StateFlow`s for both the phone UI and the Android Auto `Screen` to observe, and survives configuration changes.


---

## Concurrency

Between the Bluetooth connection, the PID-polling loop, and two UIs that redraw on every state change, there's a lot going on off the main thread. Kotlin coroutines and `StateFlow` are what keep it coordinated.

- **`StateFlow` as the source of truth** — RPM, speed, connection status, and the device list are all `StateFlow`s. New subscribers immediately get the latest value.
- **`repeatOnLifecycle(STARTED)`** on the phone side, so Fragment collectors pause with the view and never leak past it.
- **`suspend fun sendCommand`** wraps the callback-based BLE GATT API in a linear one — the caller writes `val response = sendCommand("010C")` and awaits the parsed response
- **ELM327 init handshake as a coroutine** — after notifications are enabled, `scope.launch { performHandshake() }` sends the 7-command init sequence (`ATZ`, `ATE0`, `ATL0`, …) one after another using `sendCommand`, so a serial protocol reads as a top-to-bottom loop instead of a callback chain.

---

## The Protocol Stack

What makes this project interesting from a learning perspective is that it sits on three protocols stacked on top of each other, and the code makes that stack visible:

| Layer | Format | What gets parsed |
|---|---|---|
| **BLE GATT** | binary characteristic writes / notifies | bytes of an ASCII stream |
| **ELM327** | CR-terminated AT commands, `>` prompt as response terminator | one full response string per command |
| **OBD-II** | hex ASCII representing CAN frames, optionally ISO-TP framed | typed values (RPM as `Float`, speed as `Float`) |

The most involved piece is `ObdReader.parseResponse`, which has to:
1. Split the response on `\r` and drop blanks, echoes of the command, and `SEARCHING...` filler lines.
2. Match against the 9 documented ELM327 error literals and the `BUS INIT:` prefix.
3. Detect whether the response is single-frame or **ISO-TP multi-frame** (a length header on the first line, `N:` prefixes on subsequent lines).
4. For multi-frame: read the declared length, strip frame numbers, concatenate, and truncate trailing padding.
5. Convert the hex string to a `ByteArray`, validating odd length and non-hex characters.

Only after all that does the domain code apply the per-PID formula (e.g. RPM = `((A * 256) + B) / 4`).

---

## Tech Stack

- **Language:** Kotlin
- **Android Auto:** Android for Cars App Library (`androidx.car.app`)
- **Architecture:** MVVM with `AndroidViewModel`, `Fragment`, `ViewBinding`
- **Bluetooth:** Android BLE GATT API directly — no `RxAndroidBle`, no Nordic wrapper
- **Min SDK:** 24 / **Target SDK:** 35 / **JVM:** 17

---


## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/hobbbbies/autotach.git
cd autotach
```

### 2. Open in Android Studio

Open the project in Android Studio (Giraffe or newer). Gradle sync will pull dependencies.

### 3. Run on a physical device

BLE doesn't work in the emulator. You'll need a real Android device and an ELM327-compatible OBD-II adapter (Carista EVO, OBDLink, generic clone, etc.) plugged into a car's OBD-II port.

To test the Android Auto experience without leaving the driveway, install the **Desktop Head Unit (DHU)** from the Android SDK — it simulates a car's head unit on your desktop and renders the in-car UI live.
