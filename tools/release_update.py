#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rill-Phone Automated One-Click Fast Release & Update Tool
Follows the PhotoApp / Reminder updater architecture:
1. Version bump in app/build.gradle (versionCode & versionName)
2. Release APK build (gradlew assembleFossRelease)
3. Checksum calculation (SHA-256) and update_manifest.json generation
4. Syncing ONLY update files (update_manifest.json & README.md) to GitHub updates repository (https://github.com/abdallahashour98/Rill-Phone.git)
5. Creating and publishing official GitHub Release with APK asset
* NOTE: Source code of the project is NEVER pushed to this repository!
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
UPDATES_DIR = DIST_DIR / "updates"
TOOLS_DIR = PROJECT_ROOT / "tools"

# Dedicated Updates Channel on GitHub
UPDATES_REPO_URL = "https://github.com/abdallahashour98/Rill-Phone.git"
UPDATES_REPO_NAME = "abdallahashour98/Rill-Phone"
BASE_DOWNLOAD_URL = f"https://github.com/{UPDATES_REPO_NAME}/releases/latest/download"

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
TOKEN_FILE = Path.home() / ".rill_phone_github_token"

def setup_github_token() -> Optional[str]:
    """Ensure GH_TOKEN and GITHUB_TOKEN are set in environment from Git Credential Manager or local token file"""
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        os.environ["GH_TOKEN"] = token
        os.environ["GITHUB_TOKEN"] = token
        return token

    if TOKEN_FILE.exists():
        try:
            token = TOKEN_FILE.read_text(encoding="utf-8").strip()
            if token:
                os.environ["GH_TOKEN"] = token
                os.environ["GITHUB_TOKEN"] = token
                return token
        except Exception:
            pass

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

    print_header("GitHub Personal Access Token Required")
    print("GitHub requires a Personal Access Token (PAT) with 'repo' scope to upload releases.")
    print("You can generate one at: https://github.com/settings/tokens")
    try:
        token = input("\n🔑 Please paste your GitHub Personal Access Token (PAT): ").strip()
        if token:
            TOKEN_FILE.write_text(token, encoding="utf-8")
            os.environ["GH_TOKEN"] = token
            os.environ["GITHUB_TOKEN"] = token
            print("Saved token locally to ~/.rill_phone_github_token")
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

def sync_to_updates_repo(new_version: str, notes: str, token: Optional[str] = None):
    """Push update_manifest.json and README.md ONLY to GitHub updates repository (isolated from source code)"""
    temp_dir = DIST_DIR / ".updates_git"
    print_header(f"Syncing updates repository ({UPDATES_REPO_NAME})...")

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

    # 1. Copy update_manifest.json
    manifest_src = UPDATES_DIR / "update_manifest.json"
    if manifest_src.exists():
        shutil.copy2(manifest_src, temp_dir / "update_manifest.json")
        print("Copied update_manifest.json to updates repository.")

    # 2. Ensure clean README.md exists
    readme_content = f"""# Rill-Phone Official Releases & Updates

This repository serves official updates, manifests, and APK releases for **Rill-Phone**.

- Latest Manifest: [update_manifest.json](update_manifest.json)
- Downloads & Releases: [Releases Section](https://github.com/{UPDATES_REPO_NAME}/releases)
"""
    with open(temp_dir / "README.md", 'w', encoding='utf-8') as f:
        f.write(readme_content)

    # 3. Copy any patch files if present
    for patch in UPDATES_DIR.glob("*.patch"):
        shutil.copy2(patch, temp_dir / patch.name)
        print(f"Copied patch: {patch.name}")

    # 4. Commit and push ONLY updates files to updates repo
    clean_notes = notes.replace("&", "and").replace('"', "'")
    commit_msg = f"Update manifest for v{new_version}: {clean_notes}"
    run_cmd(["git", "config", "user.name", "abdallahashour98"], cwd=temp_dir)
    run_cmd(["git", "config", "user.email", "abdallahashour98@users.noreply.github.com"], cwd=temp_dir)
    run_cmd(["git", "add", "."], cwd=temp_dir)
    run_cmd(["git", "commit", "-m", commit_msg], cwd=temp_dir, check=False)
    run_cmd(["git", "push", "-u", "origin", "main"], cwd=temp_dir)
    print("SUCCESS: Update files pushed to updates repository!")

def create_github_release_api(token: str, tag_name: str, title: str, notes: str, asset_path: Path):
    """Direct GitHub REST API release creator (fallback if gh CLI is missing or unauthenticated)"""
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
    parser.add_argument("--version", help="New version string (e.g. 0.5.6). Automatically bumped if not specified")
    parser.add_argument("--code", type=int, help="New build/version code (e.g. 56). Automatically bumped if not specified")
    parser.add_argument("--skip-build", action="store_true", help="Skip APK build and use latest existing built APK")
    parser.add_argument("--skip-push", action="store_true", help="Skip syncing updates and creating release on GitHub")
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
    UPDATES_DIR.mkdir(parents=True, exist_ok=True)

    new_apk_target = DIST_DIR / f"rill-phone-{new_code}-foss-release.apk"
    built_apk_path = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "foss" / "release" / f"rill-phone-{new_code}-foss-release.apk"

    if not args.skip_build:
        print_header("Building Android Release APK (assembleFossRelease)...")
        gradle_cmd = ["cmd.exe", "/c", "gradlew.bat", "assembleFossRelease"] if sys.platform == 'win32' else ["./gradlew", "assembleFossRelease"]
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
            candidates = list(DIST_DIR.glob("*.apk")) + list((PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "foss" / "release").glob("*.apk"))
            if candidates:
                shutil.copy2(candidates[0], new_apk_target)
            else:
                raise FileNotFoundError("No release APK found in dist/ or build outputs!")

    apk_sha = compute_sha256(new_apk_target)
    apk_size = new_apk_target.stat().st_size
    print(f"[INFO] APK SHA-256: {apk_sha}")

    # 4. Generate update_manifest.json
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
    print(f"SUCCESS: Generated update manifest at: {manifest_path}")

    # 5. Automated Upload of UPDATE FILES ONLY (Manifest + GitHub Release Asset)
    if not args.skip_push:
        token = setup_github_token()

        # a) Sync update files only to updates repo
        try:
            sync_to_updates_repo(new_version, notes, token)
        except Exception as e:
            print(f"[WARNING] Note while syncing updates repo: {e}")

        # b) Create official GitHub Release
        tag_name = f"v{new_version}"
        print_header(f"Publishing Official GitHub Release {tag_name} on {UPDATES_REPO_NAME}...")

        release_published = False

        if token:
            env = os.environ.copy()
            env["GH_TOKEN"] = token
            env["GITHUB_TOKEN"] = token

            try:
                gh_cmd = [
                    "gh", "release", "create", tag_name, str(new_apk_target), str(manifest_path),
                    "--repo", UPDATES_REPO_NAME,
                    "--title", f"Rill-Phone v{new_version}",
                    "--notes", notes,
                    "--latest"
                ]
                res = subprocess.run(gh_cmd, cwd=PROJECT_ROOT, env=env, capture_output=True, text=True)
                if res.returncode == 0:
                    release_published = True
                    print(f"SUCCESS: GitHub Release created via GitHub CLI!")
                else:
                    print(f"[INFO] gh CLI returned {res.returncode}. Using GitHub REST API directly...")
            except Exception as e:
                print(f"[INFO] GitHub CLI not active ({e}). Using GitHub REST API directly...")

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
            print(f"\nSUCCESS: GitHub Release {tag_name} is LIVE and Published!")
            print(f"URL: https://github.com/{UPDATES_REPO_NAME}/releases/tag/{tag_name}")

    print_header("SUCCESS: Release process completed successfully!")
    print(f"New version v{new_version} (Build {new_code}) is ready for distribution.")
    print("=" * 65 + "\n")

if __name__ == "__main__":
    main()
