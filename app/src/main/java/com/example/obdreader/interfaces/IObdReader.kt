package com.example.obdreader.interfaces

interface IObdReader {
    fun connect()

    fun disconnect()

    fun isConnected(): Boolean

    suspend fun getRpm(): Float

    suspend fun getSpeed(): Float
}