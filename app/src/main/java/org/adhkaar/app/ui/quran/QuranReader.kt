@file:OptIn(eu.wewox.pagecurl.ExperimentalPageCurlApi::class)

package org.adhkaar.app.ui.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.page.rememberPageCurlState
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.adhkaar.app.R
import org.adhkaar.app.data.quran.Meanings
import org.adhkaar.app.data.quran.Mushaf
import org.adhkaar.app.data.quran.Quran
import org.adhkaar.app.data.quran.QuranPlan
import org.adhkaar.app.data.quran.QuranStore
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.settings.GlassDialog
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import org.adhkaar.app.ui.theme.UthmanicHafs
import java.time.LocalDate
import java.time.LocalTime

/**
 * The mushaf, page by page. Pages turn right to left, as in print. A page counts as read after
 * it has been open for the set time (the ring at the top fills, then shows a tick); the page you
 * are on is kept, so the reading continues from it next time. Tap an ayah for its meaning.
 */
@Composable
fun QuranReader(startPage: Int, onClose: () -> Unit, target: Int? = null, onTargetMet: (() -> Unit)? = null) {
    val context = LocalContext.current
    val mushaf = remember { Mushaf.get(context) }
    val store = remember { QuranStore.get(context) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layouts = remember(density) { PageLayouts(context.applicationContext, density) }
    val settings by store.settings.collectAsState()
    val log by store.log.collectAsState()
    val curl = rememberPageCurlState(initialCurrent = (startPage - 1).coerceIn(0, Quran.PAGES - 1))
    val page = curl.current + 1
    var chrome by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val night = remember { LocalTime.now().let { it.hour >= 19 || it.hour < 6 } }
    val colors = PageColors.of(settings.theme, night)
    val today = LocalDate.now()
    val readToday = QuranPlan.pagesReadOn(log, today)
    val pageRead = "$today|$page" in log

    BackHandler { if (selected != null) selected = null else onClose() }
    KeepScreenOn()

    // Where you are is where the reading continues from.
    LaunchedEffect(page) {
        store.setLastPage(page)
    }

    // The page's time: counted only while the reader is in front, and only once a day per page.
    var seconds by remember(page) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(page, pageRead) {
        if (pageRead) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (seconds < settings.secondsPerPage) {
                delay(1000)
                seconds++
            }
            store.markRead(page)
        }
    }
    LaunchedEffect(readToday) {
        if (target != null && readToday >= target) onTargetMet?.invoke()
    }

    // Opening: the mushaf opens like a cover, turning out from its spine on the right as it comes up.
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(620, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f))) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = open.value
                transformOrigin = TransformOrigin(1f, 0.5f)
                cameraDistance = 40f * this.density
                rotationY = -24f * (1f - t)
                scaleX = 0.94f + 0.06f * t
                scaleY = 0.94f + 0.06f * t
                alpha = (t * 1.6f).coerceAtMost(1f)
            }
            .background(colors.paper),
    ) {
        // Turned like paper: right to left, as in a printed mushaf.
        CurlPager(
            curl, mushaf, layouts, colors, selected,
            onAyah = { selected = it },
            onBackground = { chrome = !chrome },
            pageModifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(top = 52.dp, bottom = 44.dp),
        )

        // The bar: back, where you are, and the page's time.
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(
                Modifier.fillMaxWidth().background(colors.paper).statusBarsPadding().height(56.dp).padding(horizontal = Space.s),
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape).pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.quran_close), tint = colors.ink, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    val surah = mushaf.surahsOfPage(page).last()
                    Text(Quran.latinNames[surah - 1], style = Type.titleM.copy(color = colors.ink, lineHeight = 20.sp))
                    Text(
                        stringResource(R.string.quran_page_juz, page, mushaf.juzOfPage(page)),
                        style = Type.caption.copy(color = colors.ink.copy(alpha = 0.6f), lineHeight = 14.sp),
                    )
                }
                Box(Modifier.align(Alignment.CenterEnd)) { PageTimer({ seconds }, settings.secondsPerPage, pageRead, colors) }
            }
        }

        // Today's reading, at the foot of the page.
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val goal = target ?: QuranPlan.target(settings, ramadan = false)
            Row(
                Modifier.navigationBarsPadding().padding(bottom = 8.dp)
                    .background(colors.band, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.quran_today_progress, readToday.coerceAtMost(goal), goal),
                    style = Type.caption.copy(color = colors.ink, fontSize = 13.sp),
                )
            }
        }
    }

    selected?.let { ayah -> AyahSheet(ayah, onDismiss = { selected = null }) }
}

