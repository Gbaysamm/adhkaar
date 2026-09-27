package org.adhkaar.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.adhkaar.app.R
import org.adhkaar.app.data.AdhkaarRepository
import org.adhkaar.app.data.AdhkaarWindows
import org.adhkaar.app.data.CollectionMode
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.data.SessionDhikr
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.UserDuaStore
import org.adhkaar.app.session.SessionLauncher
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.GlassIconButton
import org.adhkaar.app.ui.components.GlassPill
import org.adhkaar.app.ui.components.GlassSegmented
import org.adhkaar.app.ui.components.GlassSurface
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.NurOrb
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.SecondaryButton
import org.adhkaar.app.ui.components.glass
import org.adhkaar.app.ui.components.lateLabel
import org.adhkaar.app.ui.components.formatTime
import org.adhkaar.app.ui.components.opensLabel
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.components.withHonorifics
import org.adhkaar.app.ui.share.ShareSheet
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Motion
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import java.time.ZonedDateTime

/**
 * The Adhkaar tab: a gallery of shelves (the two daily sessions, then the collections),
 * each opening onto its list. Session shelves can be begun from there.
 * [openShelf] asks for a shelf to be opened from elsewhere (the Today screen); [onShelfOpened]
 * clears the request once it's shown.
 */
@Composable
fun LibraryScreen(onOpenCollection: (String) -> Unit, openShelf: String? = null, onShelfOpened: () -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openShelf) {
        if (openShelf != null) {
            open = openShelf
            onShelfOpened()
        }
    }
    BackHandler(enabled = open != null) { open = if (open.isEditor()) MY_DUAS else null }
    AnimatedContent(
        targetState = open,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally(Motion.enter()) { if (forward) it / 4 else -it / 4 } + fadeIn(Motion.enter())) togetherWith
                (slideOutHorizontally(Motion.enter()) { if (forward) -it / 6 else it / 6 } + fadeOut(Motion.exit()))
        },
        label = "library",
    ) { shelf ->
        when {
            shelf == null -> Shelves(onOpen = { open = it })
            shelf.isEditor() -> DuaEditor(shelf.removePrefix(EDIT).ifEmpty { null }, onDone = { open = MY_DUAS })
            shelf == MY_DUAS -> MyDuas(onBack = { open = null }, onEdit = { id -> open = EDIT + (id ?: "") })
            else -> ShelfDetail(shelf, onBack = { open = null }, onOpenCollection = onOpenCollection)
        }
    }
}

private const val FAVOURITES = "favourites"
private const val MY_DUAS = "my_duas"
private const val EDIT = "edit:"
private fun String?.isEditor() = this?.startsWith(EDIT) == true

@Composable
private fun Shelves(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val collections = remember { CollectionsRepository.all(context) }
    val favorites by SessionState.get(context).favoritesFlow.collectAsState()
    val myDuas by UserDuaStore.get(context).flow.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Space.gutter),
    ) {
        Spacer(Modifier.height(Space.xl))
        Text(stringResource(R.string.library_title), style = Type.displayL)
        Spacer(Modifier.height(Space.xs))
        Text(stringResource(R.string.library_subtitle), style = Type.bodyM)

        ShelfHeader(stringResource(R.string.library_every_day))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SessionType.entries.forEach { type ->
                val aura = Auras.of(type)
                GlassSurface(
                    Modifier.weight(1f).height(184.dp),
                    RoundedCornerShape(Radius.card),
                    onClick = { onOpen(type.key) },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(aura.glows[0].copy(alpha = 0.28f), Color.Transparent))),
                    )
                    Column(Modifier.fillMaxSize().padding(Space.gutter)) {
                        NurOrb(44.dp, aura = aura, float = false)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(if (type == SessionType.MORNING) R.string.library_morning else R.string.library_evening), style = Type.titleL)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            AdhkaarRepository.forSession(context, type).size.let { n -> pluralStringResource(if (type == SessionType.MORNING) R.plurals.library_morning_tile else R.plurals.library_evening_tile, n, n) },
                            style = Type.caption,
                        )
                    }
                }
            }
        }

        ShelfHeader(stringResource(R.string.library_collections))
        val tiles = collections.map { Triple(it.id, it.title, pluralStringResource(if (it.mode == CollectionMode.REFERENCE) R.plurals.library_count_duas else R.plurals.library_count_adhkaar, it.items.size, it.items.size)) } +
            Triple(FAVOURITES, stringResource(R.string.library_favourites), if (favorites.isEmpty()) stringResource(R.string.library_favourites_none) else pluralStringResource(R.plurals.library_favourites_saved, favorites.size, favorites.size)) +
            Triple(MY_DUAS, stringResource(R.string.library_my_duas), if (myDuas.isEmpty()) stringResource(R.string.library_my_duas_empty) else pluralStringResource(R.plurals.library_my_duas_written, myDuas.size, myDuas.size))
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.padding(bottom = Space.m)) {
                row.forEach { (id, title, subtitle) ->
                    GlassCard(Modifier.weight(1f), onClick = { onOpen(id) }) {
                        IconBadge(
                            shelfIcon(id),
                            tint = when (id) {
                                FAVOURITES -> Color(0xFFFF7A8A)
                                MY_DUAS -> Nur.gold
                                else -> LocalAura.current.accent
                            },
                        )
                        Spacer(Modifier.height(Space.xl))
                        Text(title, style = Type.label)
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, style = Type.caption)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.tabBarClearance))
    }
}

