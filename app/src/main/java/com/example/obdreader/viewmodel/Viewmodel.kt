package com.example.obdreader.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obdreader.bluetooth.BluetoothCommunicator
import com.example.obdreader.bluetooth.BluetoothScanner
import com.example.obdreader.bluetooth.ConnectionState
import com.example.obdreader.bluetooth.ObdReader
import com.example.obdreader.interfaces.IObdReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.log

private const val TAG="ObdViewModel"
class ObdViewModel(application: Application) : AndroidViewModel(application) {

    enum class Screen { MAIN, CONNECT }

    private val _screen = MutableStateFlow(Screen.CONNECT)
    val screen: StateFlow<Screen> = _screen

    private val scanner = BluetoothScanner(application, viewModelScope)

    private var obdMessenger: IObdReader? = null

    val devices = scanner.devices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keeps flow active for 5s after UI leaves
            initialValue = emptySet())

    val chosenDevice = scanner.chosenDevice
        .stateIn(
            scope =viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keeps flow active for 5s after UI leaves
            initialValue = null
        )

    private val _rpm = MutableStateFlow<Float?>(null)
    val rpm: StateFlow<Float?> = _rpm

    private var pollJob: Job? = null
    fun startPollingRpm() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    _rpm.value = obdMessenger?.getRpm()
                } catch (e: IOException) {
                    Log.w(TAG, "RPM read failed: ${e.message}")
                    _rpm.value = null
                }
                delay(200)
            }
        }
    }
    fun stopPollingRpm() { pollJob?.cancel() }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(): Unit {
        scanner.startScan()
    }

    val isBluetoothSupported: Boolean
        get() = scanner.isBluetoothSupported

    val isBluetoothEnabled: Boolean
        get() = scanner.isBluetoothEnabled

    @android.annotation.SuppressLint("MissingPermission")
    fun setChosenDevice(device: BluetoothDevice) {
        scanner.setChosenDevice(device)
        scanner.stopScan()
        val reader = ObdReader(getApplication(), device)
        obdMessenger = reader
        reader.connect()
        viewModelScope.launch {
            reader.state.first { it == ConnectionState.READY }
            Log.i(TAG, "setChosenDevice: READY, starting RPM poller")
            startPollingRpm()
        }
    }

    suspend fun getRpms(): Float? {
        val isConnected = obdMessenger?.isConnected()
        if (isConnected == true) {
            Log.i(TAG, "getRpms: Test!!")
            return 0.0f
        } else {
            Log.i(TAG, "getRpms: Not connected...")
        }
        return obdMessenger?.getRpm()
    }

    fun navigateTo(screen: Screen) {
        _screen.value = screen
    }

}
