package org.adhkaar.app.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.adhkaar.app.data.Prayer
import org.adhkaar.app.session.Notifications
import org.adhkaar.app.session.PopupCard
import org.adhkaar.app.session.ReminderPopup
import org.adhkaar.app.ui.theme.AdhkaarTheme
import org.adhkaar.app.ui.theme.Auras

/**
 * The salah or collection reminder card over the lock screen. A window drawn over other apps
 * can't appear there, so with the phone locked the reminder's notification opens this instead
 * (a full-screen intent), which also lights the screen. Unlocked, [ReminderPopup] shows the same card.
 */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        // The lock screen or app behind is blurred, like the pop-up's window (Android 12+).
        if (Build.VERSION.SDK_INT >= 31) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = ReminderPopup.BLUR_RADIUS }
        }
        val kind = kindOf(intent) ?: return finish()
        setContent {
            AdhkaarTheme(aura = if (kind is ReminderPopup.Kind.Collection && kind.id == "before_sleep") Auras.evening else Auras.dawn) {
                PopupCard(kind, onDismiss = ::finish, onOpen = { open(kind) })
            }
        }
    }

    /** Opening a collection needs the phone unlocked: ask for it, then open the app there. */
    private fun open(kind: ReminderPopup.Kind) {
        val start = {
            val app = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (kind is ReminderPopup.Kind.Collection) app.putExtra(Notifications.EXTRA_OPEN_COLLECTION, kind.id)
            startActivity(app)
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = start()
            })
        } else {
            start()
        }
    }

    companion object {
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_COLLECTION = "collection"

        fun intent(context: Context, kind: ReminderPopup.Kind): Intent =
            Intent(context, ReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .apply {
                    when (kind) {
                        is ReminderPopup.Kind.Salah -> putExtra(EXTRA_PRAYER, kind.prayer.id)
                        is ReminderPopup.Kind.Collection -> putExtra(EXTRA_COLLECTION, kind.id)
                    }
                }

        private fun kindOf(intent: Intent): ReminderPopup.Kind? {
            intent.getStringExtra(EXTRA_PRAYER)?.let { id ->
                return Prayer.entries.firstOrNull { it.id == id }?.let { ReminderPopup.Kind.Salah(it) }
            }
            return intent.getStringExtra(EXTRA_COLLECTION)?.let { ReminderPopup.Kind.Collection(it) }
        }
    }
}
