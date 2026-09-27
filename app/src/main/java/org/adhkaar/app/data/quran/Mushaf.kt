package org.adhkaar.app.data.quran

import android.content.Context

/**
 * The Madinah mushaf (1421 AH print) page by page, line by line, as the King Fahd Glorious Qur'an
 * Printing Complex set it. Built by tools/quran/build.py into assets/quran/:
 *  * mushaf.txt: every page's lines. "P590" starts a page, "H85" is surah 85's heading, "B" the
 *    basmala, and "L <glyphs>|5908x10,5909x4" a line in the page's own font (see [PageFonts]): its
 *    glyphs in reading order, then which ayah (0-based, in order) each run of them belongs to.
 *  * quran.txt: the Uthmanic Hafs text, one ayah per line, for the meaning sheet and saved ayahs.
 */
class Mushaf private constructor(val ayahs: List<List<String>>, val pages: List<Page>) {
    sealed interface Line {
        data class Header(val surah: Int) : Line
        data object Basmala : Line
        /** [glyphs] in the page font; [ayahOf] gives each glyph's ayah. */
        class Text(val glyphs: String, val ayahOf: IntArray) : Line
    }

    /** [number] is 1-based, as printed. */
    data class Page(val number: Int, val lines: List<Line>) {
        /** Ayahs whose words (or number) are on this page, in order. */
        val ayahIndices: List<Int> by lazy { lines.filterIsInstance<Line.Text>().flatMap { it.ayahOf.toList() }.distinct() }
    }

    /** The page an ayah (0-based index) begins on. */
    fun pageOfAyah(index: Int): Int = pageStarts[index]

    private val pageStarts: IntArray by lazy {
        val out = IntArray(ayahs.size)
        val seen = BooleanArray(ayahs.size)
        pages.forEach { page ->
            page.ayahIndices.forEach { if (!seen[it]) { seen[it] = true; out[it] = page.number } }
        }
        out
    }

    fun pageOfSurah(surah: Int): Int = pageOfAyah(Quran.firstAyah(surah))

    fun pageOfJuz(juz: Int): Int = pageOfAyah(Quran.index(Quran.juzStarts[juz - 1].first, Quran.juzStarts[juz - 1].second))

    /** The juz the page's first ayah is in, as the page's heading shows it. */
    fun juzOfPage(page: Int): Int = Quran.juzOf(pages[page - 1].ayahIndices.first())

    /** The surahs on a page, in order. */
    fun surahsOfPage(page: Int): List<Int> = pages[page - 1].ayahIndices.map { Quran.ref(it).first }.distinct()

    companion object {
        @Volatile private var cached: Mushaf? = null

        fun get(context: Context): Mushaf = cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }

        private fun load(context: Context): Mushaf {
            val assets = context.applicationContext.assets
            val ayahs = assets.open("quran/quran.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.map { it.split(' ').filter(String::isNotEmpty) }.toList()
            }
            val pages = mutableListOf<Page>()
            var number = 0
            var lines = mutableListOf<Line>()
            assets.open("quran/mushaf.txt").bufferedReader().useLines { all ->
                all.forEach { raw ->
                    when {
                        raw.startsWith("P") -> {
                            if (number > 0) pages += Page(number, lines)
                            number = raw.substring(1).toInt()
                            lines = mutableListOf()
                        }
                        raw.startsWith("H") -> lines += Line.Header(raw.substring(1).toInt())
                        raw == "B" -> lines += Line.Basmala
                        raw.startsWith("L ") -> lines += parseLine(raw.substring(2))
                    }
                }
            }
            if (number > 0) pages += Page(number, lines)
            return Mushaf(ayahs, pages)
        }

        private fun parseLine(body: String): Line.Text {
            val glyphs = body.substringBeforeLast('|')
            val owners = IntArray(glyphs.length)
            var at = 0
            body.substringAfterLast('|').split(',').forEach { run ->
                val ayah = run.substringBefore('x').toInt()
                repeat(run.substringAfter('x').toInt()) { if (at < owners.size) owners[at++] = ayah }
            }
            return Line.Text(glyphs, owners)
        }
    }
}

