"""Build Play Store assets from Android previews and the Afterglow artwork.

Run the opt-in preview tests first:
    PHOSPHOR_PREVIEWS=1 ./gradlew testDebugUnitTest --tests '*ArtworkPreviewTest*' --tests '*ScreenshotTest*' --rerun
then:
    python tools/store_assets.py
Requires Pillow.
"""
import os
from PIL import Image, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PREVIEWS = os.path.join(ROOT, "app/build/reports/previews")
SCREENS = os.path.join(ROOT, "app/build/reports/screens")
OUT = os.path.join(ROOT, "docs/store")


def crop_to_two_to_one(im):
    """Play rejects screenshots whose long side exceeds twice the short side."""
    w, h = im.size
    if h <= 2 * w:
        return im
    excess = h - 2 * w
    return im.crop((0, excess // 2, w, h - (excess - excess // 2)))


def feature_graphic():
    with Image.open(os.path.join(OUT, "source", "afterglow.png")) as artwork:
        canvas = ImageOps.fit(artwork.convert("RGB"), (1024, 500), method=Image.Resampling.LANCZOS)
        canvas.save(os.path.join(OUT, "feature-graphic-1024x500.jpg"), quality=94, subsampling=0)


def screenshots():
    picks = [
        ("orbit-exposure-night", PREVIEWS), ("abstract-ribbon-night", PREVIEWS),
        ("orbit-threshold-night", PREVIEWS), ("aurora-veil-night", PREVIEWS),
        ("glass-prism-night", PREVIEWS), ("ascii-signal-night", PREVIEWS),
        ("gallery-night", SCREENS), ("gallery-light", SCREENS), ("editor-home-night", SCREENS),
    ]
    editor = os.path.join(SCREENS, "editor-light.png")
    if os.path.exists(editor):
        docs_screens = os.path.join(ROOT, "docs", "screenshots")
        os.makedirs(docs_screens, exist_ok=True)
        with Image.open(editor) as image:
            image.convert("RGB").save(os.path.join(docs_screens, "editor.jpg"), quality=90, subsampling=0, optimize=True)

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
