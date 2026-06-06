package com.example.obdreader.data

import android.Manifest
import android.app.Application
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.obdreader.bluetooth.BluetoothScanner
import com.example.obdreader.bluetooth.ConnectionState
import com.example.obdreader.bluetooth.ObdReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "ObdRepository"

class ObdRepository(
    private val application: Application,
    private val scope: CoroutineScope,
) {

    private val scanner = BluetoothScanner(application, scope)

    private var obdReader: ObdReader? = null

    val devices: StateFlow<Set<BluetoothDevice>> = scanner.devices
    val chosenDevice: StateFlow<BluetoothDevice?> = scanner.chosenDevice

    private val _rpm = MutableStateFlow<Float?>(null)
    val rpm: StateFlow<Float?> = _rpm.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var pollJob: Job? = null
    private var stateMirrorJob: Job? = null

    val isBluetoothSupported: Boolean get() = scanner.isBluetoothSupported
    val isBluetoothEnabled: Boolean get() = scanner.isBluetoothEnabled

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() = scanner.startScan()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() = scanner.stopScan()

    @SuppressLint("MissingPermission")
    fun setChosenDevice(device: BluetoothDevice) {
        scanner.setChosenDevice(device)
        scanner.stopScan()

        val reader = ObdReader(application, device)
        obdReader = reader

        stateMirrorJob?.cancel()
        stateMirrorJob = scope.launch {
            reader.state.collect { _connectionState.value = it }
        }

        reader.connect()
        scope.launch {
            reader.state.first { it == ConnectionState.READY }
            Log.i(TAG, "setChosenDevice: READY, starting RPM poller")
            startPollingRpm()
        }
    }

    fun startPollingRpm() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    _rpm.value = obdReader?.getRpm()
                } catch (e: IOException) {
                    Log.w(TAG, "RPM read failed: ${e.message}")
                    _rpm.value = null
                }
                delay(200)
            }
        }
    }

    fun stopPollingRpm() {
        pollJob?.cancel()
        pollJob = null
    }
}
