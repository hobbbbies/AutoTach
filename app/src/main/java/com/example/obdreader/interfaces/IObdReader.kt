package com.example.obdreader.interfaces

import com.example.obdreader.bluetooth.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface IObdReader {
    val state: StateFlow<ConnectionState>

    fun connect()

    fun disconnect()

    fun isConnected(): Boolean

    suspend fun getRpm(): Int

    suspend fun getSpeed(): Int
}