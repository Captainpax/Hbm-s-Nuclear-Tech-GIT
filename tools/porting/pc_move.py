"""Package and verify the selected, stopped QA worlds for a PC migration.

The source belongs in Git. This tool preserves ignored QA data as draft-release
assets, removes local credentials, and refuses to overwrite different files on
restore. It never launches Minecraft or seeds an existing world.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "Captainpax/Hbm-s-Nuclear-Tech-GIT"
BRANCH = "captainpax/forge-1.20.1"
WORLDS = (
    "run-survival-client/saves/HBM Survival Progression",
    "run-checkpoint-client/saves/HBM Survival Checkpoint QA",
    "run-multiplayer/survival-checkpoint",
    "run/saves/HBM Ore QA",
    "run/saves/HBM Port Visual QA",
)
PROFILES = (
    "run", "run-survival-client", "run-checkpoint-client", "run-multiplayer",
    "run-multiplayer-client", "run-multiplayer-tests", "run-gneiss-seed",
    "run-ore-config", "run-tagged-inputs", "run-client-smoke",
)
REFERENCE = "run-checkpoint-client/evidence/pc-move-reference"
VENDOR_NAMES = {"joined.srg", "fields.csv", "methods.csv"}
SKIP_SUFFIXES = {".jar", ".class", ".pyc", ".hprof", ".dmp", ".tmp"}
TEXT_SUFFIXES = {".json", ".log", ".txt", ".md", ".csv", ".xml", ".html",
                 ".toml", ".properties", ".mcfunction", ".py", ".java", ".ps1", ".sh"}


def git(*args: str) -> str:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT, text=True).strip()


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="")


def safe_relative(value: str) -> PurePosixPath:
    path = PurePosixPath(value)
    if not value or "\\" in value or ":" in value or path.is_absolute() or any(p in ("", "..", ".") for p in value.split("/")):
        raise ValueError(f"Unsafe backup path: {value}")
    return path


def local_secrets() -> set[bytes]:
    secrets = set()
    for profile in PROFILES:
        path = ROOT / profile / "server.properties"
        if path.is_file():
            for line in path.read_text(encoding="utf-8").splitlines():
                if line.startswith("rcon.password=") and len(line.partition("=")[2]) >= 8:
                    secrets.add(line.partition("=")[2].encode())
    return secrets


def sanitize(data: bytes, secrets: set[bytes]) -> bytes:
    for secret in secrets:
        data = data.replace(secret, b"[REDACTED]")
    data = re.sub(rb"(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})", b"[REDACTED]", data)
    data = re.sub(rb'(?im)^(\s*(?:rcon\.password|password|access[_-]?token|refresh[_-]?token|api[_-]?key|client[_-]?secret)\s*=)[^\r\n]+',
                  lambda m: m[1] + b"[REDACTED]", data)
    data = re.sub(rb'(?i)("(?:accessToken|access_token|refreshToken|refresh_token|client_secret|api_key|authorization)"\s*:\s*")[^"]+(")',
                  lambda m: m[1] + b"[REDACTED]" + m[2], data)
    data = re.sub(rb"(?i)(--accessToken[\s,='\"]+)([^\s,'\"\]\)]+)",
                  lambda m: m[0] if m[2].startswith(b"[REDACTED") else m[1] + b"[REDACTED]", data)
    return data


def payload(path: Path, secrets: set[bytes]) -> tuple[bytes, bytes]:
    if path.is_symlink():
        raise RuntimeError(f"Refusing to archive a symbolic link: {path.relative_to(ROOT)}")
    before = path.stat()
    original = path.read_bytes()
    after = path.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise RuntimeError(f"File changed during backup; stop its writer: {path.relative_to(ROOT)}")
    stored = original
    if path.name.endswith(".log.gz"):
        raw = gzip.decompress(original)
        cleaned = sanitize(raw, secrets)
        if raw != cleaned:
            stored = gzip.compress(cleaned, mtime=0)
    elif path.suffix.lower() in TEXT_SUFFIXES:
        stored = sanitize(original, secrets)
    if any(secret in stored for secret in secrets):
        raise RuntimeError(f"Credential remains in payload: {path.relative_to(ROOT)}")
    return original, stored


def select_files() -> tuple[dict[str, Path], dict[str, Path], list[str]]:
    worlds, evidence, excluded = {}, {}, []
    for name in WORLDS:
        directory = ROOT / name
        if not (directory / "level.dat").is_file():
            raise RuntimeError(f"Selected world is missing: {name}")
        for path in directory.rglob("*"):
            if path.is_file():
                relative = path.relative_to(ROOT).as_posix()
                if path.name == "session.lock":
                    excluded.append(relative)
                else:
                    worlds[relative] = path
    for name in PROFILES:
        profile = ROOT / name
        if not profile.is_dir():
            continue
        for folder in ("evidence", "screenshots", "logs", "crash-reports", "reports", "config", "defaultconfigs"):
            for path in (profile / folder).rglob("*"):
                if not path.is_file():
                    continue
                relative = path.relative_to(ROOT).as_posix()
                if path.suffix.lower() in SKIP_SUFFIXES or path.name in VENDOR_NAMES or "__pycache__" in path.parts:
                    excluded.append(relative)
                else:
                    evidence[relative] = path
        for path in profile.iterdir():
            if path.is_file() and (path.suffix == ".log" or path.name.startswith("hbm-") and path.suffix == ".json"
                                   or path.name in ("options.txt", "eula.txt")):
                evidence[path.relative_to(ROOT).as_posix()] = path
        # Preserve settings as reference copies. Actual RCON settings must be
        # regenerated on the new PC rather than extracted into server startup.
        for path in [profile / "server.properties", *profile.glob("*/serverconfig/*.toml")]:
            if path.is_file() and path.relative_to(ROOT).as_posix() not in worlds:
                relative = path.relative_to(ROOT).as_posix()
                evidence[f"{REFERENCE}/{relative}"] = path
    if set(worlds) & set(evidence):
        raise RuntimeError("Duplicate paths between world and evidence archives")
    return worlds, evidence, excluded


def restoration_text(commit: str, tag: str) -> str:
    return f"""# Restore the HBM port on the new PC

