#!/usr/bin/env bash
# Prints the TV's free memory and what the home screens use, for before/after comparisons.
# Usage: ADB="adb -s <ip>:5555" tools/ram.sh   (leave the TV idle on its home screen for a few minutes first)
set -euo pipefail
ADB="${ADB:-adb}"
echo "date: $(date '+%Y-%m-%d %H:%M')"
$ADB shell cat /proc/meminfo | grep -E '^(MemTotal|MemFree|MemAvailable):'
summary="$($ADB shell dumpsys -t 60 meminfo -s)"
printf '%s\n' "$summary" | grep -E '^ *(Total RAM|Free RAM|Used RAM):'
printf '%s\n' "$summary" | grep -E '^ *[0-9,]+K: .*(io\.freemytv|tvlauncher|tvrecommendations|katniss|mitv\.tvhome)' \
  | sed 's/^ *//' | sort -u || true
