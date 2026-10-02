#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rill-Phone Automated One-Click Release & Fast Update Tool
Automates the complete release workflow:
1. Version bump in app/build.gradle (versionCode & versionName)
2. Release APK build (gradlew assembleFossRelease)
3. Asset management and SHA256 checksum calculation in dist/
4. Git commit & push source code updates to https://github.com/abdallahashour98/Rill-Phone.git
5. Official GitHub Release publishing with release notes and APK asset upload
"""

import os
import sys
import re
import time
import json
import shutil
import hashlib
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
TOOLS_DIR = PROJECT_ROOT / "tools"

# Remote Repositories
CODE_REPO_URL = "https://github.com/abdallahashour98/Rill-Phone.git"
REPO_NAME = "abdallahashour98/Rill-Phone"
BASE_DOWNLOAD_URL = f"https://github.com/{REPO_NAME}/releases/latest/download"

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

def setup_github_token() -> Optional[str]:
    """Ensure GH_TOKEN and GITHUB_TOKEN are set in environment from Git Credential Manager"""
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        os.environ["GH_TOKEN"] = token
        os.environ["GITHUB_TOKEN"] = token
        return token
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
    return None

def run_cmd(cmd: list, cwd=PROJECT_ROOT, check=True):
    print(f">> Executing: {' '.join(cmd)}")
    res = subprocess.run(cmd, cwd=cwd, shell=True if sys.platform == 'win32' else False)
    if check and res.returncode != 0:
        raise RuntimeError(f"Command failed: {' '.join(cmd)} (Exit code: {res.returncode})")
    return res.returncode

def ensure_git_remote():
    """Ensure origin points to the target repository abdallahashour98/Rill-Phone.git"""
    try:
        res = subprocess.run(["git", "remote", "-v"], cwd=PROJECT_ROOT, capture_output=True, text=True)
        if "origin" in res.stdout:
            for line in res.stdout.splitlines():
                if line.startswith("origin") and "(push)" in line:
                    if CODE_REPO_URL not in line:
                        print(f"[INFO] Updating git origin remote to: {CODE_REPO_URL}")
                        run_cmd(["git", "remote", "set-url", "origin", CODE_REPO_URL])
                    break
        else:
            run_cmd(["git", "remote", "add", "origin", CODE_REPO_URL])
    except Exception as e:
        print(f"[WARNING] Remote configuration: {e}")

def create_github_release_api(token: str, tag_name: str, title: str, notes: str, asset_path: Path):
    """Fallback release creator via direct GitHub REST API (no gh CLI dependency)"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "Rill-Phone-Release-Tool"
    }

    # 1. Check if release already exists
    release_id = None
    upload_url = None
    try:
        check_req = urllib.request.Request(
            f"https://api.github.com/repos/{REPO_NAME}/releases/tags/{tag_name}",
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

    # 2. Create release if not exists
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
            f"https://api.github.com/repos/{REPO_NAME}/releases",
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST"
        )
        with urllib.request.urlopen(create_req) as resp:
            data = json.loads(resp.read().decode())
            release_id = data.get("id")
            upload_url = data.get("upload_url", "").split("{")[0]
            print(f"SUCCESS: Created release {tag_name} (ID: {release_id})")

    # 3. Upload asset
    if upload_url and asset_path.exists():
        filename = asset_path.name
        file_size = asset_path.stat().st_size
        print(f">> Uploading APK asset: {filename} ({format_size(file_size)})...")

        # Check existing assets to avoid duplicates or delete existing with same name
        try:
            assets_req = urllib.request.Request(
                f"https://api.github.com/repos/{REPO_NAME}/releases/{release_id}/assets",
                headers=headers
            )
            with urllib.request.urlopen(assets_req) as resp:
                existing_assets = json.loads(resp.read().decode())
                for ast in existing_assets:
                    if ast.get("name") == filename:
                        del_id = ast.get("id")
                        print(f">> Replacing existing asset (ID: {del_id})...")
                        del_req = urllib.request.Request(
                            f"https://api.github.com/repos/{REPO_NAME}/releases/assets/{del_id}",
                            headers=headers,
                            method="DELETE"
                        )
                        urllib.request.urlopen(del_req)
        except Exception as e:
            print(f"[INFO] Asset check: {e}")

        # Upload binary stream
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
    parser.add_argument("--version", help="New version string (e.g. 0.5.6). Automatically bumped if not specified")
    parser.add_argument("--code", type=int, help="New build/version code (e.g. 56). Automatically bumped if not specified")
    parser.add_argument("--skip-build", action="store_true", help="Skip APK build and use latest existing built APK")
    parser.add_argument("--skip-push", action="store_true", help="Skip committing and pushing to GitHub")
    args = parser.parse_args()

    print_header("Rill-Phone - Automated One-Click Release Tool")

    # 1. Determine current and new version
    curr_version, curr_code = get_current_version()
    print(f"[INFO] Current project version: v{curr_version} (versionCode: {curr_code})")

    if args.version:
        new_version = args.version
    else:
        parts = curr_version.split('.')
        while len(parts) < 3:
            parts.append('0')
        parts[-1] = str(int(parts[-1]) + 1)
        new_version = '.'.join(parts)

    new_code = args.code if args.code else (curr_code + 1)

    notes = args.notes
    if not notes:
        print("\n>> Enter release notes (Changelog):")
        print("   (e.g.: Bug fixes, UI enhancements, Arabic localization)")
        try:
            user_input = input("   Notes: ").strip()
        except EOFError:
            user_input = ""
        notes = user_input if user_input else f"General improvements, UI enhancements, and bug fixes for v{new_version}."

    print(f"\n[TARGET] Releasing version: v{new_version} (versionCode: {new_code})")
    print(f"[NOTES]:\n   {notes}\n")

    # 2. Update app/build.gradle
    update_gradle_version(new_version, new_code)

    # 3. Build release APK
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    new_apk_target = DIST_DIR / f"rill-phone-{new_code}-foss-release.apk"
    built_apk_path = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "foss" / "release" / f"rill-phone-{new_code}-foss-release.apk"

    if not args.skip_build:
        print_header("Building Android Release APK (assembleFossRelease)...")
        gradle_cmd = ["cmd.exe", "/c", "gradlew.bat", "assembleFossRelease"] if sys.platform == 'win32' else ["./gradlew", "assembleFossRelease"]
        run_cmd(gradle_cmd)

        if not built_apk_path.exists():
            # Check for any APK in release directory
            candidates = list((PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "foss" / "release").glob("*.apk"))
            if candidates:
                built_apk_path = candidates[0]
            else:
                raise FileNotFoundError(f"Built release APK not found at: {built_apk_path}")

        shutil.copy2(built_apk_path, new_apk_target)
        # Also copy with friendly name
        friendly_apk_target = DIST_DIR / f"Rill-Phone_v{new_version}.apk"
        shutil.copy2(built_apk_path, friendly_apk_target)
        print(f"SUCCESS: Copied APK to: {new_apk_target.name} ({format_size(new_apk_target.stat().st_size)})")
    else:
        print("[INFO] Skipped APK build as requested.")
        if not new_apk_target.exists():
            candidates = list(DIST_DIR.glob("*.apk")) + list((PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "foss" / "release").glob("*.apk"))
            if candidates:
                shutil.copy2(candidates[0], new_apk_target)
            else:
                raise FileNotFoundError(f"No release APK found in dist/ or build outputs!")

    apk_sha = compute_sha256(new_apk_target)
    print(f"[INFO] APK SHA-256: {apk_sha}")

    # 4. Git Commit and Push to Repository
    if not args.skip_push:
        ensure_git_remote()

        print_header("Saving & Pushing Code to GitHub Repository...")
        run_cmd(["git", "config", "user.name", "abdallahashour98"])
        run_cmd(["git", "config", "user.email", "abdallahashour98@users.noreply.github.com"])

        run_cmd(["git", "add", "-u"])
        run_cmd(["git", "add", "tools/", "release.bat", ".gitignore"])

        clean_notes = notes.replace("&", "and").replace('"', "'")
        commit_msg = f"Release v{new_version}: {clean_notes}"
        run_cmd(["git", "commit", "-m", commit_msg], check=False)

        print(">> Pushing code to origin/main...")
        run_cmd(["git", "push", "-u", "origin", "main"])
        print("SUCCESS: Code committed and pushed to repository.")

        # 5. Create official GitHub Release
        tag_name = f"v{new_version}"
        print_header(f"Publishing Official GitHub Release {tag_name}...")

        token = setup_github_token()
        release_published = False

        if token:
            env = os.environ.copy()
            env["GH_TOKEN"] = token
            env["GITHUB_TOKEN"] = token

            # Try gh CLI first
            try:
                gh_cmd = [
                    "gh", "release", "create", tag_name, str(new_apk_target),
                    "--repo", REPO_NAME,
                    "--title", f"Rill-Phone v{new_version}",
                    "--notes", notes,
                    "--latest"
                ]
                res = subprocess.run(gh_cmd, cwd=PROJECT_ROOT, env=env, capture_output=True, text=True)
                if res.returncode == 0:
                    release_published = True
                    print(f"SUCCESS: GitHub Release created via GitHub CLI!")
                else:
                    print(f"[INFO] gh CLI exited with code {res.returncode}. Using GitHub REST API directly...")
            except Exception as e:
                print(f"[INFO] GitHub CLI not active ({e}). Using GitHub REST API directly...")

            # Fallback to direct GitHub REST API
            if not release_published:
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
                    print(f"[WARNING] API Release upload error: {e}")
        else:
            print("[WARNING] GitHub token not found in git credentials. Skipped automated GitHub Release asset upload.")

        if release_published:
            print(f"\nSUCCESS: GitHub Release {tag_name} is LIVE!")
            print(f"URL: https://github.com/{REPO_NAME}/releases/tag/{tag_name}")

    print_header("SUCCESS: Release process completed successfully!")
    print(f"New version v{new_version} (Build {new_code}) is ready.")
    print("=" * 65 + "\n")

if __name__ == "__main__":
    main()
