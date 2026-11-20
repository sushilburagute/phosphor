"""Build Play Store assets from the renderer's own output.

Run the opt-in preview tests first:
    PHOSPHOR_PREVIEWS=1 ./gradlew testDebugUnitTest --tests '*ArtworkPreviewTest*' --tests '*ScreenshotTest*' --rerun
then:
    python tools/store_assets.py
Requires Pillow. Everything here is original procedural artwork; no reference images are used.
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PREVIEWS = os.path.join(ROOT, "app/build/reports/previews")
SCREENS = os.path.join(ROOT, "app/build/reports/screens")
OUT = os.path.join(ROOT, "docs/store")
FONT = os.path.join(ROOT, "app/src/main/res/font/bricolage_grotesque.ttf")


def font(size, weight, opsz, width):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([opsz, weight, width])  # axis order: opsz, wght, wdth
    return f


def crop_to_two_to_one(im):
    """Play rejects screenshots whose long side exceeds twice the short side."""
    w, h = im.size
    if h <= 2 * w:
        return im
    excess = h - 2 * w
    return im.crop((0, excess // 2, w, h - (excess - excess // 2)))


def feature_graphic():
    art = Image.open(os.path.join(PREVIEWS, "orbit-exposure-night.png")).convert("RGB")
    w, h = art.size
    # A wide band through the horizon: the trails, the line, the figure.
    band_height = round(w * 500 / 1024)
    top = round(h * 0.55 - band_height * 0.60)
    canvas = art.crop((0, top, w, top + band_height)).resize((1024, 500), Image.LANCZOS)
    # Darken the left third so the wordmark reads.
    shade = Image.new("L", (1024, 500))
    d = ImageDraw.Draw(shade)
    for x in range(1024):
        d.line([(x, 0), (x, 500)], fill=int(max(0, min(235, 300 - x * 0.4))))
    canvas = Image.composite(Image.new("RGB", (1024, 500), (3, 6, 15)), canvas, shade)
    d = ImageDraw.Draw(canvas)
    d.text((56, 150), "Phosphor", font=font(118, 640, 96, 82), fill=(233, 236, 246))
    d.text((60, 290), "Living light for your home screen.", font=font(30, 420, 24, 100), fill=(165, 172, 198))
    d.text((60, 334), "Glows at night. Prints in ink by day.", font=font(30, 420, 24, 100), fill=(165, 172, 198))
    d.line([(62, 404), (122, 404)], fill=(255, 106, 58), width=3)
    canvas.save(os.path.join(OUT, "feature-graphic-1024x500.jpg"), quality=92)


def screenshots():
    picks = [
        ("orbit-exposure-night", PREVIEWS), ("orbit-exposure-day", PREVIEWS),
        ("orbit-threshold-night", PREVIEWS), ("aurora-veil-night", PREVIEWS),
        ("glass-prism-day", PREVIEWS), ("ascii-signal-night", PREVIEWS),
        ("gallery-night", SCREENS), ("gallery-day", SCREENS), ("editor-home-night", SCREENS),
    ]
    for index, (name, folder) in enumerate(picks, 1):
        path = os.path.join(folder, name + ".png")
        if not os.path.exists(path):
            print("missing", path)
            continue
        crop_to_two_to_one(Image.open(path).convert("RGB")).save(os.path.join(OUT, "screenshots", f"{index:02d}-{name}.jpg"), quality=90)


if __name__ == "__main__":
    os.makedirs(os.path.join(OUT, "screenshots"), exist_ok=True)
    icon = os.path.join(SCREENS, "play-icon-512.png")
    if os.path.exists(icon):
        Image.open(icon).convert("RGB").save(os.path.join(OUT, "play-icon-512.png"))
    feature_graphic()
    screenshots()
    print("Store assets written to", OUT)
