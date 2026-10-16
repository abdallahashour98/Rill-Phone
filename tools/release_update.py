#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rill-Phone Automated One-Click Fast Release & Smart Delta Update Tool
Follows the PhotoApp / Reminder updater architecture:
1. Version bump in app/build.gradle (versionName & versionCode)
2. Release APK build (gradlew assembleFossRelease)
3. Ultra-lightweight delta patch generation (BSDIFF delta) from previous APKs
4. Checksum calculation, security hashing, and update_manifest.json generation
5. Publishing manifest and patches to the updates repository (https://github.com/abdallahashour98/Rill-Phone.git)
6. Creating and uploading GitHub Releases with assets (manifest, patches, APK)
"""

import os
import sys
import re
import time
import json
import shutil
import hashlib
import zipfile
import struct
import argparse
import subprocess
import urllib.request
import urllib.error
from typing import Optional, List, Tuple
from pathlib import Path

# Fix Windows console UTF-8 output
if sys.platform == 'win32':
    try:
        getattr(sys.stdout, 'reconfigure', lambda **k: None)(encoding='utf-8', errors='replace')
        getattr(sys.stderr, 'reconfigure', lambda **k: None)(encoding='utf-8', errors='replace')
    except Exception:
        pass

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BUILD_GRADLE_PATH = PROJECT_ROOT / "app" / "build.gradle"
DIST_DIR = PROJECT_ROOT / "dist"
UPDATES_DIR = DIST_DIR / "updates"
TOOLS_DIR = PROJECT_ROOT / "tools"

# Remote Repositories - Dedicated Updates Channel on GitHub
UPDATES_REPO_URL = "https://github.com/abdallahashour98/Rill-Phone.git"
UPDATES_REPO_NAME = "abdallahashour98/Rill-Phone"
BASE_DOWNLOAD_URL = f"https://github.com/{UPDATES_REPO_NAME}/releases/latest/download"
BASELINE_VERSION_CODES = {}

def print_header(title: str):
    print("\n" + "=" * 65)
    print(f"✨ {title}")
    print("=" * 65)

def format_size(bytes_len: int) -> str:
    if bytes_len < 1024:
        return f"{bytes_len} B"
    elif bytes_len < 1024 * 1024:
        return f"{bytes_len / 1024:.2f} KB"
    elif bytes_len < 1024 * 1024 * 1024:
        return f"{bytes_len / (1024 * 1024):.2f} MB"
    else:
        return f"{bytes_len / (1024 * 1024 * 1024):.2f} GB"

def compute_sha256(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def get_current_version() -> Tuple[str, int]:
    with open(BUILD_GRADLE_PATH, 'r', encoding='utf-8') as f:
        content = f.read()

    code_match = re.search(r'versionCode\s*=\s*([0-9]+)', content)
    name_match = re.search(r'versionName\s*=\s*["\']([^"\']+)["\']', content)

    if not code_match or not name_match:
        raise ValueError("Could not read versionCode or versionName from app/build.gradle")

    return name_match.group(1), int(code_match.group(1))

def update_gradle_version(new_version: str, new_code: int):
    with open(BUILD_GRADLE_PATH, 'r', encoding='utf-8') as f:
        content = f.read()

    updated = re.sub(
        r'versionCode\s*=\s*[0-9]+',
        f'versionCode = {new_code}',
        content
    )
    updated = re.sub(
        r'versionName\s*=\s*["\'][^"\']+["\']',
        f'versionName = "{new_version}"',
        updated
    )

    with open(BUILD_GRADLE_PATH, 'w', encoding='utf-8') as f:
        f.write(updated)
    print(f"[INFO] Updated app/build.gradle to: versionName = \"{new_version}\", versionCode = {new_code}")

def read_apk_version_info(apk_path: Path):
    """Directly extracts versionName and versionCode from an APK's binary AndroidManifest.xml."""
    try:
        with zipfile.ZipFile(apk_path, 'r') as z:
            data = z.read('AndroidManifest.xml')
            chunk_type, header_size, chunk_size, string_count, style_count, flags, strings_start, styles_start = struct.unpack('<HHIIIIII', data[8:36])
            string_offsets = struct.unpack(f'<{string_count}I', data[36:36 + string_count * 4])
            pool_start = 8 + strings_start
            strings = []
            for offset in string_offsets:
                pos = pool_start + offset
                if flags & (1 << 8):
                    u8len = data[pos + 1]
                    s = data[pos + 2:pos + 2 + u8len].decode('utf-8', errors='ignore')
                else:
                    u16len = struct.unpack('<H', data[pos:pos+2])[0]
                    s = data[pos+2:pos+2+u16len*2].decode('utf-16le', errors='ignore')
                strings.append(s)

            pos = 8 + chunk_size
            version_code = None
            version_name = None
            while pos < len(data) - 4:
                c_type = struct.unpack('<I', data[pos:pos+4])[0]
                if c_type == 0x00100102:
                    attr_count = struct.unpack('<H', data[pos+28:pos+30])[0]
                    for i in range(attr_count):
                        offset = pos + 36 + i * 20
                        ns, name_idx, raw_idx, tv_size, res0, data_type, val = struct.unpack('<IIIHBBI', data[offset:offset+20])
                        attr_name = strings[name_idx] if name_idx < len(strings) else ''
                        if attr_name == 'versionCode':
                            version_code = val
                        elif attr_name == 'versionName':
                            version_name = strings[val] if val < len(strings) else str(val)
                    break
                pos += 4
            return version_name, version_code
    except Exception:
        return None, None

def find_recent_dist_apks(exclude_version: Optional[str] = None, max_count: int = 3) -> List[Path]:
    """Find up to max_count recent APK files in dist directory for delta comparison, excluding current version."""
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    apks = list(DIST_DIR.glob("rill-phone-*-foss-release.apk")) + list(DIST_DIR.glob("Rill-Phone_v*.apk"))
    if not apks:
        apks = list(DIST_DIR.glob("*.apk"))

    filtered = []
    seen_names = set()
    for p in apks:
        if p.name in seen_names:
            continue
        if exclude_version and (f"v{exclude_version}.apk" in p.name or f"-{exclude_version}-" in p.name):
            continue
        seen_names.add(p.name)
        filtered.append(p)

    filtered.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return filtered[:max_count]

def find_latest_dist_apk(exclude_version: Optional[str] = None) -> Optional[Path]:
    recent = find_recent_dist_apks(exclude_version=exclude_version, max_count=1)
    return recent[0] if recent else None

def extract_version_from_filename(filename: str) -> str:
    match = re.search(r'v([0-9]+(?:\.[0-9]+)*)', filename)
    if match:
        return match.group(1)
    code_match = re.search(r'rill-phone-([0-9]+)-', filename)
    if code_match:
        return f"0.6.{code_match.group(1)}"
    return "0.6.0"

def find_version_code(version: str, fallback: int) -> int:
    """Use the updater manifest's real baseline code for delta patches."""
    if version in BASELINE_VERSION_CODES:
        return BASELINE_VERSION_CODES[version]
    manifest_path = UPDATES_DIR / "update_manifest.json"
    try:
        with open(manifest_path, 'r', encoding='utf-8') as f:
            manifest = json.load(f)
        if str(manifest.get("latestVersion")) == str(version):
            return int(manifest.get("latestVersionCode", fallback))
        for patch in manifest.get("patches", []):
            if str(patch.get("fromVersion")) == str(version):
                return int(patch.get("fromVersionCode", fallback))
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        pass
    return fallback

def setup_github_token() -> Optional[str]:
    """Ensure GH_TOKEN and GITHUB_TOKEN are set in environment from gh auth token, git credentials, or PAT"""
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        os.environ["GH_TOKEN"] = token
        os.environ["GITHUB_TOKEN"] = token
        return token

    # 1. Try gh CLI auth token
    try:
        res = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True)
        if res.returncode == 0:
            token = res.stdout.strip()
            if token:
                os.environ["GH_TOKEN"] = token
                os.environ["GITHUB_TOKEN"] = token
                return token
    except Exception:
        pass

    # 2. Check local token cache
    token_file = Path.home() / ".rill_phone_github_token"
    if token_file.exists():
        try:
            token = token_file.read_text(encoding="utf-8").strip()
            if token:
                os.environ["GH_TOKEN"] = token
                os.environ["GITHUB_TOKEN"] = token
                return token
        except Exception:
            pass

    # 3. Try Git Credential Manager
    for cmd in [
        ["git", "credential-manager", "get"],
        ["git", "credential", "fill"]
    ]:
        try:
            res = subprocess.run(
                cmd,
                input="protocol=https\nhost=github.com\n\n",
                text=True,
                capture_output=True
            )
            if res.returncode == 0:
                for line in res.stdout.splitlines():
                    if line.startswith("password="):
                        token = line.split("=", 1)[1].strip()
                        if token:
                            os.environ["GH_TOKEN"] = token
                            os.environ["GITHUB_TOKEN"] = token
                            return token
        except Exception:
            pass

    # 4. Interactive fallback if running in terminal
    if sys.stdin.isatty():
        try:
            print_header("GitHub Authentication")
            token = input("🔑 Please paste your GitHub Personal Access Token (PAT): ").strip()
            if token:
                token_file.write_text(token, encoding="utf-8")
                os.environ["GH_TOKEN"] = token
                os.environ["GITHUB_TOKEN"] = token
                return token
        except Exception:
            pass

    return None

