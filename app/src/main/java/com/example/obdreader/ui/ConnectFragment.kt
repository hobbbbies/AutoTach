package com.example.obdreader.ui

import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.obdreader.R
import com.example.obdreader.dataClasses.DiscoveredDevice
import com.example.obdreader.viewmodel.ObdViewModel
import kotlinx.coroutines.launch
import kotlin.getValue

private const val TAG="ConnectFragment"
class ConnectFragment : Fragment(R.layout.fragment_main) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!viewModel.isBluetoothEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }

        val rvDevices = view.findViewById<RecyclerView>(R.id.rvDevices)

        val devices = mutableListOf<BluetoothDevice>()

        val adapter = ListAdapter(devices)
        rvDevices.adapter = adapter
        rvDevices.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.devices.collect {
                for (device in it) {
                    devices.add(device)
                }
                adapter.notifyItemInserted(devices.size - 1)
            }
        }
    }
}