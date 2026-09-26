package org.adhkaar.app.session

import org.adhkaar.app.data.PendingSession
import org.adhkaar.app.data.AdhkaarWindows
import android.content.Context
import org.adhkaar.app.data.SessionState
import org.adhkaar.app.data.SessionType
import org.adhkaar.app.data.SettingsStore
import org.adhkaar.app.data.Strictness
import org.adhkaar.app.enforce.EnforcementService
import org.adhkaar.app.schedule.AlarmScheduler
import org.adhkaar.app.schedule.PrayerClock
import org.adhkaar.app.schedule.SessionTimeCalculator
import org.adhkaar.app.widget.AdhkaarWidget
import java.time.LocalDate
import java.time.ZonedDateTime

/** Decides what happens when a session starts, ends, or needs to be resumed. */
object SessionLauncher {
    /** How long a test session is enforced before it lets go by itself. */
    private const val TEST_MINUTES = 10

    /** How long [pending] stays enforced: to its adhkaar window's close, at most the user's maximum. */
    fun windowMinutes(context: Context, pending: PendingSession?): Int {
        val settings = SettingsStore.get(context).current
        pending ?: return settings.lockdownMaxMinutes
        // A test shows how the mode behaves, then lets go by itself.
        if (pending.test) return TEST_MINUTES
        val zone = java.time.ZoneId.systemDefault()
        val started = java.time.Instant.ofEpochMilli(pending.startedAtMillis).atZone(zone)
        val closes = AdhkaarWindows.window(settings, pending.type, started.toLocalDate(), zone).closes
        return AlertPolicy.windowMinutes(pending.startedAtMillis, closes.toInstant().toEpochMilli(), settings.lockdownMaxMinutes)
    }


    /** Called by the alarm at the session time. */
    fun onSessionTime(context: Context, type: SessionType, test: Boolean = false) {
        val state = SessionState.get(context)
        // Arm tomorrow's alarm first, so a crash below can never break the daily chain.
        AlarmScheduler.scheduleSession(context, type)
        if (!test && state.lastCompleted(type) == LocalDate.now()) return
        begin(context, type, test)
    }

    private fun begin(context: Context, type: SessionType, test: Boolean = false) {
        val settings = SettingsStore.get(context).current
        val now = System.currentTimeMillis()
        SessionState.get(context).start(type, LocalDate.now(), now, enforced = true, test = test)
        AdhkaarWidget.refresh(context)
        Notifications.show(context, type, fullScreen = settings.strictness != Strictness.GENTLE)
        if (settings.strictness == Strictness.LOCKDOWN) AlarmScheduler.scheduleWatchdog(context)
        if (settings.strictness == Strictness.GENTLE) {
            // Gentle is a reminder: the sound plays once, like a notification, and never again.
            AlertPlayer.start(context, once = true) { Notifications.refresh(context) }
            return
        }
        ring(context)
        AlarmScheduler.scheduleReRing(context, now + AlertPolicy.RERING_DELAY_MS)
    }

    /**
     * Rings from the enforcement service, which keeps the process alive with the screen off and
     * (Full screen, Lockdown) brings the session up. If Android refuses to start it, ring anyway.
     */
    private fun ring(context: Context) {
        if (!EnforcementService.start(context, ring = true)) AlertPlayer.start(context) { Notifications.refresh(context) }
    }

    /**
     * Ten minutes after the session time, or when a break ends: ring once more if nobody has
     * answered it. Lockdown was let go for the break, so its watchdog comes back with the ring.
     */
    fun onReRing(context: Context) {
        val state = SessionState.get(context)
        val window = windowMinutes(context, SessionState.get(context).pending)
        if (!AlertPolicy.shouldReRing(state.pending, state.alertAcknowledged, window, System.currentTimeMillis())) return
        ring(context)
        if (isLockdownActive(context)) AlarmScheduler.scheduleWatchdog(context)
    }

    /**
     * The user is busy: pause the session for [minutes]. Ringing stops, the session and the
     * Lockdown cover go away and other apps can be used; when the break ends the session rings
     * again ([onReRing]) and, in Full screen and Lockdown, comes back. Only breaks that end
     * before the adhkaar window closes are allowed, so the adhkaar are still said today.
     */
    fun takeBreak(context: Context, minutes: Int) {
        val state = SessionState.get(context)
        val pending = state.pending ?: return
        val now = System.currentTimeMillis()
        val windowEnd = AlertPolicy.windowEndMillis(pending, windowMinutes(context, pending))
        if (minutes !in AlertPolicy.breakOptions(now, windowEnd)) return
        val until = now + minutes * 60_000L
        AlertPlayer.stop()
        state.startBreak(until)
        AlarmScheduler.cancelWatchdog(context)
        // Replaces any second ring still to come: the break's end is the next ring.
        AlarmScheduler.scheduleReRing(context, until)
        // A running service sees the break, re-posts its notification ("On a break until…") and
        // stops, leaving the notification behind. Without the service, post it here.
        if (!EnforcementService.start(context)) Notifications.refresh(context)
    }

    /**
     * The user answered the alert (opened or touched the session, unlocked into it, or pressed
     * Stop): stop ringing and don't ring again for this session. Cheap enough to call on every touch.
     * On a break the ring at its end must stay, so it only stops the sound.
     */
    fun silence(context: Context) {
        AlertPlayer.stop()
        val state = SessionState.get(context)
        if (state.pending == null || state.alertAcknowledged) return
        if (AlertPolicy.isPaused(state.pending, System.currentTimeMillis())) return
        state.acknowledgeAlert()
        AlarmScheduler.cancelReRing(context)
    }

