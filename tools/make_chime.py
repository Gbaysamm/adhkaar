"""
Synthesises the Adhkaar alert chime: app/src/main/res/raw/adhkaar_chime.wav

A short, warm, rising bell motif (A4 -> C#5 -> E5 -> A5, then a soft E5/A5 echo) that
fades out naturally, so the app can loop it with a pause in between. Deliberately not a
melody from the adhan, which belongs to salah.

Standard library only. Run from the repository root:

    python tools/make_chime.py
"""

import math
import os
import random
import struct
import wave

RATE = 22050
LENGTH_S = 7.0
PEAK = 0.72  # about -3 dBFS: loud enough for an alarm, with headroom for the phone's speaker

# Partials of a soft struck bell: (ratio to the fundamental, amplitude, decay per second).
# The slightly inharmonic upper partials give the "bell" colour; they die away faster than
# the fundamental, so each note starts bright and settles into a pure, calm tone.
PARTIALS = [
    (1.0, 1.00, 0.9),
    (2.0, 0.32, 1.6),
    (2.76, 0.18, 2.6),
    (4.07, 0.07, 4.0),
    (5.4, 0.035, 6.0),
]

# (start seconds, frequency Hz, loudness). A4, C#5, E5 rise to A5, which rings the longest.
NOTES = [
    (0.00, 440.00, 0.55),
    (0.42, 554.37, 0.50),
    (0.84, 659.25, 0.50),
    (1.40, 880.00, 0.62),
    # A quiet echo of the last two notes, like the bell answering itself.
    (3.30, 659.25, 0.20),
    (3.62, 880.00, 0.24),
]

ATTACK_S = 0.012  # a soft mallet: no click, no hard transient


def bell(t, freq):
    """One bell note at time t (seconds after it was struck)."""
    if t < 0:
        return 0.0
    attack = min(1.0, t / ATTACK_S)
    value = 0.0
    for ratio, amp, decay in PARTIALS:
        # Two voices a fraction of a hertz apart beat slowly: the warm shimmer of a real bell.
        f = freq * ratio
        voice = math.sin(2 * math.pi * f * t) + 0.6 * math.sin(2 * math.pi * (f + 0.7) * t + 0.9)
        value += amp * math.exp(-decay * t) * voice / 1.6
    return attack * value


def render():
    n = int(RATE * LENGTH_S)
    dry = [0.0] * n
    for start, freq, loud in NOTES:
        first = int(start * RATE)
        for i in range(first, n):
            dry[i] += loud * bell((i - first) / RATE, freq)

    # A little room: a few quiet early reflections so the bell doesn't sound boxed in.
    wet = list(dry)
    for delay_s, gain in ((0.047, 0.22), (0.089, 0.16), (0.137, 0.11), (0.211, 0.07)):
        d = int(delay_s * RATE)
        for i in range(d, n):
            wet[i] += gain * dry[i - d]

    # Fade the last half second to true silence so the loop point never clicks.
    fade = int(0.5 * RATE)
    for i in range(fade):
        wet[n - fade + i] *= math.cos(0.5 * math.pi * i / fade)

    peak = max(abs(v) for v in wet) or 1.0
    scale = PEAK / peak
    rng = random.Random(7)
    frames = bytearray()
    for v in wet:
        # Triangular dither hides the quantisation hiss of the long, quiet tail.
        dither = (rng.random() - rng.random()) / 32768
        sample = max(-1.0, min(1.0, v * scale + dither))
        frames += struct.pack("<h", int(round(sample * 32767)))
    return bytes(frames)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "app", "src", "main", "res", "raw", "adhkaar_chime.wav")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(render())
    print(f"Wrote {out} ({os.path.getsize(out) // 1024} KB)")


if __name__ == "__main__":
    main()
