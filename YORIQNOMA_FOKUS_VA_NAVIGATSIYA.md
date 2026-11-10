# Fokus va navigatsiya yo'riqnomasi (O'zbegim 0.9.16, v31)

Bu hujjat uch narsani aytadi: (1) teginib o'rganish va haqiqiy amalni bajarish o'rtasidagi muvozanat qanday qoidalarga tayanadi,
(2) v29 dagi qaysi nuqsonlar topildi va qanday tuzatildi, (3) tuzatishlarni telefonda qanday tekshirish kerak.

**Muhim:** bu muhitda Gradle bilan qurib ham, telefonda sinab ham bo'lmaydi. Kod kompilyator darajasida (yangi xato yo'qligi) va
mantiqiy modellar bilan tekshirildi. Sabablar jurnal va kodni o'qish orqali topilgan; ularning hech biri telefonda tasdiqlanmagan.
3-bo'limdagi sinov tartibi shuning uchun yozilgan: haqiqiy hukmni telefon beradi.

v31 (7-bo'lim): soat va batareya e'loni TalkBack bilan ham ishlashi va ikki marta bosish tasdig'i. 1-6-bo'limlar v30 holatini tasvirlaydi va
v31 da o'zgarmagan (faqat 6-bo'limdagi tebranish haqidagi band yangilandi).

## 1. Qanday ishlaydi va muvozanat qoidalari

Teginib o'rganish yoqiq paytda har bir teginish ikki bosqichli ishdir:

1. **Tanlash.** Barmoq element ustida yuradi, tizim `TYPE_VIEW_HOVER_ENTER` yuboradi, O'zbegim elementga **fokus** beradi va o'qiydi.
   Bu bosqichda hech narsa bosilmaydi.