def run_cmd(cmd: list, cwd=PROJECT_ROOT, check=True):
    print(f">> Executing: {' '.join(cmd)}")
    res = subprocess.run(cmd, cwd=cwd, shell=True if sys.platform == 'win32' else False)
    if check and res.returncode != 0:
        raise RuntimeError(f"Command failed: {' '.join(cmd)} (Exit code: {res.returncode})")
    return res.returncode

def sync_to_updates_repo(new_version: str, notes: str):
    """Push update_manifest.json, README.md, and delta patches to updates repository on GitHub"""
    temp_dir = DIST_DIR / ".updates_git"
    print_header(f"Syncing public updates repository ({UPDATES_REPO_NAME})...")

    token = setup_github_token()
    repo_url = f"https://{token}@github.com/{UPDATES_REPO_NAME}.git" if token else UPDATES_REPO_URL

    if not (temp_dir / ".git").exists():
        if temp_dir.exists():
            shutil.rmtree(temp_dir, ignore_errors=True)
        print("Cloning updates repository...")
        try:
            run_cmd(["git", "clone", repo_url, str(temp_dir)])
        except Exception:
            pass

    if not (temp_dir / ".git").exists():
        temp_dir.mkdir(parents=True, exist_ok=True)
        run_cmd(["git", "init", "-b", "main"], cwd=temp_dir)
        run_cmd(["git", "remote", "add", "origin", repo_url], cwd=temp_dir, check=False)
    else:
        print("Pulling latest changes from updates repository...")
        run_cmd(["git", "remote", "set-url", "origin", repo_url], cwd=temp_dir, check=False)
        run_cmd(["git", "pull", "origin", "main"], cwd=temp_dir, check=False)

    # 1. Copy update_manifest.json to root of updates repo
    manifest_src = UPDATES_DIR / "update_manifest.json"
    if manifest_src.exists():
        shutil.copy2(manifest_src, temp_dir / "update_manifest.json")
        print("Copied update_manifest.json to updates repository.")

    # 2. Ensure clean README.md exists
    readme_content = f"""# Rill-Phone Official Releases & Updates

This repository serves official updates, manifests, delta patches, and APK releases for **Rill-Phone**.

- Latest Manifest: [update_manifest.json](update_manifest.json)
- Downloads & Releases: [Releases Section](https://github.com/{UPDATES_REPO_NAME}/releases)
"""
    with open(temp_dir / "README.md", 'w', encoding='utf-8') as f:
        f.write(readme_content)

    # 3. Copy any delta patch files
    for patch in UPDATES_DIR.glob("*.patch"):
        shutil.copy2(patch, temp_dir / patch.name)
        print(f"Copied patch: {patch.name}")

    # 4. Commit and push ONLY updates files to updates repo
    clean_notes = notes.replace("&", "and").replace('"', "'")
    commit_msg = f"Update manifest and patches for v{new_version}: {clean_notes}"
    run_cmd(["git", "config", "user.name", "abdallahashour98"], cwd=temp_dir)
    run_cmd(["git", "config", "user.email", "abdallahashour98@users.noreply.github.com"], cwd=temp_dir)
    run_cmd(["git", "add", "."], cwd=temp_dir)
    run_cmd(["git", "commit", "-m", commit_msg], cwd=temp_dir, check=False)
    run_cmd(["git", "push", "-u", "origin", "main"], cwd=temp_dir)
    print("SUCCESS: Update files pushed to public repository!")

