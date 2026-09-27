package org.adhkaar.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.data.SessionType

// Tokens from docs/design/DESIGN.md. Change them there first.

object Nur {
    val ink = Color(0xFF05070F)
    val textPrimary = Color(0xFFF5F7FF)
    val textSecondary = Color.White.copy(alpha = 0.72f)
    val textTertiary = Color.White.copy(alpha = 0.48f)
    val textDisabled = Color.White.copy(alpha = 0.28f)
    val gold = Color(0xFFF2CF8A)
    val success = Color(0xFF5BE3A4)
    val danger = Color(0xFFFF7A7A)
}

/** Light and colour for one session. */
@Immutable
data class Aura(
    val base: List<Color>,
    val glows: List<Color>,
    val accent: Color,
    val action: List<Color>,
)

object Auras {
    val evening = Aura(
        base = listOf(Color(0xFF070B1F), Color(0xFF05070F)),
        glows = listOf(Color(0xFF2B5BFF), Color(0xFF6A4CFF), Color(0xFF00B8FF)),
        accent = Color(0xFF8FB1FF),
        action = listOf(Color(0xFF4B74FF), Color(0xFF2A4BD8)),
    )
    val dawn = Aura(
        base = listOf(Color(0xFF120A18), Color(0xFF07050C)),
        glows = listOf(Color(0xFFFF7A59), Color(0xFFC04CFF), Color(0xFFFFB86B)),
        accent = Color(0xFFFFB88A),
        action = listOf(Color(0xFFFF8E5E), Color(0xFFE0567E)),
    )

    /** The Qur'an's own light: deep green and gold, so reading it never looks like the adhkaar. */
    val mushaf = Aura(
        base = listOf(Color(0xFF06120F), Color(0xFF040908)),
        glows = listOf(Color(0xFF1F8A6E), Color(0xFFB58A3C), Color(0xFF0E6B5C)),
        accent = Color(0xFFE6C27A),
        action = listOf(Color(0xFF2FA283), Color(0xFF1C6E5A)),
    )

    fun of(type: SessionType) = if (type == SessionType.MORNING) dawn else evening
}

val LocalAura = staticCompositionLocalOf { Auras.evening }

/**
 * Chart marks (week rings, 30-day grid, calendar, bars). Deeper than the UI accents so they sit
 * in the dark-mode lightness band; validated with the dataviz palette checker on #0E1224
 * (CVD ΔE 25, normal ΔE 27, contrast ≥ 3:1). UI elements keep the lighter accents.
 */
object ChartColors {
    val morning = Color(0xFFD6733E)
    val evening = Color(0xFF6A8FEF)
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val gutter = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 56.dp
    /**
     * Where a top bar's 48dp icon button starts, so its 44dp glass circle sits exactly on the page
     * margin, in line with the title below it.
     */
    val iconEdge = gutter - 2.dp
    /** Room left at the bottom of scrolling screens so content clears the floating tab bar. */
    val tabBarClearance = 112.dp
}

object Radius {
    val chip = 12.dp
    val card = 28.dp
    val hero = 32.dp
}

object Motion {
    val emphasized = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    fun <T> enter() = tween<T>(420, easing = emphasized)
    fun <T> exit() = tween<T>(180, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
    fun <T> press() = spring<T>(dampingRatio = 0.7f, stiffness = 500f)
    fun <T> move() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

object Glass {
    val fill1 = Color.White.copy(alpha = 0.05f)
    val fill2 = Color.White.copy(alpha = 0.09f)
    val fillPressed = Color.White.copy(alpha = 0.14f)
    val edge1 = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.03f)))
    val edge2 = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.05f)))
    val highlight = Color.White.copy(alpha = 0.18f)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Reading: Amiri Quran, a Naskh drawn for Qur'anic text, with full tashkeel and ayah ornaments. */
val AmiriQuran = FontFamily(Font(R.font.amiri_quran, FontWeight.Normal))

/** The King Fahd Complex's Uthmanic Hafs, for the mushaf pages. */
val UthmanicHafs = FontFamily(Font(R.font.uthmanic_hafs, FontWeight.Normal))

/** The Complex's heading font: surah names, their framed band, and the basmala as printed. */
val QcfHeadings = FontFamily(Font(R.font.qcf_bsml, FontWeight.Normal))

/** Display only (short phrases such as الحمد لله): calligraphic Ruqaa. Never for adhkaar text. */
val ArefRuqaa = FontFamily(Font(R.font.aref_ruqaa, FontWeight.Normal))

/**
 * Display (heading) typeface for a language. Instrument Serif has no Hausa/Yoruba/Igbo letters
 * (ɓ ɗ ƙ ẹ ọ ṣ ị ụ ṅ) or Arabic script, so those languages get a serif that has them, instead of
 * mixing two fonts inside one word.
 */
