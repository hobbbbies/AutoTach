package com.example.obdreader.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.example.obdreader.interfaces.IObdReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

private const val TAG = "MockObdReader"
class MockObdReader(context: Context, device: BluetoothDevice) : IObdReader {
    private var rpm: Int = 1000
    private var speed: Int = 0


    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> get() = _state.asStateFlow()

    override fun connect() {
        Log.i(TAG, "connect: Hitting mock")
        _state.value = ConnectionState.READY
    }

    override fun disconnect() {
        rpm = 1000
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean {
        return true
    }

    override suspend fun getRpm(): Int {
        val currentRpm = rpm
        val direction = arrayOf(-1, 2).random()
        val nextTarget = currentRpm + (100 * direction)
        rpm = nextTarget.coerceIn(0, 6000)
        return currentRpm
    }

    override suspend fun getSpeed(): Int {
        val result = speed
        speed += 1
        return result
    }

}