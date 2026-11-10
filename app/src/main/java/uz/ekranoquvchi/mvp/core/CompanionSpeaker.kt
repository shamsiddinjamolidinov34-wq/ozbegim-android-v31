package uz.ekranoquvchi.mvp.core

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import uz.ekranoquvchi.mvp.diag.Diag
import uz.ekranoquvchi.mvp.diag.swallowed
import uz.ekranoquvchi.mvp.tts.EngineChoice
import uz.ekranoquvchi.mvp.tts.TtsActivity
import uz.ekranoquvchi.mvp.tts.TtsText
import uz.ekranoquvchi.mvp.util.Prefs
import uz.ekranoquvchi.mvp.util.SystemUtil
import java.util.Locale

/**
 * Ekran o'quvchi xizmatisiz gapiradigan yordamchi: vaqt va batareya e'loni (TimeAnnouncer) uchun.
 *
 * Muammo (v30): O'zbegim "Afzal ko'rilgan mexanizm" bo'lib, ekran o'quvchi TalkBack (yoki boshqa) bo'lganda, e'lon umuman
 * aytilmasdi. Sabab: e'lon faqat O'zbegimning ekran o'quvchi xizmati (ReaderService) orqali chiqardi, u esa boshqa ekran o'quvchi
 * yoqilganda kutish rejimiga o'tib jim turadi (yoki butunlay o'chirilgan bo'ladi).
 *
 * Yechim: ReaderService yo'q bo'lsa, e'lonni shu yordamchi aytadi. U o'z nutq boshqaruvchisini (SpeechManager) kerak bo'lganda
 * yaratadi, matnni O'zbegimning o'z qoidalari bilan tayyorlaydi (TtsText: raqamlar so'zga, yozuv dvigatelga mos), ovozni
 * telefondagi haqiqiy dvigatel beradi (O'zbegimning o'zi emas: o'ziga ulanish bo'lmaydi), jim turganda esa o'zini bo'shatadi.
 *
 * TalkBack bilan aralashmaslik: TalkBack O'zbegim dvigateli orqali gapirayotgan bo'lsa (tts/TtsActivity), e'lon shu gap
 * tugashini kutadi. "Matn o'qilayotgan bo'lsa kutish" tanlangan bo'lsa eng ko'pi bilan 2 daqiqa, "darhol ayt" tanlangan bo'lsa
 * qisqa tanaffusni (4 soniyagacha) kutadi. TalkBack'ning o'z nutqini to'xtata olmaymiz: bu O'zbegimning ekran o'quvchisi
 * ishlagandagidan farq.
 *
 * Hamma holat o'zgarishi asosiy oqimda.
 */
internal object CompanionSpeaker {
    /** Dvigatel shuncha vaqt jim bo'lgach, e'lon boshlanadi (ms): ijro oxiri sintezdan sal keyin tugaydi. */
    private const val QUIET_MS = 700L

    /** Nutq boshqaruvchisi ishga tushishini kutish (ms): undan keyin matn baribir navbatga qo'yiladi va tayyor bo'lgach aytiladi. */
    private const val READY_WAIT_MS = 10_000L

    /** "Matn o'qilayotgan bo'lsa kutish": eng uzun kutish (ms). */
    private const val WAIT_DEADLINE_MS = 120_000L

    /** "Darhol ayt" yoki qo'lda bosilgan "hozir ayt": tanaffusni shuncha kutamiz, keyin baribir aytamiz (ms). */
    private const val NOW_DEADLINE_MS = 4_000L

    private const val POLL_MS = 300L

    /** Aytib bo'lingach nutq boshqaruvchisi shuncha vaqt saqlanadi, keyin bo'shatiladi (ms). */
    private const val IDLE_RELEASE_MS = 45_000L

    /** Ekran o'chiq paytida e'lon oxirigacha aytilishi uchun qurilma uyg'oq tutiladigan eng ko'p vaqt (ms). */
    private const val WAKE_MS = 40_000L

    /** Alarm signali bilan kelgan e'lon tugashini kutish chegarasi (ms): tizim signalni 10 soniyadan keyin xato deb hisoblaydi. */
    private const val HOLD_BROADCAST_MS = 9_000L

    private val main = Handler(Looper.getMainLooper())
    private var speech: SpeechManager? = null
    private var wake: PowerManager.WakeLock? = null

    /** Aytilishi kutilayotgan e'lonlar soni (navbatga qo'yilgan, hali nutq boshqaruvchisiga berilmagan). */
    private var queued = 0
    private val releaseTask = Runnable { releaseIfIdle() }

    /**
     * Shu jarayonda e'lonni aytishga haqli holat: O'zbegim afzal ko'rilgan mexanizm (ekran o'quvchi TalkBack bo'lishi mumkin)
     * yoki O'zbegimning ekran o'quvchi xizmati sozlamalarda yoqilgan (jarayon yangi ishga tushgan, xizmat hali ulanmagan bo'lishi
     * mumkin). Ikkalasi ham yo'q bo'lsa, O'zbegim e'lon qilmaydi: foydalanuvchi uni ishlatmayapti.
     */
    fun eligible(app: Context): Boolean = try {
        EngineChoice.isDefault(app) || SystemUtil.isServiceEnabled(app)
    } catch (ex: Throwable) {
        swallowed("CompanionSpeaker.eligible", ex)
        false
    }

