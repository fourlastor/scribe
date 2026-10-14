#!/usr/bin/env python3
# Copied unchanged from fourlastor/appfredo, scripts/prepare-release.py at 47f0a74: Appfredo's own release helper,
# which defines the release format its store reads. Take updates from there rather than editing this copy.
"""Prepare (but never upload) a signed APK and optional Wharf delta for Appfredo."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def artifact(path):
    return {"asset": path.name, "sha256": sha256(path), "size": path.stat().st_size}


def package_info(apk, aapt):
    result = subprocess.run([aapt, "dump", "badging", str(apk)], check=True, capture_output=True, text=True)
    line = next((line for line in result.stdout.splitlines() if line.startswith("package:")), "")
    fields = dict(re.findall(r"(\w+)='([^']*)'", line))
    if not all(key in fields for key in ("name", "versionCode", "versionName")):
        raise ValueError("Could not read APK package/version metadata")
    if "split" in fields or "uses-split:" in result.stdout:
        raise ValueError("Appfredo currently accepts standalone APKs only")
    return fields["name"], int(fields["versionCode"]), fields["versionName"]


def signing_keys(apk, apksigner):
    result = subprocess.run([apksigner, "verify", "--print-certs", str(apk)], check=True, capture_output=True, text=True)
    # Older apksigners print "Signer #1 certificate …"; 37 names the scheme ("V3.0 Signer: …", "V2 Signer #2: …",
    # "V3.2 Hybrid PQC Signer: (minSdkVersion=…) …"). A source stamp says where an APK came from, not who signed it.
    signers = re.findall(r"^(.*Signer.*) certificate SHA-256 digest: ([0-9a-f]{64})$", result.stdout, re.MULTILINE)
    keys = {digest for signer, digest in signers if not signer.startswith("Source Stamp")}
    if not keys:
        raise ValueError("APK has no verified signing certificate")
    return keys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path, help="New, already signed standalone APK")
    parser.add_argument("--name", required=True, help="App display name")
    parser.add_argument("--previous-apk", type=Path, action="append", default=[],
                        help="An earlier signed APK of the same app; repeat it for patches from several releases")
    parser.add_argument("--out", required=True, type=Path, help="New or empty output directory")
    parser.add_argument("--butler", default="butler")
    parser.add_argument("--aapt", default="aapt")
    parser.add_argument("--apksigner", default="apksigner")
    args = parser.parse_args()
    if not args.name.strip() or len(args.name) > 100:
        parser.error("--name must be 1–100 characters")
    if args.out.exists() and any(args.out.iterdir()):
        parser.error("--out must be empty; existing artifacts are never overwritten")
    package, code, version = package_info(args.apk, args.aapt)
    if code <= 0:
        parser.error("APK versionCode must be positive")
    keys = signing_keys(args.apk, args.apksigner)
    previous = {}  # versionCode -> APK
    for old_apk in args.previous_apk:
        old_package, old_code, _ = package_info(old_apk, args.aapt)
        if old_package != package or old_code >= code:
            parser.error(f"{old_apk}: a previous APK must be the same package with a lower versionCode")
        if signing_keys(old_apk, args.apksigner) != keys:
            parser.error(f"{old_apk}: signing certificates differ; signing-key rotation is not supported by this helper")
        if old_code in previous:
            parser.error(f"Two previous APKs share versionCode {old_code}")
        previous[old_code] = old_apk
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="appfredo-release-", dir=args.out.parent) as temporary:
        work = Path(temporary)
        staged = work / "release"
        staged.mkdir()
        apk = staged / "app.apk"
        shutil.copyfile(args.apk, apk)
        manifest = {"schemaVersion": 1, "packageName": package, "name": args.name,
                    "versionCode": code, "versionName": version, "apk": artifact(apk), "patches": []}
        new = work / "new"
        new.mkdir()
        shutil.copyfile(apk, new / "app.apk")
        for old_code, old_apk in sorted(previous.items(), reverse=True):
            old = work / f"old-{old_code}"
            old.mkdir()
            shutil.copyfile(old_apk, old / "app.apk")
            patch = staged / f"from-{old_code}-to-{code}.pwr"
            subprocess.run([args.butler, "diff", "--verify", str(old), str(new), str(patch)], check=True)
            if patch.stat().st_size >= apk.stat().st_size:
                # Appfredo would never pick it over the full APK, so it is not worth uploading.
                for leftover in (patch, patch.with_name(patch.name + ".sig")):
                    leftover.unlink(missing_ok=True)
                print(f"Skipped the patch from {old_code}: it is no smaller than the full APK")
                continue
            manifest["patches"].append({"fromVersionCode": old_code, "fromSha256": sha256(old_apk), **artifact(patch)})
        (staged / "appfredo.json").write_text(json.dumps(manifest, indent=2) + "\n")
        if args.out.exists():
            args.out.rmdir()  # Only an empty directory can be removed.
        os.rename(staged, args.out)
    print(f"Prepared {package} {version} ({code}) in {args.out}")
    print("Upload every file in that directory to the same stable GitHub release.")


if __name__ == "__main__":
    main()
