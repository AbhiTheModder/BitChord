#!/usr/bin/env bash
#
# Wraps the app image jpackage produced into an AppImage, the one Linux format
# Gradle cannot make itself.
#
# Usage: desktopApp/packaging/appimage.sh <app-image-dir> <out.AppImage> [appimagetool]
set -euo pipefail

APP_DIR_SRC=${1:?usage: appimage.sh <app-image-dir> <out.AppImage> [appimagetool]}
OUT=${2:?usage: appimage.sh <app-image-dir> <out.AppImage> [appimagetool]}
TOOL=${3:-appimagetool}

[ -x "$APP_DIR_SRC/bin/BitChord" ] || { echo "no launcher in $APP_DIR_SRC" >&2; exit 1; }

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
APPDIR="$STAGE/BitChord.AppDir"

mkdir -p "$APPDIR/usr"
cp -a "$APP_DIR_SRC/." "$APPDIR/usr/"

# The mount point differs every run, so nothing may be hard-coded.
cat > "$APPDIR/AppRun" <<'APPRUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/BitChord" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/bitchord.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=BitChord
GenericName=Music Player
Comment=Aesthetic YouTube Music client
Exec=BitChord
Icon=bitchord
Categories=AudioVideo;Audio;Player;
Terminal=false
StartupWMClass=com-music-bitchord-desktop-MainKt
DESKTOP

# appimagetool reads the icon by the desktop entry; the desktop reads .DirIcon.
cp "$ROOT/Logo.png" "$APPDIR/bitchord.png"
cp "$ROOT/Logo.png" "$APPDIR/.DirIcon"

mkdir -p "$(dirname "$OUT")"
# --appimage-extract-and-run: no FUSE on CI runners.
ARCH=x86_64 "$TOOL" --appimage-extract-and-run "$APPDIR" "$OUT"
echo "appimage: $OUT"
