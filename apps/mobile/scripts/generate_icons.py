#!/usr/bin/env python
"""Generate Android launcher icons from logo.svg.

Renders the SVG at high resolution via Chromium headless (installed with the
Hermes playwright browsers) and composites onto the app background color
(#2D1B69) for legacy icons, or keeps transparency for adaptive foregrounds.

Centering: after render, the PNG is cropped to its real alpha bounding box,
then re-centered on the canvas with a generous, homogeneous padding so the
logo occupies only a fraction of the canvas (legacy ~52%, foreground ~40%).
"""

import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

# --- config ---------------------------------------------------------------
REPO = Path(__file__).resolve().parents[1]
SVG = REPO / "static" / "logo.svg"
RES = REPO / "android" / "app" / "src" / "main" / "res"

BG = (0x2D, 0x1B, 0x69)          # #2D1B69
PINK = "#E879F9"

# fraction of the canvas occupied by the logo bbox (rest = homogeneous padding)
LEGACY_RATIO = 0.52
FOREGROUND_RATIO = 0.40

# optical right-shift to compensate for the left-heavy smartphone body
# (the pure alpha centroid still *looks* off to the left). Fraction of the
# scaled content width. Tuned visually in a 412x892 fake-screen preview.
OPTICAL_BIAS_X = 0.025

# render size (high-res master, then downscale with LANCZOS)
RENDER = 864

# legacy square/round icons: (density, px)
LEGACY = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
# adaptive foregrounds: (density, px)
FOREGROUND = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# chromium candidates (ms-playwright install from Hermes)
CHROMIUM_CANDIDATES = [
    Path.home() / "AppData/Local/ms-playwright",
]


def find_chromium() -> Path:
    for base in CHROMIUM_CANDIDATES:
        if not base.is_dir():
            continue
        for exe in sorted(base.rglob("chrome.exe")) + sorted(base.rglob("headless_shell.exe")):
            return exe
    sys.exit("chromium introuvable dans %LOCALAPPDATA%/ms-playwright")


# --- svg -> png via headless chromium -------------------------------------
def render_svg_to_png(chromium: Path, svg_path: Path, size: int, out: Path,
                      stroke: str | None = None) -> None:
    """Render svg_path scaled to `size`x`size` onto a transparent PNG."""
    svg_text = svg_path.read_text(encoding="utf-8")
    if stroke:
        # recolour every path
        svg_text = re.sub(r"<path ", f'<path fill="{stroke}" ', svg_text)
    html = f"""<!doctype html><meta charset=utf-8>
<style>html,body{{margin:0;background:transparent}}svg{{display:block;width:{size}px;height:{size}px}}</style>
{svg_text}"""
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False, encoding="utf-8") as f:
        f.write(html)
        html_path = Path(f.name)
    shot = out.with_suffix(".shot.png")
    try:
        subprocess.run(
            [
                str(chromium),
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--default-background-color=00000000",
                f"--screenshot={shot}",
                f"--window-size={size},{size}",
                "--force-device-scale-factor=1",
                html_path.as_uri(),
            ],
            check=True,
            capture_output=True,
        )
        shot.replace(out)
    finally:
        html_path.unlink(missing_ok=True)


def crop_to_alpha_bbox(im: Image.Image) -> Image.Image:
    """Crop a transparent PNG to its real content bounding box (alpha > 0)."""
    bbox = im.getchannel("A").getbbox()
    if bbox:
        return im.crop(bbox)
    return im


def compute_centroid(im: Image.Image) -> tuple[float, float]:
    """Compute the alpha-weighted centroid (center of mass) of the image."""
    alpha = im.getchannel("A")
    w, h = alpha.size
    # total mass
    total = sum(alpha.getdata())
    if total == 0:
        return w / 2.0, h / 2.0
    # weighted sums
    xs = 0.0
    ys = 0.0
    pix = alpha.load()
    for y in range(h):
        for x in range(w):
            a = pix[x, y]
            if a:
                xs += x * a
                ys += y * a
    return xs / total, ys / total


def center_on_canvas(im: Image.Image, canvas_size: int, ratio: float,
                     bg: tuple[int, int, int, int],
                     optical_bias_x: float = 0.0) -> Image.Image:
    """Scale `im` (cropped to bbox) to fit inside `ratio` of the canvas and
    center it *optically* by aligning the alpha-weighted centroid with the
    canvas centre (instead of centring the bounding box).

    `optical_bias_x` (fraction of the scaled content width) shifts the
    content slightly to the right to compensate for asymmetric visual weight
    (dense solid body on the left, thin pin on the right): the pure centroid
    still *looks* left-heavy, so we over-correct a bit."""
    target = int(canvas_size * ratio)
    scale = target / max(im.width, im.height)
    new_w = max(1, round(im.width * scale))
    new_h = max(1, round(im.height * scale))
    im = im.resize((new_w, new_h), Image.LANCZOS)

    # centroid of the resized content
    cx, cy = compute_centroid(im)

    canvas = Image.new("RGBA", (canvas_size, canvas_size), bg)
    centre = canvas_size / 2.0
    off_x = int(round(centre - cx + new_w * optical_bias_x))
    off_y = int(round(centre - cy))
    canvas.alpha_composite(im, (off_x, off_y))
    return canvas


def round_mask(im: Image.Image) -> Image.Image:
    size = im.width
    mask = Image.new("L", (size * 4, size * 4), 0)
    from PIL import ImageDraw
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4, size * 4), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = im.copy()
    out.putalpha(mask)
    return out


def main() -> None:
    chromium = find_chromium()
    print("chromium:", chromium)

    tmp = Path(tempfile.mkdtemp(prefix="icon_gen_"))

    # master render at RENDER px, cropped to real content bbox
    master_raw = tmp / "master_raw.png"
    render_svg_to_png(chromium, SVG, RENDER, master_raw, stroke=PINK)
    master = crop_to_alpha_bbox(Image.open(master_raw).convert("RGBA"))
    print(f"master {RENDER}px OK, bbox content: {master.width}x{master.height}")

    for density, px in LEGACY.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)

        # square legacy icon on solid bg, logo = LEGACY_RATIO of canvas
        square = folder / "ic_launcher.png"
        center_on_canvas(master, px, LEGACY_RATIO, BG + (255,),
                         optical_bias_x=OPTICAL_BIAS_X).save(square)

        # round legacy icon
        rnd = round_mask(Image.open(square).convert("RGBA"))
        rnd.save(folder / "ic_launcher_round.png")
        print(f"{density:8s} {px:3d}px  ic_launcher.png + round")

    for density, px in FOREGROUND.items():
        folder = RES / f"mipmap-{density}"
        fg = folder / "ic_launcher_foreground.png"
        center_on_canvas(master, px, FOREGROUND_RATIO, (0, 0, 0, 0),
                         optical_bias_x=OPTICAL_BIAS_X).save(fg)
        print(f"{density:8s} {px:3d}px  ic_launcher_foreground.png")

    print("done ->", RES)


if __name__ == "__main__":
    main()
