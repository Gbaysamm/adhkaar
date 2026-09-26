package org.adhkaar.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarRepository
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.session.MissedAdhkaar
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.settings.GlassDialog
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Said once, kindly, when a morning or evening was missed: that it happens, when the next ones
 * are, what these adhkaar hold (their own virtues, with sources), and a way back.
 */
@Composable
fun MissedSheet(missed: MissedAdhkaar.Missed, onRead: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val morning = missed.type == SessionType.MORNING
    val title = stringResource(
        when {
            missed.date.isBefore(LocalDate.now()) -> R.string.missed_title_yesterday
            morning -> R.string.missed_title_morning
            else -> R.string.missed_title_evening
        },
    )
    // Three of the session's own promises, each with where it's found.
    val held = remember(missed) { AdhkaarRepository.forSession(context, missed.type).filter { !it.virtue.isNullOrBlank() }.take(3) }
    val next = remember {
        val now = ZonedDateTime.now()
        SessionType.entries.mapNotNull { t -> AlarmScheduler.nextTime(context, t, now)?.let { t to it } }.minByOrNull { it.second }
    }
    GlassDialog(title, onDismiss) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconBadge(if (morning) Icons.Rounded.WbTwilight else Icons.Rounded.DarkMode, size = 52.dp)
            }
            Spacer(Modifier.height(Space.m))
            Text(stringResource(R.string.missed_body), style = Type.bodyM)
            next?.let { (type, at) ->
                Spacer(Modifier.height(Space.s))
                Text(
                    stringResource(
                        R.string.missed_next,
                        stringResource(if (type == SessionType.MORNING) R.string.missed_name_morning else R.string.missed_name_evening),
                        formatTime(context, at),
                    ),
                    style = Type.label.copy(color = LocalAura.current.accent),
                )
            }
            if (held.isNotEmpty()) {
                Spacer(Modifier.height(Space.l))
                Text(stringResource(R.string.missed_held).uppercase(), style = Type.overline)
                held.forEach { d ->
                    Spacer(Modifier.height(Space.s))
                    GlassCard(Modifier.fillMaxWidth(), level = 2) {
                        Text(d.title, style = Type.label)
                        Spacer(Modifier.height(4.dp))
                        Text(d.virtue.orEmpty(), style = Type.bodyM.copy(color = Nur.textPrimary))
                        Spacer(Modifier.height(4.dp))
                        Text(d.reference, style = Type.caption)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            Text("أَحَبُّ الأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ", style = Type.arabicAccent, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.missed_hadith), style = Type.bodyM, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.missed_hadith_source).uppercase(),
                style = Type.overline.copy(color = LocalAura.current.accent), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.missed_ok), Modifier.fillMaxWidth(), icon = null, onClick = onDismiss)
        Box(Modifier.fillMaxWidth().height(48.dp).pressable(onClick = onRead), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.missed_read), style = Type.label.copy(color = Nur.textSecondary))
        }
    }
}
