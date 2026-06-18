package com.shinochanwww.touch

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets

private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
private val STATUS_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
private val STATUS_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
private val SELECT_TOUCH_AID = byteArrayOf(
    0x00.toByte(),
    0xA4.toByte(),
    0x04.toByte(),
    0x00.toByte(),
    0x07.toByte(),
    0xF0.toByte(),
    0x39.toByte(),
    0x41.toByte(),
    0x48.toByte(),
    0x14.toByte(),
    0x81.toByte(),
    0x00.toByte(),
    0x00.toByte()
)

class NfcTapService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || !commandApdu.contentEquals(SELECT_TOUCH_AID)) {
            return STATUS_NOT_FOUND
        }
        val payload = currentPayload ?: return STATUS_CONDITIONS_NOT_SATISFIED
        return payload.toByteArray(StandardCharsets.UTF_8) + STATUS_SUCCESS
    }

    override fun onDeactivated(reason: Int) = Unit

    companion object {
        private var currentPayload: String? = null

        fun setPayload(payload: String) {
            currentPayload = payload
        }

        fun clearPayload() {
            currentPayload = null
        }
    }
}

object TouchNfcProtocol {
    val selectAidCommand: ByteArray = SELECT_TOUCH_AID.copyOf()

    fun stripSuccessStatus(response: ByteArray): String? {
        if (response.size <= STATUS_SUCCESS.size) {
            return null
        }
        val statusStart = response.size - STATUS_SUCCESS.size
        val status = response.copyOfRange(statusStart, response.size)
        if (!status.contentEquals(STATUS_SUCCESS)) {
            return null
        }
        return response.copyOfRange(0, statusStart).toString(StandardCharsets.UTF_8)
    }
}
