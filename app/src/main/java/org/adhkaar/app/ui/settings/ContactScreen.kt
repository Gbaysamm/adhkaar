package org.adhkaar.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.adhkaar.app.BuildConfig
import org.adhkaar.app.R
import org.adhkaar.app.data.DeviceReport
import org.adhkaar.app.data.Feedback
import org.adhkaar.app.data.FeedbackTopic
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.GlassSurface
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.SecondaryButton
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.glowBelow
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.library.GlassTextField
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

private enum class Phase { EDITING, SENDING, SENT, FAILED }

/**
 * Pushed screen: a plain message form for people who will never use GitHub. The message goes to
 * a small web service (tools/report-worker) that files it for the maintainers; the device details
 * sent with it are shown on the screen, so nothing leaves the phone unseen. The draft survives a
 * failed send (and rotation), so nothing typed is lost.
 */
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    val report = remember(settings) { DeviceReport.of(context, settings) }
    val scope = rememberCoroutineScope()

    var topic by rememberSaveable { mutableStateOf(FeedbackTopic.PROBLEM) }
    var message by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    // Not saved: a send in flight doesn't survive the activity, so it comes back as a draft.
    var phase by remember { mutableStateOf(Phase.EDITING) }

    fun emailInstead() = openEmail(context, topic, message, contact, report)

    fun send() {
        val url = BuildConfig.REPORT_URL
        if (url.isBlank()) {
            if (BuildConfig.CONTACT_EMAIL.isNotBlank()) emailInstead() else phase = Phase.FAILED
            return
        }
        phase = Phase.SENDING
        val body = Feedback.json(topic, message, contact, report)
        scope.launch {
            phase = if (Feedback.send(url, body)) {
                message = ""
                contact = ""
                Phase.SENT
            } else {
                Phase.FAILED
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = Space.iconEdge, vertical = Space.xs)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.setup_back), onClick = onBack)
        }
        AnimatedContent(
            targetState = phase == Phase.SENT,
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            label = "contact",
        ) { sent ->
            if (sent) {
                ContactSentView(onBack)
            } else {
                ContactForm(
                    topic = topic, onTopic = { topic = it },
                    message = message, onMessage = { message = it; if (phase == Phase.FAILED) phase = Phase.EDITING },
                    contact = contact, onContact = { contact = it },
                    report = report,
                    sending = phase == Phase.SENDING,
                    failed = phase == Phase.FAILED,
                    onSend = ::send,
                    onEmail = ::emailInstead,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactForm(
    topic: FeedbackTopic,
    onTopic: (FeedbackTopic) -> Unit,
    message: String,
    onMessage: (String) -> Unit,
    contact: String,
    onContact: (String) -> Unit,
    report: DeviceReport,
    sending: Boolean,
    failed: Boolean,
    onSend: () -> Unit,
    onEmail: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Spacer(Modifier.height(Space.s))
        Text(stringResource(R.string.contact_title), style = Type.displayL)
        Spacer(Modifier.height(Space.s))
        Text(stringResource(R.string.contact_intro), style = Type.bodyL)
        Spacer(Modifier.height(Space.xl))

        Text(stringResource(R.string.contact_topic_label).uppercase(), style = Type.overline, modifier = Modifier.padding(bottom = Space.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            FeedbackTopic.entries.forEach { t -> TopicChip(topicLabel(t), t == topic) { onTopic(t) } }
        }
        Spacer(Modifier.height(Space.xl))

        GlassTextField(
            label = stringResource(messageLabel(topic)),
            value = message,
            onValueChange = { if (it.length <= Feedback.MAX_MESSAGE) onMessage(it) },
            placeholder = stringResource(messagePlaceholder(topic)),
            minHeight = 150,
        )
        Spacer(Modifier.height(Space.l))
        GlassTextField(
            label = stringResource(R.string.contact_reply_label),
            value = contact,
            onValueChange = { if (it.length <= 200) onContact(it) },
            placeholder = stringResource(R.string.contact_reply_placeholder),
            singleLine = true,
        )
        Spacer(Modifier.height(Space.l))
        DetailsCard(report)
        Spacer(Modifier.height(Space.xl))

        AnimatedVisibility(failed, enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()), exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit())) {
            Row(Modifier.padding(bottom = Space.l), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Nur.danger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.s))
                Text(stringResource(R.string.contact_failed), style = Type.bodyM.copy(color = Nur.textPrimary))
            }
        }

        val canSend = Feedback.canSend(message)
        if (sending) {
            SendingButton()
        } else {
            PrimaryButton(
                stringResource(if (failed) R.string.contact_try_again else R.string.contact_send),
                enabled = canSend,
                onClick = { if (canSend) onSend() },
            )
        }
        if (!canSend && !sending) {
            Text(
                stringResource(R.string.contact_min_hint),
                style = Type.caption, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Space.s),
            )
        }
        if (failed && BuildConfig.CONTACT_EMAIL.isNotBlank()) {
            Spacer(Modifier.height(Space.s))
            SecondaryButton(stringResource(R.string.contact_email_instead), onClick = onEmail)
        }
        // To paste into WhatsApp or anywhere else: the message and the phone details together.
        Spacer(Modifier.height(Space.s))
        val context = LocalContext.current
        SecondaryButton(stringResource(R.string.contact_copy)) { copyReport(context, message, contact, report) }
        Spacer(Modifier.height(Space.xxxl))
    }
}

