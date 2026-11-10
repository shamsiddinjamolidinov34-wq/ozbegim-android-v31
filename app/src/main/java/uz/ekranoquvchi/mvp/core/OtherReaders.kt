package uz.ekranoquvchi.mvp.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import uz.ekranoquvchi.mvp.diag.swallowed

/**
 * Boshqa ekran o'quvchilarni (TalkBack, Samsung Voice Assistant, Jieshuo va h.k.) aniqlaydi.
 *
 * Ikkita ekran o'quvchi bir vaqtda ishlasa, ikkalasi ham barmoq ishoralarini ushlashga urinadi va ikki ovoz
 * aralashib gapiradi. Shuning uchun O'zbegim boshqa ekran o'quvchi yoqilganda kutish rejimiga o'tadi (ReaderService.enterStandby)
 * va u o'chirilganda o'zi qaytadi. Shu sababli bosh ekrandagi "TalkBack yoqilgan bo'lsa..." ogohlantirishi kerak emas.
 *
 * Qaror qoidalari ReaderRules'da (Android'siz, sinash oson); bu yerda tizimdan faqat ma'lumot yig'iladi:
 *  1) tizimning ulangan (ishlayotgan) xizmatlari ro'yxati: har biri PAKET bo'yicha emas, XIZMAT (paket + sinf) bo'yicha
 *     baholanadi. Ilgari Google'ning umumiy paketidagi har qanday xizmat (Select to Speak, Switch Access...) "TalkBack"
 *     hisoblanib, O'zbegimni jim qilib qo'yardi. Endi faqat haqiqiy ekran o'quvchi (yoki teginib o'rganishni hozir talab
 *     qilayotgan xizmat) hisobga olinadi;
 *  2) sozlamalardagi "yoqilgan xizmatlar" qatori: ro'yxatda bor, lekin tizim hali ulamagan ma'lum ekran o'quvchi ham
 *     hisobga olinadi (TalkBack yoqilgan zahoti O'zbegim to'xtashi uchun), ammo faqat qisqa muddatga: ulanmay qolgan
 *     (buzilgan) xizmat O'zbegimni abadiy jim qilib qo'ymasin. Aksincha, ulangan, lekin sozlamalardan allaqachon olib
 *     tashlangan xizmat hisobga olinmaydi (TalkBack o'chirilgan zahoti O'zbegim qaytadi).
 * O'zbegimning o'zi (paket nomi bo'yicha) hech qachon hisobga olinmaydi.
 */
object OtherReaders {
    private val unbound = ReaderRules.UnboundTracker()

    /** Yoqilgan boshqa ekran o'quvchi xizmatining nomi ("paket/sinf" qisqa ko'rinishida); yo'q bo'lsa null. */
    fun find(ctx: Context): String? {
        val own = ctx.packageName
        val enabled = enabledComponents(ctx, own)
        val enabledKeys: Set<String>? = enabled?.mapTo(HashSet<String>()) { it.flattenToString() }
        val boundKeys = HashSet<String>()
        try {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val list = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (list != null) {
                for (info in list) {
                    val comp = componentOf(info) ?: continue
                    if (comp.packageName == own) continue
                    val key = comp.flattenToString()
                    boundKeys.add(key)
                    // Sozlamalardan olib tashlangan, lekin tizim hali uzmagan xizmat: u allaqachon o'chirilmoqda
                    if (!ReaderRules.stillEnabled(enabledKeys, key)) continue
                    val exploring = (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0
                    if (ReaderRules.isScreenReader(ReaderRules.Service(comp.packageName, comp.className, exploring))) {
                        unbound.clear()
                        return comp.flattenToShortString()
                    }
                }
            }
        } catch (ex: Throwable) {
            swallowed("OtherReaders.find", ex)
        }
        try {
            if (enabled == null) {
                unbound.clear()
                return null
            }
            val pending = ArrayList<ComponentName>()
            for (c in enabled) {
                if (c.packageName == own) continue
                if (c.flattenToString() in boundKeys) continue
                if (ReaderRules.isKnownReader(c.packageName, c.className)) pending.add(c)
            }
            val alive = unbound.counted(pending.map { it.flattenToString() }, SystemClock.elapsedRealtime())
            if (alive.isNotEmpty()) {
                val comp = ComponentName.unflattenFromString(alive[0])
                return comp?.flattenToShortString() ?: alive[0]
            }
        } catch (ex: Throwable) {
            swallowed("OtherReaders.find", ex)
        }
        return null
    }

    /**
     * Sozlamalardagi yoqilgan xizmatlar. O'zimiz ro'yxatda bo'lmasak, qator kutilganidan boshqacha shaklda deb
     * hisoblab, unga ishonmaymiz (null): shunda ulangan xizmatlar ro'yxatining o'zi hal qiladi.
     */
    private fun enabledComponents(ctx: Context, own: String): Set<ComponentName>? {
        try {
            val raw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (raw.isNullOrBlank()) return null
            val out = HashSet<ComponentName>()
            for (part in raw.split(':')) {
                val c = ComponentName.unflattenFromString(part.trim()) ?: continue
                out.add(c)
            }
            return if (out.any { it.packageName == own }) out else null
        } catch (ex: Throwable) {
            swallowed("OtherReaders.enabled", ex)
            return null
        }
    }

    private fun componentOf(info: AccessibilityServiceInfo): ComponentName? {
        val si = info.resolveInfo?.serviceInfo
        val pkg = si?.packageName
        val cls = si?.name
        if (!pkg.isNullOrEmpty() && !cls.isNullOrEmpty()) return ComponentName(pkg, cls)
        return info.id?.let { ComponentName.unflattenFromString(it) }
    }

    /**
     * Tizim sozlamalaridagi yoqilgan xizmatlar o'zgarganda (foydalanuvchi TalkBack'ni yoqdi yoki o'chirdi) onChange
     * chaqiriladi. Qo'shimcha ruxsat kerak emas: bu sozlamalarni har bir ilova o'qiy oladi va o'zgarishini kuzata oladi.
     */
    class Watcher(private val ctx: Context, handler: Handler, private val onSettingsChanged: () -> Unit) {
        private val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onSettingsChanged()
            }
        }
        private var registered = false

        fun start() {
            if (registered) return
            try {
                val cr = ctx.contentResolver
                cr.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
                cr.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer)
                registered = true
            } catch (ex: Throwable) {
                swallowed("OtherReaders.start", ex)
            }
        }

        fun stop() {
            if (!registered) return
            registered = false
            try {
                ctx.contentResolver.unregisterContentObserver(observer)
            } catch (ex: Throwable) {
                swallowed("OtherReaders.stop", ex)
            }
        }
    }
}