    /**
     * Matnni aytadi (ReaderService bo'lmasa). wait=true: boshqa nutq tugashini kutadi (eng ko'pi bilan 2 daqiqa),
     * false: qisqa tanaffusni kutib, keyin aytadi va o'zining oldingi e'lonini to'xtatadi.
     */
    fun say(app: Context, text: String, wait: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { say(app, text, wait) }
            return
        }
        if (text.isBlank() || !eligible(app)) return
        if (ensure(app) == null) return
        holdAwake(app)
        queued += 1
        val now = SystemClock.uptimeMillis()
        attempt(app, text, wait, now + READY_WAIT_MS, now + if (wait) WAIT_DEADLINE_MS else NOW_DEADLINE_MS)
    }

    private fun ensure(app: Context): SpeechManager? {
        speech?.let { return it }
        return try {
            // Jarayon faqat alarm signali bilan ishga tushgan bo'lishi mumkin: jurnal oldindan tayyorlanadi (SpeechManager unga yozadi)
            Diag.init(app)
            SpeechManager(app).also { speech = it }
        } catch (ex: Throwable) {
            swallowed("CompanionSpeaker.ensure", ex)
            null
        }
    }

    private fun attempt(app: Context, text: String, wait: Boolean, readyBy: Long, deadline: Long) {
        val sp = speech
        if (sp == null) {
            // Bu orada bo'shatilgan yoki to'xtatilgan: e'lon yo'qoladi, lekin hisob buzilmaydi
            queued = maxOf(0, queued - 1)
            return
        }
        val now = SystemClock.uptimeMillis()
        if (!sp.isReady && now < readyBy) {
            main.postDelayed({ attempt(app, text, wait, readyBy, deadline) }, POLL_MS)
            return
        }
        val busy = sp.isBusy() || !TtsActivity.isQuiet(QUIET_MS)
        if (busy && now < deadline) {
            main.postDelayed({ attempt(app, text, wait, readyBy, deadline) }, POLL_MS)
            return
        }
        queued = maxOf(0, queued - 1)
        speakNow(app, sp, text, interrupt = !wait)
    }

    private fun speakNow(app: Context, sp: SpeechManager, text: String, interrupt: Boolean) {
        val prepared = try {
            // Matn tayyorlanishi dvigatelga bog'liq (yozuv, raqamlar): dvigatel ishga tushgach aniq nomi ma'lum bo'ladi
            val locale = if (Prefs.isUzbekText(app)) Locale.forLanguageTag("uz-UZ") else Locale.getDefault()
            val engine = sp.effectiveEngine ?: EngineChoice.backendPackage(app)
            TtsText.prepare(app, text, locale, engine, Prefs.ttsRules(app))
        } catch (ex: Throwable) {
            swallowed("CompanionSpeaker.prepare", ex)
            text
        }
        sp.speak(prepared, interrupt, 0L, false, false)
        main.removeCallbacks(releaseTask)
        main.postDelayed(releaseTask, IDLE_RELEASE_MS)
    }

    /** Nutq boshqaruvchisi bo'sh bo'lsa bo'shatiladi (xotira va dvigatel bilan ulanish bekor band bo'lmasin). */
    private fun releaseIfIdle() {
        val sp = speech
        if (sp == null) {
            releaseWake()
            return
        }
        if (queued > 0 || sp.isBusy()) {
            main.postDelayed(releaseTask, 5_000L)
            return
        }
        speech = null
        try {
            sp.shutdown()
        } catch (ex: Throwable) {
            swallowed("CompanionSpeaker.release", ex)
        }
        releaseWake()
    }

    private fun holdAwake(app: Context) {
        try {
            val w = wake ?: (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ozbegim:announce")
                .also {
                    it.setReferenceCounted(false)
                    wake = it
                }
            w.acquire(WAKE_MS)
        } catch (ex: Throwable) {
            swallowed("CompanionSpeaker.holdAwake", ex)
        }
    }

    private fun releaseWake() {
        try {
            wake?.let { if (it.isHeld) it.release() }
        } catch (ex: Throwable) {
            swallowed("CompanionSpeaker.releaseWake", ex)
        }
    }

    /** Hozir e'lon aytilmoqda yoki navbatda. */
    fun isWorking(): Boolean = queued > 0 || speech?.isBusy() == true

    /**
     * Alarm signali (TimeAlarmReceiver) bilan kelgan e'lon tugamaguncha (eng ko'pi bilan 9 soniya) signal "tugamagan" deb
     * ushlab turiladi: jarayon signal uchun yangi ishga tushgan bo'lsa, dvigatel ishga tushib gapirib bo'lgunicha o'chirilib
     * qo'yilmasligi uchun. Hech narsa aytilmayotgan bo'lsa, darhol tugatiladi.
     */
    fun finishWhenQuiet(pending: BroadcastReceiver.PendingResult) {
        val start = SystemClock.uptimeMillis()
        val check = object : Runnable {
            override fun run() {
                val over = SystemClock.uptimeMillis() - start >= HOLD_BROADCAST_MS
                if (over || !isWorking()) {
                    try {
                        pending.finish()
                    } catch (ex: Throwable) {
                        swallowed("CompanionSpeaker.finish", ex)
                    }
                } else {
                    main.postDelayed(this, 200L)
                }
            }
        }
        main.post(check)
    }
}
