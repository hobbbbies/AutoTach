package com.example.obdreader.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.recyclerview.widget.RecyclerView
import com.example.obdreader.R

class ListAdapter(private val devices: List<BluetoothDevice>): RecyclerView.Adapter<ListAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView

        init {
            tvName = view.findViewById<TextView>(R.id.tvName)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.text_row_item, parent, false)
        return ViewHolder(view)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.tvName.text = devices[position].name
    }

    override fun getItemCount(): Int {
        return devices.size
    }
}
