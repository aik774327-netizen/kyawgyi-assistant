#!/usr/bin/env python3
"""Export a trained icefall streaming Zipformer RNN-T to Sherpa-ONNX files.

This is a thin, recipe-aware wrapper. It does not convert MMS, Whisper, or
SeamlessM4T checkpoints: those are CTC/seq2seq models, not icefall stateless
RNN-T models accepted by this export path.
"""
from __future__ import annotations
import argparse, hashlib, shutil, subprocess, sys
from pathlib import Path

REQUIRED = ("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--recipe-export", type=Path, required=True,
                   help="icefall recipe export-onnx.py, e.g. pruned_transducer_stateless7_streaming/export-onnx.py")
    p.add_argument("--tokens", type=Path, required=True)
    p.add_argument("--exp-dir", type=Path, required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    p.add_argument("--epoch", type=int, required=True)
    p.add_argument("--avg", type=int, default=1)
    p.add_argument("--decode-chunk-len", type=int, default=32)
    p.add_argument("--device", default="cpu")
    p.add_argument("--use-averaged-model", type=int, default=0)
    p.add_argument("--extra", nargs="*", default=[], help="Additional recipe-specific arguments")
    args = p.parse_args()

    for path in (args.recipe_export, args.tokens, args.exp_dir):
        if not path.exists():
            p.error(f"Missing path: {path}")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    cmd = [sys.executable, str(args.recipe_export),
           "--tokens", str(args.tokens),
           "--use-averaged-model", str(args.use_averaged_model),
           "--epoch", str(args.epoch),
           "--avg", str(args.avg),
           "--decode-chunk-len", str(args.decode_chunk_len),
           "--exp-dir", str(args.exp_dir),
           "--onnx"] + args.extra
    print("Running:", " ".join(map(str, cmd)))
    subprocess.run(cmd, check=True)

    for name in ("encoder.onnx", "decoder.onnx", "joiner.onnx"):
        source = args.exp_dir / name
        if not source.is_file() or source.stat().st_size == 0:
            raise RuntimeError(f"Exporter did not produce {source}")
        shutil.copy2(source, args.output_dir / name)
    shutil.copy2(args.tokens, args.output_dir / "tokens.txt")

    missing = [n for n in REQUIRED if not (args.output_dir / n).is_file()]
    if missing:
        raise RuntimeError(f"Missing deployment files: {missing}")
    manifest = args.output_dir / "SHA256SUMS"
    with manifest.open("w", encoding="utf-8") as f:
        for name in REQUIRED:
            path = args.output_dir / name
            f.write(f"{sha256(path)}  {name}\n")
    print(f"Sherpa-ONNX model ready: {args.output_dir}")
    print(f"Manifest: {manifest}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
