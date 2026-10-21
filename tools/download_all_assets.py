#!/usr/bin/env python3
"""Download KyawGyi build-time assets with retries, progress, atomic writes and SHA-256.

The script is deliberately manifest-driven. Do not put guessed URLs or hashes in
source code. Obtain release URLs and SHA-256 values from a trusted model release,
then run:

  python tools/download_all_assets.py --manifest tools/assets.json --project-root .
"""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, sys, time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from zipfile import ZipFile, BadZipFile

BUF = 1024 * 1024
REQUIRED_SHERPA = {"encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"}

class DownloadError(RuntimeError): pass

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(BUF), b""):
            digest.update(chunk)
    return digest.hexdigest()

def progress(done: int, total: int | None, label: str) -> None:
    if total and total > 0:
        pct = min(100, done * 100 // total)
        width = 28
        filled = width * pct // 100
        print(f"\r{label:<28} [{'#' * filled}{'.' * (width-filled)}] {pct:3d}%", end="", flush=True)
    else:
        print(f"\r{label:<28} {done / 1024 / 1024:8.1f} MiB", end="", flush=True)

def download(url: str, destination: Path, expected_sha256: str, retries: int = 3) -> Path:
    if not url.startswith("https://"):
        raise DownloadError(f"Only HTTPS URLs are accepted: {url}")
    expected_sha256 = expected_sha256.strip().lower()
    if len(expected_sha256) != 64 or any(c not in "0123456789abcdef" for c in expected_sha256):
        raise DownloadError(f"Invalid SHA-256 for {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_file() and sha256(destination) == expected_sha256:
        print(f"OK (cached) {destination}")
        return destination
    if destination.exists(): destination.unlink()
    temp = destination.with_name(destination.name + ".part")
    for attempt in range(1, retries + 1):
        try:
            request = Request(url, headers={"User-Agent": "KyawGyi-asset-downloader/1.0", "Accept": "*/*"})
            with urlopen(request, timeout=60) as response, temp.open("wb") as output:
                total = int(response.headers.get("Content-Length", "0")) or None
                done = 0
                while True:
                    chunk = response.read(BUF)
                    if not chunk: break
                    output.write(chunk); done += len(chunk); progress(done, total, destination.name)
            print()
            actual = sha256(temp)
            if actual != expected_sha256:
                temp.unlink(missing_ok=True)
                raise DownloadError(f"SHA-256 mismatch for {destination}: expected {expected_sha256}, got {actual}")
            os.replace(temp, destination)
            print(f"Downloaded {destination} ({destination.stat().st_size / 1024 / 1024:.1f} MiB)")
            return destination
        except (HTTPError, URLError, TimeoutError, OSError, DownloadError) as error:
            temp.unlink(missing_ok=True)
            if attempt == retries: raise DownloadError(f"Failed {url}: {error}") from error
            delay = 2 ** (attempt - 1)
            print(f"Attempt {attempt}/{retries} failed: {error}; retrying in {delay}s")
            time.sleep(delay)
    raise AssertionError("unreachable")

def load_manifest(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    for key in ("sherpa", "vosk", "sherpa_aar"):
        if key not in data: raise DownloadError(f"Manifest missing {key}")
    return data

def verify_zip(path: Path, kind: str) -> None:
    try:
        with ZipFile(path) as archive:
            names = [n.rstrip("/").split("/")[-1] for n in archive.namelist() if not n.endswith("/")]
            if kind == "sherpa":
                missing = REQUIRED_SHERPA - set(names)
                if missing: raise DownloadError(f"Sherpa ZIP misses: {sorted(missing)}")
            elif kind == "vosk" and not any(n.endswith("/am") or n == "am" for n in archive.namelist()):
                if not any("/am/" in n for n in archive.namelist()):
                    raise DownloadError("ZIP does not look like a Vosk model (am/ not found)")
    except BadZipFile as error:
        raise DownloadError(f"Invalid ZIP: {path}") from error

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--project-root", type=Path, default=Path("."))
    parser.add_argument("--retries", type=int, default=3)
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    root = args.project_root.resolve()
    assets = root / "app" / "src" / "main" / "assets"
    libs = root / "app" / "libs"

    sherpa_zip = root / ".cache" / "myanmar-sherpa-model.zip"
    download(manifest["sherpa"]["zip_url"], sherpa_zip, manifest["sherpa"]["sha256"], args.retries)
    verify_zip(sherpa_zip, "sherpa")
    sherpa_target = assets / "models" / "myanmar"
    sherpa_target.mkdir(parents=True, exist_ok=True)
    with ZipFile(sherpa_zip) as archive:
        members = {Path(n).name: n for n in archive.namelist() if not n.endswith("/")}
        for name in REQUIRED_SHERPA:
            source = members[name]
            temp = sherpa_target / (name + ".part")
            with archive.open(source) as input_stream, temp.open("wb") as output:
                shutil.copyfileobj(input_stream, output, BUF)
            os.replace(temp, sherpa_target / name)
    for item in manifest["sherpa"].get("files", []):
        target = sherpa_target / item["name"]
        if sha256(target) != item["sha256"].lower(): raise DownloadError(f"Extracted hash mismatch: {target}")

    vosk = assets / "models" / "vosk" / "vosk-model-myanmar.zip"
    download(manifest["vosk"]["url"], vosk, manifest["vosk"]["sha256"], args.retries)
    verify_zip(vosk, "vosk")

    aar = libs / "sherpa-onnx.aar"
    download(manifest["sherpa_aar"]["url"], aar, manifest["sherpa_aar"]["sha256"], args.retries)
    if aar.suffix != ".aar": raise DownloadError("Sherpa library must have .aar extension")
    print("All KyawGyi assets downloaded and verified.")
    return 0

if __name__ == "__main__":
    try: raise SystemExit(main())
    except (DownloadError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr); raise SystemExit(2)
