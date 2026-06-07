package com.example.obdreader.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.example.obdreader.interfaces.IObdReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MockObdReader"
class MockObdReader(context: Context, device: BluetoothDevice) : IObdReader {
    private var rpm: Float = 1000f

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> get() = _state.asStateFlow()

    override fun connect() {
        Log.i(TAG, "connect: Hitting mock")
        _state.value = ConnectionState.READY
    }

    override fun disconnect() {
        rpm = 1000f
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean {
        return true
    }

    override suspend fun getRpm(): Float {
        val result = rpm
        rpm += 100
        return result
    }

    override suspend fun getSpeed(): Float {
        return 100f
    }

}