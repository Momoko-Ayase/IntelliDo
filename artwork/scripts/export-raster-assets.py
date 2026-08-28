import base64
from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "source"
FINAL = ROOT / "final"
ICON_SIZES = (16, 20, 24, 32, 40, 48, 64, 128, 256, 512, 1024)
ICO_SIZES = ((16, 16), (20, 20), (24, 24), (32, 32), (40, 40), (48, 48), (64, 64), (128, 128), (256, 256))


def load_rgba(name: str) -> Image.Image:
    return Image.open(SOURCE / name).convert("RGBA")


def crop_opaque(image: Image.Image, alpha_threshold: int = 8) -> Image.Image:
    alpha = image.getchannel("A")
    mask = alpha.point(lambda value: 255 if value > alpha_threshold else 0)
    box = mask.getbbox()
    if box is None:
        return image
    cropped = image.crop(box)
    side = max(cropped.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(cropped, ((side - cropped.size[0]) // 2, (side - cropped.size[1]) // 2))
    return square


def export_icon(channel: str, source_name: str, master_name: str, ico_name: str) -> None:
    source = crop_opaque(load_rgba(source_name))
    master = source.resize((2048, 2048), Image.Resampling.LANCZOS)
    master.save(FINAL / master_name, optimize=True)

    target_dir = FINAL / "icons" / channel
    target_dir.mkdir(parents=True, exist_ok=True)
    for size in ICON_SIZES:
        master.resize((size, size), Image.Resampling.LANCZOS).save(
            target_dir / f"icon-{size}.png", optimize=True
        )

    master.save(FINAL / ico_name, format="ICO", sizes=ICO_SIZES)
    wrap_png_as_svg(target_dir / "icon-32.png", target_dir / "icon.svg", 32)
    wrap_png_as_svg(target_dir / "icon-16.png", target_dir / "icon-16.svg", 16)


def wrap_png_as_svg(png_path: Path, svg_path: Path, size: int) -> None:
    payload = base64.standard_b64encode(png_path.read_bytes()).decode("ascii")
    svg_path.write_text(
        (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" '
            f'width="{size}" height="{size}">\n'
            f'  <image href="data:image/png;base64,{payload}" width="{size}" height="{size}"/>\n'
            f"</svg>\n"
        ),
        encoding="utf-8",
    )


SPLASH_MASTER_SIZE = (2048, 1152)
SPLASH_WINDOW_SIZE = (800, 450)


def export_splash(source_name: str, output_name: str, window_name: str) -> None:
    source = Image.open(SOURCE / source_name).convert("RGB")
    splash = ImageOps.fit(
        source,
        SPLASH_MASTER_SIZE,
        method=Image.Resampling.LANCZOS,
        centering=(0.5, 0.5),
    )
    splash.save(FINAL / output_name, optimize=True)
    # Platform splash windows use the PNG pixel size as the window size.
    # Ship an ~IDEA-sized derivative so first paint is not a full-screen bitmap.
    splash.resize(SPLASH_WINDOW_SIZE, Image.Resampling.LANCZOS).save(
        FINAL / window_name,
        optimize=True,
    )


def main() -> None:
    FINAL.mkdir(parents=True, exist_ok=True)
    export_icon("stable", "app-icon-stable-generated.png", "app-icon-master.png", "intellido.ico")
    export_icon(
        "nightly",
        "app-icon-nightly-generated.png",
        "app-icon-nightly-master.png",
        "intellido-nightly.ico",
    )
    export_splash("splash-stable-generated.png", "splash.png", "splash-window.png")
    export_splash("splash-nightly-generated.png", "splash-nightly.png", "splash-window-nightly.png")


if __name__ == "__main__":
    main()
