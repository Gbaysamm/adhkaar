"""
Turns MotionTest frames (app/build/motion/<name>/*.png) into GIFs for the preview board:
  docs/screenshots/motion-<name>.gif      the whole screen, at half size
  docs/screenshots/motion-<name>-bar.gif  the tab bar, full size
  ...-slow.gif                            the same at a quarter speed
Run after: ./gradlew testDebugUnitTest --tests "*MotionTest*"
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
for folder in sorted((ROOT / "app/build/motion").glob("*")):
    frames = [Image.open(f).convert("RGB") for f in sorted(folder.glob("*.png"))]
    if not frames:
        continue
    w, h = frames[0].size
    # Hold the last frame a moment so the settle is visible before the loop restarts.
    durations = [33] * (len(frames) - 1) + [900]
    screen = [f.resize((w // 2, h // 2), Image.LANCZOS) for f in frames]
    screen[0].save(ROOT / f"docs/screenshots/motion-{folder.name}.gif", save_all=True, append_images=screen[1:], duration=durations, loop=0, optimize=True)
    # Slow motion, a quarter of real speed, so each stage of the movement can be seen.
    slow = [d * 4 for d in durations[:-1]] + [1200]
    if folder.name.startswith("tab-"):
        # The tab bar: the bottom 120dp (xhdpi, 2px per dp).
        bar = [f.crop((0, h - 240, w, h - 40)) for f in frames]
        bar[0].save(ROOT / f"docs/screenshots/motion-{folder.name}-bar.gif", save_all=True, append_images=bar[1:], duration=durations, loop=0, optimize=True)
        bar[0].save(ROOT / f"docs/screenshots/motion-{folder.name}-bar-slow.gif", save_all=True, append_images=bar[1:], duration=slow, loop=0, optimize=True)
    screen[0].save(ROOT / f"docs/screenshots/motion-{folder.name}-slow.gif", save_all=True, append_images=screen[1:], duration=slow, loop=0, optimize=True)
    print(folder.name, len(frames), "frames")
