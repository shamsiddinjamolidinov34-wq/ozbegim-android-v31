package uz.ekranoquvchi.mvp.tts

import android.os.SystemClock

/**
 * O'zbegimning tizim dvigateli (OzbegimTtsService) hozir matn sintez qilyaptimi: bir jarayon ichidagi oddiy hisoblagich.
 *
 * Nima uchun kerak: O'zbegim "Afzal ko'rilgan mexanizm" bo'lib, ekran o'quvchi TalkBack (yoki boshqa) bo'lganda, vaqt va
 * batareya e'lonlarini (core/TimeAnnouncer) O'zbegim o'zi aytadi. Ular ekran o'quvchining gapiga aralashib ketmasligi uchun,
 * dvigatel band bo'lsa, jim bo'lishini kutadi. Dvigatel va e'lon bitta jarayonda ishlaydi, shuning uchun alohida
 * ulanish yoki ruxsat kerak emas.
 *
 * Hisoblagich sintez boshlanganda oshadi, tugaganda (xato yoki bekor qilishda ham) kamayadi: OzbegimTtsService
 * buni try/finally bilan kafolatlaydi.
 */
object TtsActivity {
    private val lock = Any()
    private var active = 0
    private var lastEndAt = 0L

    fun begin() {
        synchronized(lock) { active += 1 }
    }

    fun end() {
        synchronized(lock) {
            if (active > 0) active -= 1
            lastEndAt = SystemClock.uptimeMillis()
        }
    }

    /**
     * Dvigatel hozir sintez qilmayapti va oxirgi sintez tugaganiga kamida quietMs o'tgan bo'lsa true.
     * Sintez tugashi bilan ijro oxirigacha yetmagan bo'lishi mumkin (tizim ovozni biroz oldinroq oladi), shuning uchun
     * qisqa kutish muddati qo'shiladi.
     */
    fun isQuiet(quietMs: Long): Boolean = synchronized(lock) {
        active == 0 && (lastEndAt == 0L || SystemClock.uptimeMillis() - lastEndAt >= quietMs)
    }
}
