#!/bin/bash
# Compila el APK firmado y lo deja listo para subir al hosting.
#   ./build.sh                 → para sampcity.app
#   ./build.sh otro.dominio    → para otro dominio
# Necesita: JDK 17+, el SDK de Android (ANDROID_HOME) y android/keystore.properties (la llave de firma; NO se sube a git).
# Sube versionCode/versionName en app/build.gradle antes de cada versión nueva (si no, Android no deja actualizar).
set -euo pipefail
cd "$(dirname "$0")"
HOST="${1:-sampcity.app}"
[ -f keystore.properties ] || { echo "Falta android/keystore.properties (la llave de firma)"; exit 1; }
./gradlew --no-daemon :app:assembleRelease -PWEB_HOST="$HOST"
APK=app/build/outputs/apk/release/app-release.apk
BT="$(ls -d "${ANDROID_HOME:-$HOME/android-sdk}"/build-tools/* | sort -V | tail -1)"
"$BT/apksigner" verify --print-certs "$APK" | sed -n 1,3p
mkdir -p ../public/downloads
cp "$APK" ../public/downloads/SampCity-App.apk
VN=$(grep -m1 -E '^\s+versionName' app/build.gradle | sed -E "s/.*'([^']+)'.*/\1/"); VC=$(grep -m1 -E '^\s+versionCode' app/build.gradle | sed -E 's/[^0-9]*([0-9]+).*/\1/')
SHA=$(sha256sum ../public/downloads/SampCity-App.apk | cut -d' ' -f1)
printf '{\n  "versionName": "%s",\n  "versionCode": %s,\n  "sha256": "%s",\n  "host": "%s"\n}\n' "$VN" "$VC" "$SHA" "$HOST" > ../data/app.json
echo "Listo: public/downloads/SampCity-App.apk ($(du -h ../public/downloads/SampCity-App.apk | cut -f1)) · versión $VN ($VC)"
