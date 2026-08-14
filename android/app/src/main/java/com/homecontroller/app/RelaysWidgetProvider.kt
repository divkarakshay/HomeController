package com.homecontroller.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/** Home-screen widget: 8 rows, one per channel, tap a row to toggle. */
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
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.relays_widget)
            for (ch in 0 until Protocol.RELAY_COUNT) {
                val name = store.name(ch)
                val on = holder.states.getOrElse(ch) { false }
                views.setTextViewText(rowNameId(ch), name)
                views.setTextViewText(rowStateId(ch), if (on) "ON" else "OFF")
                views.setTextColor(
                    rowStateId(ch),
                    if (on) COLOR_ON else COLOR_OFF
                )
                val base = Intent(context, RelaysWidgetProvider::class.java)
                    .setAction(ACTION_TOGGLE)
                    .putExtra(EXTRA_CHANNEL, ch)
                    .setData(Uri.parse("hc://w/$id/$ch"))
                val pi = PendingIntent.getBroadcast(
                    context, id * 10 + ch, base,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(rowId(ch), pi)
            }
            manager.updateAppWidget(id, views)
        }
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

    companion object {
        const val ACTION_TOGGLE = "com.homecontroller.app.WIDGET_TOGGLE"
        const val ACTION_REFRESH = "com.homecontroller.app.WIDGET_REFRESH"
        const val EXTRA_CHANNEL = "channel"

        private const val COLOR_ON = 0xFF34C759.toInt()
        private const val COLOR_OFF = 0xFF8E979F.toInt()

        fun refresh(context: Context) {
            context.sendBroadcast(Intent(context, RelaysWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }
}