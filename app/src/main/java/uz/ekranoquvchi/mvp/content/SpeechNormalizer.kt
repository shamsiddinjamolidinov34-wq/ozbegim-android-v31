package uz.ekranoquvchi.mvp.content

/**
 * Normalizator (FR-06, monografiya 9.5): raqam, sana, vaqt, pul, foiz, telefon, URL, pochta va hashtag'larni
 * o'zbekcha so'zga aylantiradi. Nutq dvigatelidan mustaqil (har qanday dvigatel bilan ishlaydi).
 * Aniq naqshlar (sana, vaqt, pul, foiz, o'nlik kasr va h.k.) avval o'z qoidasi bilan o'qiladi; qolgan har qanday raqam
 * (versiya, kod, probelsiz ro'yxat, 13+ xonali son) sweepDigits'da so'zga aylanadi: dvigatelga xom raqam ketmaydi.
 * Raqam dvigatelga xom holda ketmasligi kerak: dvigatellar uni o'zicha va ba'zan noto'g'ri o'qiydi (masalan, 105 ni).
 */
object SpeechNormalizer {
    private val MONTHS = arrayOf(
        "yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
    )

    private val EMAIL = Regex("""[\p{L}\p{N}._%+\-]+@[\p{L}\p{N}\-]+(?:\.[\p{L}\p{N}\-]+)+""")
    private val URL = Regex("""(?:https?://|www\.)[^\s]+""", RegexOption.IGNORE_CASE)
    private val DATE_DMY = Regex("""(?<!\d)(\d{1,2})[./](\d{1,2})[./](\d{4})(?!\d)""")
    private val DATE_ISO = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
    private val TIME = Regex("""(?<![\d:])([01]?\d|2[0-3]):([0-5]\d)(?![\d:])""")
    // O'lchov birliklari ("5 kg", "20 \u00B0C") va boshqa fan qisqartmalari endi ScienceAbbr'da: u bu bosqichdan oldin ishlaydi
    // va har bir fan Tafsilotlarda alohida yoqiladi/o'chiriladi.
    // $ = \u0024, euro = \u20AC, funt = \u00A3, rubl = \u20BD
    // Pul miqdori: "1.050.000 so'm" kabi nuqta/vergul bilan guruhlangan son ham to'liq olinadi, oxiridagi raqam ortda qolmaydi
    private val CUR_BEFORE = Regex("""([\u0024\u20AC\u00A3\u20BD])\s?(\d{1,3}(?:[.,]\d{3})+(?!\d)|\d+(?:[ \u00A0\u202F]\d{3})*(?:[.,]\d{1,2})?(?!\d))""")
    private val CUR_AFTER = Regex("""((?<![\d.,])\d{1,3}(?:[.,]\d{3})+|\d+(?:[ \u00A0\u202F]\d{3})*(?:[.,]\d{1,2})?)\s?([\u0024\u20AC\u00A3\u20BD])""")
    private val PERCENT = Regex("""(\d+(?:[.,]\d+)?)\s?%""")
    private val PHONE = Regex("""\+\d[\d\s\-()]{7,}\d""")
    private val HASHTAG = Regex("""#([\p{L}\p{N}_]+)""")
    // Nuqta yoki vergul bilan guruhlangan son: "1.050.000", "1,050,000", "2.105" (har guruh aniq 3 raqam). Ilgari bunday son so'zga
    // aylanmay, dvigatelga raqam holida ketardi va u (masalan, 105 ni) noto'g'ri o'qirdi. Foiz va o'lchov oldidagi son (1,105 km) va
    // "0,105" kabi o'nlik kasrlarga tegilmaydi.
    private val THOUSANDS = Regex(
        """(?<![\p{L}\p{N}_.,+])\d{1,3}(?:[.,]\d{3})+(?![\p{L}\p{N}_]|[.,]\d|\s(?:foiz|daraja|""" +
            ScienceAbbr.spokenUnitWords.joinToString("|") + """)(?![\p{L}\p{N}_]))"""
    )
    private val DECIMAL = Regex("""(?<![\p{L}\p{N}_.,])(\d{1,12})[.,](\d{1,2})(?![\p{L}\p{N}_]|[.,]\d)""")
    private val GROUPED = Regex("""(?<![\p{L}\p{N}_.,+])\d{1,3}(?:[ \u00A0\u202F]\d{3})+(?![\p{L}\p{N}_]|[ \u00A0\u202F]\d|[.,]\d)""")
    // Tartib son: "5-chi", "20-nchi", "105-inchi" -> "beshinchi", "yigirmanchi", "yuz beshinchi"
    private val ORDINAL = Regex("""(?<![\p{L}\p{N}_.,+])(\d{1,9})-?(?:inchi|nchi|chi)(?![\p{L}\p{N}_])""")
    // Harfga yopishib yozilgan son: "105ta", "105m", "105та", "3D". Son so'zga aylanadi, harflar alohida so'z bo'ladi.
    // Raqamdan keyin yana raqam kelsa (3a8f kabi kod) va inglizcha 1st, 2nd, 3rd, 4th tegilmaydi.
    private val GLUED = Regex("""(?<![\p{L}\p{N}_.,+])(\d{1,12})(?=(?!(?i:st|nd|rd|th)(?![\p{L}\p{N}_]))\p{L}+(?![\p{L}\p{N}_]))""")
    private val PLAIN = Regex("""(?<![\p{L}\p{N}_.,])\d{1,12}(?![\p{L}\p{N}_]|[.,]\d)""")

