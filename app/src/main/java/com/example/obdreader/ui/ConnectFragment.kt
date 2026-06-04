package com.example.obdreader.ui

import android.Manifest
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.obdreader.R
import com.example.obdreader.viewmodel.ObdViewModel
import kotlinx.coroutines.launch

private const val TAG = "ConnectFragment"

class ConnectFragment : Fragment(R.layout.fragment_connect) {
    private val viewModel: ObdViewModel by activityViewModels()

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(requireContext(), "Bluetooth enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Bluetooth is required for this app", Toast.LENGTH_LONG).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            Log.i(TAG, "Bluetooth permissions granted, starting scan")
            startScanWithPermission()
        } else {
            Toast.makeText(
                requireContext(),
                "Bluetooth permissions are required to find adapters",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvDevices = view.findViewById<RecyclerView>(R.id.rvDevices)

        // Use the new DeviceListAdapter and pass the click lambda
        val adapter = DeviceListAdapter { device ->
            viewModel.setChosenDevice(device)
            Log.i(TAG, "Device selected: ${device.address}")
        }

        rvDevices?.adapter = adapter
        rvDevices?.layoutManager = LinearLayoutManager(requireContext())

        if (!viewModel.isBluetoothEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }

        ensurePermissionsThenScan()

        // Observe the devices Flow and submit to the adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devices.collect { deviceSet ->
                    adapter.submitList(deviceSet.toList())
                }
            }
        }
    }

    private fun ensurePermissionsThenScan() {
        val needed = REQUIRED_BT_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startScanWithPermission()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScanWithPermission() {
        viewModel.startScan()
    }

    companion object {
        private val REQUIRED_BT_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    }
}
