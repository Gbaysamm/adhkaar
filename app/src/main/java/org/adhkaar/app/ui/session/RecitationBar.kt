package org.adhkaar.app.ui.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adhkaar.app.R
import org.adhkaar.app.session.RecitationPlayer
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/**
 * The recitation orbits the counter: a thin playback ring around it, play/pause on the left
 * (repeat beneath), speed on the right (elapsed time beneath). Without a recording, both sides
 * keep their width so the counter stays exactly centred.
 */
@Composable
fun OrbWithRecitation(audioId: String?, orb: @Composable () -> Unit) {
    val context = LocalContext.current
    val aura = LocalAura.current
    val playback by RecitationPlayer.playback.collectAsState()
    val speed by RecitationPlayer.speed.collectAsState()
    val repeat by RecitationPlayer.repeat.collectAsState()
    val mine = playback?.takeIf { audioId != null && it.id == audioId }
    val isPlaying = mine?.isPlaying == true
    val progress = if (mine != null && mine.durationMs > 0) mine.positionMs.toFloat() / mine.durationMs else 0f
    val side = 64.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(side), horizontalAlignment = Alignment.CenterHorizontally) {
            if (audioId != null) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(aura.action))
                        .pressable(scale = 0.9f, haptic = true) { RecitationPlayer.toggle(context, audioId) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = Color.White, modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(Space.s))
                val repeatTint by animateColorAsState(if (repeat) aura.accent else Nur.textTertiary, label = "repeat")
                Box(
                    Modifier.size(32.dp).clip(CircleShape).pressable(scale = 0.9f) { RecitationPlayer.toggleRepeat() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Repeat, stringResource(R.string.player_repeat), tint = repeatTint, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.width(Space.l))
        Box(contentAlignment = Alignment.Center) {
            orb()
            if (mine != null) {
                // Playback ring, just outside the counter: its own quiet track and progress.
                Canvas(Modifier.size(164.dp)) {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2
                    val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.07f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(
                        aura.accent.copy(alpha = 0.85f), -90f, 360f * progress, false,
                        androidx.compose.ui.geometry.Offset(inset, inset), arc,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.l))
        Column(Modifier.width(side), horizontalAlignment = Alignment.CenterHorizontally) {
            if (audioId != null) {
                Box(
                    Modifier
                        .size(48.dp)
                        .glass(CircleShape, level = 2)
                        .pressable(scale = 0.9f) { RecitationPlayer.cycleSpeed() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "×" + if (speed % 1f == 0f) speed.toInt().toString() else speed.toString(),
                        style = Type.label.copy(fontSize = 13.sp, fontFeatureSettings = "tnum", color = if (speed == 1f) Nur.textPrimary else aura.accent),
                    )
                }
                Spacer(Modifier.height(Space.s))
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                    Text(clock(mine?.positionMs ?: 0), style = Type.caption.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

private fun clock(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