    private fun currencyWord(c: String): String = when (c) {
        "\u0024" -> "dollar"
        "\u20AC" -> "yevro"
        "\u00A3" -> "funt"
        else -> "rubl"
    }

    private fun digitsToWords(s: String): String = s.filter { it.isDigit() }.map { UzNumbers.words(it - '0') }.joinToString(" ")

    private fun host(s: String): String =
        s.replace(".", " nuqta ").replace("-", " chiziqcha ").replace("_", " pastki chiziq ").replace(Regex("\\s+"), " ").trim()

    private fun urlToSpeech(raw: String): String {
        val core = raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
        val tail = raw.substring(core.length)
        var h = core.replaceFirst(Regex("^(?i)https?://"), "")
        h = h.removePrefix("www.").removePrefix("WWW.")
        val cut = h.indexOfAny(charArrayOf('/', '?', '#'))
        if (cut >= 0) h = h.substring(0, cut)
        return host(h) + tail
    }

    private fun phoneToSpeech(raw: String): String {
        val d = raw.filter { it.isDigit() }
        val groups = if (d.startsWith("998") && d.length == 12) {
            listOf(d.substring(0, 3), d.substring(3, 5), d.substring(5, 8), d.substring(8, 10), d.substring(10, 12))
        } else {
            d.chunked(3)
        }
        return groups.joinToString(", ") { digitsToWords(it) }
    }

    /** @param fluent ravon o'qish ("Urg'u berib o'qish" o'chiq): oy nomidan keyin vergul (to'xtash) qo'yilmaydi. */
    private fun dateToSpeech(d: Int, m: Int, y: Int, fluent: Boolean): String? {
        if (d !in 1..31 || m !in 1..12 || y !in 1000..2999) return null
        val gap = if (fluent) " " else ", "
        return UzNumbers.ordinal(d) + " " + MONTHS[m - 1] + gap + UzNumbers.words(y.toLong()) + " yil"
    }

    private fun numberToSpeech(digits: String, digitsMode: Boolean): String {
        // Tasdiqlash kodi kabi matnda 4-8 xonali son raqamma-raqam o'qiladi
        if (digitsMode && digits.length in 4..8) return digitsToWords(digits)
        // Nol bilan boshlangan son: "012" - "nol o'n ikki" (ilgari raqamma-raqam: "nol bir ikki")
        if (digits.length > 1 && digits.startsWith("0")) return zeroLedToSpeech(digits)
        return UzNumbers.words(digits.toLong())
    }

    /**
     * Nol bilan boshlangan son: bosh nollarning har biri alohida "nol" deyiladi, qolgani oddiy son kabi o'qiladi
     * ("012" - "nol o'n ikki", "007" - "nol nol yetti", "05" - "nol besh"). Qolgani 4 xonadan uzun bo'lsa (kod, hisob
     * raqami) yoki faqat nollardan iborat bo'lsa, raqamma-raqam o'qiladi.
     */
    private fun zeroLedToSpeech(digits: String): String {
        val zeros = digits.takeWhile { it == '0' }.length
        val rest = digits.substring(zeros)
        if (rest.isEmpty() || rest.length > 4) return digitsToWords(digits)
        val sb = StringBuilder()
        repeat(zeros) { sb.append(UzNumbers.words(0)).append(' ') }
        sb.append(UzNumbers.words(rest.toLong()))
        return sb.toString()
    }

