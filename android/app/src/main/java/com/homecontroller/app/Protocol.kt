package com.homecontroller.app

import java.util.UUID

object Protocol {

    const val DEVICE_NAME = "HomeController"

    val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val WRITE_CHAR = UUID.fromString("4fafc202-1fb5-459e-8fcc-c5c9c331914b")
    val STATUS_CHAR = UUID.fromString("4fafc203-1fb5-459e-8fcc-c5c9c331914b")

    const val CMD_AUTH: Byte = 0xFF.toByte()
    const val CMD_SET: Byte = 0x01
    const val CMD_GET: Byte = 0x02

    const val RSP_AUTH_OK: Byte = 0xA1.toByte()
    const val RSP_SET_OK: Byte = 0xA2.toByte()
    const val RSP_GET_ALL: Byte = 0xA4.toByte()
    const val RSP_STATE_CHANGED: Byte = 0xA5.toByte()
    const val RSP_ERR_UNAUTH: Byte = 0xE3.toByte()
    const val RSP_ERR_PIN: Byte = 0xE4.toByte()

    const val RELAY_COUNT = 8
    const val RELAY_CH_FAN = 0

    val RELAY_NAMES = listOf(
        "Fan", "Light 1", "Light 2", "Light 3",
        "Light 4", "Light 5", "Light 6", "Light 7"
    )

    fun authPacket(pin: Int): ByteArray =
        byteArrayOf(CMD_AUTH, (pin shr 8).toByte(), pin.toByte())

    fun setPacket(channel: Int, on: Boolean): ByteArray =
        byteArrayOf(CMD_SET, channel.toByte(), if (on) 1 else 0)

    val getPacket: ByteArray = byteArrayOf(CMD_GET)

    fun parseStates(data: ByteArray): List<Boolean>? {
        if (data.size != 1 + RELAY_COUNT) return null
        return (1 until data.size).map { data[it] == 1.toByte() }
    }
}