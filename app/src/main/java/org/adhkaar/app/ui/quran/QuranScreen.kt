package org.adhkaar.app.ui.quran

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.Quran
import org.adhkaar.app.data.quran.QuranPlan
import org.adhkaar.app.data.quran.QuranStore
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.GlassSegmented
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import org.adhkaar.app.ui.theme.UthmanicHafs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Qur'an tab: today's page and where you are in the whole Qur'an, the surahs of the day, what
 * you saved, and every surah and juz to open. [onRead] opens the reader on a page.
 */
@Composable
fun QuranScreen(onRead: (Int) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val mushaf = remember { Mushaf.get(context) }
    val store = remember { QuranStore.get(context) }
    val settings by store.settings.collectAsState()
    val lastPage by store.lastPage.collectAsState()
    val log by store.log.collectAsState()
    val pass by store.pass.collectAsState()
    val saved by store.saved.collectAsState()
    val today = LocalDate.now()
    val readToday = QuranPlan.pagesReadOn(log, today)
    val goal = QuranPlan.target(settings, ramadan = false)
    val start = QuranPlan.startPage(settings, lastPage, pass, today) { s ->
        mushaf.pageOfSurah(s)..(if (s == 114) Quran.PAGES else mushaf.pageOfSurah(s + 1))
    }
    val accent = LocalAura.current.accent
    var browse by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.tabBarClearance),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = Space.xl), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.quran_title), style = Type.displayL)
                    Text(stringResource(R.string.quran_subtitle), style = Type.bodyM)
                }
                GlassIconButton(Icons.Rounded.Tune, stringResource(R.string.quran_settings), onClick = onOpenSettings)
            }
            Spacer(Modifier.height(Space.s))
        }

        // Today: the ring of today's pages, and where the reading opens.
        item {
            GlassCard(Modifier.fillMaxWidth(), level = 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(readToday.coerceAtMost(goal) / goal.toFloat(), accent, Modifier.size(76.dp)) {
                        Text("$readToday/$goal", style = Type.titleM)
                    }
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.quran_today).uppercase(), style = Type.overline)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(if (readToday >= goal) R.string.quran_today_done else R.string.quran_today_left, (goal - readToday).coerceAtLeast(0)),
                            style = Type.titleM,
                        )
                        val surah = mushaf.surahsOfPage(start).last()
                        Text(stringResource(R.string.quran_page_of, start, Quran.latinNames[surah - 1]), style = Type.bodyM)
                    }
                }
                Spacer(Modifier.height(Space.l))
                PrimaryButton(stringResource(if (readToday == 0) R.string.quran_read_today else R.string.quran_continue), Modifier.fillMaxWidth()) { onRead(start) }
            }
        }

        // The whole Qur'an: this pass, and when it ends at this pace.
        item {
            val finish = QuranPlan.finishDate(pass, store.passStarted(), today)
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.quran_pass).uppercase(), style = Type.overline)
                        Spacer(Modifier.height(4.dp))
                        Text("${pass.size * 100 / Quran.PAGES}%", style = Type.displayM.copy(color = accent))
                        Text(stringResource(R.string.quran_pass_pages, pass.size, Quran.PAGES), style = Type.bodyM)
                    }
                    Text(
                        finish?.let { stringResource(R.string.quran_pass_finish, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                            ?: stringResource(R.string.quran_pass_start),
                        style = Type.caption, textAlign = TextAlign.End, modifier = Modifier.width(140.dp),
                    )
                }
                Spacer(Modifier.height(Space.m))
                JuzBar(pass, mushaf, accent)
                if (store.completedPasses() > 0) {
                    Spacer(Modifier.height(Space.s))
                    Text(stringResource(R.string.quran_passes_done, store.completedPasses()), style = Type.caption.copy(color = accent))
                }
            }
        }

        // The surahs of the day.
        if (settings.sunnahSurahs) {
            val friday = today.dayOfWeek == DayOfWeek.FRIDAY
            val night = LocalTime.now().hour >= 17 || LocalTime.now().hour < 4
            if (friday || night) item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    if (friday) SunnahCard(18, R.string.quran_sunnah_kahf, Icons.Rounded.WbSunny, Modifier.weight(1f)) { onRead(mushaf.pageOfSurah(18)) }
                    if (night) SunnahCard(67, R.string.quran_sunnah_mulk, Icons.Rounded.DarkMode, Modifier.weight(1f)) { onRead(mushaf.pageOfSurah(67)) }
                }
            }
        }

        // Saved ayahs.
        if (saved.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.quran_saved_title, saved.size)) }
            items(saved.sorted().take(5), key = { "saved$it" }) { ayah ->
                val (s, a) = Quran.ref(ayah)
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.l), onClick = { onRead(mushaf.pageOfAyah(ayah)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bookmark, null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s))
                        Text("${Quran.latinNames[s - 1]} $s:$a", style = Type.label)
                    }
                    Spacer(Modifier.height(Space.s))
                    Text(
                        mushaf.ayahs[ayah].joinToString(" "),
                        style = Type.arabicAccent.copy(fontFamily = UthmanicHafs, fontSize = 20.sp),
                        maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Every surah or juz.
        item {
            Spacer(Modifier.height(Space.s))
            GlassSegmented(listOf(stringResource(R.string.quran_surahs), stringResource(R.string.quran_juz)), browse) { browse = it }
        }
        if (browse == 0) {
            items(114, key = { "s$it" }) { i ->
                SurahRow(i + 1, mushaf.pageOfSurah(i + 1), accent) { onRead(mushaf.pageOfSurah(i + 1)) }
            }
        } else {
            items(10, key = { "j$it" }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    (1..3).forEach { col ->
                        val juz = row * 3 + col
                        val read = (mushaf.pageOfJuz(juz) until (if (juz == 30) Quran.PAGES + 1 else mushaf.pageOfJuz(juz + 1))).count { it in pass }
                        GlassCard(Modifier.weight(1f), padding = PaddingValues(Space.m), onClick = { onRead(mushaf.pageOfJuz(juz)) }) {
                            Text(stringResource(R.string.quran_juz_n, juz), style = Type.label)
                            Text(stringResource(R.string.quran_page_n, mushaf.pageOfJuz(juz)), style = Type.caption)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))) {
                                Box(Modifier.fillMaxWidth((read / 20f).coerceIn(0f, 1f)).height(3.dp).background(accent, RoundedCornerShape(50)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = Type.overline, modifier = Modifier.padding(top = Space.m))
}

@Composable
private fun SurahRow(surah: Int, page: Int, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Text("$surah", style = Type.caption.copy(color = accent, fontSize = 13.sp))
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(Quran.latinNames[surah - 1], style = Type.label)
            Text(stringResource(R.string.quran_surah_meta, Quran.ayahCounts[surah - 1], page), style = Type.caption)
        }
        Text(Quran.arabicNames[surah - 1], style = Type.arabicAccent.copy(fontFamily = UthmanicHafs, fontSize = 20.sp, color = Nur.textSecondary))
    }
}

@Composable
private fun SunnahCard(surah: Int, line: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, padding = PaddingValues(Space.l), onClick = onClick) {
        Icon(icon, null, tint = LocalAura.current.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(Space.s))
        Text(Quran.latinNames[surah - 1], style = Type.titleM)
        Text(stringResource(line), style = Type.caption)
    }
}

/** The thirty juz as one bar, each filled by how much of it this pass has read. */
@Composable
private fun JuzBar(pass: Set<Int>, mushaf: Mushaf, accent: Color) {
    val fills = remember(pass) {
        (1..30).map { j ->
            val from = mushaf.pageOfJuz(j)
            val to = if (j == 30) Quran.PAGES else mushaf.pageOfJuz(j + 1) - 1
            (from..to).count { it in pass } / (to - from + 1f)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * 29) / 30
        fills.forEachIndexed { i, f ->
            val x = size.width - (i + 1) * w - i * gap
            drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(x, 0f), Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            if (f > 0f) drawRoundRect(accent.copy(alpha = 0.35f + 0.65f * f), Offset(x, 0f), Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}

/** A ring filled to [fraction], with [content] in the middle. */
@Composable
fun ProgressRing(fraction: Float, color: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            val box = Size(size.width - stroke, size.height - stroke)
            val at = Offset(stroke / 2, stroke / 2)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, at, box, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}
