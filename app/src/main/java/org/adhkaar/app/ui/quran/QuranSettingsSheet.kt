package org.adhkaar.app.ui.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.data.quran.PageTheme
import org.adhkaar.app.data.quran.QuranStore
import org.adhkaar.app.data.quran.ReadingOrder
import org.adhkaar.app.ui.components.GlassSegmented
import org.adhkaar.app.ui.components.GlassSwitch
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.settings.GlassDialog
import org.adhkaar.app.ui.settings.Stepper
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/** The reading's settings: how much a day, how long a page takes, how pages look, what opens. */
@Composable
fun QuranSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { QuranStore.get(context) }
    val s by store.settings.collectAsState()
    GlassDialog(stringResource(R.string.quran_settings), onDismiss) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Field(stringResource(R.string.quran_set_daily), stringResource(R.string.quran_set_daily_hint)) {
                Stepper(
                    "${s.dailyPages}",
                    onMinus = { store.update { it.copy(dailyPages = it.dailyPages - 1) } },
                    onPlus = { store.update { it.copy(dailyPages = it.dailyPages + 1) } },
                    canMinus = s.dailyPages > 1, canPlus = s.dailyPages < 40,
                )
            }
            Label(stringResource(R.string.quran_set_time), stringResource(R.string.quran_set_time_hint))
            val times = listOf(60, 90, 120)
            GlassSegmented(times.map { "${it / 60}:${"%02d".format(it % 60)}" }, times.indexOf(s.secondsPerPage).coerceAtLeast(0)) { i ->
                store.update { it.copy(secondsPerPage = times[i]) }
            }
            Label(stringResource(R.string.quran_set_theme), null)
            val themes = PageTheme.entries
            GlassSegmented(
                listOf(R.string.quran_theme_auto, R.string.quran_theme_sepia, R.string.quran_theme_night, R.string.quran_theme_white).map { stringResource(it) },
                themes.indexOf(s.theme),
            ) { i -> store.update { it.copy(theme = themes[i]) } }
            Label(stringResource(R.string.quran_set_order), stringResource(R.string.quran_set_order_hint))
            val orders = ReadingOrder.entries
            GlassSegmented(
                listOf(R.string.quran_order_continue, R.string.quran_order_chosen, R.string.quran_order_random).map { stringResource(it) },
                orders.indexOf(s.order),
            ) { i -> store.update { it.copy(order = orders[i]) } }
            Spacer(Modifier.height(Space.l))
            Field(stringResource(R.string.quran_set_sunnah), stringResource(R.string.quran_set_sunnah_hint)) {
                GlassSwitch(s.sunnahSurahs, { on -> store.update { it.copy(sunnahSurahs = on) } })
            }
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.quran_done), Modifier.fillMaxWidth(), icon = null, onClick = onDismiss)
    }
}

@Composable
private fun Label(title: String, hint: String?) {
    Spacer(Modifier.height(Space.l))
    Text(title, style = Type.label)
    if (hint != null) Text(hint, style = Type.caption)
    Spacer(Modifier.height(Space.s))
}

@Composable
private fun Field(title: String, hint: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.label)
            Text(hint, style = Type.caption)
        }
        trailing()
    }
}
