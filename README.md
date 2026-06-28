# AutoTach

**AutoTach** is an Android Auto app that turns your car's infotainment screen into a live, color-coded tachometer. It connects to an OBD-II Bluetooth adapter, reads engine RPM directly off the CAN bus, and renders a full-screen tach on the dashboard display that shifts color in real time as the revs climb.  Green at idle, amber through the powerband, red near redline.

Built and tested against the **Carista EVO** adapter (ELM327 v2.2 compatible), but the parser is generic and works with any standards-compliant ELM327 device.

--- 
## Demonstration


https://github.com/user-attachments/assets/5d28f9d3-4caa-4ae7-ab0d-93bda1850c8a

---

## Features

- **Android Auto integration** rendering a full-screen tachometer on the car's head unit while driving
- **RPM-driven color shift** that smoothly transitions the screen from green → amber → red as engine speed climbs toward redline, designed to be readable at a glance
- **BLE device scanning** filtered by SPP service UUID, with Android 12+ runtime permissions handled correctly
- **GATT connection lifecycle** with explicit state tracking (`DISCONNECTED → CONNECTING → LINK_UP → READY`)
- **ELM327 init handshake** sent automatically after notifications are enabled
- **Live PID polling** for engine RPM (`010C`) and vehicle speed (`010D`), with parsing general enough to add more
- **ISO-TP multi-frame** response reassembly for PIDs that return more than 7 bytes
- **Error-tolerant parser** that handles ELM327's full vocabulary: `NO DATA`, `SEARCHING...`, `BUS BUSY`, `BUS INIT:`, `CAN ERROR`, echo lines, etc.

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

The ViewModel never sees an AT command. The communicator never sees a PID. Each layer can be tested or swapped independently.

---

## Concurrency

The Android BLE API is **callback-based and strictly serial** — you can only have one GATT operation in flight at a time, and every result comes back on a system thread. Bridging that into the ViewModel cleanly is where most of the interesting design work happened.

**Key points:**
- `sendCommand` is a `suspend` function. It writes the command on the GATT thread and then `await`s the response from a `Channel<String>` that the notification callback feeds.
- Notification chunks are buffered until the ELM327's `>` prompt is seen, at which point the full response is dispatched as a single message — turning the chunked async stream into a clean request/response API.
- `BluetoothCommunicator` owns its own `CoroutineScope(Dispatchers.IO + SupervisorJob())` for the handshake. The ViewModel owns its own `viewModelScope` for UI-driven polling. Cancelling one doesn't poison the other.
- Connection progress and live RPM are exposed as `StateFlow`s, so both the phone UI and the Android Auto `Screen` can render reactively without polling — the head unit redraws automatically whenever a fresh PID response lands.

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
