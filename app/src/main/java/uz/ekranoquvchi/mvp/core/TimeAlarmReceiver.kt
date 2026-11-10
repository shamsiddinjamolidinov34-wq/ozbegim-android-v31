package uz.ekranoquvchi.mvp.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.ekranoquvchi.mvp.diag.swallowed

/** Tizim signali (AlarmManager) kelganda vaqt e'lonini ishga tushiradi: qurilma uxlayotgan bo'lsa ham uyg'otadi. */
class TimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Jarayon shu signal uchun yangi ishga tushgan bo'lishi mumkin: e'lon (dvigatel ishga tushishi bilan) bir necha soniya
        // oladi, signal esa onReceive qaytishi bilan tugagan hisoblanadi. goAsync e'lon aytilib bo'lguncha (eng ko'pi bilan
        // 9 soniya) jarayonni tirik tutadi.
        val pending = try {
            goAsync()
        } catch (ex: Throwable) {
            swallowed("TimeAlarmReceiver.goAsync", ex)
            null
        }
        try {
            TimeAnnouncer.onAlarm(context)
        } finally {
            if (pending != null) CompanionSpeaker.finishWhenQuiet(pending)
        }
    }
}