Backup date: {tag.split('pc-move-')[1][:10]}. Source commit: `{commit}`.
Repository: https://github.com/{REPOSITORY}
Branch: `{BRANCH}`. This is an unpublished migration backup of a development mod.

1. Install Git, GitHub CLI, Python 3.11 or newer, and a Java 17 JDK. Sign in to
   GitHub CLI as Captainpax with `gh auth login` to access the draft assets.
2. Clone the port branch, then enter the clone:

   ```text
   git clone --single-branch --branch {BRANCH} https://github.com/{REPOSITORY}.git
   cd Hbm-s-Nuclear-Tech-GIT
   git rev-parse HEAD
   ```

   The saved commit is `{commit}`. If the branch later advances, use
   `git switch --detach {commit}` to reproduce this snapshot.
3. Download and verify the draft assets in a separate directory:

   ```text
   gh release download {tag} --repo {REPOSITORY} --dir ../hbm-backup
   python tools/porting/pc_move.py verify --backup ../hbm-backup --extract .
   ```

   Verification checks asset hashes, ZIP contents and every restored file.
   Extraction refuses to overwrite different files. Use a fresh clone for an
   exact restore. Worlds retain their original relative run-directory paths.
4. Build with `gradlew.bat build --no-daemon` on Windows or
   `./gradlew build --no-daemon` on other systems. Point JAVA_HOME at Java 17.
   Forge stays pinned to 1.20.1-47.4.10; Gradle downloads its dependencies.
