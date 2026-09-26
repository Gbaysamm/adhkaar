package org.adhkaar.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/** How the app works, in plain words: times, the three modes and their trade-offs, and the rest. */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = Space.iconEdge, vertical = Space.xs)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.setup_back), onClick = onBack)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .navigationBarsPadding()
                .testTag("guide"),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.guide_title), style = Type.displayL)
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.guide_intro), style = Type.bodyL)

            GroupTitle(stringResource(R.string.guide_group_times))
            GuideCard(Icons.Rounded.Schedule, R.string.guide_times_title, R.string.guide_times_body)
            GuideCard(Icons.Rounded.Mosque, R.string.guide_salah_title, R.string.guide_salah_body)

            GroupTitle(stringResource(R.string.guide_group_modes))
            Text(stringResource(R.string.guide_modes_intro), style = Type.bodyM.copy(color = Nur.textSecondary))
            Spacer(Modifier.height(Space.m))
            ModeCard(Icons.Rounded.NotificationsActive, R.string.mode_gentle, R.string.guide_gentle_body, R.string.guide_gentle_pros, R.string.guide_gentle_cons)
            ModeCard(Icons.Rounded.Fullscreen, R.string.mode_full_screen, R.string.guide_full_body, R.string.guide_full_pros, R.string.guide_full_cons)
            ModeCard(Icons.Rounded.Lock, R.string.mode_lockdown, R.string.guide_lockdown_body, R.string.guide_lockdown_pros, R.string.guide_lockdown_cons)
            GuideCard(Icons.Rounded.Coffee, R.string.guide_breaks_title, R.string.guide_breaks_body)

            GroupTitle(stringResource(R.string.guide_group_more))
            GuideCard(Icons.Rounded.NotificationsActive, R.string.guide_collections_title, R.string.guide_collections_body)
            GuideCard(Icons.Rounded.CalendarMonth, R.string.guide_calendar_title, R.string.guide_calendar_body)
            GuideCard(Icons.Rounded.Widgets, R.string.guide_widgets_title, R.string.guide_widgets_body)
            GuideCard(Icons.Rounded.BatteryChargingFull, R.string.guide_phone_title, R.string.guide_phone_body)
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun GuideCard(icon: ImageVector, title: Int, body: Int, extra: @Composable () -> Unit = {}) {
    GlassCard(Modifier.fillMaxWidth().padding(bottom = Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = LocalAura.current.accent)
            Spacer(Modifier.width(Space.m))
            Text(stringResource(title), style = Type.titleM)
        }
        Spacer(Modifier.height(Space.s))
        Text(stringResource(body), style = Type.bodyM.copy(color = Nur.textSecondary))
        extra()
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: Int, body: Int, pros: Int, cons: Int) =
    GuideCard(icon, title, body) {
        Spacer(Modifier.height(Space.m))
        TradeOff(R.string.guide_pros, pros, LocalAura.current.accent)
        Spacer(Modifier.height(Space.s))
        TradeOff(R.string.guide_cons, cons, Nur.textTertiary)
    }

@Composable
private fun TradeOff(label: Int, text: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(stringResource(label).uppercase(), style = Type.overline.copy(color = color))
        Spacer(Modifier.height(Space.xs))
        Text(stringResource(text), style = Type.bodyM)
    }
}