@OptIn(ExperimentalTextApi::class)
private fun notoSansArabic(weight: Int) = Font(
    R.font.noto_sans_arabic,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Interface text in Arabic and Urdu. Phones' own Arabic fallback fonts vary widely in size and spacing. */
val NotoSansArabic = FontFamily(notoSansArabic(400), notoSansArabic(500), notoSansArabic(600), notoSansArabic(700))

fun uiFamilyFor(language: String): FontFamily = if (language == "ar" || language == "ur") NotoSansArabic else Inter

@OptIn(ExperimentalTextApi::class)
fun displayFamilyFor(language: String): FontFamily = when (language) {
    "ha", "yo", "ig" -> FontFamily(
        Font(
            R.font.noto_serif_display, FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.width(82f)),
        ),
    )
    "ar", "ur" -> AmiriQuran
    else -> InstrumentSerif
}

object Type {
    /** Set once per activity from the locale (a language change recreates the activity). */
    var displayFamily: FontFamily = InstrumentSerif
        internal set

    private val serif get() = TextStyle(
        fontFamily = displayFamily, fontWeight = FontWeight.Normal, color = Nur.textPrimary,
        lineHeightStyle = if (displayFamily == AmiriQuran) centred else null,
    )
    /** Interface typeface: Inter, or Noto Sans Arabic for Arabic and Urdu. Set with [displayFamily]. */
    var uiFamily: FontFamily = Inter
        internal set

    // Arabic letters join; any letter spacing, even tightening, breaks them apart.
    // Arabic stacks marks above and below the letters, so its lines get 30% more room.
    private fun ui(style: TextStyle) = if (uiFamily == Inter) style else style.copy(
        fontFamily = uiFamily, letterSpacing = 0.sp, lineHeight = style.lineHeight * 1.3f, lineHeightStyle = centred,
    )

    // Arabic fonts reserve tall space above the letters for stacked marks, which sinks the letters
    // low in their line; centring them keeps spacing even with the Latin layout.
    private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
    private val sans = TextStyle(fontFamily = Inter, color = Nur.textPrimary)

    val displayXl get() = serif.copy(fontSize = 60.sp, lineHeight = 62.sp, letterSpacing = (-1.2).sp)
    val displayL get() = serif.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp)
    val displayM get() = serif.copy(fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
    val titleL get() = ui(sans.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp))
    val titleM get() = ui(sans.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp))
    val bodyL get() = ui(sans.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp, color = Nur.textSecondary))
    val bodyM get() = ui(sans.copy(fontSize = 14.sp, lineHeight = 20.sp, color = Nur.textSecondary))
    val label get() = ui(sans.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp))
    val caption get() = ui(sans.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Nur.textTertiary))
    val overline get() = ui(sans.copy(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp, color = Nur.textTertiary,
    ))
    /** Big numbers (countdown). Inter, not the serif: its "1" reads as "l". */
    val countdown get() = ui(sans.copy(
        fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium,
        letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum",
    ))
    val stat get() = ui(sans.copy(
        fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum",
    ))

    private val arabic = TextStyle(
        fontFamily = AmiriQuran,
        color = Nur.textPrimary,
        textDirection = TextDirection.Rtl,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val arabicReading = arabic.copy(fontSize = 28.sp, lineHeight = 2.1.em)
    val arabicAccent = arabic.copy(fontSize = 22.sp, lineHeight = 1.8.em)
    val arabicDisplay = arabic.copy(fontFamily = ArefRuqaa, fontSize = 44.sp, lineHeight = 1.6.em)
}

private val MaterialColors = darkColorScheme(
    primary = Auras.evening.accent,
    onPrimary = Nur.ink,
    background = Nur.ink,
    onBackground = Nur.textPrimary,
    surface = Color(0xFF0E1224),
    onSurface = Nur.textPrimary,
    surfaceVariant = Color(0xFF161B33),
    onSurfaceVariant = Nur.textSecondary,
    surfaceContainerHigh = Color(0xFF12172C),
    outline = Color.White.copy(alpha = 0.16f),
    outlineVariant = Color.White.copy(alpha = 0.08f),
    error = Nur.danger,
)

/** Dark-only by design (see DESIGN.md). [aura] sets the session colours for everything inside. */
@Composable
fun AdhkaarTheme(aura: Aura = Auras.evening, content: @Composable () -> Unit) {
    val hapticsOn = org.adhkaar.app.data.SettingsStore.get(androidx.compose.ui.platform.LocalContext.current).flow.collectAsState().value.haptics
    // Haptics is also called outside composition (callbacks, services), so it keeps its own copy.
    org.adhkaar.app.ui.components.Haptics.enabled = hapticsOn
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    Type.displayFamily = displayFamilyFor(language)
    Type.uiFamily = uiFamilyFor(language)
    MaterialTheme(
        colorScheme = MaterialColors.copy(primary = aura.accent),
        typography = Typography(bodyLarge = Type.bodyL, bodyMedium = Type.bodyM, labelLarge = Type.label, titleMedium = Type.titleM),
    ) {
        CompositionLocalProvider(
            org.adhkaar.app.ui.components.LocalHaptics provides hapticsOn,
            LocalContentColor provides Nur.textPrimary,
            LocalAura provides aura,
            content = content,
        )
    }
}