def create_github_release_api(token: str, tag_name: str, title: str, notes: str, asset_path: Path):
    """Direct GitHub REST API release creator (fallback if gh CLI is missing)"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "Rill-Phone-Release-Tool"
    }

    release_id = None
    upload_url = None
    try:
        check_req = urllib.request.Request(
            f"https://api.github.com/repos/{UPDATES_REPO_NAME}/releases/tags/{tag_name}",
            headers=headers
        )
        with urllib.request.urlopen(check_req) as resp:
            data = json.loads(resp.read().decode())
            release_id = data.get("id")
            upload_url = data.get("upload_url", "").split("{")[0]
            print(f">> Found existing GitHub Release (ID: {release_id})")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            print(f"[WARNING] HTTP check returned: {e.code}")

    if not release_id:
        print(f">> Creating new release {tag_name} via GitHub API...")
        payload = {
            "tag_name": tag_name,
            "target_commitish": "main",
            "name": title,
            "body": notes,
            "draft": False,
            "prerelease": False,
            "make_latest": "true"
        }
        create_req = urllib.request.Request(
            f"https://api.github.com/repos/{UPDATES_REPO_NAME}/releases",
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST"
        )
        with urllib.request.urlopen(create_req) as resp:
            data = json.loads(resp.read().decode())
            release_id = data.get("id")
            upload_url = data.get("upload_url", "").split("{")[0]
            print(f"SUCCESS: Created release {tag_name} (ID: {release_id})")

    if upload_url and asset_path.exists():
        filename = asset_path.name
        file_size = asset_path.stat().st_size
        print(f">> Uploading APK asset: {filename} ({format_size(file_size)})...")

        try:
            assets_req = urllib.request.Request(
                f"https://api.github.com/repos/{UPDATES_REPO_NAME}/releases/{release_id}/assets",
                headers=headers
            )
            with urllib.request.urlopen(assets_req) as resp:
                existing_assets = json.loads(resp.read().decode())
                for ast in existing_assets:
                    if ast.get("name") == filename:
                        del_id = ast.get("id")
                        print(f">> Replacing existing asset (ID: {del_id})...")
                        del_req = urllib.request.Request(
                            f"https://api.github.com/repos/{UPDATES_REPO_NAME}/releases/assets/{del_id}",
                            headers=headers,
                            method="DELETE"
                        )
                        urllib.request.urlopen(del_req)
        except Exception as e:
            print(f"[INFO] Asset check: {e}")

        upload_endpoint = f"{upload_url}?name={filename}"
        with open(asset_path, "rb") as f:
            apk_data = f.read()

        up_headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(len(apk_data)),
            "User-Agent": "Rill-Phone-Release-Tool"
        }
        up_req = urllib.request.Request(upload_endpoint, data=apk_data, headers=up_headers, method="POST")
        with urllib.request.urlopen(up_req) as resp:
            print(f"SUCCESS: Uploaded {filename} successfully to GitHub Release!")

def main():
    parser = argparse.ArgumentParser(description="Rill-Phone Automated One-Click Release & Fast Update Tool")
    parser.add_argument("--notes", help="Changelog and release notes")
    parser.add_argument("--version", help="New version string (e.g. 0.6.14). Automatically bumped if not specified")
    parser.add_argument("--code", type=int, help="New build/version code (e.g. 74). Automatically bumped if not specified")
    parser.add_argument("--skip-build", action="store_true", help="Skip APK build and use latest existing built APK")
    parser.add_argument("--skip-push", action="store_true", help="Skip syncing updates and creating release on GitHub")
    parser.add_argument("--full-apk", action="store_true", help="Upload full APK to GitHub Releases (default: enabled)")
    parser.add_argument("--patch-only", action="store_true", help="Upload only delta patch and manifest to GitHub Releases")
    args = parser.parse_args()

    print_header("Rill-Phone - Automated One-Click Release & Delta Update Tool")

    # 1. Determine current and new version
    curr_version, curr_code = get_current_version()
    print(f"[INFO] Current project version: v{curr_version} (versionCode: {curr_code})")

    # Load baseline codes from existing manifest
    try:
        with open(UPDATES_DIR / "update_manifest.json", 'r', encoding='utf-8') as f:
            previous_manifest = json.load(f)
        latest_ver = str(previous_manifest.get("latestVersion", ""))
        if latest_ver:
            BASELINE_VERSION_CODES[latest_ver] = int(previous_manifest.get("latestVersionCode", 0))
        for patch in previous_manifest.get("patches", []):
            from_ver = str(patch.get("fromVersion", ""))
            if from_ver:
                BASELINE_VERSION_CODES[from_ver] = int(patch.get("fromVersionCode", 0))
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        pass

    # Automatic sub-version / patch increment: e.g. 0.6.12 -> 0.6.13
    if args.version:
        new_version = args.version
    else:
        parts = [int(p) for p in re.findall(r'\d+', curr_version)]
        while len(parts) < 3:
            parts.append(0)
        parts[-1] += 1
        new_version = '.'.join(str(p) for p in parts)

    new_code = args.code if args.code else (curr_code + 1)

    # 2. Release Notes (Changelog)
    notes = args.notes
    if not notes:
        if sys.stdin.isatty():
            print("\n>> Enter release notes (Changelog):")
            print("   (Press Enter for default: General performance improvements and bug fixes)")
            user_input = input("   Notes: ").strip()
            notes = user_input if user_input else f"General performance improvements, UI enhancements, and bug fixes for v{new_version}."
        else:
            notes = f"General performance improvements, UI enhancements, and bug fixes for v{new_version}."

    # 3. Upload mode preference
    upload_full_apk = True
    if args.patch_only:
        upload_full_apk = False
    elif args.full_apk:
        upload_full_apk = True
    elif not args.skip_push and sys.stdin.isatty():
        print("\n" + "-" * 60)
        print("📦 What would you like to upload to GitHub Releases?")
        print("   [1] Full Release: Delta patches + Full APK [Recommended - Safe for ALL users & fallbacks] - Default")
        print("   [2] Fast Delta patches only & manifest [Skip full APK - Fast update]")
        choice = input("   >> Your choice [1/2] (Press Enter for default [1]): ").strip()
        upload_full_apk = (choice != "2")
        if upload_full_apk:
            print("   >> Selected: Upload Delta Patches + Full APK (Recommended)")
        else:
            print("   >> Selected: Upload Delta Patches Only & Manifest")
        print("-" * 60)

    print(f"\n[TARGET] Releasing version: v{new_version} (versionCode: {new_code})")
    print(f"[NOTES]:\n   {notes}\n")

    # 4. Update app/build.gradle
    update_gradle_version(new_version, new_code)

    # 5. Save previous APKs for multi-version delta comparison
    prev_apks = find_recent_dist_apks(exclude_version=new_version, max_count=3)
    if prev_apks:
        print(f"[INFO] Found {len(prev_apks)} baseline APK(s) for multi-version delta coverage:")
        for p in prev_apks:
            print(f"       - {p.name} ({format_size(p.stat().st_size)})")

    # 6. Build or locate Release APK
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    UPDATES_DIR.mkdir(parents=True, exist_ok=True)

    new_apk_target = DIST_DIR / f"rill-phone-{new_code}-foss-release.apk"
    built_apk_path = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "foss" / "release" / f"rill-phone-{new_code}-foss-release.apk"

    if not args.skip_build:
        if sys.platform == 'win32':
            gradle_cmd = ["cmd.exe", "/c", "gradlew.bat", "assembleFossRelease"]
        else:
            gradle_cmd = ["./gradlew", "assembleFossRelease"]
            if os.path.exists("/usr/lib/jvm/java-25-openjdk"):
                gradle_cmd.insert(1, "-Dorg.gradle.java.home=/usr/lib/jvm/java-25-openjdk")
        run_cmd(gradle_cmd)

        if not built_apk_path.exists():
            candidates = list((PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "foss" / "release").glob("*.apk"))
            if candidates:
                built_apk_path = candidates[0]
            else:
                raise FileNotFoundError(f"Built release APK not found at: {built_apk_path}")

        shutil.copy2(built_apk_path, new_apk_target)
        friendly_apk_target = DIST_DIR / f"Rill-Phone_v{new_version}.apk"
        shutil.copy2(built_apk_path, friendly_apk_target)
        print(f"SUCCESS: Copied APK to: {new_apk_target.name} ({format_size(new_apk_target.stat().st_size)})")
    else:
        print("[INFO] Skipped APK build as requested.")
        if not new_apk_target.exists():
            candidates = list(DIST_DIR.glob(f"*{new_code}*.apk")) + list(DIST_DIR.glob(f"*v{new_version}*.apk")) + list(DIST_DIR.glob("*.apk"))
            if candidates:
                shutil.copy2(candidates[0], new_apk_target)
                friendly_apk_target = DIST_DIR / f"Rill-Phone_v{new_version}.apk"
                shutil.copy2(candidates[0], friendly_apk_target)
            else:
                raise FileNotFoundError("No release APK found in dist/ or build outputs!")

    # Synchronize version info if APK internal manifest differs
    actual_name, actual_code = read_apk_version_info(new_apk_target)
    if actual_code is not None:
        if actual_code != new_code or (actual_name and actual_name != new_version):
            print(f"[WARNING] APK internal version (v{actual_name}+{actual_code}) differs from requested (v{new_version}+{new_code}).")
            print(f"[INFO] Synchronizing release version info to match real APK: v{actual_name}+{actual_code}")
            new_version = actual_name or new_version
            new_code = actual_code

    apk_sha = compute_sha256(new_apk_target)
    apk_size = new_apk_target.stat().st_size
    print(f"[INFO] APK SHA-256: {apk_sha}")

    # 7. Generate Smart Delta Patches
    manifest_path = UPDATES_DIR / "update_manifest.json"
    manifest = {
        "latestVersion": new_version,
        "latestVersionCode": new_code,
        "releaseDate": time.strftime("%Y-%m-%d"),
        "releaseNotes": notes,
        "fullApk": {
            "fileName": new_apk_target.name,
            "url": f"{BASE_DOWNLOAD_URL}/{new_apk_target.name}",
            "sizeBytes": apk_size,
            "sizeFormatted": format_size(apk_size),
            "sha256": apk_sha
        },
        "patches": []
    }
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    generated_patches_count = 0
    patch_creator = TOOLS_DIR / "create_android_patch.py"

    if patch_creator.exists():
        for idx, prev_apk in enumerate(prev_apks):
            if not prev_apk or not prev_apk.exists() or prev_apk.name == new_apk_target.name:
                continue
            old_v_apk, old_c_apk = read_apk_version_info(prev_apk)
            old_ver = old_v_apk or extract_version_from_filename(prev_apk.name)
            old_c = old_c_apk if old_c_apk is not None else find_version_code(old_ver, max(1, new_code - (idx + 1)))

            print_header(f"Generating delta patch ({idx+1}/{len(prev_apks)}): v{old_ver}+{old_c} -> v{new_version}+{new_code}...")
            try:
                run_cmd([
                    sys.executable,
                    str(patch_creator),
                    "--old", str(prev_apk),
                    "--new", str(new_apk_target),
                    "--version", new_version,
                    "--code", str(new_code),
                    "--old-version", old_ver,
                    "--old-code", str(old_c),
                    "--notes", notes,
                    "--out-dir", str(UPDATES_DIR),
                    "--base-url", BASE_DOWNLOAD_URL
                ])
                generated_patches_count += 1
            except Exception as e:
                print(f"[WARNING] Failed generating patch from {prev_apk.name}: {e}")

    if generated_patches_count == 0:
        print("[INFO] No delta patches generated; manifest contains full APK only.")
    else:
        print(f"\nSUCCESS: Generated {generated_patches_count} delta patch(es) for multi-version coverage!")

    # 8. Automated Upload
    if not args.skip_push:
        token = setup_github_token()

        # a) Save and commit code locally
        print_header("1. Saving and committing project code...")
        try:
            run_cmd(["git", "config", "user.name", "abdallahashour98"])
            run_cmd(["git", "add", "-A"])
            clean_notes = notes.replace("&", "and").replace('"', "'")
            commit_msg = f"Release v{new_version}: {clean_notes}"
            run_cmd(["git", "commit", "-m", commit_msg], check=False)
            print("SUCCESS: Code changes committed locally.")

            # Attempt push to origin if origin is a personal writable repository
            try:
                chk = subprocess.run(["git", "remote", "get-url", "origin"], cwd=PROJECT_ROOT, capture_output=True, text=True)
                origin_url = chk.stdout.strip()
                if origin_url and "Goodwy/RPhone" not in origin_url:
                    run_cmd(["git", "push", "-u", "origin", "main"])
                    print("SUCCESS: Pushed code to repository.")
            except Exception as e:
                print(f"[INFO] Remote origin push: {e}")
        except Exception as e:
            print(f"[INFO] Local git commit: {e}")

        # b) Push update files to GitHub updates repo
        try:
            sync_to_updates_repo(new_version, notes)
        except Exception as e:
            print(f"[WARNING] Note while syncing updates repo: {e}")

        # c) Create and publish official GitHub Release
        tag_name = f"v{new_version}"
        patch_files = list(UPDATES_DIR.glob(f"*_to_v{new_version}.patch"))
        manifest_file = UPDATES_DIR / "update_manifest.json"

        print_header(f"Creating & Publishing GitHub Release {tag_name} on {UPDATES_REPO_NAME}...")

        env = os.environ.copy()
        if token:
            env["GH_TOKEN"] = token
            env["GITHUB_TOKEN"] = token

        # Step 1: Upload manifest and delta patches first (instant publishing)
        fast_assets = [str(manifest_file)] + [str(p) for p in patch_files if p.exists()]

        check_res = subprocess.run(
            ["gh", "release", "view", tag_name, "--repo", UPDATES_REPO_NAME],
            capture_output=True,
            text=True,
            env=env
        )
        release_exists = (check_res.returncode == 0)

        release_published = False
        if not release_exists:
            print(f">> Creating official release {tag_name}...")
            create_cmd = ["gh", "release", "create", tag_name] + fast_assets + [
                "--repo", UPDATES_REPO_NAME,
                "--title", f"Rill-Phone v{new_version}",
                "--notes", notes,
                "--latest"
            ]
            gh_res = subprocess.run(create_cmd, cwd=PROJECT_ROOT, env=env)
            if gh_res.returncode == 0:
                release_published = True
            else:
                print(f"[WARNING] 'gh release create' returned code {gh_res.returncode}")
        else:
            print(f">> Release {tag_name} already exists. Updating release...")
            edit_res = subprocess.run(
                ["gh", "release", "edit", tag_name, "--repo", UPDATES_REPO_NAME, "--draft=false", "--latest"],
                cwd=PROJECT_ROOT,
                env=env
            )
            upload_cmd = ["gh", "release", "upload", tag_name] + fast_assets + [
                "--repo", UPDATES_REPO_NAME,
                "--clobber"
            ]
            upload_res = subprocess.run(upload_cmd, cwd=PROJECT_ROOT, env=env)
            if upload_res.returncode == 0:
                release_published = True

        def gh_upload(files, label, retries=3):
            cmd = ["gh", "release", "upload", tag_name] + [str(f) for f in files] + [
                "--repo", UPDATES_REPO_NAME, "--clobber"
            ]
            for attempt in range(1, retries + 1):
                res = subprocess.run(cmd, cwd=PROJECT_ROOT, env=env)
                if res.returncode == 0:
                    print(f"SUCCESS: {label} uploaded successfully to release {tag_name}!")
                    return True
                print(f"[WARNING] {label} upload failed (attempt {attempt}/{retries}, code {res.returncode})")
                time.sleep(3 * attempt)
            return False

        # Step 2: Upload full APK with live progress
        if upload_full_apk:
            if new_apk_target.exists():
                print(f"\n>> Uploading full APK ({format_size(new_apk_target.stat().st_size)}) to release {tag_name}...")
                apk_success = gh_upload([new_apk_target], "Full APK")
                if apk_success:
                    release_published = True
            else:
                print(f"[WARNING] Full APK not found at: {new_apk_target}")
        else:
            print(f"\n[INFO] Skipped full APK upload as selected.")
            print(f"       Release {tag_name} was published with delta patches and manifest only.")

        # Fallback to direct REST API if gh CLI failed
        if not release_published and token:
            print("[INFO] Falling back to direct GitHub REST API...")
            try:
                create_github_release_api(
                    token=token,
                    tag_name=tag_name,
                    title=f"Rill-Phone v{new_version}",
                    notes=notes,
                    asset_path=new_apk_target
                )
                release_published = True
            except Exception as e:
                print(f"[WARNING] REST API Release upload error: {e}")

        if release_published:
            print(f"\nSUCCESS: GitHub Release {tag_name} is LIVE and Published!")
            print(f"URL: https://github.com/{UPDATES_REPO_NAME}/releases/tag/{tag_name}")

    print_header("SUCCESS: Release process completed successfully!")
    print(f"New version v{new_version} (Build {new_code}) is ready for distribution.")
    print("=" * 65 + "\n")

if __name__ == "__main__":
    main()