/** Surahs, ayah numbering and juz. Pure, so it can be unit tested. */
object Quran {
    const val PAGES = 604
    const val AYAHS = 6236

    val ayahCounts = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77,
        227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55,
        78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25,
        22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
    )

    private val firstIndex = IntArray(114).also { out -> var sum = 0; for (i in 0 until 114) { out[i] = sum; sum += ayahCounts[i] } }

    /** 0-based index of the first ayah of [surah] (1-based). */
    fun firstAyah(surah: Int) = firstIndex[surah - 1]

    fun index(surah: Int, ayah: Int) = firstIndex[surah - 1] + ayah - 1

    /** (surah, ayah), both 1-based, of a 0-based ayah index. */
    fun ref(index: Int): Pair<Int, Int> {
        val s = firstIndex.indexOfLast { it <= index }
        return (s + 1) to (index - firstIndex[s] + 1)
    }

    /** Where each juz begins, as (surah, ayah). */
    val juzStarts = listOf(
        1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
        9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
        29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
    )

    fun juzOf(index: Int): Int = juzStarts.indexOfLast { index(it.first, it.second) <= index } + 1

    /**
     * Where each surah's name is in the Complex's heading font (QCF2BSML): 0x5D for al-Fatihah on,
     * skipping the three codes the font leaves empty.
     */
    val headingCodes: IntArray = ((0x5D..0x7F) + (0xA0..0xF1)).filter { it !in setOf(0xA0, 0xAD, 0xB7) }.toIntArray()
        .also { check(it.size == 114) }

    /** "سورة" in the heading font, the heading's frame, and the basmala. */
    const val HEADING_SURAH = 0x5C
    const val HEADING_FRAME = 0xF2
    const val HEADING_BASMALA = 0xF3

    /** Surah names in Arabic, for lists. */
    val arabicNames = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
        "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
        "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
        "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
        "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
        "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
        "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
        "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
        "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
        "المسد", "الإخلاص", "الفلق", "الناس",
    )

    /** Surah names in Latin letters, for lists and for readers who don't read Arabic yet. */
    val latinNames = listOf(
        "Al-Fatihah", "Al-Baqarah", "Al ʿImran", "An-Nisaʾ", "Al-Maʾidah", "Al-Anʿam", "Al-Aʿraf", "Al-Anfal", "At-Tawbah", "Yunus",
        "Hud", "Yusuf", "Ar-Raʿd", "Ibrahim", "Al-Hijr", "An-Nahl", "Al-Israʾ", "Al-Kahf", "Maryam", "Ta-Ha",
        "Al-Anbiyaʾ", "Al-Hajj", "Al-Muʾminun", "An-Nur", "Al-Furqan", "Ash-Shuʿaraʾ", "An-Naml", "Al-Qasas", "Al-ʿAnkabut", "Ar-Rum",
        "Luqman", "As-Sajdah", "Al-Ahzab", "Sabaʾ", "Fatir", "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar", "Ghafir",
        "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah", "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf",
        "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman", "Al-Waqiʿah", "Al-Hadid", "Al-Mujadilah", "Al-Hashr", "Al-Mumtahanah",
        "As-Saff", "Al-Jumuʿah", "Al-Munafiqun", "At-Taghabun", "At-Talaq", "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Maʿarij",
        "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah", "Al-Insan", "Al-Mursalat", "An-Nabaʾ", "An-Naziʿat", "ʿAbasa",
        "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj", "At-Tariq", "Al-Aʿla", "Al-Ghashiyah", "Al-Fajr", "Al-Balad",
        "Ash-Shams", "Al-Layl", "Ad-Duha", "Ash-Sharh", "At-Tin", "Al-ʿAlaq", "Al-Qadr", "Al-Bayyinah", "Az-Zalzalah", "Al-ʿAdiyat",
        "Al-Qariʿah", "At-Takathur", "Al-ʿAsr", "Al-Humazah", "Al-Fil", "Quraysh", "Al-Maʿun", "Al-Kawthar", "Al-Kafirun", "An-Nasr",
        "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas",
    )

    /** Arabic-Indic digits, as the mushaf numbers pages and juz. */
    fun arabicDigits(n: Int): String = n.toString().map { c -> '٠' + (c - '0') }.joinToString("")
}