/** The icon for a shelf: a collection id, favourites or my duas. Shared with Today and Insights. */
fun shelfIcon(id: String): ImageVector = when (id) {
    "after_salah" -> Icons.Rounded.Mosque
    "before_sleep" -> Icons.Rounded.Bedtime
    "waking" -> Icons.Rounded.WbSunny
    "daily" -> Icons.Rounded.AutoStories
    MY_DUAS -> Icons.Rounded.EditNote
    else -> Icons.Rounded.Favorite
}

@Composable
private fun ShelfHeader(title: String) {
    Text(title, style = Type.titleL, modifier = Modifier.padding(top = Space.xxl, bottom = Space.m))
}

@Composable
private fun ShelfDetail(shelf: String, onBack: () -> Unit, onOpenCollection: (String) -> Unit) {
    val context = LocalContext.current
    val state = remember { SessionState.get(context) }
    val favorites by state.favoritesFlow.collectAsState()
    val sessionType = SessionType.fromKey(shelf)
    val collection = remember(shelf) { CollectionsRepository.get(context, shelf) }
    val title: String
    val subtitle: String
    val items: List<SessionDhikr>
    when {
        sessionType != null -> {
            title = stringResource(if (sessionType == SessionType.MORNING) R.string.library_morning_title else R.string.library_evening_title)
            subtitle = if (sessionType == SessionType.MORNING) {
                val ends = AdhkaarWindows.window(SettingsStore.get(context).current, SessionType.MORNING, java.time.LocalDate.now()).closes
                stringResource(R.string.library_morning_subtitle_until, formatTime(context, ends))
            } else {
                stringResource(R.string.library_evening_subtitle)
            }
            val mine by UserDuaStore.get(context).flow.collectAsState()
            items = remember(shelf, mine) { AdhkaarRepository.forSession(context, sessionType) + UserDuaStore.get(context).forSession(sessionType) }
        }
        collection != null -> {
            title = collection.title
            subtitle = collection.subtitle
            items = collection.items
        }
        else -> {
            title = stringResource(R.string.library_favourites)
            subtitle = stringResource(R.string.library_favourites_subtitle)
            items = remember(favorites) {
                val all = AdhkaarRepository.forSession(context, SessionType.MORNING) +
                    AdhkaarRepository.forSession(context, SessionType.EVENING) +
                    CollectionsRepository.all(context).flatMap { it.items }
                all.distinctBy { it.id }.filter { it.id in favorites }
            }
        }
    }
    var expanded by rememberSaveable(shelf) { mutableStateOf<String?>(null) }

    AdhkaarTheme(aura = if (sessionType != null) Auras.of(sessionType) else collectionAura(shelf)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.tabBarClearance),
        ) {
            item {
                Row(Modifier.padding(vertical = Space.xs).offset(x = Space.iconEdge - Space.gutter)) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.library_back), onClick = onBack)
                }
                Spacer(Modifier.height(Space.s))
                Text(title, style = Type.displayL)
                Spacer(Modifier.height(Space.xs))
                Text(
                    stringResource(
                        R.string.library_detail_subtitle,
                        subtitle,
                        pluralStringResource(if (collection?.mode == CollectionMode.REFERENCE) R.plurals.library_count_duas else R.plurals.library_count_adhkaar, items.size, items.size),
                    ),
                    style = Type.bodyM,
                )
                Spacer(Modifier.height(Space.xl))
                if (sessionType != null) {
                    BeginSession(sessionType)
                    Spacer(Modifier.height(Space.xl))
                } else if (collection?.mode == CollectionMode.SESSION) {
                    PrimaryButton(stringResource(R.string.library_begin)) { onOpenCollection(shelf) }
                    Spacer(Modifier.height(Space.xl))
                }
                if (items.isEmpty()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FavoriteBorder, null, tint = Nur.textTertiary)
                        Spacer(Modifier.height(Space.m))
                        Text(stringResource(R.string.library_favourites_empty_title), style = Type.label)
                        Spacer(Modifier.height(Space.xs))
                        Text(stringResource(R.string.library_favourites_empty_body), style = Type.bodyM)
                    }
                }
            }
            itemsIndexed(items, key = { _, d -> "$shelf-${d.id}" }) { i, dhikr ->
                DhikrRow(
                    index = i + 1,
                    dhikr = dhikr,
                    favorite = dhikr.id in favorites,
                    onToggleFavorite = { state.toggleFavorite(dhikr.id) },
                    expanded = expanded == dhikr.id,
                    modifier = Modifier.padding(bottom = Space.m).animateItem(),
                ) { expanded = if (expanded == dhikr.id) null else dhikr.id }
            }
        }
    }
}

/**
 * Begin for the morning or evening adhkaar, offered only inside the session's window (see
 * [AdhkaarWindows]); a session already waiting can always be continued. Late is allowed, with a hint.
 */
