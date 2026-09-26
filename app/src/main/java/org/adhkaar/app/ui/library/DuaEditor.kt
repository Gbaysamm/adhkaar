package org.adhkaar.app.ui.library

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import org.adhkaar.app.session.RecitationPlayer
import org.adhkaar.app.session.Recordings
import org.adhkaar.app.session.VoiceRecorder
import org.adhkaar.app.ui.components.GlassCard
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.data.UserDua
import org.adhkaar.app.data.UserDuaStore
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.GlassSwitch
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.settings.RowDivider
import org.adhkaar.app.ui.settings.SettingsGroup
import org.adhkaar.app.ui.settings.SettingsRow
import org.adhkaar.app.ui.settings.Stepper
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/** Label above, glass field below, placeholder inside. */
@Composable
fun GlassTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = Type.bodyL.copy(color = Nur.textPrimary),
    minHeight: Int = 52,
    singleLine: Boolean = false,
) {
    val accent = LocalAura.current.accent
    Column(modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = Type.overline, modifier = Modifier.padding(bottom = Space.s))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
                .glass(RoundedCornerShape(Radius.chip + 6.dp), level = 2)
                .padding(horizontal = Space.l, vertical = 14.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = Nur.textDisabled))
                    inner()
                }
            },
        )
    }
}

@Composable
fun DuaEditor(existingId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { UserDuaStore.get(context) }
    val existing = remember(existingId) { store.flow.value.firstOrNull { it.id == existingId } }
    // One id for the whole edit, so a recording made before saving belongs to this dua.
    val draftId = rememberSaveable { existing?.id ?: UserDua(title = "").id }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var arabic by rememberSaveable { mutableStateOf(existing?.arabic ?: "") }
    var transliteration by rememberSaveable { mutableStateOf(existing?.transliteration ?: "") }
    var translation by rememberSaveable { mutableStateOf(existing?.translation ?: "") }
    var count by rememberSaveable { mutableStateOf(existing?.count ?: 1) }
    var inMorning by rememberSaveable { mutableStateOf(existing?.inMorning ?: false) }
    var inEvening by rememberSaveable { mutableStateOf(existing?.inEvening ?: false) }

    val draft = (existing ?: UserDua(id = draftId, title = "")).copy(
        title = title.trim(), arabic = arabic.trim(), transliteration = transliteration.trim(),
        translation = translation.trim(), count = count, inMorning = inMorning, inEvening = inEvening,
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.padding(horizontal = Space.iconEdge, vertical = Space.xs)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.library_back), onClick = onDone)
        }
        // Leaving without saving throws away a new take (the saved one stays).
        DisposableEffect(draftId) { onDispose { RecitationPlayer.stop(); Recordings.discardPending(context, draftId) } }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(stringResource(if (existing == null) R.string.dua_new else R.string.dua_edit), style = Type.displayL)
            Spacer(Modifier.height(Space.xs))
            Text(stringResource(R.string.dua_intro), style = Type.bodyM)
            Spacer(Modifier.height(Space.xl))
            GlassTextField(stringResource(R.string.dua_field_title), title, { title = it }, stringResource(R.string.dua_field_title_placeholder), singleLine = true)
            Spacer(Modifier.height(Space.l))
            GlassTextField(
                stringResource(R.string.dua_field_arabic), arabic, { arabic = it }, "اكتب الدعاء هنا",
                textStyle = Type.arabicAccent.copy(textAlign = TextAlign.Right), minHeight = 96,
            )
            Spacer(Modifier.height(Space.l))
            GlassTextField(stringResource(R.string.dua_field_transliteration), transliteration, { transliteration = it }, stringResource(R.string.dua_field_transliteration_placeholder), minHeight = 72)
            Spacer(Modifier.height(Space.l))
            GlassTextField(stringResource(R.string.dua_field_meaning), translation, { translation = it }, stringResource(R.string.dua_field_meaning_placeholder), minHeight = 96)

            Spacer(Modifier.height(Space.xl))
            VoiceCard(draftId)
            Spacer(Modifier.height(Space.xl))
            SettingsGroup {
                SettingsRow(stringResource(R.string.dua_repeat), null, Icons.Rounded.Repeat, iconTint = Nur.textTertiary, trailing = {
                    Stepper(pluralStringResource(R.plurals.counter_times, count, count), onMinus = { count-- }, onPlus = { count++ }, canMinus = count > 1, canPlus = count < 100)
                })
                RowDivider()
                SettingsRow(stringResource(R.string.dua_in_morning), null, Icons.Rounded.WbTwilight, iconTint = Auras.dawn.accent, trailing = {
                    GlassSwitch(inMorning, { inMorning = it })
                })
                RowDivider()
                SettingsRow(stringResource(R.string.dua_in_evening), null, Icons.Rounded.NightsStay, iconTint = Auras.evening.accent, trailing = {
                    GlassSwitch(inEvening, { inEvening = it })
                })
            }
            if (existing != null) {
                Spacer(Modifier.height(Space.xl))
                Text(
                    stringResource(R.string.dua_delete),
                    style = Type.label.copy(color = Nur.danger),
                    modifier = Modifier
                        .offset(x = -Space.s)
                        .pressable {
                            store.delete(existing.id)
                            Recordings.delete(context, existing.id)
                            onDone()
                        }
                        .padding(Space.s),
                )
            }
            Spacer(Modifier.height(Space.xl))
        }
        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.l)) {
            if (!draft.isValid) {
                Text(stringResource(R.string.dua_invalid_hint), style = Type.caption, modifier = Modifier.padding(bottom = Space.m))
            }
            PrimaryButton(
                stringResource(R.string.dua_save),
                icon = Icons.Rounded.Check,
                modifier = Modifier.graphicsLayer { alpha = if (draft.isValid) 1f else 0.4f },
            ) {
                if (draft.isValid) {
                    store.save(draft)
                    Recordings.commit(context, draftId)
                    onDone()
                }
            }
        }
    }
}

