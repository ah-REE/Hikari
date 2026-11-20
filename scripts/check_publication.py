#!/usr/bin/env python3
"""Check source-publication candidates without printing sensitive match values.

Default: existing tracked and non-ignored untracked files, without staging anything.
--staged: the exact index snapshot. --history: also inspect all reachable Git refs.
This is a focused leak-prevention check, not an exhaustive secret or legal audit.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parent.parent
PRIVATE_DIRS = {
    ".git", ".gradle", ".kotlin", ".idea", ".settings", ".playwright-mcp",
    ".publication-work", "artifacts", "build", "backups", "private", "__pycache__",
}
PRIVATE_SUFFIXES = {
    ".apk", ".aab", ".apks", ".log", ".hprof", ".heapdump", ".jks",
    ".keystore", ".pem", ".key", ".p12", ".pfx", ".bak", ".backup",
    ".mp4", ".webm", ".mov", ".mkv", ".pyc", ".pyo",
}
REQUIRED_FILES = {
    ".gitattributes", ".gitignore", ".github/workflows/android.yml", "README.md", "LICENSE.md",
    "THIRD_PARTY_NOTICES.md", "build.gradle.kts", "settings.gradle.kts",
    "gradle.properties", "gradlew", "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/hikari/app/MainActivity.kt",
    "app/src/main/assets/licenses/Hikari-LICENSE.md",
    "app/src/main/assets/licenses/THIRD_PARTY_NOTICES.md",
    "app/src/main/assets/licenses/NewPipeExtractor-GPL-3.0.txt",
    "app/src/main/assets/licenses/Manrope-OFL.txt",
    "app/src/main/assets/licenses/Nanojson-Apache-2.0.txt",
    "app/src/main/assets/licenses/Protobuf-BSD-3-Clause.txt",
    "app/src/main/assets/licenses/Lottie-Apache-2.0.txt",
    "app/src/main/assets/licenses/MPL-2.0.txt",
    "licenses/Nanojson-Apache-2.0.txt", "licenses/Protobuf-BSD-3-Clause.txt",
    "licenses/NewPipeExtractor-GPL-3.0.txt", "licenses/MPL-2.0.txt",
    "scripts/check_publication.py",
}
MAX_BYTES = 5 * 1024 * 1024
PATTERNS = {
    "private-key material": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----"),
    "GitHub token": re.compile(rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})"),
    "AWS access-key identifier": re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "Slack token": re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b"),
    "IP-bearing captured request": re.compile(rb"(?:[?&]|%26|\\u0026)(?:ip=|ip%3[dD])(?:\d{1,3}(?:\.|%2[eE])){3}\d{1,3}"),
    "captured visitor token": re.compile(rb"[\"'](?:VISITOR_DATA|visitorData)[\"']\s*:\s*[\"'][A-Za-z0-9_%=+/-]{25,}[\"']"),
    "machine-specific home path": re.compile(rb"/(?:home|Users)/(?!developer/|user/|username/|runner/|<[^>]+>/)[A-Za-z0-9_.-]+/"),
}


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-pager", *args], cwd=ROOT)


def path_issues(name: str) -> list[str]:
    path = PurePosixPath(name)
    issues = []
    if path.is_absolute() or ".." in path.parts or any(c in name for c in "\r\n\t"):
        issues.append("unsafe publication path")
    if PRIVATE_DIRS.intersection(path.parts):
        issues.append("private/generated directory")
    if path.suffix.lower() in PRIVATE_SUFFIXES or path.name == "local.properties":
        issues.append("private/generated file type")
    if path.name == ".env" or (path.name.startswith(".env.") and not path.name.endswith(".example")):
        issues.append("local environment configuration")
    if len(path.parts) == 1 and ("dump" in path.name.lower() or path.name.lower() in {
        "output.json", "search.html", "ytinitialdata.json", "test_yt.json",
    }) and path.suffix.lower() in {".json", ".html"}:
        issues.append("legacy upstream capture")
    return issues


def mode_issues(name: str, mode: str) -> list[str]:
    return ["Gradle wrapper must be executable (Git mode 100755)"] if name == "gradlew" and mode != "100755" else []


def required_issues(names: set[str]) -> list[tuple[str, str]]:
    return [(name, "required native source/license input missing") for name in sorted(REQUIRED_FILES - names)]


def content_issues(data: bytes) -> list[str]:
    if len(data) > MAX_BYTES:
        return ["file exceeds 5 MiB source-publication review limit"]
    if b"\0" in data[:8192]:
        return []
    return [label for label, pattern in PATTERNS.items() if pattern.search(data)]


def working_files():
    names = set(git("ls-files", "--cached", "--others", "--exclude-standard", "-z").split(b"\0"))
    for raw in sorted(names):
        if not raw:
            continue
        name = raw.decode("utf-8", "surrogateescape")
        path = ROOT / name
        if path.is_symlink():
            if not path.resolve().is_relative_to(ROOT):
                yield name, None, ["symlink escapes project"]
            else:
                yield name, str(path.readlink()).encode(), []
        elif path.is_file():
            data = path.read_bytes() if path.stat().st_size <= MAX_BYTES else b" " * (MAX_BYTES + 1)
            mode = "100755" if path.stat().st_mode & 0o111 else "100644"
            yield name, data, mode_issues(name, mode)
        # Missing tracked files represent pending deletions, not publication inputs.


def index_files():
    for entry in git("ls-files", "--stage", "-z").split(b"\0"):
        if not entry:
            continue
        metadata, raw = entry.split(b"\t", 1)
        mode, oid, stage = metadata.decode().split()
        name = raw.decode("utf-8", "surrogateescape")
        if stage != "0":
            yield name, None, ["unresolved index merge"]
        elif mode == "160000":
            yield name, None, ["submodule requires separate publication review"]
        else:
            yield name, git("cat-file", "blob", oid), mode_issues(name, mode)


def history_issues():
    # Include filenames from all changes, not just one representative name per blob.
    paths = git("log", "--all", "--format=", "--name-only", "--no-renames", "-z").split(b"\0")
    for raw in sorted(set(paths)):
        name = raw.lstrip(b"\n").decode("utf-8", "surrogateescape")
        if name:
            for label in path_issues(name):
                yield "history:" + name, label
    objects = {}
    for line in git("rev-list", "--objects", "--all").splitlines():
        oid, _, raw = line.partition(b" ")
        objects.setdefault(oid, raw.decode("utf-8", "surrogateescape"))
    process = subprocess.Popen(["git", "--no-pager", "cat-file", "--batch"], cwd=ROOT,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        for oid, name in objects.items():
            process.stdin.write(oid + b"\n")
            process.stdin.flush()
            header = process.stdout.readline().split()
            if len(header) != 3:
                raise RuntimeError("Could not read a reachable Git object")
            kind, size = header[1], int(header[2])
            data = process.stdout.read(size)
            if process.stdout.read(1) != b"\n" or len(data) != size:
                raise RuntimeError("Incomplete Git object read")
            if kind in {b"blob", b"commit", b"tag"}:
                location = name or kind.decode() + "-" + oid[:12].decode()
                for label in content_issues(data):
                    yield "history:" + location, label
    finally:
        process.stdin.close()
        process.wait()
    if process.returncode:
        raise RuntimeError("Git object scan failed")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="check the complete index instead of working files")
    parser.add_argument("--history", action="store_true", help="also scan all locally reachable Git refs")
    args = parser.parse_args()
    findings = set()
    names = set()
    count = 0
    for name, data, issues in index_files() if args.staged else working_files():
        names.add(name)
        count += 1
        findings.update((name, label) for label in path_issues(name) + issues)
        if data is not None:
            findings.update((name, label) for label in content_issues(data))
    findings.update(required_issues(names))
    if args.history:
        findings.update(history_issues())
    for name, label in sorted(findings):
        print("FAIL:", name, "—", label)
    if findings:
        print("Review required. Sensitive match values were not printed.")
        return 1
    print("PASS:", count, "publication files checked" + ("; reachable history checked." if args.history else "."))
    print("Ignored local files and unreachable/remote-only objects are outside this check. Review manually too.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print("Publication check could not complete:", error, file=sys.stderr)
        sys.exit(2)
