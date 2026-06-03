package com.example.obdreader.ui

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.TextView
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tvTitle: TextView? = view?.findViewById<TextView>(R.id.tvTitle)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chosenDevice.collect { device ->
                    if (device != null) {
                        Log.i(TAG, "Device chosen: ${device.name ?: device.address}")
                        tvTitle?.text = device.name
                    }
                }
            }
        }
    }
}