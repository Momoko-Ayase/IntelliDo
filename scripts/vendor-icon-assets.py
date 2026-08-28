#!/usr/bin/env python3
"""Download pinned Font Awesome + discourse-emojis archives and generate local resources."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LOCK = Path(__file__).resolve().with_name("vendor-lock.json")
DEFAULT_CACHE = ROOT / "tmp" / "vendor-dl"
DEFAULT_OUTPUT = ROOT / "domain" / "build" / "generated" / "resources"
USER_AGENT = "IntelliDo-vendor-icon-assets/0.1 (+https://github.com/Momoko-Ayase/IntelliDo)"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(1024 * 256)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def download(url: str, dest: Path, expected: str) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.is_file() and sha256_file(dest) == expected:
        print(f"cache hit {dest.name}")
        return
    print(f"download {url}")
    partial = dest.with_suffix(dest.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=180) as response, partial.open("wb") as handle:
        shutil.copyfileobj(response, handle)
    actual = sha256_file(partial)
    if actual != expected:
        partial.unlink(missing_ok=True)
        raise SystemExit(f"sha256 mismatch for {dest.name}: {actual} != {expected}")
    partial.replace(dest)


INVALID_WIN = set('<>:"|?*')


def member_rest(name: str) -> str:
    normalized = name.replace("\\", "/")
    if "/" in normalized:
        return normalized.split("/", 1)[1]
    return normalized


def wanted_member(name: str, prefixes: tuple[str, ...]) -> bool:
    rest = member_rest(name)
    if any(ch in rest for ch in INVALID_WIN):
        return False
    return any(rest == prefix or rest.startswith(prefix.rstrip("/") + "/") for prefix in prefixes)


def extract_archive(archive: Path, dest: Path, prefixes: tuple[str, ...]) -> Path:
    stamp = dest / ".ok"
    if dest.is_dir() and stamp.is_file():
        return dest
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    with tarfile.open(archive, "r:*") as tar:
        members = [member for member in tar.getmembers() if wanted_member(member.name, prefixes)]
        try:
            tar.extractall(dest, members=members, filter="data")
        except TypeError:
            tar.extractall(dest, members=members)
    stamp.write_text("ok\n", encoding="utf-8")
    return dest


def find_path(root: Path, *parts: str) -> Path:
    needle = tuple(parts)
    for path in root.rglob(parts[-1]):
        if tuple(path.parts[-len(needle) :]) == needle:
            return path
    raise FileNotFoundError(f"missing {'/'.join(parts)} under {root}")


def parse_svg(text: str) -> tuple[str, str] | None:
    view = re.search(r'viewBox="([^"]+)"', text)
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', text)
    if not view or not paths:
        return None
    return view.group(1), " ".join(paths)


def write_fontawesome(package: Path, out_dir: Path) -> int:
    svgs = package / "svgs"
    out_dir.mkdir(parents=True, exist_ok=True)
    glyphs: dict[str, tuple[int, str, str]] = {}
    order = {"solid": 0, "regular": 1, "brands": 2}
    for style, rank in order.items():
        folder = svgs / style
        if not folder.is_dir():
            continue
        for svg in sorted(folder.glob("*.svg")):
            parsed = parse_svg(svg.read_text(encoding="utf-8"))
            if parsed is None:
                continue
            view, path = parsed
            current = glyphs.get(svg.stem)
            if current is None or rank < current[0]:
                glyphs[svg.stem] = (rank, view, path)
    tsv = out_dir / "glyphs.tsv"
    with tsv.open("w", encoding="utf-8", newline="\n") as handle:
        for name in sorted(glyphs):
            _, view, path = glyphs[name]
            handle.write(f"{name}\t{view}\t{path}\n")
    license_src = package / "LICENSE.txt"
    if license_src.exists():
        shutil.copy2(license_src, out_dir / "LICENSE.txt")
    print(f"fontawesome glyphs {len(glyphs)} -> {tsv}")
    return len(glyphs)


def copy_twemoji(src: Path, dest: Path) -> dict[str, str]:
    dest.mkdir(parents=True, exist_ok=True)
    aliases: dict[str, str] = {}
    png_count = 0
    for path in src.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(src).as_posix()
        data = path.read_bytes()
        if data.startswith(b"\x89PNG\r\n\x1a\n"):
            target = dest / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            png_count += 1
        else:
            try:
                text = data.decode("utf-8").strip()
            except UnicodeDecodeError:
                continue
            if text:
                aliases[rel] = text.replace("\\", "/")
    print(f"twemoji png {png_count} aliases {len(aliases)}")
    return aliases


def display_glyph(glyph: str) -> str:
    if len(glyph) == 2 and glyph[1] == "️" and ord(glyph[0]) >= 0x1F000:
        return glyph[0]
    return glyph


def unicode_from_code(code: str) -> str:
    chars: list[str] = []
    for part in code.split("-"):
        if not part:
            continue
        chars.append(chr(int(part, 16)))
    return "".join(chars)


def write_emoji(dist: Path, file_aliases: dict[str, str], out_dir: Path) -> int:
    emojis = json.loads((dist / "emojis.json").read_text(encoding="utf-8"))
    aliases = json.loads((dist / "aliases.json").read_text(encoding="utf-8"))
    emoji_to_name = json.loads((dist / "emoji_to_name.json").read_text(encoding="utf-8"))
    name_to_emoji = {str(name).lower(): glyph for glyph, name in emoji_to_name.items()}
    rows: dict[str, tuple[str, str]] = {}

    def add(name: str, glyph: str, png: str) -> None:
        key = name.strip().lower()
        if key:
            rows[key] = (glyph, png)

    for item in emojis:
        name = str(item["name"]).lower()
        glyph = display_glyph(name_to_emoji.get(name) or unicode_from_code(str(item["code"])))
        png = f"{name}.png"
        add(name, glyph, png)
        for alias in aliases.get(name, []):
            alias_name = str(alias).lower()
            add(alias_name, display_glyph(name_to_emoji.get(alias_name) or glyph), png)

    for alias_rel, target_rel in file_aliases.items():
        alias_name = Path(alias_rel).stem.lower()
        target_name = Path(target_rel).stem.lower()
        if target_name in rows:
            glyph, png = rows[target_name]
            add(alias_name, glyph, png)
        elif alias_name in rows:
            glyph, _ = rows[alias_name]
            add(alias_name, glyph, target_rel if target_rel.endswith(".png") else f"{target_name}.png")

    out_dir.mkdir(parents=True, exist_ok=True)
    tsv = out_dir / "shortcodes.tsv"
    with tsv.open("w", encoding="utf-8", newline="\n") as handle:
        for name in sorted(rows):
            glyph, png = rows[name]
            handle.write(f"{name}\t{glyph}\t{png}\n")
    print(f"emoji shortcodes {len(rows)} -> {tsv}")
    return len(rows)


def generate(lock: dict, cache: Path, output: Path) -> None:
    fa_meta = lock["fontawesome"]
    emoji_meta = lock["discourseEmojis"]
    fa_archive = cache / fa_meta["name"]
    emoji_archive = cache / emoji_meta["name"]
    download(fa_meta["url"], fa_archive, fa_meta["sha256"])
    download(emoji_meta["url"], emoji_archive, emoji_meta["sha256"])
    fa_root = extract_archive(
        fa_archive,
        cache / "extracted" / fa_meta["sha256"],
        ("package/svgs", "package/LICENSE.txt"),
    )
    emoji_root = extract_archive(
        emoji_archive,
        cache / "extracted" / emoji_meta["sha256"],
        (
            "dist/emojis.json",
            "dist/aliases.json",
            "dist/emoji_to_name.json",
            "dist/emoji/twemoji",
            "attributions.md",
        ),
    )
    package = find_path(fa_root, "package", "svgs").parent
    twemoji = find_path(emoji_root, "dist", "emoji", "twemoji")
    dist = twemoji.parent.parent

    vendor = output / "vendor"
    if vendor.exists():
        shutil.rmtree(vendor)
    write_fontawesome(package, vendor / "fontawesome")
    file_aliases = copy_twemoji(twemoji, vendor / "twemoji")
    write_emoji(dist, file_aliases, vendor / "emoji")
    (vendor / ".stamp").write_text(
        f"{fa_meta['sha256']}\n{emoji_meta['sha256']}\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK)
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    lock = json.loads(args.lock.read_text(encoding="utf-8"))
    generate(lock, args.cache, args.output)


if __name__ == "__main__":
    main()
