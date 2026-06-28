package com.example.obdreader.ui

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.obdreader.R
import com.example.obdreader.viewmodel.ObdViewModel
import kotlinx.coroutines.launch

private const val TAG="MainFragment"
class MainFragment : Fragment(R.layout.fragment_main) {
    private val viewModel: ObdViewModel by activityViewModels()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvRpm = view.findViewById<TextView>(R.id.tvRpm)
        val btnConnect = view.findViewById<Button>(R.id.btnConnect)
        val btnPair = view.findViewById<Button>(R.id.btnPair)

        btnConnect.setOnClickListener {
            viewModel.navigateTo(ObdViewModel.Screen.CONNECT)
        }

        btnPair.setOnClickListener {
            viewModel.connect()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rpm.collect { rpm ->
                    tvRpm.text = if (rpm == null) "-- rpm" else "$rpm rpm"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chosenDevice.collect { device ->
                    if (device != null) {
                        Log.i(TAG, "Device chosen: ${device.name ?: device.address}")
                        tvTitle?.text = device.name ?: "Unknown Device"
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect { msg ->
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