/**
 * Record the dua in your own voice: record → stop → listen, re-record or delete.
 * The microphone is asked for only on the first tap of Record.
 */
@Composable
private fun VoiceCard(duaId: String) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var take by remember { mutableStateOf(Recordings.current(context, duaId)) }
    var seconds by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    val playing by RecitationPlayer.playing.collectAsState()
    val isPlaying = take != null && playing == take?.path

    fun startRecording() {
        RecitationPlayer.stop()
        recording = recorder.start(Recordings.pending(context, duaId))
        seconds = 0
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) startRecording()
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1_000)
            seconds++
            if (seconds >= MAX_SECONDS) {
                recorder.stop()
                recording = false
                take = Recordings.current(context, duaId)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.stop() } }

    Text(stringResource(R.string.dua_voice_overline), style = Type.overline, modifier = Modifier.padding(bottom = Space.s))
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                recording -> {
                    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
                        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
                    )
                    Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Nur.danger))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dua_voice_recording), style = Type.label)
                        Text(clock(seconds), style = Type.caption.copy(fontFeatureSettings = "tnum"))
                    }
                    RoundAction(Icons.Rounded.Stop, stringResource(R.string.dua_voice_stop), Nur.danger) {
                        recorder.stop()
                        recording = false
                        take = Recordings.current(context, duaId)
                    }
                }
                take != null -> {
                    RoundAction(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (isPlaying) R.string.dua_voice_pause else R.string.dua_voice_play), LocalAura.current.accent) {
                        take?.let { if (isPlaying) RecitationPlayer.stop() else RecitationPlayer.playFile(it) }
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dua_voice_your_recording), style = Type.label)
                        Text(stringResource(R.string.dua_voice_plays_with), style = Type.caption)
                    }
                    RoundAction(Icons.Rounded.Mic, stringResource(R.string.dua_voice_record_again), Nur.textSecondary) {
                        if (hasMic(context)) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    Spacer(Modifier.width(Space.s))
                    RoundAction(Icons.Rounded.DeleteOutline, stringResource(R.string.dua_voice_delete), Nur.textSecondary) {
                        RecitationPlayer.stop()
                        Recordings.delete(context, duaId)
                        take = null
                    }
                }
                else -> {
                    RoundAction(Icons.Rounded.Mic, stringResource(R.string.dua_voice_record), Nur.danger) {
                        if (hasMic(context)) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dua_voice_record_prompt), style = Type.label)
                        Text(
                            stringResource(if (denied) R.string.dua_voice_mic_denied else R.string.dua_voice_limit),
                            style = Type.caption.copy(color = if (denied) Nur.danger else Nur.textTertiary),
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_SECONDS = 180

private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun hasMic(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .glass(CircleShape, level = 2)
            .pressable(scale = 0.9f, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
