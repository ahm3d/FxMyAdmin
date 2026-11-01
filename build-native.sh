#!/usr/bin/env bash
# Builds FxMyAdmin as a native Linux app with its own bundled Java runtime.
#   dist/FxMyAdmin/bin/FxMyAdmin             runnable app folder
#   dist/FxMyAdmin-<ver>-linux-x64.tar.gz    the same folder, zipped up to share
#   dist/fxmyadmin_<ver>_amd64.deb           installer (adds the app to the menu)
# Usage: ./build-native.sh [--no-deb]
set -euo pipefail
cd "$(dirname "$0")"

APP=FxMyAdmin
VERSION=$(mvn -q -B help:evaluate -Dexpression=project.version -DforceStdout)
ICON=packaging/icon.png

echo "==> Building jar"
mvn -q -B clean package

echo "==> Creating trimmed Java runtime"
rm -rf dist target/jpackage-input target/runtime
mkdir -p target/jpackage-input dist
cp target/fxmyadmin.jar target/jpackage-input/
MODULES=$(jdeps --print-module-deps --ignore-missing-deps --multi-release 21 target/fxmyadmin.jar)
# crypto.ec: TLS connections to MySQL; charsets: non-UTF8 server charsets
MODULES="$MODULES,jdk.crypto.ec,jdk.charsets"
jlink --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages \
      --compress=zip-6 --output target/runtime

COMMON=(
  --name "$APP"
  --app-version "$VERSION"
  --vendor "FxMyAdmin"
  --description "FxMyAdmin - a modern, native MySQL / MariaDB manager for Linux"
  --input target/jpackage-input
  --main-jar fxmyadmin.jar
  --main-class com.javamyadmin.Launcher
  --runtime-image target/runtime
  --icon "$ICON"
  --dest dist
)

echo "==> Creating app image"
jpackage --type app-image "${COMMON[@]}"
tar -C dist -czf "dist/$APP-$VERSION-linux-x64.tar.gz" "$APP"

if [[ "${1:-}" != "--no-deb" ]]; then
  echo "==> Creating .deb installer"
  jpackage --type deb "${COMMON[@]}" \
    --linux-package-name fxmyadmin \
    --linux-shortcut \
    --resource-dir packaging/linux \
    --linux-menu-group "Development;Database;" \
    --linux-app-category database \
    --linux-deb-maintainer "${MAINTAINER:-ahm3d <507116+ahm3d@users.noreply.github.com>}"
fi

echo "==> Done"
ls -lh dist
