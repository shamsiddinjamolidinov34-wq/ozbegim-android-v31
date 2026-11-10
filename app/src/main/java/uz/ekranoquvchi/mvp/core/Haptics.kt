package uz.ekranoquvchi.mvp.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.util.Prefs

/**
 * Bosish tasdig'i uchun qisqa tebranish (TalkBack'da ham shunday: element faollashtirilganda bir zum titraydi).
 * v30 gacha ikki marta bosishdan keyin hech qanday belgi yo'q edi: tugma nomi o'zgarmasa, foydalanuvchi "bosilmadi" deb o'ylab
 * qayta bosardi. Sozlamalarda o'chirish mumkin (Prefs.HAPTIC, standart: yoqiq).
 */
object Haptics {
    private const val TICK_MS = 18L

    @Volatile
    private var vibrator: Vibrator? = null

    private fun vibrator(c: Context): Vibrator? {
        val cached = vibrator
        if (cached != null) return cached
        val v: Vibrator? = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (ex: Throwable) {
            swallowed("Haptics.vibrator", ex)
            null
        }
        vibrator = v
        return v
    }

    /** Qisqa "bosildi" tebranishi. Sozlamada o'chirilgan yoki qurilmada tebratgich bo'lmasa, hech narsa qilmaydi. */
    fun click(c: Context) {
        if (!Prefs.haptic(c)) return
        try {
            val v = vibrator(c) ?: return
            if (!v.hasVibrator()) return
            val effect = if (Build.VERSION.SDK_INT >= 29) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        } catch (ex: Throwable) {
            swallowed("Haptics.click", ex)
        }
    }
}
