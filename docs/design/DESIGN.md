# Nur — Adhkaar design system

Every value in the app comes from this file. If a screen needs something that isn't here,
add it here first. The code lives in `ui/theme/` (tokens) and `ui/components/` (components).

## Concept

A dark, quiet space lit from within. Light gives each session its identity:

- **Evening (Maghrib & night):** midnight blue, lit by azure and violet glows.
- **Morning (Fajr & dawn):** the same darkness, warmed by ember, orchid and amber glows.

On top of the light sit **glass panels**: translucent layers whose edges catch the light.
The only solid colours are the text and the primary action, so the eye goes to the words
of the dhikr and the next step.

## Colour

| Token | Value | Use |
|---|---|---|
| `ink` | `#05070F` | Base background |
| `text.primary` | `#F5F7FF` | Titles, Arabic |
| `text.secondary` | white 72% | Body, translation |
| `text.tertiary` | white 48% | Captions, references |
| `text.disabled` | white 28% | |
| `evening.accent` | `#8FB1FF` | Icons, progress, links in the evening |
| `evening.action` | `#4B74FF → #2A4BD8` | Primary button gradient |
| `evening.glows` | `#2B5BFF`, `#6A4CFF`, `#00B8FF` | Aura |
| `dawn.accent` | `#FFB88A` | |
| `dawn.action` | `#FF8E5E → #E0567E` | |
| `dawn.glows` | `#FF7A59`, `#C04CFF`, `#FFB86B` | |
| `gold` | `#F2CF8A` | Completion only |
| `chart.morning` | `#D6733E` | Chart marks for morning (rings, grid, calendar) |
| `chart.evening` | `#6A8FEF` | Chart marks for evening, and single-series bars |
| `success` | `#5BE3A4` | Granted, done |
| `danger` | `#FF7A7A` | Missing permission |

The app is dark-only by design: glass and light need darkness to read.

Chart colours are deeper than the UI accents, so they sit in the dark-mode lightness band.
They were validated with the dataviz palette checker on `#0E1224`: colour-blind ΔE 25, normal
vision ΔE 27, contrast at least 3:1. "Both" is a hard diagonal split of the two colours, never a
blend. A blend passes through grey and reads as a third colour.

## Glass

| Level | Fill | Edge | Use |
|---|---|---|---|
| `glass.1` | white 5% | 1dp gradient stroke, white 20% top-left → 3% bottom-right | Cards |
| `glass.2` | white 9% | same, 26% → 5% | Buttons, chips, nested panels |
| `glass.pressed` | white 14% | | Pressed state |
| `glass.bar` | backdrop blur 24dp + white 7% | | Tab bar and anything content scrolls under |

Each glass surface also gets an inner top highlight: a 1dp line, white 18%, fading out at
the corners. This is what makes it look like glass instead of a grey box.

## Type

| Style | Font | Size / line | Weight | Tracking |
|---|---|---|---|---|
| `display.xl` | Instrument Serif | 60 / 62 | 400 | -1.2 |
| `display.l` | Instrument Serif | 40 / 44 | 400 | -0.6 |
| `display.m` | Instrument Serif | 32 / 36 | 400 | -0.4 |
| `title.l` | Inter | 20 / 26 | 600 | -0.3 |
| `title.m` | Inter | 17 / 22 | 600 | -0.2 |
| `body.l` | Inter | 16 / 24 | 400 | -0.1 |
| `body.m` | Inter | 14 / 20 | 400 | 0 |
| `label` | Inter | 14 / 18 | 600 | -0.1 |
| `caption` | Inter | 12 / 16 | 500 | 0 |
| `overline` | Inter | 11 / 14 | 600 | +1.2, uppercase |
| `stat` | Inter | 20 / 24 | 600 | -0.4, tabular numerals |
| `arabic.reading` | Amiri Quran | 28 / 2.1em | 400 | — |
| `arabic.accent` | Amiri Quran | 22 / 1.8em | 400 | — |
| `arabic.display` | Aref Ruqaa | 44 / 1.6em | 400 | — |

Aref Ruqaa is calligraphy: short phrases only (الحمد لله), never adhkaar text.

The serif is used only for **moments**: the greeting, the countdown, screen titles and
the completion screen. Everything you *read* or *tap* is Inter.

## Alignment

One rule, applied everywhere:

- **Left-aligned by default.** Titles, body text, lists, settings and cards.
- **Centred only for:**
  1. *Moments*: the welcome and completion screens.
  2. *Reading*: everything inside a dhikr (Arabic, transliteration, translation,
     virtue, source), in the session and in the Adhkaar list.
  3. *Numbers in a stat row*, each centred in its column.
  4. *A title between two symmetric controls* (the session top bar).
- Nothing else is centred, and a block never mixes the two.

**Edges.** Page content starts at the 20dp gutter. Section titles sit on that line with
no extra inset. Every card's content starts 20dp inside the card, so text on the page
falls on two vertical lines: 20 and 40.

## Space

A 4dp grid. Allowed steps: **4, 8, 12, 16, 20, 24, 32, 40, 56**.

- Screen gutter: **20**
- Card padding: **20** (compact rows 16)
- Gap between cards: **12**; between sections: **32**
- Section title to its content: **12**

## Shape

- Cards **28**, the hero card and sheets **32**, chips **12**, buttons and pills fully round.
- Nested corners are concentric: inner radius = outer radius − padding.

## Touch

- Minimum target **48dp**. Icon buttons are drawn at 44dp inside a 48dp target.
- Primary button: **56dp** high, full width inside the gutter, with a trailing 40dp
  circle holding the icon.
- Every tappable surface shrinks to **0.97** on press with a spring, and there's a light
  haptic on primary actions.

## Motion

| Token | Spec | Use |
|---|---|---|
| `spring.press` | damping 0.7, stiffness 500 | Press feedback |
| `spring.move` | damping 0.85, stiffness 300 | Counters, progress, selection |
| `enter` | 420ms, cubic(0.22, 1, 0.36, 1) | Screens, sheets, content |
| `exit` | 180ms, ease-in | |
| `stagger` | 40ms per item | Lists appearing |
| `aura.drift` | 18–24s loops | Background light, barely perceptible |

Nothing moves without a reason. Loops are reserved for the aura and the orb, and they're slow.

## Navigation

- **Floating glass tab bar**: *Today · Adhkaar · Settings*. A 64dp capsule, 20dp from the
  bottom edge, with the active tab as an inner glass pill carrying its icon and label.
- **Pushed screens** (Phone setup) slide in from the right with a slight parallax, and
  have a 44dp glass back button.
- **A session is a place, not a page.** It opens full screen with no tab bar. Close is the
  only way out, and it's hidden while Lockdown is active.

## Components

`AuraBackground`, `GlassCard`, `GlassButton` (icon circle), `PrimaryButton`,
`GlassPill`, `GlassTabBar`, `NurOrb`, `CounterOrb`, `StatRow`, `WeekRings`,
`SettingsGroup` / `SettingsRow`, `GlassSegmented`, `GlassSwitch`.