@Composable
private fun BeginSession(type: SessionType) {
    val context = LocalContext.current
    val settings by SettingsStore.get(context).flow.collectAsState()
    val pending by SessionState.get(context).pendingFlow.collectAsState()
    val completed by SessionState.get(context).completedFlow.collectAsState()
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(20_000)
        }
    }
    val status = AdhkaarWindows.status(settings, type, now)
    val waiting = pending?.type == type
    val opens = if (waiting) null else opensLabel(context, status, now)
    val late = if (waiting) null else lateLabel(context, status)
    if (!waiting && completed[type] == now.toLocalDate()) {
        GlassPill(stringResource(R.string.library_done_today), color = LocalAura.current.accent, icon = Icons.Rounded.CheckCircle)
        if (opens == null) {
            // Reading them again is still welcome; it's just no longer the main action.
            Spacer(Modifier.height(Space.m))
            SecondaryButton(stringResource(R.string.library_read_again)) { SessionLauncher.startManual(context, type) }
        }
        return
    }
    PrimaryButton(
        opens ?: stringResource(R.string.library_begin),
        icon = if (opens == null) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Schedule,
        enabled = opens == null,
    ) { SessionLauncher.startManual(context, type) }
    if (late != null) {
        Spacer(Modifier.height(Space.m))
        GlassPill(late, dot = LocalAura.current.accent)
    }
}

@Composable
private fun MyDuas(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val context = LocalContext.current
    val duas by UserDuaStore.get(context).flow.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.tabBarClearance),
    ) {
        item {
            Row(Modifier.padding(vertical = Space.xs).offset(x = Space.iconEdge - Space.gutter)) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.library_back), onClick = onBack)
            }
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.library_my_duas), style = Type.displayL)
            Spacer(Modifier.height(Space.xs))
            Text(stringResource(R.string.library_my_duas_description), style = Type.bodyM)
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(stringResource(R.string.library_write_dua), icon = Icons.Rounded.Add) { onEdit(null) }
            Spacer(Modifier.height(Space.xl))
        }
        items(duas, key = { it.id }) { dua ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = Space.m).animateItem(), onClick = { onEdit(dua.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dua.title, style = Type.label)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            dua.arabic.ifBlank { dua.translation },
                            style = if (dua.arabic.isNotBlank()) Type.arabicAccent.copy(fontSize = 18.sp, textAlign = TextAlign.Right) else Type.bodyM,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Space.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            if (dua.count > 1) GlassPill(pluralStringResource(R.plurals.counter_times, dua.count, dua.count))
                            if (dua.inMorning) GlassPill(stringResource(R.string.library_morning), dot = Auras.dawn.accent)
                            if (dua.inEvening) GlassPill(stringResource(R.string.library_evening), dot = Auras.evening.accent)
                        }
                    }
                    Spacer(Modifier.width(Space.m))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Nur.textTertiary)
                }
            }
        }
    }
}

/** Colour for a collection: dawn light for waking, night light for the rest. */
fun collectionAura(id: String) = if (id == "waking") Auras.dawn else Auras.evening

@Composable
private fun DhikrRow(
    index: Int,
    dhikr: SessionDhikr,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    expanded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.move(), label = "chevron")
    GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).glass(CircleShape, level = 2),
                contentAlignment = Alignment.Center,
            ) {
                Text("$index", style = Type.label.copy(fontFeatureSettings = "tnum"), color = Nur.textSecondary)
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(dhikr.title, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(dhikr.reference, style = Type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).pressable(scale = 0.85f, haptic = true, onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (favorite) R.string.session_favourite_remove else R.string.session_favourite_add),
                    tint = if (favorite) Color(0xFFFF7A8A) else Nur.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (dhikr.count > 1) GlassPill(pluralStringResource(R.plurals.counter_times, dhikr.count, dhikr.count))
            Icon(
                Icons.Rounded.KeyboardArrowDown, null, tint = Nur.textTertiary,
                modifier = Modifier.padding(start = Space.xs).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
            exit = shrinkVertically(Motion.enter()) + fadeOut(Motion.exit()),
        ) {
            Column(Modifier.padding(top = Space.l)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Spacer(Modifier.height(Space.l))
                if (dhikr.arabic.isNotBlank()) {
                    Text(dhikr.arabic, style = Type.arabicAccent, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.m))
                }
                Text(
                    dhikr.transliteration, style = Type.bodyM.copy(fontStyle = FontStyle.Italic), color = Nur.textTertiary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.s))
                Text(withHonorifics(dhikr.translation), style = Type.bodyM, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.l))
                var sharing by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GlassSurface(Modifier.height(36.dp), RoundedCornerShape(50), level = 2, onClick = { sharing = true }) {
                        Row(Modifier.align(Alignment.Center).padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.IosShare, null, tint = Nur.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Space.s))
                            Text(stringResource(R.string.share_action), style = Type.caption.copy(color = Nur.textSecondary))
                        }
                    }
                }
                if (sharing) ShareSheet(dhikr, onDismiss = { sharing = false })
            }
        }
    }
}