5. To resume a Survival walkthrough, read the session from the restored
   `run-survival-client/hbm-survival-session.json` or
   `run-checkpoint-client/hbm-survival-session.json`, then launch:

   ```text
   gradlew.bat runSurvivalClient -PhbmSurvivalDriver=<session> -PhbmSurvivalResume --no-daemon
   gradlew.bat runSurvivalClient -PhbmCheckpointDriver=<session> -PhbmCheckpointResume --no-daemon
   ```

   Choose the matching world/profile. Existing sessions are preserved; do not
   run `prepare` again, invoke the supply function, or reseed restored worlds.
   The driver pauses these walkthroughs on entry.
6. For the two-client fixture, run `python tools/porting/multiplayer_smoke.py prepare`
   to recreate its loopback-only configuration with a new RCON password. The old
   server settings are sanitized reference copies under
   `{REFERENCE}`. Then run `python tools/porting/client_driver.py prepare` to make
   a fresh two-client session before launching either client. This clears old
   command inboxes. Do not invoke the seed function unless intentionally starting
   a fresh fixture; the restored players retain the previous round's test plates.

The archives include both Survival worlds, the multiplayer checkpoint world,
the Ore QA and Port Visual QA worlds, screenshots, evidence and logs. Generated
GameTest/world-generation worlds, Gradle/build caches and downloaded Minecraft
libraries are excluded. The source and historical references are in GitHub.
The included JAR remains `1.20.1-0.1.0-dev`; full source parity is still open.
"""


def package(output: Path, date: str, validation: Path | None) -> None:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
        raise ValueError("Use an explicit YYYY-MM-DD backup date")
    if git("branch", "--show-current") != BRANCH or git("remote", "get-url", "--push", "origin") != f"https://github.com/{REPOSITORY}.git":
        raise RuntimeError("Package only the intended Captainpax port branch")
    if git("status", "--porcelain=v1", "--untracked-files=all"):
        raise RuntimeError("Commit the reviewed source work before packaging")
    output.mkdir(parents=True, exist_ok=True)
    if (output / "manifest.json").exists():
        raise RuntimeError("Preserve the existing package; choose a fresh output directory")
    commit = git("rev-parse", "HEAD")
    tag = f"captainpax/pc-move-{date}-{commit[:12]}"
    worlds, evidence, excluded = select_files()
    secrets = local_secrets()
    entries = []
    artifacts = []
    for archive_name, files in (("hbm-qa-worlds.zip", worlds), ("hbm-qa-evidence.zip", evidence)):
        with zipfile.ZipFile(output / archive_name, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for relative, path in sorted(files.items()):
                safe_relative(relative)
                original, stored = payload(path, secrets)
                archive.writestr(relative, stored)
                entries.append(dict(archive=archive_name, path=relative, source=path.relative_to(ROOT).as_posix(),
                                    bytes=len(stored), sha256=digest(stored), originalSha256=digest(original),
                                    sanitized=stored != original))
        artifacts.append(archive_name)
    jar = ROOT / "build/libs/hbm-1.20.1-0.1.0-dev.jar"
    if not jar.is_file():
        raise RuntimeError("Build the development JAR first")
    shutil.copy2(jar, output / jar.name)
    artifacts.append(jar.name)
    (output / "RESTORE.md").write_text(restoration_text(commit, tag), encoding="utf-8", newline="")
    artifacts.append("RESTORE.md")
    if validation:
        raw = validation.read_bytes()
        (output / "VALIDATION.log").write_bytes(sanitize(raw, secrets))
        artifacts.append("VALIDATION.log")
    manifest = dict(format=1, repository=REPOSITORY, branch=BRANCH, commit=commit, backupDate=date, tag=tag,
                    worlds=list(WORLDS), policy="Selected QA saves/evidence; regenerable worlds, caches and credentials excluded",
                    excludedFiles=sorted(set(excluded)), entries=entries,
                    artifacts=[dict(name=name, bytes=(output/name).stat().st_size, sha256=file_digest(output/name)) for name in artifacts])
    write_json(output / "manifest.json", manifest)
    artifacts.append("manifest.json")
    (output / "SHA256SUMS").write_text("".join(f"{file_digest(output/name)}  {name}\n" for name in sorted(artifacts)), encoding="ascii")
    verify(output, None)
    print(json.dumps(dict(commit=commit, tag=tag, files=len(entries), sanitizedFiles=sum(e["sanitized"] for e in entries),
                          output=str(output), archiveMiB=round(sum((output/n).stat().st_size for n in artifacts)/1048576, 2))))


def verify(backup: Path, extract: Path | None) -> None:
    manifest = json.loads((backup / "manifest.json").read_text(encoding="utf-8"))
    if manifest["format"] != 1 or manifest["repository"] != REPOSITORY or manifest["branch"] != BRANCH:
        raise ValueError("Unrecognized backup manifest")
    expected = {a["name"] for a in manifest["artifacts"]} | {"manifest.json"}
    checksums = {}
    for line in (backup / "SHA256SUMS").read_text(encoding="ascii").splitlines():
        checksum, name = line.split("  ", 1)
        if safe_relative(name).name != name or name in checksums or not re.fullmatch(r"[0-9a-f]{64}", checksum):
            raise ValueError("Unsafe or duplicate checksum entry")
        checksums[name] = checksum
        if file_digest(backup / name) != checksum:
            raise RuntimeError(f"Asset checksum mismatch: {name}")
    if set(checksums) != expected:
        raise ValueError("Asset list differs from the manifest")
    for asset in manifest["artifacts"]:
        if file_digest(backup / asset["name"]) != asset["sha256"] or (backup / asset["name"]).stat().st_size != asset["bytes"]:
            raise RuntimeError(f"Manifest artifact mismatch: {asset['name']}")
    seen = set()
    for archive_name in ("hbm-qa-worlds.zip", "hbm-qa-evidence.zip"):
        wanted = {entry["path"]:entry for entry in manifest["entries"] if entry["archive"] == archive_name}
        with zipfile.ZipFile(backup / archive_name) as archive:
            members = archive.infolist()
            if len(members) != len(wanted) or {m.filename for m in members} != set(wanted):
                raise ValueError(f"Unexpected or duplicate ZIP entries: {archive_name}")
            for member in members:
                if member.is_dir() or stat.S_ISLNK(member.external_attr >> 16):
                    raise ValueError("Only regular file payloads can be restored")
                relative = safe_relative(member.filename)
                if member.filename.casefold() in seen or member.filename.split("/", 1)[0] not in PROFILES:
                    raise ValueError("Duplicate or unexpected restore path")
                seen.add(member.filename.casefold())
                data = archive.read(member)
                entry = wanted[member.filename]
                if len(data) != entry["bytes"] or digest(data) != entry["sha256"]:
                    raise RuntimeError(f"Payload checksum mismatch: {member.filename}")
                if extract:
                    target = extract.joinpath(*relative.parts).resolve()
                    if not target.is_relative_to(extract.resolve()):
                        raise ValueError("Restore path escapes the destination")
                    if target.exists():
                        if not target.is_file() or file_digest(target) != entry["sha256"]:
                            raise RuntimeError(f"Refusing to overwrite a different file: {member.filename}")
                    else:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(data)
                    if file_digest(target) != entry["sha256"]:
                        raise RuntimeError(f"Restored checksum mismatch: {member.filename}")
    if len(seen) != len(manifest["entries"]):
        raise ValueError("Manifest entries were not all verified")
    print(f"Verified {len(checksums)} assets and {len(seen)} archived files" + ("; restored bytes match" if extract else ""))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("package")
    create.add_argument("--output", type=Path, required=True)
    create.add_argument("--date", required=True)
    create.add_argument("--validation", type=Path)
    check = commands.add_parser("verify")
    check.add_argument("--backup", type=Path, required=True)
    check.add_argument("--extract", type=Path)
    args = parser.parse_args()
    if args.command == "package":
        package(args.output.resolve(), args.date, args.validation)
    else:
        verify(args.backup.resolve(), args.extract.resolve() if args.extract else None)
