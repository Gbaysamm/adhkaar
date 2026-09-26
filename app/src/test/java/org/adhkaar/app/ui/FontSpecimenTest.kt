package org.adhkaar.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Type
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders Arabic font candidates inside a session card, one image each, for docs/screens.html.
 * Only runs when FONT_DIR points at the downloaded candidates (they aren't all bundled).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h620dp-xhdpi")
class FontSpecimenTest {
    @get:Rule
    val compose = createComposeRule()

    private val dir = System.getenv("FONT_DIR")?.let(::File)

    private val dua = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَىٰ عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ"
    private val ayah = "قُلْ هُوَ اللَّهُ أَحَدٌ ۝١ اللَّهُ الصَّمَدُ ۝٢"

    private fun render(file: String, name: String, label: String, size: Float = 28f, lineHeight: Float = 2.0f) {
        assumeTrue(dir != null)
        val family = FontFamily(Font(File(dir, file)))
        compose.setContent {
            AdhkaarTheme(Auras.evening) {
                AuraBackground(Modifier.fillMaxSize(), intensity = 0.8f) {
                    Column(
                        Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(32.dp))
                            .padding(24.dp),
                    ) {
                        Text(label, style = Type.label, color = Auras.evening.accent)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            dua,
                            style = Type.arabicReading.copy(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.em),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            ayah,
                            style = Type.arabicReading.copy(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.em),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Allāhumma anta rabbī lā ilāha illā ant, khalaqtanī wa ana ʿabduk…",
                            style = Type.bodyM, color = Nur.textTertiary, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/font-$name.png")
    }

    @Test fun a() = render("scheherazade_regular.ttf", "a", "A · Scheherazade New", 30f)
    @Test fun b() = render("ruwudu.ttf", "b", "B · Ruwudu", 26f, 2.2f)
    @Test fun c() = render("amiri_quran.ttf", "c", "C · Amiri Quran", 28f, 2.1f)
    @Test fun d() = render("hafs_try.ttf", "d", "D · KFGQPC Hafs (Madinah Mushaf)", 28f, 2.1f)
    @Test fun e() = render("lateef.ttf", "e", "E · Lateef", 32f)
    @Test fun f() = render("noto_naskh.ttf", "f", "F · Noto Naskh (current)", 28f)
    @Test fun g() = render("aref_ruqaa.ttf", "g", "G · Aref Ruqaa (calligraphic)", 28f, 2.1f)
}
