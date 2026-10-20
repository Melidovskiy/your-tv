# Third-party notices

your-tv is a modified version of Free My TV. It includes or uses the following third-party work.

## Free My TV

<https://github.com/simbasjungles/free-my-tv> (imported at commit `895063f`). MIT License, Copyright (c) 2026 Free My TV
contributors. The full text is in [LICENSE](LICENSE). Free My TV's own credits also apply: it uses the TMDB API but is
not endorsed or certified by TMDB; weather data by Open-Meteo.com (CC BY 4.0).

## Bundled in this repository

| Work | Where | License |
|---|---|---|
| [Material Symbols](https://fonts.google.com/icons) by Google | `app/assets/symbols/` | Apache 2.0, text in `app/assets/symbols/LICENSE-material-symbols.txt` |
| [Inter](https://rsms.me/inter/) by Rasmus Andersson | `app/assets/fonts/` | SIL Open Font License 1.1, text in `app/assets/fonts/OFL.txt` |
| [Lucide](https://lucide.dev) icons `shield`, `wifi`, `settings` | `app/res/drawable/ic_*.xml` | ISC, text below |
| "Beech and ferns in Gullmarsskogen" by W.carter, Wikimedia Commons | `app/assets/wallpaper.jpg` | CC0 1.0 |

## Downloaded, not stored in the repository

| Work | When | License |
|---|---|---|
| [Xray-core](https://github.com/XTLS/Xray-core) via [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) v26.10.17 (`libv2ray.aar`) | `tools/fetch_libs.sh`, at build time; built into the APK | Mozilla Public License 2.0; source at the links |
| [JSON-java](https://github.com/stleary/JSON-java) 20240303 | `tools/fetch_libs.sh`, host tests only | Public domain |
| Wallpaper photos from Wikimedia Commons featured pictures | by the TV, at run time | Per photo (CC BY-SA 4.0/3.0, CC BY 4.0/3.0/2.0, public domain): author, license and source page of each in `app/assets/wallpapers.json` |

## Recommended, not included

- [TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt) by reisxd: the YouTube client the YouTube tile opens.

## Lucide license

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```
