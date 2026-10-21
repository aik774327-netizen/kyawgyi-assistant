#!/usr/bin/env python3
"""Download, verify and install KyawGyi speech model assets.

The script intentionally requires a trusted HTTPS URL and a 64-character SHA-256
in the JSON config. It will never guess a Myanmar model URL or accept an
unverified artifact.

Usage:
  python3 tools/download_and_extract_assets.py \
    --config tools/model-assets.json --project-root .
"""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, sys, tempfile, time
from pathlib import Path, PurePosixPath
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from zipfile import BadZipFile, ZipFile

BUF = 1024 * 1024
SHERPA_FILES = {"encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"}

class AssetError(RuntimeError):
    pass

def require_sha(value: str, label: str) -> str:
    value = value.strip().lower()
    if len(value) != 64 or any(c not in "0123456789abcdef" for c in value):
        raise AssetError(f"{label} must be a 64-character hexadecimal SHA-256")
    return value

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(BUF), b""):
            digest.update(chunk)
    return digest.hexdigest()

def download(url: str, output: Path, expected: str, label: str, retries: int) -> None:
    if not url.startswith("https://"):
        raise AssetError(f"{label}: HTTPS URL required")
    expected = require_sha(expected, f"{label} SHA-256")
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.is_file() and sha256(output) == expected:
        print(f"cached and verified: {label}")
        return
    for attempt in range(1, retries + 1):
        partial = output.with_name(output.name + ".part")
        partial.unlink(missing_ok=True)
        try:
            request = Request(url, headers={"User-Agent": "KyawGyi-ModelInstaller/1.0"})
            with urlopen(request, timeout=90) as response, partial.open("wb") as sink:
                total = int(response.headers.get("Content-Length", "0")) or None
                done = 0
                while True:
                    block = response.read(BUF)
                    if not block:
                        break
                    sink.write(block); done += len(block)
                    if total:
                        print(f"\r{label}: {done * 100 // total:3d}%", end="", flush=True)
                    else:
                        print(f"\r{label}: {done / 1024 / 1024:.1f} MiB", end="", flush=True)
            print()
            actual = sha256(partial)
            if actual != expected:
                raise AssetError(f"{label}: SHA-256 mismatch; expected {expected}, got {actual}")
            os.replace(partial, output)
            return
        except (HTTPError, URLError, TimeoutError, OSError, AssetError) as error:
            partial.unlink(missing_ok=True)
            if attempt == retries:
                raise AssetError(f"{label}: download failed after {retries} attempts: {error}") from error
            delay = 2 ** (attempt - 1)
            print(f"{label}: attempt {attempt} failed; retrying in {delay}s: {error}")
            time.sleep(delay)

def safe_member(root: Path, name: str) -> Path:
    relative = PurePosixPath(name)
    if relative.is_absolute() or ".." in relative.parts:
        raise AssetError(f"Unsafe ZIP member: {name}")
    target = (root / Path(*relative.parts)).resolve()
    base = root.resolve()
    if target != base and base not in target.parents:
        raise AssetError(f"Unsafe ZIP member: {name}")
    return target

def extract_sherpa(zip_path: Path, target: Path) -> None:
    staging = Path(tempfile.mkdtemp(prefix="kyawgyi-sherpa-", dir=target.parent))
    try:
        with ZipFile(zip_path) as archive:
            files = {}
            for info in archive.infolist():
                if info.is_dir(): continue
                out = safe_member(staging, info.filename)
                out.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as source, out.open("wb") as sink:
                    shutil.copyfileobj(source, sink, BUF)
                if out.name in SHERPA_FILES:
                    files[out.name] = out
        missing = SHERPA_FILES - files.keys()
        if missing: raise AssetError(f"Sherpa ZIP missing: {sorted(missing)}")
        normalized = Path(tempfile.mkdtemp(prefix="kyawgyi-sherpa-ready-", dir=target.parent))
        try:
            for name, source in files.items(): shutil.move(str(source), normalized / name)
            activate(normalized, target)
        except Exception:
            shutil.rmtree(normalized, ignore_errors=True); raise
    except BadZipFile as error:
        raise AssetError(f"Invalid Sherpa ZIP: {zip_path}") from error
    finally:
        shutil.rmtree(staging, ignore_errors=True)

def extract_vosk(zip_path: Path, target: Path) -> None:
    staging = Path(tempfile.mkdtemp(prefix="kyawgyi-vosk-", dir=target.parent))
    try:
        with ZipFile(zip_path) as archive:
            for info in archive.infolist():
                if info.is_dir(): continue
                out = safe_member(staging, info.filename); out.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(info) as source, out.open("wb") as sink: shutil.copyfileobj(source, sink, BUF)
        roots = [staging] + [p for p in staging.iterdir() if p.is_dir()]
        roots = [p for p in roots if (p / "am").is_dir() or (p / "conf").is_dir()]
        if len(roots) != 1: raise AssetError("Vosk ZIP must contain one root with am/ or conf/")
        activate(roots[0], target)
    except BadZipFile as error:
        raise AssetError(f"Invalid Vosk ZIP: {zip_path}") from error
    finally:
        shutil.rmtree(staging, ignore_errors=True)

def activate(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    backup = target.with_name(target.name + ".old")
    if backup.exists(): shutil.rmtree(backup)
    if target.exists(): os.replace(target, backup)
    try:
        os.replace(source, target)
    except Exception:
        if backup.exists() and not target.exists(): os.replace(backup, target)
        raise
    if backup.exists(): shutil.rmtree(backup)

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--project-root", type=Path, default=Path("."))
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--keep-downloads", action="store_true")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    root = args.project_root.resolve()
    cache = root / ".cache" / "kyawgyi-models"; cache.mkdir(parents=True, exist_ok=True)
    myanmar = root / "app/src/main/assets/models/myanmar"; vosk = root / "app/src/main/assets/models/vosk"
    sherpa = config.get("sherpa")
    if not sherpa: raise AssetError("config.sherpa is required")
    sherpa_zip = cache / "sherpa-myanmar.zip"
    download(sherpa["url"], sherpa_zip, sherpa["sha256"], "Sherpa Myanmar", args.retries)
    extract_sherpa(sherpa_zip, myanmar)
    optional_vosk = config.get("vosk")
    if optional_vosk:
        vosk_zip = cache / "vosk-myanmar.zip"
        download(optional_vosk["url"], vosk_zip, optional_vosk["sha256"], "Vosk Myanmar", args.retries)
        extract_vosk(vosk_zip, vosk)
    if not args.keep_downloads: shutil.rmtree(cache, ignore_errors=True)
    print("Installed Sherpa assets:", myanmar)
    if optional_vosk: print("Installed Vosk assets:", vosk)
    return 0

if __name__ == "__main__":
    try: raise SystemExit(main())
    except (AssetError, OSError, KeyError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr); raise SystemExit(2)
