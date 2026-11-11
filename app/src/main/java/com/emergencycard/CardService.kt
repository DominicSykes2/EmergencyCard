// Copyright (c) 2026 Dominic Sykes. All rights reserved.
package com.emergencycard

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Makes this phone look like an NFC Forum "Type 4" tag holding the card as a text record,
 * so any NFC phone that taps it can read the card. Read-only. Only answers when sharing is switched on.
 */
class CardService : HostApduService() {

    private var selected = 0 // 0 = nothing, 1 = capability container, 2 = NDEF file
    private var ndefFile = ByteArray(2)

    override fun processCommandApdu(apdu: ByteArray?, extras: Bundle?): ByteArray {
        if (apdu == null || apdu.size < 4) return SW_ERROR
        val ins = apdu[1].toInt() and 0xFF
        when (ins) {
            0xA4 -> { // SELECT
                val p1 = apdu[2].toInt() and 0xFF
                val lc = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
                val data = if (lc > 0 && apdu.size >= 5 + lc) apdu.copyOfRange(5, 5 + lc) else ByteArray(0)
                if (p1 == 0x04) { // select the application
                    if (!data.contentEquals(AID)) return SW_NOT_FOUND
                    if (!Cards.nfcOn(this)) return SW_NOT_FOUND
                    ndefFile = buildNdefFile()
                    selected = 0
                    return SW_OK
                }
                if (p1 == 0x00) { // select a file
                    if (data.contentEquals(CC_ID)) {
                        selected = 1
                        return SW_OK
                    }
                    if (data.contentEquals(NDEF_ID)) {
                        selected = 2
                        return SW_OK
                    }
                }
                return SW_NOT_FOUND
            }
            0xB0 -> { // READ BINARY
                val file = when (selected) {
                    1 -> CC_FILE
                    2 -> ndefFile
                    else -> return SW_NOT_ALLOWED
                }
                val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
                var le = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
                if (le == 0) le = 256
                if (offset > file.size) return SW_WRONG_OFFSET
                val end = minOf(file.size, offset + le)
                return file.copyOfRange(offset, end) + SW_OK
            }
        }
        return SW_ERROR
    }

    override fun onDeactivated(reason: Int) {
        selected = 0
    }

    private fun buildNdefFile(): ByteArray {
        var text = Cards.toText(Cards.load(this)).take(3000)
        while (text.toByteArray().size > 3800) text = text.dropLast(100)
        val msg = NdefMessage(NdefRecord.createTextRecord("en", text)).toByteArray()
        val out = ByteArray(2 + msg.size)
        out[0] = (msg.size shr 8).toByte()
        out[1] = msg.size.toByte()
        System.arraycopy(msg, 0, out, 2, msg.size)
        return out
    }

    companion object {
        private val AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        private val CC_ID = byteArrayOf(0xE1.toByte(), 0x03)
        private val NDEF_ID = byteArrayOf(0xE1.toByte(), 0x04)

        // Capability container: 15 bytes, NDEF file E104, up to 4094 bytes, read-only.
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F, 0x20, 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(),
            0x04, 0x06, 0xE1.toByte(), 0x04, 0x0F, 0xFE.toByte(), 0x00, 0xFF.toByte()
        )

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_ERROR = byteArrayOf(0x6F, 0x00)
        private val SW_NOT_ALLOWED = byteArrayOf(0x69, 0x85.toByte())
        private val SW_WRONG_OFFSET = byteArrayOf(0x6B, 0x00)
    }
}
