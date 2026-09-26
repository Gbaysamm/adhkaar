package org.adhkaar.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.adhkaar.app.R
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.ui.library.collectionAura
import org.adhkaar.app.ui.theme.AdhkaarTheme
import java.time.LocalDate

/**
 * A collection said as a session: no Lockdown, no break; progress lives only while it's open, so
 * leaving part-way asks first. Counts are paced like morning and evening (see [DhikrSession]).
 */
@Composable
fun CollectionSessionScreen(collectionId: String, onFinish: () -> Unit) {
    val context = LocalContext.current
    val collection = remember(collectionId) { CollectionsRepository.get(context, collectionId) } ?: return onFinish()
    val state = remember { SessionState.get(context) }
    // Saved with the screen like the counts, so the time taken is still right after a restore.
    val startedAt = rememberSaveable(collectionId) { System.currentTimeMillis() }

    AdhkaarTheme(aura = collectionAura(collectionId)) {
        DhikrSession(
            key = collectionId,
            overline = collection.title.uppercase(),
            items = remember(collectionId) { forPrayerJustPrayed(context, collectionId, collection.items) },
            // Kept nowhere but on screen (saved with it, so a killed process doesn't lose them).
            initialProgress = null,
            onProgress = {},
            initialPage = -1,
            onPage = {},
            showClose = true,
            // Its progress isn't kept, so closing part-way would lose the counts: ask first.
            closeConfirmTitle = stringResource(R.string.pacing_leave_collection),
            bottomControls = null,
            onComplete = {
                val today = LocalDate.now()
                state.logCollection(collectionId, today)
                val timesToday = state.collectionLogFlow.value.count { it.startsWith("$today|$collectionId|") }
                Summary(
                    adhkaar = collection.items.size,
                    minutes = ((System.currentTimeMillis() - startedAt) / 60_000L).toInt().coerceAtLeast(1),
                    streak = timesToday,
                    streakLabel = context.resources.getQuantityString(R.plurals.completion_times_today, timesToday),
                )
            },
            completion = { summary ->
                val text = collection.completion
                CompletionView(
                    headline = text?.headline ?: stringResource(R.string.completion_collection_headline, collection.title),
                    quote = text?.quote ?: "",
                    reference = text?.reference ?: "",
                    summary = summary,
                    onDone = onFinish,
                )
            },
            onFinish = onFinish,
        )
    }
}

/** The three Quls, which after Fajr and Maghrib are each said three times. */
private val QULS = setOf("al_ikhlas", "al_falaq", "an_nas")

/**
 * After salah, the counts follow the prayer just prayed: the latest of the day's five whose time
 * has come (Isha's, before Fajr). After Fajr or Maghrib the Quls are said three times each.
 */
private fun forPrayerJustPrayed(
    context: android.content.Context,
    collectionId: String,
    items: List<org.adhkaar.app.data.SessionDhikr>,
): List<org.adhkaar.app.data.SessionDhikr> {
    if (collectionId != "after_salah") return items
    val settings = org.adhkaar.app.data.SettingsStore.get(context).current
    val now = java.time.LocalTime.now()
    val prayed = org.adhkaar.app.data.Prayer.five.lastOrNull { !now.isBefore(org.adhkaar.app.schedule.PrayerClock.time(settings, it)) }
        ?: org.adhkaar.app.data.Prayer.ISHA
    if (prayed != org.adhkaar.app.data.Prayer.FAJR && prayed != org.adhkaar.app.data.Prayer.MAGHRIB) return items
    return items.map { if (it.id in QULS) it.copy(count = 3) else it }
}
