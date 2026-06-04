package com.example.obdreader.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.obdreader.interfaces.IBluetoothCommunicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ConnectionState { DISCONNECTED, CONNECTING, LINK_UP, READY }

private const val TAG = "BluetoothCommunicator"

private val SERVICE_UUID = UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")
private val NOTIFY_UUID  = UUID.fromString("00002af0-0000-1000-8000-00805f9b34fb")
private val WRITE_UUID   = UUID.fromString("00002af1-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private val INIT_SEQUENCE = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100")

class BluetoothCommunicator(
    private val context: Context,
    private val device: BluetoothDevice,
) : IBluetoothCommunicator {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val responseBuffer = StringBuilder()
    private val responses = Channel<String>(Channel.UNLIMITED)

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected, discovering services")
                    _state.value = ConnectionState.LINK_UP
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected")
                    _state.value = ConnectionState.DISCONNECTED
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            logServiceTree(gatt)
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Service $SERVICE_UUID not found")
                return
            }
            val notifyChar = service.getCharacteristic(NOTIFY_UUID)
            val wChar = service.getCharacteristic(WRITE_UUID)
            if (notifyChar == null || wChar == null) {
                Log.e(TAG, "Notify/write characteristics not found in $SERVICE_UUID")
                return
            }
            wChar.writeType =
                if ((wChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            writeChar = wChar

            gatt.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(CCCD_UUID)
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            Log.i(TAG, "Notifications enabled, starting handshake")
            scope.launch { performHandshake() }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val chunk = String(characteristic.value, Charsets.US_ASCII)
            Log.d(TAG, "RX chunk: ${chunk.replace("\r", "\\r")}")
            responseBuffer.append(chunk)
            if (responseBuffer.contains('>')) {
                val response = responseBuffer.toString().substringBefore('>').trim()
                responseBuffer.clear()
                Log.i(TAG, "RX: ${response.replace("\r", "\\r")}")
                responses.trySend(response)
            }
        }
    }

    /*
    ------------------------------------------------------------------------------------
    DEBUGGING STUFF
    */
    private fun logServiceTree(gatt: BluetoothGatt) {
        Log.i(TAG, "Discovered ${gatt.services.size} services on ${device.address}:")
        for (service in gatt.services) {
            Log.i(TAG, "  service ${service.uuid}")
            for (char in service.characteristics) {
                Log.i(TAG, "    char ${char.uuid} props=${propsString(char.properties)}")
            }
        }
    }

    private fun propsString(props: Int): String {
        val flags = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) flags += "READ"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) flags += "WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) flags += "WRITE_NR"
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) flags += "NOTIFY"
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) flags += "INDICATE"
        return flags.joinToString("|")
    }

    /*
    DEBUGGING STUFF
    ------------------------------------------------------------------------------------
    */

    private suspend fun performHandshake() {
        for (cmd in INIT_SEQUENCE) {
            val response = sendCommand(cmd)
            Log.i(TAG, "$cmd -> $response")
        }
        Log.i(TAG, "Handshake complete")
        _state.value = ConnectionState.READY
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override suspend fun sendCommand(cmd: String): String {
        val char = writeChar ?: error("Write characteristic not available")
        Log.i(TAG, "TX: $cmd")
        char.value = (cmd + "\r").toByteArray(Charsets.US_ASCII)
        gatt?.writeCharacteristic(char)
        return responses.receive()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect() {
        Log.i(TAG, "Connecting to ${device.address}")
        _state.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect() {
        gatt?.disconnect()
        gatt = null
        writeChar = null
        responses.close()
        scope.cancel()
        _state.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean = _state.value == ConnectionState.READY
}
