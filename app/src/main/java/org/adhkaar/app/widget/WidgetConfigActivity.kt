package org.adhkaar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import org.adhkaar.app.R
import org.adhkaar.app.data.CollectionsRepository
import org.adhkaar.app.data.Languages
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.ui.components.AuraBackground
import org.adhkaar.app.ui.components.GlassCard
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.library.shelfIcon
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Radius
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * Opens when a widget is added (and, on Android 12+, from the widget's Reconfigure option): the
 * style, and for the collection shortcut which collection it opens.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Languages.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Backing out cancels: the launcher then doesn't place the widget, as it expects.
        setResult(RESULT_CANCELED, resultIntent())
        val kind = WidgetKind.of(AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className)
        if (kind == null) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val style = WidgetPrefs.style(this, appWidgetId)
        val collection = WidgetPrefs.collection(this, appWidgetId)
        setContent {
            AdhkaarTheme {
                AuraBackground(Modifier.fillMaxSize()) {
                    WidgetConfigScreen(kind, style, collection) { chosenStyle, chosenCollection -> save(kind, chosenStyle, chosenCollection) }
                }
            }
        }
    }

    private fun save(kind: WidgetKind, style: WidgetStyle, collection: String) {
        WidgetPrefs.save(this, appWidgetId, style, collection.takeIf { kind == WidgetKind.COLLECTION })
        lifecycleScope.launch {
            // With a configuration screen the launcher leaves the first draw to us.
            runCatching { kind.create().update(applicationContext, GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)) }
            WidgetTicker.schedule(applicationContext)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun WidgetConfigScreen(
    kind: WidgetKind,
    initialStyle: WidgetStyle,
    initialCollection: String,
    onSave: (WidgetStyle, String) -> Unit,
) {
    val context = LocalContext.current
    var style by rememberSaveable { mutableStateOf(initialStyle) }
    var collection by rememberSaveable { mutableStateOf(initialCollection) }
    val collections = remember { CollectionsRepository.all(context).associateBy { it.id } }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = Space.gutter)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Space.xxl))
            Text(stringResource(kind.title), style = Type.displayM)
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.widget_config_subtitle), style = Type.bodyM)
            Spacer(Modifier.height(Space.xxl))
            Text(stringResource(R.string.widget_config_style_title).uppercase(), style = Type.overline)
            Spacer(Modifier.height(Space.m))
            WidgetStyle.entries.forEach { option ->
                ChoiceCard(
                    selected = option == style,
                    title = stringResource(option.title),
                    subtitle = stringResource(option.description),
                    onClick = { style = option },
                ) { StyleSwatch(option) }
                Spacer(Modifier.height(Space.m))
            }
            if (kind == WidgetKind.COLLECTION) {
                Spacer(Modifier.height(Space.xl))
                Text(stringResource(R.string.widget_config_collection_title).uppercase(), style = Type.overline)
                Spacer(Modifier.height(Space.m))
                WidgetCollections.all.forEach { id ->
                    collections[id]?.let { option ->
                        ChoiceCard(
                            selected = id == collection,
                            title = option.title,
                            subtitle = option.subtitle,
                            onClick = { collection = id },
                        ) { IconBadge(shelfIcon(id)) }
                        Spacer(Modifier.height(Space.m))
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
        PrimaryButton(stringResource(R.string.widget_config_save), Modifier.padding(vertical = Space.gutter)) { onSave(style, collection) }
    }
}

/** One option: a picture of it, its name and a line about it, and whether it is chosen. */
@Composable
private fun ChoiceCard(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    GlassCard(
        Modifier.fillMaxWidth().semantics {
            this.selected = selected
            role = Role.RadioButton
        },
        padding = PaddingValues(Space.l),
        level = if (selected) 2 else 1,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.titleM)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.caption)
            }
            Spacer(Modifier.width(Space.m))
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) LocalAura.current.accent else Nur.textDisabled,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A tiny widget in [style] on a bright wallpaper, so the three read differently at a glance. */
@Composable
private fun StyleSwatch(style: WidgetStyle) {
    val palette = style.palette
    val panel = RoundedCornerShape(10.dp)
    val panelModifier = when (style) {
        WidgetStyle.GLASS -> Modifier
            .background(Brush.linearGradient(listOf(Color(0xE61A2A6B), Color(0xF205070F))), panel)
            .border(1.dp, Color.White.copy(alpha = 0.15f), panel)
        WidgetStyle.PARCHMENT -> Modifier
            .background(Brush.verticalGradient(listOf(Color(0xFFFBF3E2), Color(0xFFF3E4C4))), panel)
            .border(1.dp, Color(0x59B8903F), panel)
        WidgetStyle.CLEAR -> Modifier
    }
    Box(
        Modifier
            .size(width = 72.dp, height = 52.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .background(Brush.linearGradient(listOf(Color(0xFF2F6F8F), Color(0xFFB5677A), Color(0xFFE3A45C)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 58.dp, height = 38.dp).then(panelModifier), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.widget_morning),
                style = Type.caption.copy(
                    color = palette.accent(SessionType.MORNING),
                    fontSize = 10.sp,
                    shadow = if (palette.shadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f) else null,
                ),
                maxLines = 1,
            )
        }
    }
}
