package com.homecontroller.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

/** Home-screen widget: status header, one row per channel (icon, name,
 *  state, quick off-timer), tap a row to toggle, tap the alarm to cycle
 *  a 15/30/60-minute off-timer. */
class RelaysWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RelayHolder.init(context.applicationContext)
        RelayService.start(context)
        if (!RelayHolder.state.value.connected) {
            RelayHolder.connect()
        }
        render(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                RelayHolder.init(context.applicationContext)
                val channel = intent.getIntExtra(EXTRA_CHANNEL, -1)
                if (channel in 0 until Protocol.RELAY_COUNT) {
                    val on = RelayHolder.state.value.states.getOrElse(channel) { false }
                    RelayHolder.toggleRelay(channel, !on)
                }
            }
            ACTION_TIMER -> {
                RelayHolder.init(context.applicationContext)
                val channel = intent.getIntExtra(EXTRA_CHANNEL, -1)
                if (channel in 0 until Protocol.RELAY_COUNT && channel != Protocol.RELAY_CH_FAN) {
                    val current = RelayHolder.state.value.timers[channel]?.minutes ?: 0
                    RelayHolder.setTimer(channel, nextTimerMinutes(current), nextTimerMinutes(current) > 0)
                }
            }
            ACTION_FAN -> {
                RelayHolder.init(context.applicationContext)
                val on = RelayHolder.state.value.states.getOrElse(Protocol.RELAY_CH_FAN) { false }
                RelayHolder.toggleRelay(Protocol.RELAY_CH_FAN, !on)
            }
            ACTION_REFRESH -> {
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, RelaysWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    RelayHolder.init(context.applicationContext)
                    render(context, AppWidgetManager.getInstance(context), ids)
                }
            }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val holder = RelayHolder.state.value
        val store = ChannelStore(context.applicationContext)
        val iconCache = mutableMapOf<String, Int>()
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.relays_widget)

            val status: String
            val statusColor: Int
            val dotColor: Int
            when {
                holder.connected -> {
                    status = if (holder.timeSynced) {
                        "Connected · %02d:%02d"
                            .format(holder.deviceEpoch / 3600 % 24, holder.deviceEpoch % 3600 / 60)
                    } else "Connected"
                    statusColor = COLOR_GREEN
                    dotColor = COLOR_GREEN
                }
                holder.connecting -> {
                    status = "Connecting…"
                    statusColor = COLOR_ORANGE
                    dotColor = COLOR_ORANGE
                }
                else -> {
                    status = "Offline"
                    statusColor = COLOR_RED
                    dotColor = COLOR_RED
                }
            }
            views.setTextViewText(R.id.w_status, status)
            views.setTextColor(R.id.w_status, statusColor)
            views.setInt(R.id.w_dot, "setColorFilter", dotColor)

            for (ch in 0 until Protocol.RELAY_COUNT) {
                val name = store.name(ch)
                val on = holder.states.getOrElse(ch) { false }
                val pending = holder.timers[ch]?.takeIf { it.active }?.minutes ?: 0
                views.setTextViewText(rowNameId(ch), name)

                val isFan = ch == Protocol.RELAY_CH_FAN
                val stateText: String
                val stateColor: Int
                if (isFan) {
                    val level = holder.fanLevel
                    stateText = when {
                        !on -> "Off"
                        level > 0 -> "Speed $level"
                        else -> "On"
                    }
                    stateColor = if (on) COLOR_GREEN else COLOR_OFF
                    views.setTextColor(rowStateId(ch), if (on) COLOR_GREEN else COLOR_OFF)
                } else {
                    stateText = if (on) (if (pending > 0) "ON · ${pending}m" else "ON") else "OFF"
                    stateColor = if (on) COLOR_GREEN else COLOR_OFF
                    views.setTextColor(rowStateId(ch), stateColor)
                }
                views.setTextViewText(rowStateId(ch), stateText)

                val iconRes = iconCache.getOrPut(store.iconKey(ch)) {
                    mapIconRes(store.iconKey(ch))
                }
                views.setImageViewResource(rowIconId(ch), iconRes)
                views.setInt(rowIconId(ch), "setColorFilter", if (on) COLOR_GREEN else Color.WHITE)

                val base = Intent(context, RelaysWidgetProvider::class.java)
                    .setData(Uri.parse("hc://w/$id/$ch"))
                val togglePi = PendingIntent.getBroadcast(
                    context, id * 100 + ch,
                    base.setAction(ACTION_TOGGLE).putExtra(EXTRA_CHANNEL, ch),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(rowId(ch), togglePi)

                if (isFan) {
                    val fanPi = PendingIntent.getBroadcast(
                        context, id * 100 + ch,
                        Intent(base).setAction(ACTION_FAN),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(rowId(ch), fanPi)
                    views.setViewVisibility(rowTimerId(ch), View.GONE)
                } else {
                    views.setImageViewResource(rowTimerId(ch), R.drawable.ic_w_alarm)
                    views.setViewVisibility(rowTimerId(ch), View.VISIBLE)
                    views.setInt(rowTimerId(ch), "setColorFilter", if (pending > 0) COLOR_GREEN else COLOR_OFF)
                    val timerPi = PendingIntent.getBroadcast(
                        context, id * 100 + 50 + ch,
                        Intent(base).setAction(ACTION_TIMER).putExtra(EXTRA_CHANNEL, ch),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(rowTimerId(ch), timerPi)
                    views.setContentDescription(
                        rowTimerId(ch),
                        if (pending > 0) "Off-timer $pending minutes" else "Set off-timer"
                    )
                }
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun mapIconRes(key: String): Int = when (key) {
        "ac" -> R.drawable.ic_w_ac
        "tv" -> R.drawable.ic_w_tv
        "kitchen" -> R.drawable.ic_w_kitchen
        "music" -> R.drawable.ic_w_music
        "power" -> R.drawable.ic_w_power
        "home" -> R.drawable.ic_w_home
        "star" -> R.drawable.ic_w_star
        else -> R.drawable.ic_w_bulb
    }

    private fun nextTimerMinutes(current: Int): Int = when {
        current < 15 -> 15
        current < 30 -> 30
        current < 60 -> 60
        else -> 0
    }

    private fun rowId(ch: Int): Int =
        when (ch) {
            0 -> R.id.w_row_0; 1 -> R.id.w_row_1; 2 -> R.id.w_row_2; 3 -> R.id.w_row_3
            4 -> R.id.w_row_4; 5 -> R.id.w_row_5; 6 -> R.id.w_row_6; else -> R.id.w_row_7
        }

    private fun rowNameId(ch: Int): Int =
        when (ch) {
            0 -> R.id.w_name_0; 1 -> R.id.w_name_1; 2 -> R.id.w_name_2; 3 -> R.id.w_name_3
            4 -> R.id.w_name_4; 5 -> R.id.w_name_5; 6 -> R.id.w_name_6; else -> R.id.w_name_7
        }

    private fun rowStateId(ch: Int): Int =
        when (ch) {
            0 -> R.id.w_state_0; 1 -> R.id.w_state_1; 2 -> R.id.w_state_2; 3 -> R.id.w_state_3
            4 -> R.id.w_state_4; 5 -> R.id.w_state_5; 6 -> R.id.w_state_6; else -> R.id.w_state_7
        }

    private fun rowIconId(ch: Int): Int =
        when (ch) {
            0 -> R.id.w_icon_0; 1 -> R.id.w_icon_1; 2 -> R.id.w_icon_2; 3 -> R.id.w_icon_3
            4 -> R.id.w_icon_4; 5 -> R.id.w_icon_5; 6 -> R.id.w_icon_6; else -> R.id.w_icon_7
        }

    private fun rowTimerId(ch: Int): Int =
        when (ch) {
            0 -> R.id.w_timer_0; 1 -> R.id.w_timer_1; 2 -> R.id.w_timer_2; 3 -> R.id.w_timer_3
            4 -> R.id.w_timer_4; 5 -> R.id.w_timer_5; 6 -> R.id.w_timer_6; else -> R.id.w_timer_7
        }

    companion object {
        const val ACTION_TOGGLE = "com.homecontroller.app.WIDGET_TOGGLE"
        const val ACTION_TIMER = "com.homecontroller.app.WIDGET_TIMER"
        const val ACTION_FAN = "com.homecontroller.app.WIDGET_FAN"
        const val ACTION_REFRESH = "com.homecontroller.app.WIDGET_REFRESH"
        const val EXTRA_CHANNEL = "channel"

        private const val COLOR_GREEN = 0xFF34C759.toInt()
        private const val COLOR_ORANGE = 0xFFFFA046.toInt()
        private const val COLOR_OFF = 0xFF8E979F.toInt()
        private const val COLOR_RED = 0xFFFF6B5E.toInt()

        fun refresh(context: Context) {
            context.sendBroadcast(Intent(context, RelaysWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }
}