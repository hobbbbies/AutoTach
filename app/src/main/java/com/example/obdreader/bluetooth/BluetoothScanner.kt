package com.example.obdreader.bluetooth

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG="BluetoothScanner"

private val SPP_SERVICE_UUID: UUID = UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb")
class BluetoothScanner(private val application: Application, private val scope: CoroutineScope) {

    private val bluetoothManager: BluetoothManager? =
        application.getSystemService(BluetoothManager::class.java)

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    
    private var scanning = false

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000

    private val _devices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val devices: StateFlow<Set<BluetoothDevice>> = _devices.asStateFlow()

    private val _chosenDevice = MutableStateFlow<BluetoothDevice?>(null)
    val chosenDevice: StateFlow<BluetoothDevice?> = _chosenDevice.asStateFlow()

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            _devices.value += result.device
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            _devices.value += results.map { it.device }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            scanning = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled || bluetoothLeScanner == null) {
            return
        }

        if (!scanning) {
            _devices.value = emptySet()

            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SPP_SERVICE_UUID))
                    .build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scope.launch {
                delay(SCAN_PERIOD)
                if (scanning) {
                    stopScan()
                }
            }
            scanning = true
            bluetoothLeScanner.startScan(/*filters, settings, */leScanCallback)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (scanning) {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled ?: false

    fun setChosenDevice(device: BluetoothDevice) {
        Log.i(TAG, "setChosenDevice: Setting chosen device to $device.name")
        _chosenDevice.value = device
    }
}
