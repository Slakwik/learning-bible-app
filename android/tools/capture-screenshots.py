#!/usr/bin/env python3
"""Capture the real native UI on a disposable Android emulator, without signing in."""

from pathlib import Path
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "docs" / "screenshots"
PACKAGE = "ru.spbchurch.biblelearning"
OUTPUT.mkdir(parents=True, exist_ok=True)


def adb(*args, binary=False):
    return subprocess.check_output(["adb", *args], timeout=45, text=not binary)


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/bible-screen.xml")
    source = adb("shell", "cat", "/sdcard/bible-screen.xml")
    (OUTPUT / "last-ui.xml").write_text(source, encoding="utf-8")
    return ET.fromstring(source)


def find(text, attempts=8):
    for _ in range(attempts):
        tree = hierarchy()
        for node in tree.iter("node"):
            if node.get("text") == text or node.get("content-desc") == text:
                return node
        time.sleep(1)
    raise RuntimeError(f"Expected visible control was not found: {text}")


def tap(text):
    node = find(text)
    left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(1)


def capture(name, expected):
    find(expected)
    time.sleep(1)
    image = adb("exec-out", "screencap", "-p", binary=True)
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Android did not return a PNG screenshot")
    (OUTPUT / name).write_bytes(image)
    print(f"Captured {name}", flush=True)


try:
    # This script is intended only for the fresh, disposable emulator in the workflow.
    adb("install", "-r", str(ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"))
    adb("shell", "wm", "size", "1080x2400")
    adb("shell", "wm", "density", "420")
    adb("shell", "cmd", "uimode", "night", "no")
    adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "clock", "-e", "hhmm", "0941")
    adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    capture("01-courses.png", "Открыть курс")
    tap("Открыть курс")
    capture("02-course.png", "Читать и отвечать")
    tap("Читать и отвечать")
    capture("03-lesson.png", "Оформление чтения")
    tap("Оформление чтения")
    capture("04-settings.png", "ЧТЕНИЕ И ОФОРМЛЕНИЕ")
    tap("Тема · Как в системе")
    tap("Тёмная")
    tap("Назад")
    capture("05-lesson-dark.png", "Оформление чтения")
    (OUTPUT / "capture.txt").write_text(
        "Real Android emulator screenshots; guest mode; no account or answers created.\n"
        "Android 15 / API 35, 1080×2400 px, density 420 dpi.\n"
        f"Source commit: {os.environ.get('GITHUB_SHA', 'local')}\n"
        f"Workflow run: {os.environ.get('GITHUB_RUN_ID', 'local')}\n",
        encoding="utf-8",
    )
finally:
    (OUTPUT / "logcat.txt").write_text(adb("logcat", "-d", "-t", "1000"), encoding="utf-8")
