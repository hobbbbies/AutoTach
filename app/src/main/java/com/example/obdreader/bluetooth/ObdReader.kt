package com.example.obdreader.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.obdreader.interfaces.IObdReader
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import com.example.obdreader.BuildConfig

private const val TAG = "ObdReader"

private sealed class ObdResponse {
    data class Bytes(val data: ByteArray) : ObdResponse()
    data class Error(val reason: String) : ObdResponse()
}

private val ERROR_LITERALS = setOf(
    "?",
    "NO DATA",
    "STOPPED",
    "UNABLE TO CONNECT",
    "CAN ERROR",
    "BUS BUSY",
    "FB ERROR",
    "DATA ERROR",
    "BUFFER FULL",
)

private val HEX_HEADER = Regex("[0-9A-Fa-f]{1,3}")
private val FRAME_LINE = Regex("\\d+:.*")

class ObdReader(context: Context, device: BluetoothDevice) : IObdReader {

    private val communicator = BluetoothCommunicator(context, device)

    override val state: StateFlow<ConnectionState> get() = communicator.state

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect() {
        communicator.connect()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect() = communicator.disconnect()

    override fun isConnected(): Boolean = communicator.isConnected()

    override suspend fun getRpm(): Int {
        val bytes = queryBytes("010C", minBytes = 4)
        val a = bytes[2].toInt() and 0xFF
        val b = bytes[3].toInt() and 0xFF
        return (((a * 256) + b) / 4f).toInt()
    }

    override suspend fun getSpeed(): Int {
        val bytes = queryBytes("010D", minBytes = 3)
        return (bytes[2].toInt() and 0xFF)
    }

    private suspend fun queryBytes(cmd: String, minBytes: Int): ByteArray {
        val raw = communicator.sendCommand(cmd)
        Log.d(TAG, "$cmd raw: ${raw.replace("\r", "\\r")}")
        return when (val parsed = parseResponse(cmd, raw)) {
            is ObdResponse.Bytes -> {
                Log.i(TAG, "$cmd parsed: ${parsed.data.joinToString(" ") { "%02X".format(it) }}")
                if (parsed.data.size < minBytes) {
                    throw IOException("$cmd: short response, got ${parsed.data.size} bytes, expected >= $minBytes")
                }
                parsed.data
            }
            is ObdResponse.Error -> {
                Log.w(TAG, "$cmd parse error: ${parsed.reason}")
                throw IOException("$cmd: ${parsed.reason}")
            }
        }
    }

    private fun parseResponse(cmd: String, raw: String): ObdResponse {
        val lines = raw.split('\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.equals(cmd, ignoreCase = true) || it == "SEARCHING..." }

        if (lines.isEmpty()) return ObdResponse.Error("Empty response")

        lines.firstOrNull { it in ERROR_LITERALS || it.startsWith("BUS INIT:") }
            ?.let { return ObdResponse.Error(it) }

        return if (isMultiFrame(lines)) parseMultiFrame(lines) else parseSingleFrame(lines)
    }

    private fun isMultiFrame(lines: List<String>): Boolean {
        if (lines.size < 2) return false
        if (!HEX_HEADER.matches(lines[0])) return false
        return lines.drop(1).all { FRAME_LINE.matches(it) }
    }

    private fun parseSingleFrame(lines: List<String>): ObdResponse {
        val hex = lines.joinToString("").replace(" ", "")
        return parseHex(hex)
    }

    private fun parseMultiFrame(lines: List<String>): ObdResponse {
        val totalBytes = lines[0].toInt(16)
        val hex = lines.drop(1)
            .joinToString("") { it.substringAfter(":").replace(" ", "") }
        return when (val parsed = parseHex(hex)) {
            is ObdResponse.Bytes -> ObdResponse.Bytes(
                parsed.data.copyOfRange(0, minOf(totalBytes, parsed.data.size))
            )
            is ObdResponse.Error -> parsed
        }
    }

    private fun parseHex(hex: String): ObdResponse {
        if (hex.isEmpty()) return ObdResponse.Error("Empty hex payload")
        if (hex.length % 2 != 0) return ObdResponse.Error("Odd-length hex: $hex")
        return try {
            val bytes = ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            ObdResponse.Bytes(bytes)
        } catch (e: NumberFormatException) {
            ObdResponse.Error("Non-hex characters: $hex")
        }
    }
}
