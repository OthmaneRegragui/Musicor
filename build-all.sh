#!/usr/bin/env bash
# Builds every platform artifact this machine can produce and copies the
# results into a single ./dist folder (gitignored).
#
# - Android: release + debug APK
# - Desktop: packages for the current OS
#   (Linux: deb + real single-file AppImage + tar.gz, macOS: dmg,
#    Windows: EXE installer)
#
# Usage: ./build-all.sh

set -euo pipefail
cd "$(dirname "$0")"

DIST_DIR="dist"
GRADLEW="./gradlew"

echo "==> Detected host: $(uname -s)"

# Pick the desktop packaging tasks the host OS supports.
# shellcheck disable=SC2034
case "$(uname -s)" in
  Linux*)    DESKTOP_TASKS=":desktopApp:packageAppImage :desktopApp:packageDeb" ;;
  Darwin*)   DESKTOP_TASKS=":desktopApp:packageDmg" ;;
  MINGW*|MSYS*|CYGWIN*) DESKTOP_TASKS=":desktopApp:packageExe" ;;
  *)         DESKTOP_TASKS="" ; echo "Warning: unknown OS, desktop packages skipped." ;;
esac

# Linux only: convert the jpackage app-image directory into a single-file
# AppImage using appimagetool (downloaded on first use).
build_appimage() {
  local app_img="desktopApp/build/compose/binaries/main/app/com.regtho.musicor"
  [ -d "$app_img" ] || { echo "Warning: app image directory not found; AppImage skipped."; return 1; }
  command -v curl >/dev/null 2>&1 || { echo "Warning: curl not found; AppImage skipped."; return 1; }

  local tool="$PWD/build/tools/appimagetool"
  if [ ! -f "$tool" ]; then
    echo "==> Downloading appimagetool"
    mkdir -p "$(dirname "$tool")"
    curl -fL --http1.1 --retry 3 -C - -o "$tool" \
      "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage" || return 1
    chmod +x "$tool"
    # Make sure the download is complete and the tool actually runs
    # (interrupted downloads leave a file that looks like an ELF).
    APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --appimage-extract-and-run --version >/dev/null 2>&1 || {
      rm -f "$tool"
      echo "Warning: downloaded appimagetool is unusable; AppImage skipped."
      return 1
    }
  fi

  local stage="$PWD/build/appimage-stage"
  rm -rf "$stage"
  mkdir -p "$stage"
  cp -r "$app_img"/. "$stage"/

  # AppImage metadata: icon + desktop entry at the AppDir root.
  cp media/logo.png "$stage/musicor.png"
  ln -sf musicor.png "$stage/.DirIcon"
  cat > "$stage/musicor.desktop" <<'EOF'
[Desktop Entry]
Name=Musicor
Comment=Local music player
Exec=bin/com.regtho.musicor
Icon=musicor
Type=Application
Categories=AudioVideo;Audio;Player;
Terminal=false
EOF

  echo "==> Building AppImage"
  APPIMAGE_EXTRACT_AND_RUN=1 NO_STRIP=1 \
    "$tool" --appimage-extract-and-run -n "$stage" "$PWD/$DIST_DIR/musicor-linux-amd64.AppImage" || return 1
  echo "  -> $(ls "$DIST_DIR"/*.AppImage 2>/dev/null | head -1)"
}

echo "==> Building Android APKs (release + debug)"
"$GRADLEW" :androidApp:assembleRelease :androidApp:assembleDebug --console=plain

if [ -n "$DESKTOP_TASKS" ]; then
  echo "==> Building desktop packages"
  # Not fatal: hosts without jpackage/dpkg-deb will fail here; any artifacts
  # that were produced are still collected below.
  # Intended word splitting of the task list.
  # shellcheck disable=SC2086
  "$GRADLEW" $DESKTOP_TASKS --console=plain || echo "Warning: desktop packaging failed; keeping whatever was built."
fi

echo "==> Collecting artifacts into ./$DIST_DIR"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# Android APKs.
cp androidApp/build/outputs/apk/release/*release*.apk "$DIST_DIR/musicor-android.apk" 2>/dev/null || true
cp androidApp/build/outputs/apk/debug/*debug*.apk "$DIST_DIR/musicor-android-debug.apk" 2>/dev/null || true

# Desktop packages by format.
cp desktopApp/build/compose/binaries/main/deb/*.deb "$DIST_DIR/" 2>/dev/null || true
cp desktopApp/build/compose/binaries/main/exe/*.exe "$DIST_DIR/" 2>/dev/null || true
cp desktopApp/build/compose/binaries/main/dmg/*.dmg "$DIST_DIR/" 2>/dev/null || true

# Linux: `packageAppImage` produces a portable app-image *directory*; archive
# it into a single distributable file.
APP_DIR="desktopApp/build/compose/binaries/main/app"
if [ -d "$APP_DIR" ] && [ -n "$(ls -A "$APP_DIR")" ]; then
  tar -czf "$DIST_DIR/musicor-linux-amd64.tar.gz" -C "$APP_DIR" .
  echo "  -> $DIST_DIR/musicor-linux-amd64.tar.gz"
fi

# Linux only: also produce a real single-file AppImage from the app image.
case "$(uname -s)" in
  Linux*) build_appimage || echo "Warning: AppImage creation failed; the tar.gz archive is still available." ;;
esac

echo ""
echo "==> Done. Artifacts in ./$DIST_DIR:"
ls -lh "$DIST_DIR"