@Composable
private fun TopicChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAura.current.accent
    val shape = RoundedCornerShape(50)
    val edge by animateColorAsState(if (selected) accent.copy(alpha = 0.75f) else Color.Transparent, tween(220), label = "chipEdge")
    val color by animateColorAsState(if (selected) Nur.textPrimary else Nur.textSecondary, tween(220), label = "chipText")
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .semantics { this.selected = selected; role = Role.RadioButton }
            .pressable(shape = shape, onClick = onClick)
            .glass(shape, level = if (selected) 2 else 1)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent, shape)
            .border(1.dp, edge, shape)
            .padding(horizontal = Space.l, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.label, color = color)
    }
}

/** Everything sent besides the message, shown word for word. Folded by default. */
@Composable
private fun DetailsCard(report: DeviceReport) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.move(), label = "detailsChevron")
    GlassSurface(Modifier.fillMaxWidth(), onClick = { open = !open }) {
        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = LocalAura.current.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.contact_details_title), style = Type.label)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.contact_details_note), style = Type.caption)
                }
                Spacer(Modifier.width(Space.m))
                Icon(
                    Icons.Rounded.KeyboardArrowDown, null, tint = Nur.textTertiary,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
                )
            }
            AnimatedVisibility(open, enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()), exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit())) {
                GlassCard(Modifier.fillMaxWidth().padding(top = Space.m), level = 2, padding = androidx.compose.foundation.layout.PaddingValues(Space.l)) {
                    Text(report.lines().joinToString("\n"), style = Type.caption.copy(color = Nur.textSecondary, lineHeight = Type.bodyM.lineHeight))
                }
            }
        }
    }
}

/** Same size as the primary button, so nothing jumps while the message goes. */
@Composable
private fun SendingButton() {
    Box(
        Modifier.fillMaxWidth().height(56.dp).glass(RoundedCornerShape(50), level = 2),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalAura.current.accent)
            Spacer(Modifier.width(Space.m))
            Text(stringResource(R.string.contact_sending), style = Type.label)
        }
    }
}

/** A calm thank-you once the message has gone. */
@Composable
internal fun ContactSentView(onBack: () -> Unit) {
    val aura = LocalAura.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .glowBelow(aura.accent.copy(alpha = 0.4f), 44.dp, offsetY = 0.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(aura.action)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(Space.xxl))
        Text(stringResource(R.string.contact_sent_title), style = Type.displayM, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.m))
        Text(stringResource(R.string.contact_sent_body), style = Type.bodyL, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xxl))
        SecondaryButton(stringResource(R.string.contact_back), onClick = onBack)
        // Sits a little above centre, where the eye rests.
        Spacer(Modifier.height(Space.huge + Space.xxl))
    }
}

@Composable
private fun topicLabel(t: FeedbackTopic) = stringResource(
    when (t) {
        FeedbackTopic.PROBLEM -> R.string.contact_topic_problem
        FeedbackTopic.SUGGESTION -> R.string.contact_topic_suggestion
        FeedbackTopic.CORRECTION -> R.string.contact_topic_correction
        FeedbackTopic.OTHER -> R.string.contact_topic_other
    },
)

private fun messageLabel(t: FeedbackTopic) = when (t) {
    FeedbackTopic.PROBLEM -> R.string.contact_message_problem
    FeedbackTopic.SUGGESTION -> R.string.contact_message_suggestion
    FeedbackTopic.CORRECTION -> R.string.contact_message_correction
    FeedbackTopic.OTHER -> R.string.contact_message_other
}

private fun messagePlaceholder(t: FeedbackTopic) = when (t) {
    FeedbackTopic.PROBLEM -> R.string.contact_placeholder_problem
    FeedbackTopic.SUGGESTION -> R.string.contact_placeholder_suggestion
    FeedbackTopic.CORRECTION -> R.string.contact_placeholder_correction
    FeedbackTopic.OTHER -> R.string.contact_placeholder_other
}

/** The fallback when the message can't be sent from the app: the user's own email app, filled in. */
/** The message with the phone's details: what's emailed, sent or copied. */
private fun reportText(message: String, contact: String, report: DeviceReport) = buildString {
    append(message.trim())
    if (contact.isNotBlank()) append("\n\nReply to: ").append(contact.trim())
    append("\n\n---\n").append(report.lines().joinToString("\n"))
}

private fun copyReport(context: Context, message: String, contact: String, report: DeviceReport) {
    context.getSystemService(android.content.ClipboardManager::class.java)
        .setPrimaryClip(android.content.ClipData.newPlainText("Adhkaar", reportText(message, contact, report)))
    android.widget.Toast.makeText(context, context.getString(R.string.contact_copied), android.widget.Toast.LENGTH_SHORT).show()
}

private fun openEmail(context: Context, topic: FeedbackTopic, message: String, contact: String, report: DeviceReport) {
    val body = reportText(message, contact, report)
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${BuildConfig.CONTACT_EMAIL}"))
        .putExtra(Intent.EXTRA_SUBJECT, "Adhkaar (${topic.id}): ${report.device}")
        .putExtra(Intent.EXTRA_TEXT, body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
