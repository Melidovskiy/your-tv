#!/usr/bin/env bash
# Downloads the pinned third-party files that stay out of git, and checks each against its recorded sha256.
set -euo pipefail
cd "$(dirname "$0")/.."

fetch() {   # url, destination, sha256 (empty = print it so it can be recorded here)
  local url="$1" dest="$2" want="$3"
  if [ ! -f "$dest" ]; then
    mkdir -p "$(dirname "$dest")"
    curl -fsSL -o "$dest.part" "$url"
    mv "$dest.part" "$dest"
  fi
  local got; got="$(sha256sum "$dest" | cut -d' ' -f1)"
  if [ -z "$want" ]; then echo "$dest sha256 $got (record it in tools/fetch_libs.sh)"
  elif [ "$got" != "$want" ]; then echo "$dest: sha256 mismatch ($got)"; rm -f "$dest"; exit 1
  else echo "$dest ok"; fi
}

# org.json, for the host tests only (Android has its own).
fetch https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar tests/lib/json.jar 3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed

# Xray core for Android (AndroidLibXrayLite, gomobile): the VPN engine.
fetch https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.10.17/libv2ray.aar libs/libv2ray.aar fd6cb61fed873e465c3064327dffbcb22dc3fd78260d162ec9e868db2582495f
