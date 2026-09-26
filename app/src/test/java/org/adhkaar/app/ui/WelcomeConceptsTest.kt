package org.adhkaar.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.CounterOrb
import org.adhkaar.app.ui.components.GlassPill
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Aura
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Three welcome-screen directions, rendered side by side for choosing. Delete once one is chosen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class WelcomeConceptsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { AdhkaarTheme(Auras.evening) { content() } }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/welcome-$name.png")
    }

    @Composable
    private fun Brand() {
        Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
            NurOrb(28.dp, float = false)
            Spacer(Modifier.width(Space.s))
            Text("Adhkaar", style = Type.titleM)
        }
    }

    /** A · Product glimpse: floating dhikr cards show what the app is. */
    @Test
    fun a() = shoot("a") {
        AuraBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.gutter)) {
                Brand()
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    // Cards behind, fanned out
                    Box(
                        Modifier.size(236.dp, 300.dp).offset((-34).dp, (-14).dp)
                            .graphicsLayer { rotationZ = -9f; alpha = 0.55f }
                            .glass(RoundedCornerShape(Radius.hero)),
                    )
                    Box(
                        Modifier.size(236.dp, 300.dp).offset(34.dp, (-4).dp)
                            .graphicsLayer { rotationZ = 7f; alpha = 0.75f }
                            .glass(RoundedCornerShape(Radius.hero)),
                    )
                    // Front card: a real dhikr
                    Column(
                        Modifier
                            .width(252.dp)
                            .clip(RoundedCornerShape(Radius.hero))
                            .background(Color(0xCC0C1230))
                            .glass(RoundedCornerShape(Radius.hero), level = 2)
                            .padding(Space.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GlassPill("Tasbih · ×100")
                        Spacer(Modifier.height(Space.l))
                        Text("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", style = Type.arabicReading, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Space.xs))
                        Text("Glory be to Allah and praise be to Him", style = Type.caption.copy(color = Nur.textSecondary), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Space.l))
                        CounterOrb(33, 100, size = 96.dp, onTap = {})
                    }
                    GlassPill("Fajr + 15 min", icon = Icons.Rounded.Alarm, color = Auras.evening.accent, modifier = Modifier.align(Alignment.TopEnd).offset(y = 36.dp))
                    GlassPill("5-day streak", icon = Icons.Rounded.LocalFireDepartment, color = Nur.gold, modifier = Modifier.align(Alignment.BottomStart).offset(y = (-40).dp))
                }
                Text(
                    buildAnnotatedString {
                        append("Your mornings and\nevenings, ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Auras.evening.accent)) { append("remembered.") }
                    },
                    style = Type.displayL,
                )
                Spacer(Modifier.height(Space.m))
                Text("The adhkaar open at Fajr and Asr, and stay with you until they’re said.", style = Type.bodyL)
                Spacer(Modifier.height(Space.xl))
                PrimaryButton("Get started") {}
                Spacer(Modifier.height(Space.l))
            }
        }
    }

    /** B · Calligraphy: one glowing word, nothing else competing. */
    @Test
    fun b() = shoot("b") {
        AuraBackground(Modifier.fillMaxSize(), intensity = 0.9f) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(320.dp)) {
                        drawCircle(Brush.radialGradient(listOf(Nur.gold.copy(alpha = 0.28f), Color.Transparent)))
                    }
                    Text(
                        "أذكار",
                        style = Type.arabicDisplay.copy(
                            fontSize = 118.sp, lineHeight = 150.sp,
                            brush = Brush.verticalGradient(listOf(Color(0xFFFFF4DA), Nur.gold, Color(0xFFC9973F))),
                        ),
                    )
                }
                Text("A D H K A A R", style = Type.overline.copy(letterSpacing = 6.sp, color = Nur.textSecondary))
                Spacer(Modifier.weight(1f))
                Text(
                    "Morning and evening,\nremembered.",
                    style = Type.displayM, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.m))
                Text("Opens at Fajr and Asr. Stays until you’ve finished.", style = Type.bodyM, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Space.xl))
                PrimaryButton("Get started") {}
                Spacer(Modifier.height(Space.l))
            }
        }
    }

    @Composable
    private fun MomentCard(aura: Aura, over: String, title: String, line: String, modifier: Modifier) {
        Box(
            modifier
                .clip(RoundedCornerShape(Radius.card))
                .background(Brush.verticalGradient(listOf(aura.glows[0].copy(alpha = 0.35f), aura.glows[1].copy(alpha = 0.18f), Color(0x66000000))))
                .glass(RoundedCornerShape(Radius.card)),
        ) {
            Column(Modifier.fillMaxSize().padding(Space.gutter)) {
                NurOrb(72.dp, aura = aura)
                Spacer(Modifier.weight(1f))
                Text(over, style = Type.overline)
                Spacer(Modifier.height(Space.xs))
                Text(title, style = Type.titleL)
                Spacer(Modifier.height(Space.xs))
                Text(line, style = Type.caption.copy(color = Nur.textSecondary))
            }
        }
    }

    /** C · Two moments: the rhythm of the app, explained by its two sessions. */
    @Test
    fun c() = shoot("c") {
        AuraBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.gutter)) {
                Brand()
                Spacer(Modifier.height(Space.xxl))
                Text(
                    buildAnnotatedString {
                        append("Two moments.\n")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Auras.evening.accent)) { append("Every day.") }
                    },
                    style = Type.displayL.copy(fontSize = 44.sp, lineHeight = 46.sp),
                )
                Spacer(Modifier.height(Space.m))
                Text("Your adhkaar open at the right time, and stay until you’ve said them.", style = Type.bodyL)
                Spacer(Modifier.height(Space.xl))
                Row(Modifier.fillMaxWidth().height(320.dp), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    MomentCard(Auras.dawn, "MORNING", "After Fajr", "In His care until evening", Modifier.weight(1f).fillMaxHeight())
                    MomentCard(Auras.evening, "EVENING", "After Asr", "In His care until morning", Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    GlassPill("On time", icon = Icons.Rounded.Alarm)
                    GlassPill("Focused", icon = Icons.Rounded.Lock)
                    GlassPill("Private", icon = Icons.Rounded.Shield)
                }
                Spacer(Modifier.height(Space.l))
                PrimaryButton("Get started") {}
                Spacer(Modifier.height(Space.l))
            }
        }
    }
}