/** A ring that fills over the page's time, then a tick. */
@Composable
private fun PageTimer(secondsOf: () -> Int, total: Int, read: Boolean, colors: PageColors) {
    // Read here, not by the reader: the tick each second redraws only this ring.
    val seconds = secondsOf()
    val left = (total - seconds).coerceAtLeast(0)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(38.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            drawArc(colors.frame.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            drawArc(
                colors.accent, -90f, 360f * (if (read) 1f else seconds / total.toFloat()), false,
                Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (read) {
            Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        } else {
            Text("${left / 60}:${"%02d".format(left % 60)}", style = Type.caption.copy(color = colors.ink, fontSize = 10.sp))
        }
    }
}

/**
 * An ayah: its words, its meaning with its footnotes, and what can be done with it. The Arabic
 * reads right to left from the right edge, the meaning left to right from the left, both across
 * the same width.
 */
@Composable
private fun AyahSheet(ayah: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mushaf = remember { Mushaf.get(context) }
    val meanings = remember { Meanings.get(context) }
    val store = remember { QuranStore.get(context) }
    val saved by store.saved.collectAsState()
    val (surah, number) = Quran.ref(ayah)
    val meaning = remember(ayah) { meanings.of(ayah) }
    val arabic = mushaf.ayahs[ayah].joinToString(" ")
    val accent = LocalAura.current.accent
    var sharing by remember { mutableStateOf(false) }
    GlassDialog("${Quran.latinNames[surah - 1]} · $surah:$number", onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    arabic + " " + Quran.arabicDigits(number),
                    style = Type.arabicReading.copy(
                        fontFamily = UthmanicHafs, fontSize = 25.sp, lineHeight = 2.05.em, color = Nur.textPrimary,
                        textDirection = TextDirection.Rtl,
                    ),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Space.m))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Nur.textPrimary.copy(alpha = 0.1f)))
            Spacer(Modifier.height(Space.m))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.fillMaxWidth()) {
                    Text(meaning.text, style = Type.bodyL.copy(color = Nur.textPrimary, textDirection = TextDirection.Ltr), textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    meaning.footnotes.forEach {
                        Spacer(Modifier.height(Space.s))
                        Text(it, style = Type.caption.copy(textDirection = TextDirection.Ltr), textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(Space.m))
                    Text(meanings.credit, style = Type.caption.copy(fontSize = 10.sp, color = accent.copy(alpha = 0.7f)), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            val isSaved = ayah in saved
            SheetAction(if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, stringResource(if (isSaved) R.string.quran_saved else R.string.quran_save), Modifier.weight(1f)) {
                store.toggleSaved(ayah)
            }
            SheetAction(Icons.Rounded.ContentCopy, stringResource(R.string.quran_copy), Modifier.weight(1f)) {
                val text = "$arabic\n\n${meaning.text}\n— ${Quran.latinNames[surah - 1]} $surah:$number"
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ayah", text))
            }
            SheetAction(Icons.Rounded.IosShare, stringResource(R.string.quran_share), Modifier.weight(1f)) { sharing = true }
        }
        Spacer(Modifier.height(Space.m))
        PrimaryButton(stringResource(R.string.quran_done), Modifier.fillMaxWidth(), icon = null, onClick = onDismiss)
    }
    if (sharing) {
        // The card carries the meaning only when it has no footnote markers: QuranEnc allows no
        // edits, and markers pointing at notes the card can't hold would mislead.
        val withMeaning = meaning.footnotes.isEmpty() && !Regex("\\[\\d+]").containsMatchIn(meaning.text)
        val card = org.adhkaar.app.data.SessionDhikr(
            id = "ayah_$ayah",
            title = "${Quran.latinNames[surah - 1]} · $surah:$number",
            count = 1,
            arabic = arabic,
            transliteration = "",
            translation = if (withMeaning) meaning.text else "",
            reference = if (withMeaning) "Qur'an $surah:$number · Rowwad Translation Center, QuranEnc.com" else "Qur'an $surah:$number",
            virtue = null,
        )
        org.adhkaar.app.ui.share.ShareSheet(card, quran = true, onDismiss = { sharing = false })
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(44.dp)
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
            .pressable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalAura.current.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
        Text(label, style = Type.label)
    }
}

/** Reading keeps the screen on, as a session does. */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
