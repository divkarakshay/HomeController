package com.homecontroller.app

import java.util.UUID

object Protocol {

    const val DEVICE_NAME = "HomeController"

    val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val WRITE_CHAR = UUID.fromString("4fafc202-1fb5-459e-8fcc-c5c9c331914b")
    val STATUS_CHAR = UUID.fromString("4fafc203-1fb5-459e-8fcc-c5c9c331914b")
    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val CMD_SET: Byte = 0x01
    const val CMD_GET: Byte = 0x02
    const val CMD_SET_MODE: Byte = 0x03
    const val CMD_GET_MODE: Byte = 0x04
    const val CMD_SET_TIMER: Byte = 0x05
    const val CMD_SET_EPOCH: Byte = 0x06
    const val CMD_OTA_BEGIN: Byte = 0x07
    const val CMD_OTA_WRITE: Byte = 0x08
    const val CMD_OTA_END: Byte = 0x09
    const val CMD_SET_FAN_SPEED: Byte = 0x0A
    const val CMD_GET_FAN_SPEED: Byte = 0x0B
    const val CMD_GET_RULES: Byte = 0x0C
    const val CMD_SET_RULE: Byte = 0x0D
    const val CMD_GET_TIME: Byte = 0x0E
    const val CMD_GET_TIMERS: Byte = 0x0F
    const val CMD_DEL_RULE: Byte = 0x10

    const val RSP_SET_OK: Byte = 0xA2.toByte()
    const val RSP_GET_ALL: Byte = 0xA4.toByte()
    const val RSP_STATE_CHANGED: Byte = 0xA5.toByte()
    const val RSP_MODE: Byte = 0xA6.toByte()
    const val RSP_TIMER_OK: Byte = 0xA7.toByte()
    const val RSP_OTA_BEGIN: Byte = 0xA8.toByte()
    const val RSP_OTA_ACK: Byte = 0xAD.toByte()
    const val RSP_OTA_RESEND: Byte = 0xAC.toByte()
    const val RSP_OTA_END: Byte = 0xAA.toByte()
    const val RSP_OTA_CRC_ERR: Byte = 0xAB.toByte()
    const val RSP_FAN_SPEED: Byte = 0xAE.toByte()
    const val RSP_FAN_BUSY: Byte = 0xAF.toByte()
    const val RSP_RULES: Byte = 0xB0.toByte()
    const val RSP_TIME: Byte = 0xB2.toByte()
    const val RSP_TIMERS: Byte = 0xB3.toByte()

    const val FAN_LEVELS = 6
    const val FAN_LEVEL_BOOST = 6

    const val MODE_RESTORE_LAST = 0
    const val MODE_ALL_OFF = 1

    const val RELAY_COUNT = 8
    const val RELAY_CH_FAN = 0

    val RELAY_NAMES = listOf(
        "Fan", "Light 1", "Light 2", "Light 3",
        "Light 4", "Light 5", "Light 6", "Light 7"
    )

    fun setPacket(channel: Int, on: Boolean): ByteArray =
        byteArrayOf(CMD_SET, channel.toByte(), if (on) 1 else 0)

    val getPacket: ByteArray = byteArrayOf(CMD_GET)

    fun setModePacket(mode: Int): ByteArray = byteArrayOf(CMD_SET_MODE, mode.toByte())

    val getModePacket: ByteArray = byteArrayOf(CMD_GET_MODE)

    fun timerPacket(channel: Int, minutes: Int, active: Boolean): ByteArray =
        byteArrayOf(
            CMD_SET_TIMER,
            channel.toByte(),
            ((minutes shr 8) and 0xFF).toByte(),
            (minutes and 0xFF).toByte(),
            if (active) 1 else 0
        )

    fun epochPacket(epochSeconds: Long): ByteArray =
        byteArrayOf(
            CMD_SET_EPOCH,
            (epochSeconds and 0xFF).toByte(),
            ((epochSeconds shr 8) and 0xFF).toByte(),
            ((epochSeconds shr 16) and 0xFF).toByte(),
            ((epochSeconds shr 24) and 0xFF).toByte()
        )

    data class TimerInfo(val channel: Int, val minutes: Int, val active: Boolean)

    fun parseTimer(data: ByteArray): TimerInfo? {
        if (data.size != 5) return null
        val ch = data[1].toInt()
        if (ch !in 0 until RELAY_COUNT) return null
        val minutes = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        return TimerInfo(ch, minutes, data[4] == 1.toByte())
    }

