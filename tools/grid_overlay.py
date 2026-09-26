"""
Alignment audit: draws the 4dp grid, the 20dp page margins and a 16dp card inset over screenshots,
so anything off the grid or out of line shows at a glance.

  python tools/grid_overlay.py today settings ...   writes docs/screenshots/grid-<name>.png
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "docs/screenshots"


def overlay(name: str) -> Path:
    im = Image.open(SHOTS / f"{name}.png").convert("RGBA")
    w, h = im.size
    px = w / 393  # screenshots are 393dp wide
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    # 4dp grid, faint; every 16dp a little stronger.
    step = 4 * px
    i = 0
    x = 0.0
    while x < w:
        d.line([(round(x), 0), (round(x), h)], fill=(255, 255, 255, 34 if i % 4 == 0 else 12), width=1)
        x += step
        i += 1
    i = 0
    y = 0.0
    while y < h:
        d.line([(0, round(y)), (w, round(y))], fill=(255, 255, 255, 34 if i % 4 == 0 else 12), width=1)
        y += step
        i += 1
    # Page margins (20dp) in magenta, card content inset (20dp + 20dp padding) in cyan.
    for dp, colour in ((20, (255, 60, 200, 200)), (40, (60, 220, 255, 150))):
        for xx in (dp * px, w - dp * px):
            d.line([(round(xx), 0), (round(xx), h)], fill=colour, width=max(1, round(px / 2)))
    out = SHOTS / f"grid-{name}.png"
    Image.alpha_composite(im, layer).convert("RGB").save(out)
    return out


if __name__ == "__main__":
    for name in sys.argv[1:]:
        print(overlay(name))
