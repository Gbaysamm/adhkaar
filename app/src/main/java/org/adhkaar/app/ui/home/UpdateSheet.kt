package org.adhkaar.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adhkaar.app.R
import org.adhkaar.app.data.AppUpdate
import org.adhkaar.app.ui.components.IconBadge
import org.adhkaar.app.ui.components.PrimaryButton
import org.adhkaar.app.ui.components.pressable
import org.adhkaar.app.ui.settings.GlassDialog
import org.adhkaar.app.ui.theme.LocalAura
import org.adhkaar.app.ui.theme.Nur
import org.adhkaar.app.ui.theme.Space
import org.adhkaar.app.ui.theme.Type

/** A newer testing build is out: what it brings, and one tap to download it. */
@Composable
fun UpdateSheet(latest: AppUpdate.Latest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassDialog(stringResource(R.string.update_title), onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconBadge(Icons.Rounded.SystemUpdate, size = 52.dp)
        }
        Spacer(Modifier.height(Space.m))
        Text(stringResource(R.string.update_version, latest.versionName), style = Type.label.copy(color = LocalAura.current.accent))
        if (latest.notes.isNotBlank()) {
            Spacer(Modifier.height(Space.s))
            Text(latest.notes, style = Type.bodyM.copy(color = Nur.textPrimary))
        }
        Spacer(Modifier.height(Space.s))
        Text(stringResource(R.string.update_body), style = Type.bodyM)
        Spacer(Modifier.height(Space.l))
        PrimaryButton(stringResource(R.string.update_download), Modifier.fillMaxWidth(), icon = null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            onDismiss()
        }
        Box(Modifier.fillMaxWidth().height(48.dp).pressable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.update_later), style = Type.label.copy(color = Nur.textSecondary))
        }
    }
}
