package org.adhkaar.app.ui.components

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_CLICK
import android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's touch vocabulary. Each kind of touch has its own feel, small and quiet, so a tap,
 * a click, a detent and a "not yet" can be told apart without looking.
 *
 * Three tiers, best first:
 *  1. Composed from the motor's primitives (Android 12+, motors that support them), which can be
 *     scaled well below a stock click.
 *  2. The phone's own predefined effects (Android 11+). Many phones, Xiaomi's among them, offer no
 *     primitives but tune these effects carefully for their motor, so they feel better than (3).
 *  3. The nearest system haptic constant.
 * All tiers follow the phone's own touch-feedback setting.
 */
object Haptics {
    /** Follows the Haptics setting; AdhkaarTheme keeps it in sync, so calls made outside composition obey it too. */
    @Volatile
    var enabled: Boolean = true

    /** A tap registered: light and crisp. */
    fun tap(view: View) {
        if (!enabled) return
        if (!composed { Composed.tap(view) } && !predefined { Predefined.play(view, VibrationEffect.EFFECT_CLICK) }) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** A main action (Begin, Leave, Save): a firm, clean click. */
    fun confirm(view: View) {
        if (!enabled) return
        if (!composed { Composed.confirm(view) } && !predefined { Predefined.play(view, VibrationEffect.EFFECT_HEAVY_CLICK) }) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** A stepper, switch, segment or value moved one place: a tiny detent. */
    fun tick(view: View) {
        if (!enabled) return
        if (!composed { Composed.tick(view) } && !predefined { Predefined.play(view, VibrationEffect.EFFECT_TICK) }) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Not allowed, or too soon: a soft, low double tick, like pushing against a stop. */
    fun reject(view: View) {
        if (!enabled) return
        if (!composed { Composed.reject(view) } && !predefined { Predefined.play(view, VibrationEffect.EFFECT_DOUBLE_CLICK) }) {
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** A session or collection completed: a short lift into a click, and a soft echo. */
    fun success(view: View) {
        if (!enabled) return
        if (!composed { Composed.success(view) } && !predefined { Predefined.success(view) }) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private inline fun composed(play: () -> Boolean) = Build.VERSION.SDK_INT >= 31 && play()

    private inline fun predefined(play: () -> Boolean) = Build.VERSION.SDK_INT >= 30 && play()
}

/** The phone's own tuned effects, for motors without primitives. False when the phone lacks the effect. */
@RequiresApi(30)
private object Predefined {
    private val supported = ConcurrentHashMap<Int, Boolean>()

    fun play(view: View, effectId: Int): Boolean {
        val vibrator = vibrator(view) ?: return false
        if (!supports(vibrator, effectId)) return false
        vibrate(vibrator, VibrationEffect.createPredefined(effectId))
        return true
    }

    // A firm click, then a lighter one just after: the same lift-and-echo as the composed version.
    fun success(view: View): Boolean {
        if (!play(view, VibrationEffect.EFFECT_HEAVY_CLICK)) return false
        view.postDelayed({ play(view, VibrationEffect.EFFECT_CLICK) }, 110)
        return true
    }

    // Asking the vibrator service is a system call; the answer never changes, so it is kept.
    private fun supports(vibrator: Vibrator, effectId: Int) = supported.getOrPut(effectId) {
        vibrator.areEffectsSupported(effectId).first() == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
    }
}

private fun vibrator(view: View): Vibrator? {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        view.context.getSystemService(Vibrator::class.java)
    }
    return vibrator?.takeIf { it.hasVibrator() }
}

// Marked as touch feedback, so it follows the phone's touch-feedback setting and intensity.
private fun vibrate(vibrator: Vibrator, effect: VibrationEffect) {
    if (Build.VERSION.SDK_INT >= 33) {
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    } else {
        vibrator.vibrate(effect)
    }
}

/** One primitive in a composed effect: its strength (0…1) and the pause before it, in ms. */
private class Step(val primitive: Int, val scale: Float, val delay: Int = 0)

/**
 * The composed versions. Each effect names a preferred pattern and, where it uses a primitive some
 * motors lack (the low tick), a plainer one; false means neither can play, so the caller falls back.
 */
@RequiresApi(31)
private object Composed {
    private val supported = ConcurrentHashMap<Int, Boolean>()

    // Half a click: clearly there, never louder than the finger's own touch.
    fun tap(view: View) = play(view, listOf(Step(PRIMITIVE_CLICK, 0.5f)))

    // A near-full click, kept to a single pulse so it reads as decisive rather than buzzy.
    fun confirm(view: View) = play(view, listOf(Step(PRIMITIVE_CLICK, 0.9f)))

    // A small, sharp tick, like a detent in a dial.
    fun tick(view: View) = play(view, listOf(Step(PRIMITIVE_TICK, 0.45f)))

    // Two low ticks, the second softer, 70 ms apart: a gentle "no" rather than an error buzz.
    fun reject(view: View) = play(
        view,
        listOf(Step(PRIMITIVE_LOW_TICK, 0.6f), Step(PRIMITIVE_LOW_TICK, 0.4f, delay = 70)),
        listOf(Step(PRIMITIVE_TICK, 0.35f), Step(PRIMITIVE_TICK, 0.25f, delay = 70)),
    )

    // A quick swell into a firm click, then a faint tick after it: a lift and a small echo.
    fun success(view: View) = play(
        view,
        listOf(Step(PRIMITIVE_QUICK_RISE, 0.35f), Step(PRIMITIVE_CLICK, 0.8f), Step(PRIMITIVE_TICK, 0.3f, delay = 110)),
        listOf(Step(PRIMITIVE_CLICK, 0.6f), Step(PRIMITIVE_CLICK, 0.8f, delay = 90)),
    )

    private fun play(view: View, vararg patterns: List<Step>): Boolean {
        val vibrator = vibrator(view) ?: return false
        val steps = patterns.firstOrNull { pattern -> pattern.all { supports(vibrator, it.primitive) } } ?: return false
        val effect = VibrationEffect.startComposition()
            .apply { steps.forEach { addPrimitive(it.primitive, it.scale, it.delay) } }
            .compose()
        vibrate(vibrator, effect)
        return true
    }

    // Asking the vibrator service is a system call; the answer never changes, so it is kept.
    private fun supports(vibrator: Vibrator, primitive: Int) =
        supported.getOrPut(primitive) { vibrator.arePrimitivesSupported(primitive).first() }
}
