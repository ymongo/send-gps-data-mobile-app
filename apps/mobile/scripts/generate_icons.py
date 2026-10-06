#!/usr/bin/env python
"""Generate Android launcher icons from GLM logo SVGs.

Renders the SVGs at high resolution via Chromium headless (installed with the
Hermes playwright browsers) and composites onto the app background color
(#2D1B69) for legacy icons, or keeps transparency for adaptive foregrounds.

The GLM glyph is fully symmetric: no optical bias needed, no recoloring.
"""

import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

# --- config ---------------------------------------------------------------
REPO = Path(__file__).resolve().parents[1]
RES = REPO / "android" / "app" / "src" / "main" / "res"

LEGACY_SVG = REPO / "static" / "logo.svg"
FOREGROUND_SVG = REPO / "static" / "glm-launcher-foreground.svg"

BG = (0x2D, 0x1B, 0x69)          # #2D1B69

# fraction of the canvas occupied by the logo bbox (rest = homogeneous padding)
LEGACY_RATIO = 0.52
FOREGROUND_RATIO = 0.40

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
def render_svg_to_png(chromium: Path, svg_path: Path, size: int, out: Path) -> None:
    """Render svg_path scaled to `size`x`size` onto a transparent PNG."""
    svg_text = svg_path.read_text(encoding="utf-8")
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
    total = sum(alpha.getdata())
    if total == 0:
        return w / 2.0, h / 2.0
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
                      bg: tuple[int, int, int, int]) -> Image.Image:
    """Scale `im` (cropped to bbox) to fit inside `ratio` of the canvas
    and center it by aligning the alpha-weighted centroid with the canvas centre."""
    target = int(canvas_size * ratio)
    scale = target / max(im.width, im.height)
    new_w = max(1, round(im.width * scale))
    new_h = max(1, round(im.height * scale))
    im = im.resize((new_w, new_h), Image.LANCZOS)

    cx, cy = compute_centroid(im)

    canvas = Image.new("RGBA", (canvas_size, canvas_size), bg)
    centre = canvas_size / 2.0
    off_x = int(round(centre - cx))
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

    # Master render for legacy icons: GLM glyph on transparent background
    master_raw = tmp / "master_raw.png"
    render_svg_to_png(chromium, LEGACY_SVG, RENDER, master_raw)
    master = crop_to_alpha_bbox(Image.open(master_raw).convert("RGBA"))
    print(f"master {RENDER}px OK, bbox content: {master.width}x{master.height}")

    # Master render for adaptive foregrounds: GLM launcher foreground
    fg_raw = tmp / "fg_raw.png"
    render_svg_to_png(chromium, FOREGROUND_SVG, RENDER, fg_raw)
    fg_master = crop_to_alpha_bbox(Image.open(fg_raw).convert("RGBA"))
    print(f"foreground master {RENDER}px OK, bbox content: {fg_master.width}x{fg_master.height}")

    # Legacy square/round icons on solid BG
    for density, px in LEGACY.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)

        square = folder / "ic_launcher.png"
        center_on_canvas(master, px, LEGACY_RATIO, BG + (255,)).save(square)

        rnd = round_mask(Image.open(square).convert("RGBA"))
        rnd.save(folder / "ic_launcher_round.png")
        print(f"{density:8s} {px:3d}px  ic_launcher.png + round")

    # Adaptive foreground icons (transparent bg)
    for density, px in FOREGROUND.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)

        fg = folder / "ic_launcher_foreground.png"
        center_on_canvas(fg_master, px, FOREGROUND_RATIO, (0, 0, 0, 0)).save(fg)
        print(f"{density:8s} {px:3d}px  ic_launcher_foreground.png")

    print("done ->", RES)


if __name__ == "__main__":
    main()
