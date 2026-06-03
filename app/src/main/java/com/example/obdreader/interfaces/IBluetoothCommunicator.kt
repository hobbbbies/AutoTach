package com.example.obdreader.interfaces

interface IBluetoothCommunicator {
    fun connect()

    fun disconnect()

    fun isConnected(): Boolean

    suspend fun sendCommand(cmd: String): String
}