    /** A gentle heads-up before the session, unless it's already done today. */
    fun onPreReminder(context: Context, type: SessionType) {
        if (SessionState.get(context).lastCompleted(type) == LocalDate.now()) return
        Notifications.showPreReminder(context, type, SettingsStore.get(context).current.preReminderMinutes)
    }

    /** Runs every few minutes during Lockdown and restarts the service if the phone killed it. */
    fun onWatchdog(context: Context) {
        if (isLockdownActive(context)) {
            EnforcementService.start(context)
            AlarmScheduler.scheduleWatchdog(context)
        } else {
            AlarmScheduler.cancelWatchdog(context)
        }
    }

    /**
     * After a reboot or update: resume a pending session ([resumePending]), or start a session
     * the phone missed.
     */
    fun catchUpOrResume(context: Context) {
        val state = SessionState.get(context)
        val settings = SettingsStore.get(context).current
        if (state.pending != null) {
            resumePending(context)
            return
        }
        val now = ZonedDateTime.now()
        SessionType.entries.firstOrNull { type ->
            SessionTimeCalculator.missedToday(
                schedule = settings.schedule(type),
                now = now,
                lastCompleted = state.lastCompleted(type),
                graceMinutes = windowMinutes(context, state.pending),
                prayerTime = PrayerClock.provider(settings, type),
            )
        }?.let { begin(context, it) }
    }

    /**
     * Brings back what a pending session needs while its window is open: its next ring (the
     * second ring, or the end of a break), its notification, and (Full screen, Lockdown, when
     * not on a break) the enforcement service. An update, a force-stop or an OEM cleanup loses
     * alarms and the service; this is idempotent and cheap, so every process start calls it.
     */
    fun resumePending(context: Context) {
        val state = SessionState.get(context)
        val settings = SettingsStore.get(context).current
        val pending = state.pending ?: return
        // A session already done today can only be left over from a test before tests were
        // marked as such; it must not show as due again.
        if (!pending.test && state.lastCompleted(pending.type) == pending.date) {
            state.clearPending()
            return
        }
        val nowMillis = System.currentTimeMillis()
        if (!AlertPolicy.isWindowOpen(pending, windowMinutes(context, pending), nowMillis)) return
        if (AlertPolicy.isPaused(pending, nowMillis)) {
            // The user chose to be reminded when the break ends, whatever the mode.
            AlarmScheduler.scheduleReRing(context, pending.pausedUntilMillis)
            Notifications.show(context, pending.type, fullScreen = false)
            return
        }
        val reRingAt = pending.startedAtMillis + AlertPolicy.RERING_DELAY_MS
        if (settings.strictness != Strictness.GENTLE && !state.alertAcknowledged && reRingAt > nowMillis) {
            AlarmScheduler.scheduleReRing(context, reRingAt)
        }
        if (!AlertPolicy.opensOnUnlock(settings.strictness)) return
        Notifications.show(context, pending.type, fullScreen = false)
        EnforcementService.start(context)
        if (isLockdownActive(context)) AlarmScheduler.scheduleWatchdog(context)
    }

    /** The user opened a session from the app. Nothing is enforced. */
    fun startManual(context: Context, type: SessionType) {
        // Outside its time (e.g. evening adhkaar in the morning) it doesn't start; the screens say when it opens.
        if (SessionState.get(context).pending?.type != type && !AdhkaarWindows.canStart(SettingsStore.get(context).current, type)) return
        SessionState.get(context).start(type, LocalDate.now(), System.currentTimeMillis(), enforced = false)
        context.startActivity(Notifications.sessionIntent(context, type))
    }

    fun complete(context: Context, type: SessionType) {
        val state = SessionState.get(context)
        // A test is only a test: it leaves today's record, streak and Insights as they were.
        if (state.pending?.test == true) state.clearPending() else state.complete(type, LocalDate.now())
        end(context)
    }

    /** Ends a test session at once: no ringing, no cover, nothing recorded. */
    fun endTest(context: Context) {
        val state = SessionState.get(context)
        if (state.pending?.test != true) return
        AlarmScheduler.cancelReRing(context)
        AlarmScheduler.cancelWatchdog(context)
        state.clearPending()
        end(context)
    }

    /**
     * The adhkaar window ("ends after") closed before the session was done: stop ringing and
     * blocking, and keep a quiet reminder. The day stays unmarked, so it counts as missed.
     */
    fun onWindowClosed(context: Context) {
        AlertPlayer.stop()
        AlarmScheduler.cancelWatchdog(context)
        AlarmScheduler.cancelReRing(context)
        SessionState.get(context).pending?.let { Notifications.show(context, it.type, fullScreen = false) }
    }

    fun isLockdownActive(context: Context): Boolean {
        val settings = SettingsStore.get(context).current
        val state = SessionState.get(context)
        return AlertPolicy.isLockdownActive(
            state.pending, settings.strictness, windowMinutes(context, state.pending), System.currentTimeMillis(),
        )
    }

    private fun end(context: Context) {
        AlertPlayer.stop()
        AdhkaarWidget.refresh(context)
        EnforcementService.stop(context)
        AlarmScheduler.cancelWatchdog(context)
        AlarmScheduler.cancelReRing(context)
        Notifications.cancel(context)
        AlarmScheduler.scheduleAll(context)
    }
}
