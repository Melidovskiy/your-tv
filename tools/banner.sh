#!/usr/bin/env bash
# Renders assets/banner.html into assets/banner-en.png and banner-ru.png (README headers, 1280×520 at 2x) and
# assets/social.png (link preview, 1280×640 at 2x) with headless Chrome. Needs Chrome or Chromium; set CHROME to its path if it's elsewhere.
set -euo pipefail
cd "$(dirname "$0")/.."

CHROME="${CHROME:-}"
if [ -z "$CHROME" ]; then
  for c in google-chrome chromium chromium-browser "/c/Program Files/Google/Chrome/Application/chrome.exe" \
           "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"; do
    if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then CHROME="$c"; break; fi
  done
fi
[ -n "$CHROME" ] || { echo "Chrome not found: set CHROME=/path/to/chrome"; exit 1; }

page="$(pwd)/assets/banner.html"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) page="$(cygpath -m "$page")"; out() { cygpath -m "$1"; } ;; *) out() { echo "$1"; } ;; esac

render() {   # query, height, output
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
    --default-background-color=00000000 --virtual-time-budget=8000 --window-size="1280,$2" \
    --screenshot="$(out "$(pwd)/$3")" "file:///$page$1" >/dev/null 2>&1
  echo "$3"
}
render "?lang=en" 520 assets/banner-en.png
render "?lang=ru" 520 assets/banner-ru.png
render "?kind=social" 640 assets/social.png