2. **Bajarish.** Foydalanuvchi **istalgan joyga ikki marta bosadi**, amal aynan fokusdagi (eng so'nggi eshitilgan) elementda bajariladi.
   Ikki marta bosishni kim bajarishi sozlanadi: Tafsilotlar > Bosish va navigatsiya ("Android tizimi bajaradi" yoki "O'zbegim bajaradi").

Muvozanat shu besh qoidaga tayanadi. Har bir tuzatish ulardan birini himoya qiladi:

| # | Qoida | Buzilganda foydalanuvchi nimani sezadi |
|---|-------|----------------------------------------|
| 1 | **Fokus faqat foydalanuvchining o'zi yoki yangi ekran uchun siljiydi.** Ilovaning tasodifiy hodisasi fokusni tortib olmaydi. | "Fokus mo'ljaldagi tugmadan qochib ketdi" |
| 2 | **Ikki marta bosish nishoni = oxirgi eshitilgan element.** Eskirgan yoki boshqa ekrandagi element nishon bo'lmaydi. | "Nomini eshitdim, lekin boshqa narsa bosildi" |
| 3 | **Bir ikki marta bosish = bitta amal.** Takror kelgan juftlik o'tkazib yuboriladi. | Kalit ikki marta almashib, holat o'zgarmay qoladi |
| 4 | **Bosish natijasi eshitiladi.** Holat yoki tugma nomi o'zgarsa, aytiladi. | "Amal bajarilmadi" deb qayta-qayta bosish |
| 5 | **Asosiy oqim band bo'lmaydi.** Hodisa ishlovi qisqa: barmoq ostidagi elementga fokus kechikmaydi. | Eshitish kechikadi, ikki marta bosish eski fokusga tushadi |

## 2. Topilgan nuqsonlar va tuzatishlar

Ishonch darajasi: **K** = kodni o'qib aniqlangan, **J** = jurnal ham ko'rsatadi, **T** = telefonda tasdiqlangan (hozircha hech biri).

| # | Nuqson | Sabab | Tuzatish | Fayl | Ishonch |
|---|--------|-------|----------|------|---------|
| 1 | Fokus klaviatura yoki tizim paneli ustidan "qochadi" | `maybeInitialFocus` oyna hodisasi kelgan har safar, fokus boshqa oynada (klaviatura, holat paneli, pastki tugmalar) bo'lsa ham, uni faol oynaning eng yuqori elementiga tortib olardi | Fokus klaviatura va tizim oynalarida turgan bo'lsa, barmoq tegib turgan bo'lsa yoki faol oyna klaviatura bo'lsa, siljitilmaydi | `ReaderService.maybeInitialFocus`, `focusInForeignWindow` | K |
| 2 | Dialog yoki menyu yopilgach fokus ekranning eng tepasiga sakraydi | Yopilgan oynadan qaytganda har doim birinchi elementga o'tilardi | Har oyna uchun oxirgi fokus eslab qolinadi; boshqa oynadan qaytilsa va element hali ko'rinsa, fokus unga qaytadi | `rememberWindowFocus`, `rememberedFocusFor` | K |
| 3 | Eski ekrandagi element bosilib ketadi | "Yumshoq fokus" (tizim fokus bermagan, o'zimiz o'qigan element) 120 soniya saqlanardi; ilovaning manbasiz fokus hodisasi uni tozalamasdi; ekran almashganda ham qolardi | Muddat 45 soniya; manbasiz fokus hodisasi tozalaydi; ekran almashganda yangilab bo'lmaydigan yumshoq fokus tashlanadi | `onFocused`, `currentFocus`, `dropStaleSoftFocus` | K |
| 4 | Kalit ikki marta almashadi, tugma ikki marta bosiladi | "O'zbegim bajaradi" rejimida ikki marta bosish takror kelgan: jurnalda 63 ta bosish orasidagi 62 ta oraliqning 5 tasi 350 ms dan qisqa (72, 268, 269, 280 va 316 ms); odam uchun bunday tez takror deyarli mumkin emas | 350 ms ichida kelgan ikkinchi juftlik o'tkazib yuboriladi (`dt_dup`) | `handleGesture` | J |
| 5 | Ikki marta bosish hali qayta ishlanmagan oxirgi teginishga tushmaydi | Barmoq tez ko'tarilganda oxirgi hover hodisasi navbatda turgan bo'lishi mumkin | Ikki marta bosishdan oldin kutayotgan hover yakunlanadi | `activateByDoubleTap` | K |
| 6 | Bosdim, hech narsa eshitilmadi, shuning uchun qayta bosdim | Tugma nomi o'zgarsa ("Pauza" -> "Ijro"), O'zbegim faqat holat o'zgarganda gapirardi. Jurnalda YouTube'da 25 ta bosish bor, ulardan 12 tasi oldingisidan 1,5 soniya ichida (qayta bosish ehtimoli) | Bosishdan keyin 2,5 soniya ichida bosiladigan elementning o'z nomi o'zgarsa, yangi nom aytiladi (TalkBack kabi). Vaqt yoki hisoblagich kabi o'zi o'zgaradigan matn aytilmaydi | `checkStateChange`, `labelKey` | J |
| 7 | Eshitish va fokus kechikadi | Hodisa ishlovi daraxtni qayta-qayta yurardi (`related`/`isInside`): jurnalda 9 ta `SLOW_HANDLER`, eng uzuni 364 ms; ular hover ishlovini kutdirgan bo'lishi mumkin | Oyna raqami bo'yicha tez rad etish, rejalashtirilgan tekshiruv paytida yurmaslik, yurish chuqurligi 12 | `related`, `scheduleStateCheck`, `isInside` | K, J qisman |
| 8 | Select to Speak yoqilsa O'zbegim jim qoladi | Google paketidagi har qanday xizmat "TalkBack" hisoblanardi | Qaror xizmat (paket + sinf) bo'yicha; yordamchi xizmatlar hisobga olinmaydi (5-bo'lim) | `ReaderRules`, `OtherReaders` | K |
| 9 | Bosh ekrandagi eslatma | O'zbegim boshqa ekran o'quvchi bilan o'zi almashinadi, eslatma eskirgan | Olib tashlandi (maket va 4 tildagi matn) | `activity_main.xml`, `strings.xml` | |

## 3. Telefonda sinash tartibi

Har sinovdan keyin natijani yozib boring. Sinovlarni **ikkala rejimda** (Tafsilotlar > Bosish va navigatsiya) takrorlang: 5, 6 va 7-sinovlar rejimga bog'liq.

1. **TalkBack bilan almashinuv.** O'zbegim yoqiq, TalkBack o'chiq. Sozlamalardan TalkBack'ni yoqing.
   Kutiladi: bir-uch soniyada "Boshqa ekran o'quvchi ishni boshladi, men ekranni o'qishni to'xtatdim", keyin faqat TalkBack gapiradi.
   TalkBack'ni o'chiring. Kutiladi: bir necha soniyada "Men ekranni o'qishni boshladim, boshqa ekran o'quvchi dastur o'chirilganligi uchun".
2. **Select to Speak.** TalkBack o'chiq, Select to Speak'ni yoqing. Kutiladi: O'zbegim to'xtamaydi, odatdagidek o'qiydi.
3. **Klaviatura.** Xabar yozish maydonini oching, klaviatura chiqsin, tugmalarni barmoq bilan tekshiring, bir necha soniya kuting (ilova
   takliflar chiqarishi mumkin). Kutiladi: fokus klaviaturada qoladi, ilovaning yuqori qismiga sakramaydi.
4. **Dialog yoki menyu yopilishi.** "Qo'shimcha" (uch nuqta) menyusini oching, Orqaga bilan yoping. Kutiladi: fokus uch nuqta tugmasiga
   qaytadi, ekranning birinchi elementiga emas.
5. **Orqaga qaytish.** Sozlamalarda biror qatorni oching, Orqaga bosing. Kutiladi: fokus ochgan qatoringizda.
6. **Bosish natijasi.** YouTube'da pauza/ijro tugmasini ikki marta bosing; Wi-Fi kalitini ham. Kutiladi: yangi nom ("Ijro") yoki holat aytiladi.
7. **Takror.** "O'zbegim bajaradi" rejimida kalitni tez-tez ikki marta bosing. Kutiladi: har juftlik bitta almashtirish, tez takror o'tkazib yuboriladi.
8. **Oxirgi eshitilgan.** A elementga tegib eshiting, tezda B ga o'ting, eshiting, barmoqni ko'tarib ikki marta bosing. Kutiladi: B bosiladi.
9. **Pastki tugmalar.** Pastki (Orqaga, Bosh ekran) tugmalarni teginib toping, YouTube'da video o'ynab turganda. Kutiladi: fokus tugmada qoladi.
10. **Jurnal.** Sinovdan keyin "Xatolar jurnali" ekranidan ulashing (4-bo'lim bo'yicha o'qiladi).

## 4. Jurnalni o'qish (`GESTURE_PATH`)

| Yozuv | Ma'nosi |
|-------|---------|
| `dt_click`, `dt_global`, `dt_touch` | "O'zbegim bajaradi" rejimida bosish yo'li: element bosildi, tizim tugmasi bajarildi yoki sun'iy tegish yuborildi |
| `sys_click` (yangi) | "Android tizimi bajaradi" rejimida ilova bosish hodisasini yubordi. `rel=true`: bosilgan element fokusdagi element bilan qarindosh. `rel=false` ko'p bo'lsa, bosish eshitilgan elementga tushmayapti, ya'ni fokus qochgan. Jurnal hajmi cheklangan: `rel=false` 0,7 soniyada, `rel=true` 15 soniyada bir martadan ko'p yozilmaydi, shuning uchun yozuvlar sonini bosishlar soni deb o'qimang |
| `dt_dup` (qora quti) | Takror ikki marta bosish o'tkazib yuborildi |
| `SLOW_HANDLER` | Hodisa ishlovi 120 ms dan uzoq. Soni kamayganini solishtiring: v29 jurnalida 9 ta, eng uzuni 364 ms |

Eslatma: v29 jurnalida bosish yozuvlari (`dt_*`) bor, demak u paytda rejim "O'zbegim bajaradi" bo'lgan. "Tizimga berilganda ham qiyin" degan
kuzatuvingizni tizim rejimi jurnali hali ko'rsatmaydi (v29 da bunday bosish yozilmasdi). Shu sababli `sys_click` qo'shildi: keyingi
jurnalni tizim rejimida yozing.

## 5. Boshqa ekran o'quvchi bilan almashinish qoidalari

- **Xizmat bo'yicha qaror** (paket nomi bo'yicha emas). Ekran o'quvchi hisoblanadi: TalkBack (sinf nomi `TalkBackService`), Samsung TalkBack va
  Voice Assistant, Jieshuo, Dianming: ular to'xtatilgan (suspend) bo'lsa ham; noma'lum xizmat esa faqat hozir teginib o'rganishni talab qilsagina.
- **Hisobga olinmaydi:** Select to Speak, Switch Access, Voice Access, Accessibility Menu, Braille xizmatlari, Universal Switch, Assistant menu.
- **Yoqilganda tezroq:** sozlamalarda yoqilgan, lekin tizim hali ulamagan ma'lum ekran o'quvchi 15 soniyagacha hisobga olinadi (tez to'xtash uchun).
  15 soniyada ham ulanmasa, u ishlamayapti deb hisoblanadi va O'zbegim jim qolmaydi.
- **O'chirilganda tezroq:** sozlamalardan olib tashlangan xizmat tizimdan hali uzilmagan bo'lsa ham hisobga olinmaydi.
- O'zbegim kutish rejimidan chiqqanda ikki marta bosish sozlamasini qayta moslaydi (kutish paytida o'zgartirilgan bo'lishi mumkin).

## 6. Cheklovlar va keyingi qadamlar (halol)

- Bu muhitda Gradle qurilmasi va telefon yo'q: **qurilmadagi xatti-harakat tasdiqlanmagan.** Qurishda xato chiqsa, xato matnini yuboring.
- Ikki marta bosish standarti "Android tizimi bajaradi" bo'lib qoldi. Qaysi rejim sizning telefoningizda qulay ekanini 3-bo'limdagi sinovlar ko'rsatadi.
- O'zgartirilmadi: `dt_touch` sun'iy tegish yo'li (teginib o'rganishni vaqtincha o'chiradi); TTS `ERROR_OUTPUT` (-5) xatosi (jurnalda 1 marta);
  hover ishlovi asosiy oqimda qoldi. (Bosish tasdig'i uchun tebranish va tovush v31 da qo'shildi: 7.3.)
- Agar sinovdan keyin ham fokus qochsa, jurnal va qaysi sinov (raqami) va rejimni yuboring: keyingi tuzatish shu ma'lumotga tayanadi.

## 7. v31: mustaqil o'quvchini TalkBack + O'zbegim dvigateli qulayligiga yaqinlashtirish va soat/batareya e'loni

### 7.1 Kuzatuv va tahlil

Kuzatuv: O'zbegim "Afzal ko'rilgan mexanizm" bo'lib, ekran o'quvchi TalkBack bo'lganda bosish, harakatlanish va nutq qulay; O'zbegimning o'zi
ekran o'quvchi bo'lganda qulay emas, shuning uchun uni tanlamaydilar. Shu holatda soat va batareya e'loni ishlamaydi.

Nega farq bor (kodni o'qib, telefonda tasdiqlanmagan):

| | TalkBack + O'zbegim dvigateli | O'zbegim mustaqil |
|---|---|---|
| Teginish, fokus, navigatsiya, ikki marta bosish | TalkBack (Google'ning pishgan kodi, alohida jarayon) | `ReaderService` (~5100 qator, asosiy oqimda, o'z navigatori va Telegram uchun ko'p maxsus qoidalar) |
| O'zbegim nima qiladi | Faqat matnni tayyorlaydi va ovoz beradi (`OzbegimTtsService`) | Hammasini: o'qiydi, harakatlantiradi, bosadi, e'lon qiladi |

Shuning uchun mustaqil o'quvchini TalkBack tuzilmasiga "ko'chirib" bo'lmaydi: TalkBack alohida dastur, O'zbegimning ekran o'quvchisi esa o'z
navigatoriga ega. Haqiqiy yaqinlashish yo'li: nima sekin yoki noqulayligini o'lchash va tuzatish. v31 shu yo'ldagi birinchi qadam.

v30 jurnalidan (65 yozuv) aniq raqamlar:

- Bosish yo'llari ishlayapti: `sys_click` 12 (hammasi `rel=true`, ya'ni fokusdagi elementga tushgan), `dt_click` 14, `dt_global` 8, `dt_touch` 2; `FOCUS_NODE_FAILED` yo'q.
- Ishora ishlovi sekin: `SLOW_HANDLER` 7 ta. Ishoralar: o'ngga surish 413 va 137 ms, chapga surish 233 ms, ikki marta bosish 149 ms. Hodisalar
  (`TYPE_WINDOW_CONTENT_CHANGED`): 174, 167, 179 ms. `LATENCY_BUDGET`: 409 ms (1 ta).
- Bosish bajarilganini bildiradigan hech narsa yo'q edi (tebranish yoki tovush): TalkBack'da bor.
- `TTS_ENGINE_ERROR` -5 (1 ta): qora quti izida undan 19 ms oldin kutish rejimiga o'tish (`yield`) bor.

Jurnal mustaqil rejimda bitta aniq nosozlikni ko'rsatmaydi: bosishlar bajarilgan, fokus berishda istisno yo'q. U faqat kechikishni (7 ta sekin ishlov)
va tasdiq yo'qligini ko'rsatadi. "Qulaylik" farqining to'liq sababi uchun 7.4 dagi o'lchov kerak; shu sababli v31 faqat dalil bor joyni o'zgartirdi.

### 7.2 Soat va batareya e'loni (B)

Sabab: e'lon faqat `ReaderService` orqali aytilardi. TalkBack yoqilganda u kutish rejimiga o'tadi va u yerda `speak()` jim; xizmat o'chirilgan
bo'lsa jadvalni ushlab turadigan hech narsa yo'q edi. Dvigatel jadvalga umuman aloqasi yo'q edi.

v31 da:
- Jadvalni ikki ega ushlab turadi: ekran o'quvchi xizmati va O'zbegim dvigateli. O'zbegim afzal ko'rilgan mexanizm bo'lsa, TalkBack ishlab turganda
  dvigatelga odatda ulanib turadi (Android'ning odatiy xatti-harakati, telefonda tasdiqlanmagan), shuning uchun jadval ekran o'quvchi xizmati
  o'chiq bo'lsa ham ishlaydi. Dvigatel jarayoni to'xtab qolsa ham, **soat** e'loni alarm orqali davom etadi (alarm jarayonni qayta ishga tushiradi).
  **Batareya** e'loni esa batareya hodisasini kuzatuvchi ega tirik bo'lgandagina ishlaydi (xizmat yoki dvigatel ulangan bo'lishi kerak).
- Ekran o'quvchi xizmati yoqiq bo'lsa (kutish rejimida ham) e'lonni o'zi aytadi. Faqat O'zbegimni o'zingiz to'xtatgan bo'lsangiz jim turadi.
- Xizmat yo'q bo'lsa, "yordamchi ovoz" aytadi: telefondagi haqiqiy dvigatel orqali (O'zbegimning o'zi emas), O'zbegim matn qoidalari bilan.
- TalkBack gapirayotganda e'lon uning tanaffusini kutadi (dvigatel 0,7 soniya jim bo'lguncha). TalkBack'ning gapini **to'xtata olmaymiz**:
  "Matn o'qilayotgan bo'lsa kutish" bo'lsa 2 daqiqagacha kutadi, "darhol ayt" bo'lsa 4 soniyagacha.
- Alarm signali e'lon tugaguncha (9 soniyagacha) ushlab turiladi: jarayon signal uchun yangi ishga tushsa ham e'lon yo'qolmaydi.
- O'zbegim ishlatilmayotgan bo'lsa (afzal ko'rilgan mexanizm emas va xizmat ham yoqilmagan), alarm zanjiri o'zi to'xtaydi.

**Cheklov:** O'zbegimning ekran o'quvchiga bog'liq boshqa imkoniyatlari (menyu, o'z ishoralari, ekran bo'yicha qidiruv va boshqalar) TalkBack
ekran o'quvchi bo'lganda ishlay olmaydi: ular ekranni boshqarishni talab qiladi. Soat va batareya e'loni kabi "o'quvchisiz" ishlay oladigan
narsalar ko'chirildi. Yana qaysi imkoniyat yetishmayotganini yozsangiz, ko'rib chiqaman.

### 7.3 Ikki marta bosish tasdig'i (A)

TalkBack'da element faollashganda qisqa titraydi; O'zbegimda hech narsa yo'q edi: tugma nomi o'zgarmasa foydalanuvchi "bosilmadi" deb qayta bosardi.
v31 da: Sozlamalar > "Bosganda titrash (tasdiq)" (standart: yoqiq) va "Tovush signallari" (standart: o'chiq) yoqilgan bo'lsa qisqa ikki pog'onali tovush.
Tasdiq "O'zbegim bajaradi" rejimida bosish bajarilgan zahoti, "Android tizimi bajaradi" rejimida ilovadan bosish hodisasi kelganda (ekranga yaqinda
tegilgan bo'lsa) beriladi; bir bosish uchun ikki marta berilmaydi.

### 7.4 Sekinlik sababini topish: yangi jurnal maydonlari

`SLOW_HANDLER` (ishora uchun) endi qo'shimcha maydonlar beradi (millisekund):

| Maydon | Nimaga ketgan vaqt |
|--------|--------------------|
| `ms` | Ishoraning umumiy ishlov vaqti |
| `cur` | Joriy fokusli elementni topish |
| `nav` | Navigatorning keyingi/oldingi elementni qidirishi |
| `act` | Tizimga "fokus ber" amalini yuborish (ilova javobini kutadi) |
| `ms` - (`cur`+`nav`+`act`) | Qolgan ishlar: ro'yxat tartibi, aylantirish, nutqni tayyorlash |

Qaysi maydon katta bo'lsa, keyingi tuzatish shu yerga qaratiladi (masalan, `act` katta bo'lsa kechikishni ilova beryapti va O'zbegim buni
o'zgartira olmaydi; `nav` katta bo'lsa navigatorni tezlashtirish mumkin). Navigatorni ko'r-ko'rona qayta yozish yaxshilashdan ko'ra buzish
xavfi yuqori: shuning uchun v31 da u o'zgartirilmadi.

### 7.5 Telefonda sinash (v31)

Qurish va o'rnatishdan keyin:

1. **Soat/batareya, TalkBack + O'zbegim dvigateli, O'zbegim xizmati o'chiq.** Sozlamalar > Matnni nutqqa aylantirish: O'zbegim; TalkBack yoqiq;
   O'zbegim ekran o'quvchi xizmati o'chiq. O'zbegim > Soat va batareya: yoqing, "Hozir ayt" yoki sinov tugmasini bosing. Kutiladi: e'lon eshitiladi.
2. **Xuddi shu, O'zbegim xizmati yoqiq (kutish rejimida).** Kutiladi: e'lon eshitiladi (v30 da jim edi).
3. **Ekran o'chiq.** Ekranni qulflab, kelgan e'lon vaqtini kuting (masalan, har 5 daqiqada). Kutiladi: e'lon eshitiladi; eshitilmasa, jurnalni yuboring.
4. **TalkBack gapirayotganda.** Uzun matn o'qitib turing, shu paytda e'lon vaqti kelsin. Kutiladi: e'lon TalkBack tanaffusidan keyin eshitiladi,
   aralashib ketmaydi.
5. **Bosish tasdig'i, ikkala rejim.** O'zbegim mustaqil, biror tugmani ikki marta bosing, "Bosganda titrash" yoqiq. Kutiladi: bosish bilan qisqa titroq.
   "Tovush signallari"ni ham yoqib sinang. Kutiladi: qisqa "tak-tuk" tovushi. Har bosishda bir marta; ikki marta bo'lsa, qaysi rejim ekanini yozing.
6. **Jurnal.** Bir necha daqiqa ishlatgach "Xatolar jurnali"dan ulashing: `SLOW_HANDLER` dagi `cur`/`nav`/`act` maydonlari keyingi qadamni belgilaydi.

### 7.6 Tekshirilganlik

- Kompilyator darajasida: `kotlinc` 2.0.21 (Android SDK siz), v30 bilan solishtirildi: sintaksis xatosi yo'q; yangi xatolar faqat Android
  turlariga havolalar va ulardan kelib chiqqan zanjirli xatolar.
- **Tekshirilmagan:** Gradle bilan qurish va telefondagi xatti-harakat (TalkBack bilan aralashmaslik vaqti, ekran o'chiq paytida e'lon, tebranish kuchi,
  mustaqil rejimda kechikish o'zgarishi). Bu muhitda ikkalasi ham mumkin emas.
