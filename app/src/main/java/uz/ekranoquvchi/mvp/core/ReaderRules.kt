package uz.ekranoquvchi.mvp.core

/**
 * Boshqa ekran o'quvchini aniqlash qoidalari. Bu fayl Android sinflariga bog'liq emas (toza Kotlin), shuning uchun
 * qoidalar telefonsiz, oddiy testda tekshiriladi. Tizimdan ma'lumot yig'ish OtherReaders'da, qaror shu yerda.
 *
 * Asosiy g'oya: qaror "paket nomi" bo'yicha emas, "xizmat" (paket + sinf) bo'yicha qilinadi. Google'ning "Android
 * Accessibility Suite" paketida TalkBack bilan birga Select to Speak, Switch Access, Accessibility Menu kabi xizmatlar ham
 * bor. Ular teginib o'rganishni egallamaydi, shuning uchun ulardan biri yoqilgani O'zbegimni jim qilib qo'ymasligi kerak
 * (ilgari paket nomi tanish bo'lgani uchun shunday bo'lardi).
 */
object ReaderRules {
    /** Tizim ro'yxatidagi bitta yoqilgan xizmat: kerakli xususiyatlar OtherReaders'da oldindan hisoblab beriladi. */
    class Service(
        /** Xizmat paketi. */
        val pkg: String,
        /** Xizmatning to'liq sinf nomi (bo'sh bo'lishi mumkin). */
        val cls: String,
        /** Xizmat hozir teginib o'rganishni talab qilyapti (FLAG_REQUEST_TOUCH_EXPLORATION_MODE yoniq). */
        val exploring: Boolean,
    )

    /**
     * Teginib o'rganishni egallamaydigan yordamchi xizmatlar: sinf nomida shu bo'laklardan biri bo'lsa, ular hech qachon
     * "ekran o'quvchi" hisoblanmaydi (kichik harflarda solishtiriladi). Select to Speak faqat so'rovga ko'ra o'qiydi,
     * Switch Access va Voice Access boshqaruv vositasi, Braille xizmatlari esa faqat chiqarish qurilmasi.
     */
    private val COMPANION_PARTS = listOf(
        "selecttospeak", "switchaccess", "voiceaccess", "justspeak", "a11ymenu", "accessibilitymenu",
        "brailleime", "brailledisplay", "brailleback", "universalswitch", "assistantmenu", "autoclick",
    )

    /**
     * Faqat ekran o'quvchi paketlari: ular boshqa turdagi xizmat joylamaydi (Google'ning umumiy paketi bu yerda yo'q,
     * u uchun sinf nomi tekshiriladi).
     */
    private val READER_PACKAGES = setOf(
        "com.samsung.android.accessibility.talkback",
        "com.samsung.android.app.talkback",
        "com.android.talkback",
        "com.nirenr.talkman",
        "com.dianming.phoneapp",
    )

    /** Kutilmagan yangi nom bilan chiqqan TalkBack nusxalari uchun: sinfning oxirgi qismi shu bo'lsa, u ekran o'quvchi. */
    private const val TALKBACK_CLASS = "talkbackservice"

    /** Yordamchi xizmatmi (teginib o'rganishni egallamaydi)? */
    fun isCompanion(cls: String): Boolean {
        if (cls.isEmpty()) return false
        val low = cls.lowercase()
        for (p in COMPANION_PARTS) if (low.contains(p)) return true
        return false
    }

    /**
     * Ma'lum ekran o'quvchi komponentimi (TalkBack, Samsung TalkBack/Voice Assistant, Jieshuo, Dianming)?
     * Bunday xizmat to'xtatilgan (suspend) bo'lsa ham hisobga olinadi: foydalanuvchi uni sozlamalardan o'chirmaguncha
     * o'zbegim o'rnini bosmaydi (aks holda TalkBack qayta yoqilganda ikkalasi to'qnashib qolardi).
     */
    fun isKnownReader(pkg: String, cls: String): Boolean {
        if (isCompanion(cls)) return false
        val simple = cls.substringAfterLast('.').lowercase()
        if (simple == TALKBACK_CLASS) return true
        return pkg in READER_PACKAGES
    }

    /**
     * Bu xizmat teginib o'rganishni egallagan ekran o'quvchimi?
     *  - ma'lum ekran o'quvchi: ha, bayroqlariga qaramasdan;
     *  - yordamchi xizmat: yo'q, bayroqlariga qaramasdan;
     *  - noma'lum xizmat: faqat hozir teginib o'rganishni talab qilsagina (bu aniq to'qnashuv sababi).
     * Xizmat shunchaki "teginib o'rganishni so'ray olishi" (capability) hisobga olinmaydi: bu e'lon juda ko'p oddiy
     * xizmatlarda bor va ular ishora ushlamaydi.
     */
    fun isScreenReader(s: Service): Boolean {
        if (isCompanion(s.cls)) return false
        if (isKnownReader(s.pkg, s.cls)) return true
        return s.exploring
    }

    /** Bog'langan xizmat sozlamalardagi "yoqilgan" ro'yxatda bormi? Ro'yxat ishonchsiz bo'lsa (null) cheklov qo'yilmaydi. */
    fun stillEnabled(enabledKeys: Set<String>?, key: String): Boolean = enabledKeys == null || key in enabledKeys

    /**
     * Sozlamalarda yoqilgan, lekin tizim hali ulamagan (yoki ulay olmagan) ma'lum ekran o'quvchi qancha vaqt hisobga
     * olinadi (ms). Yangi yoqilgan TalkBack odatda 1-3 soniyada ulanadi; shundan keyin ham ulanmasa, u ishlamayapti
     * (masalan, buzilgan) va O'zbegim jim qolmasligi kerak.
     */
    const val UNBOUND_GRACE_MS = 15_000L

    /**
     * "Yoqilgan, lekin ulanmagan" xizmatlarni kuzatadi: har birining birinchi ko'ringan paytini eslab, muddat tugaguncha
     * uni hisobga olinadiganlar qatorida qaytaradi. Ulangan yoki o'chirilgan xizmat unutiladi (keyin yana paydo
     * bo'lsa, muddat qaytadan boshlanadi).
     */
    class UnboundTracker(private val graceMs: Long = UNBOUND_GRACE_MS) {
        private val since = HashMap<String, Long>()

        fun counted(unbound: Collection<String>, nowMs: Long): List<String> {
            since.keys.retainAll(unbound.toSet())
            val out = ArrayList<String>()
            for (k in unbound) {
                val t = since.getOrPut(k) { nowMs }
                if (nowMs - t <= graceMs) out.add(k)
            }
            return out
        }

        fun clear() {
            since.clear()
        }
    }
}
