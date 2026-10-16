@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.brainreels.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import java.time.ZoneId

data class DayKey(val date: LocalDate, val zone: ZoneId)

/**
 * Emits the current local day whenever it changes. Event-driven: sleeps until the next local
 * midnight and also reacts to the system's DATE_CHANGED / TIME_SET / TIMEZONE_CHANGED broadcasts
 * (covers manual clock changes, timezone travel, DST, and deep sleep).
 */
object DayClock {
    fun currentDay(context: Context): Flow<DayKey> {
        val app = context.applicationContext
        return systemTimeChanges(app).onStart { emit(Unit) }.flatMapLatest {
            flow {
                while (true) {
                    val zone = ZoneId.systemDefault()
                    val now = System.currentTimeMillis()
                    emit(DayKey(DateUtils.localDate(now, zone), zone))
                    delay(DateUtils.millisUntilNextDay(now, zone))
                }
            }
        }.distinctUntilChanged()
    }

    private fun systemTimeChanges(context: Context): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