    /** Arab-hind, to'liq kenglikdagi va boshqa yozuvlarning raqamlari oddiy 0-9 ga aylantiriladi (regexdagi \\d faqat 0-9 ni taniydi). */
    private fun asciiDigits(s: String): String {
        var other = false
        for (c in s) {
            if (c > '\u007F' && c.isDigit()) {
                other = true
                break
            }
        }
        if (!other) return s
        val sb = StringBuilder(s.length)
        for (c in s) {
            val v = if (c > '\u007F' && c.isDigit()) Character.digit(c, 10) else -1
            if (v in 0..9) sb.append('0' + v) else sb.append(c)
        }
        return sb.toString()
    }

    /**
     * @param digitsMode tasdiqlash kodi kabi matnlarda 4-8 xonali sonlarni raqamma-raqam o'qiydi.
     * @param fluent ravon o'qish: sana kabi hosil qilinadigan so'zlar orasida to'xtash (vergul) qo'yilmaydi.
     */
    fun normalize(text: String, digitsMode: Boolean = false, fluent: Boolean = false): String {
        if (!text.any { it.isDigit() } && text.indexOf('@') < 0 && text.indexOf('#') < 0 &&
            !text.contains("http", ignoreCase = true) && !text.contains("www.", ignoreCase = true)
        ) return text

        var t = asciiDigits(text)
        t = EMAIL.replace(t) { m ->
            val parts = m.value.split('@', limit = 2)
            host(parts[0]) + " kuchukcha " + host(parts[1])
        }
        t = URL.replace(t) { m -> urlToSpeech(m.value) }
        t = DATE_DMY.replace(t) { m ->
            dateToSpeech(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), fluent) ?: m.value
        }
        t = DATE_ISO.replace(t) { m ->
            dateToSpeech(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt(), fluent) ?: m.value
        }
        t = TIME.replace(t) { m -> UzNumbers.time(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        t = CUR_BEFORE.replace(t) { m -> m.groupValues[2] + " " + currencyWord(m.groupValues[1]) }
        t = CUR_AFTER.replace(t) { m -> m.groupValues[1] + " " + currencyWord(m.groupValues[2]) }
        t = PERCENT.replace(t) { m -> m.groupValues[1] + " foiz" }
        t = PHONE.replace(t) { m -> phoneToSpeech(m.value) }
        t = HASHTAG.replace(t) { m -> "xeshteg " + m.groupValues[1] }
        t = THOUSANDS.replace(t) { m ->
            val digits = m.value.filter { it.isDigit() }
            val intPart = m.value.takeWhile { it.isDigit() }
            // "0,105" ming bo'lagi emas, o'nlik kasr: tegmaymiz
            if (intPart == "0" || digits.length > 12) m.value else UzNumbers.words(digits.toLong())
        }
        t = DECIMAL.replace(t) { m ->
            val frac = m.groupValues[2]
            val den = if (frac.length == 1) "o'ndan" else "yuzdan"
            UzNumbers.words(m.groupValues[1].toLong()) + " butun " + den + " " + UzNumbers.words(frac.toLong())
        }
        t = GROUPED.replace(t) { m ->
            val digits = m.value.filter { it.isDigit() }
            // 12 xonadan uzun son (masalan, uzun hisob raqami) Long'ga sig'maydi: tegmaymiz, PLAIN uni guruh-guruh o'qiydi
            if (digits.length > 12) m.value else UzNumbers.words(digits.toLong())
        }
        t = ORDINAL.replace(t) { m -> UzNumbers.ordinal(m.groupValues[1].toLong()) }
        t = GLUED.replace(t) { m -> numberToSpeech(m.groupValues[1], digitsMode) + " " }
        t = PLAIN.replace(t) { m -> numberToSpeech(m.value, digitsMode) }
        return t
    }

    /** PLAIN bilan bir xil naqsh, lekin xonalar soni cheklanmagan: 13 xonadan uzun raqam qatori ham dvigatelga xom ketmasin. */
    private val LONG_PLAIN = Regex("""(?<![\p{L}\p{N}_.,])\d{13,}(?![\p{L}\p{N}_]|[.,]\d)""")

    // Probelsiz nuqta yoki vergul bilan ulangan raqam qatori: "10,90,105", "1.2.3", "3.14159". PLAIN va DECIMAL bunday qatorga tegmaydi,
    // shuning uchun ilgari u dvigatelga xom holda ketardi va dvigatel uni o'zicha (xato) o'qirdi.
    private val CHAIN = Regex("""\d+(?:[.,]\d+)+""")
    private val ANY_RUN = Regex("""\d+""")

    /**
     * Oxirgi himoya: o'zbekcha matnda qolib ketgan har qanday raqam so'zga aylanadi (13 xonadan uzun qatorlar va harfga
     * yopishgan 3+ xonali kodlar raqamma-raqam). Sababi: ovoz dvigateli raqamni o'zicha o'qiydi va o'zbekchada xato qiladi
     * ("10" - "on", "90" - "toqson", "105" - "yuzda 5", "1500" - "mingda 500"). Endi versiya (1.2.3), probelsiz ro'yxat (10,90),
     * uzun o'nlik kasr (3.14159) va harfga yopishgan kod (A10, user10) ham so'zga aylanadi.
     */
    fun sweepDigits(text: String): String {
        val src = asciiDigits(text)
        if (!src.any { it in '0'..'9' }) return src
        var t = PLAIN.replace(src) { m -> numberToSpeech(m.value, false) }
        t = LONG_PLAIN.replace(t) { m -> digitsToWords(m.value) }
        if (t.any { it in '0'..'9' }) t = sweepLeftovers(t)
        return t
    }

    /** PLAIN tegmagan qoldiqlar: ulangan qatorlar (CHAIN), so'ngra harfga yopishgan yoki boshqa yakka raqam qatorlari. */
    private fun sweepLeftovers(text: String): String {
        val s = CHAIN.replace(text) { m -> spaced(text, m, chainToSpeech(m.value)) }
        if (s.none { it in '0'..'9' }) return s
        return ANY_RUN.replace(s) { m ->
            val run = m.value
            val words = if (run.length > 12 || (run.length >= 3 && isGlued(s, m))) digitsToWords(run) else numberToSpeech(run, false)
            spaced(s, m, words)
        }
    }

    /** Raqam qatori yonida (oldida yoki ketida) harf turibdimi: "A10", "H264", "user10". */
    private fun isGlued(src: String, m: MatchResult): Boolean {
        val first = m.range.first
        val last = m.range.last
        return (first > 0 && src[first - 1].isLetter()) || (last + 1 < src.length && src[last + 1].isLetter())
    }

    /** Raqam qatori harf yoki pastki chiziqqa yopishib turgan bo'lsa, so'zlar qo'shilib ketmasligi uchun chetiga probel qo'yadi. */
    private fun spaced(src: String, m: MatchResult, words: String): String {
        val first = m.range.first
        val last = m.range.last
        val before = if (first > 0) src[first - 1] else ' '
        val after = if (last + 1 < src.length) src[last + 1] else ' '
        val pre = if (before.isLetter() || before == '_') " " else ""
        val post = if (after.isLetter() || after == '_') " " else ""
        return pre + words + post
    }

    /**
     * "10,90,105" - ro'yxat ("o'n, to'qson, yuz besh"); "1.2.3" - versiya ("bir nuqta ikki nuqta uch");
     * "3.14159" - uzun o'nlik kasr, kasr qismi raqamma-raqam ("uch butun bir to'rt bir besh to'qqiz").
     */
    private fun chainToSpeech(chain: String): String {
        val parts = chain.split('.', ',')
        if (parts.size == 2 && parts[1].length >= 3 && parts[0].length <= 12) {
            return numberToSpeech(parts[0], false) + " butun " + digitsToWords(parts[1])
        }
        val gap = if (chain.none { it == '.' }) ", " else " nuqta "
        return parts.joinToString(gap) { if (it.length > 12) digitsToWords(it) else numberToSpeech(it, false) }
    }

    /** Matnda hali raqam (0-9) qolganmi: qolgan bo'lsa, xato jurnaliga yozish uchun (matnning o'zi yozilmaydi). */
    fun countDigits(text: String): Int {
        var n = 0
        for (c in text) if (c in '0'..'9') n++
        return n
    }
}
