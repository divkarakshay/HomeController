package com.homecontroller.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

data class ChannelIcon(val key: String, val label: String, val icon: ImageVector)

object ChannelCatalog {

    val options = listOf(
        ChannelIcon("bulb", "Bulb", Icons.Filled.Lightbulb),
        ChannelIcon("ac", "AC", Icons.Filled.AcUnit),
        ChannelIcon("tv", "TV", Icons.Filled.Tv),
        ChannelIcon("kitchen", "Kitchen", Icons.Filled.Kitchen),
        ChannelIcon("music", "Music", Icons.Filled.MusicNote),
        ChannelIcon("power", "Power", Icons.Filled.Power),
        ChannelIcon("home", "Home", Icons.Filled.Home),
        ChannelIcon("star", "Star", Icons.Filled.Star)
    )

    fun byKey(key: String): ChannelIcon = options.firstOrNull { it.key == key } ?: options.first()
}

class ChannelStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("channels", Context.MODE_PRIVATE)

    fun name(ch: Int): String = prefs.getString("name_$ch", null) ?: Protocol.RELAY_NAMES[ch]

    fun setName(ch: Int, value: String) {
        val cleaned = value.trim().ifEmpty { Protocol.RELAY_NAMES[ch] }
        prefs.edit().putString("name_$ch", cleaned).apply()
    }

    fun iconKey(ch: Int): String =
        prefs.getString("icon_$ch", null) ?: if (ch == Protocol.RELAY_CH_FAN) "ac" else "bulb"

    fun setIconKey(ch: Int, key: String) {
        prefs.edit().putString("icon_$ch", key).apply()
    }
}