package org.adhkaar.app.data

import android.content.Intent

/**
 * The app reopens where the user left it, even after Android (HyperOS and other OEM skins
 * especially) kills the process in the background.
 *
 * How it works: the tab, the pushed screens (Setup, Prayer times), an open collection session
 * (its counts and page), the Adhkaar tab's open shelf and each tab's scroll position are
 * `rememberSaveable`, so they travel in the saved-instance-state bundle that Android hands back
 * when it recreates the task. The morning/evening session keeps its counts and page in
 * [SessionState] as well, because its screen can be recreated from a notification or the widget
 * with no bundle at all. The session screen shares the app's task, so the launcher icon brings
 * back a session in progress rather than the screen it was started from.
 *
 * To test on a phone (a debug build; `runOnPhone` uses `am start -S`, which force-stops the app
 * and so always starts fresh — install, then open the app by hand):
 * 1. Open a screen: a tab scrolled part-way, a shelf in Adhkaar, a collection a few adhkaar in,
 *    or the morning/evening session on a later page.
 * 2. Press Home (don't swipe the app away from recents; that is the user closing it).
 * 3. `adb shell am kill org.adhkaar.app` — kills the process only while it's in the background,
 *    as the system does. `adb shell pidof org.adhkaar.app` should then print nothing.
 * 4. Reopen from recents or the launcher icon: the same screen, page and scroll position.
 * Also try Developer options > "Don't keep activities", which destroys each activity as soon as
 * it's left, and rotating or changing the language while a session is open.
 */
object ResumeState {

    /**
     * Whether the activity should act on the request in its launch intent (a notification asking
     * for a collection). Not when it is being recreated ([restored]): that intent was already
     * handled and the user may have closed the collection since. Not when it is relaunched from
     * recents either, which replays the intent that first started the task.
     */
    fun actsOnLaunchIntent(intentFlags: Int, restored: Boolean): Boolean =
        !restored && intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

    /**
     * The page a session opens on: the one the user was on ([saved], -1 when none), if the
     * session still has it; otherwise the first dhikr not yet said, or the first page.
     */
    fun startPage(saved: Int, pageCount: Int, firstIncomplete: Int?): Int =
        saved.takeIf { it in 0 until pageCount } ?: firstIncomplete ?: 0
}