    const val OTA_CHUNK_SIZE = 16
    const val OTA_MAX_SIZE = 0x1C0000

    fun otaBeginPacket(size: Int): ByteArray =
        byteArrayOf(
            CMD_OTA_BEGIN,
            (size and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 24) and 0xFF).toByte()
        )

    fun otaWritePacket(seq: Int, data: ByteArray): ByteArray =
        ByteArray(3 + data.size).also { out ->
            out[0] = CMD_OTA_WRITE
            out[1] = ((seq shr 8) and 0xFF).toByte()
            out[2] = (seq and 0xFF).toByte()
            data.copyInto(out, 3)
        }

    fun otaEndPacket(crc: Long): ByteArray =
        byteArrayOf(
            CMD_OTA_END,
            (crc and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
            ((crc shr 16) and 0xFF).toByte(),
            ((crc shr 24) and 0xFF).toByte()
        )

    fun parseStates(data: ByteArray): List<Boolean>? {
        if (data.size != 1 + RELAY_COUNT) return null
        return (1 until data.size).map { data[it] == 1.toByte() }
    }

    fun setFanSpeedPacket(level: Int): ByteArray =
        byteArrayOf(CMD_SET_FAN_SPEED, level.toByte())

    val getFanSpeedPacket: ByteArray = byteArrayOf(CMD_GET_FAN_SPEED)

    fun parseFanSpeed(data: ByteArray): Int? {
        if (data.size != 2) return null
        val level = data[1].toInt()
        return if (level in 1..FAN_LEVELS) level else null
    }

    fun parseFanBusy(data: ByteArray): Boolean? {
        if (data.size != 2) return null
        return data[1] == 1.toByte()
    }

    const val RULE_COUNT = 8
    const val RULE_ACTION_OFF = 0
    const val RULE_ACTION_ON = 1

    data class TimeRule(
        val index: Int,
        val enabled: Boolean,
        val channel: Int,
        val action: Int,
        val hour: Int,
        val minute: Int,
        val days: Int // bit0=Mon..bit6=Sun, 0 = every day
    ) {
        fun isDefault() = !enabled && channel == 0 && action == RULE_ACTION_OFF &&
            hour == 0 && minute == 0 && days == 0
    }

    val getRulesPacket: ByteArray = byteArrayOf(CMD_GET_RULES)

    fun setRulePacket(rule: TimeRule): ByteArray =
        byteArrayOf(
            CMD_SET_RULE,
            rule.index.toByte(),
            if (rule.enabled) 1 else 0,
            rule.channel.toByte(),
            rule.action.toByte(),
            rule.hour.toByte(),
            rule.minute.toByte(),
            rule.days.toByte()
        )

    fun deleteRulePacket(rule: TimeRule): ByteArray =
        byteArrayOf(CMD_DEL_RULE, rule.index.toByte())

    fun parseRules(data: ByteArray): List<TimeRule>? {
        if (data.size != 1 + RULE_COUNT * 6) return null
        return (0 until RULE_COUNT).map { i ->
            val o = 1 + i * 6
            TimeRule(
                index = i,
                enabled = data[o] == 1.toByte(),
                channel = data[o + 1].toInt(),
                action = data[o + 2].toInt(),
                hour = data[o + 3].toInt(),
                minute = data[o + 4].toInt(),
                days = data[o + 5].toInt()
            )
        }
    }

    val getTimePacket: ByteArray = byteArrayOf(CMD_GET_TIME)

    fun parseTime(data: ByteArray): Pair<Long, Boolean>? {
        if (data.size != 6) return null
        val epoch = (data[1].toLong() and 0xFF) or
            ((data[2].toLong() and 0xFF) shl 8) or
            ((data[3].toLong() and 0xFF) shl 16) or
            ((data[4].toLong() and 0xFF) shl 24)
        return epoch to (data[5] == 1.toByte())
    }

    val getTimersPacket: ByteArray = byteArrayOf(CMD_GET_TIMERS)

    fun parseTimers(data: ByteArray): Map<Int, TimerInfo>? {
        if (data.size != 1 + RELAY_COUNT * 2) return null
        return buildMap {
            (0 until RELAY_COUNT).forEach { ch ->
                val minutes = ((data[1 + 2 * ch].toInt() and 0xFF) shl 8) or
                    (data[2 + 2 * ch].toInt() and 0xFF)
                if (minutes > 0) put(ch, TimerInfo(ch, minutes, true))
            }
        }
    }
}