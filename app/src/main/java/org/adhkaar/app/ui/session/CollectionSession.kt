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
            items = collection.items,
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
