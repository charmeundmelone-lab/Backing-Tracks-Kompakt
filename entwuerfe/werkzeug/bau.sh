#!/bin/sh
# Baut eine Mini-App ohne Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner (Debug-Schlüssel).
# Aufruf: werkzeug/bau.sh betrachter   bzw.   werkzeug/bau.sh tontest
# Ergebnis: <ordner>/<ordner>.apk (nicht im Repo, siehe .gitignore)
set -e
cd "$(dirname "$0")/../$1"
SDK="$LOCALAPPDATA/Android/Sdk"; BT="$SDK/build-tools/36.0.0"; JAR="$SDK/platforms/android-35/android.jar"
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; JBR="$JAVA_HOME/bin"; export PATH="$JBR:$PATH"
rm -rf out && mkdir -p out/classes out/dex
if [ -d assets ]; then "$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets -0 png -o out/basis.apk
else "$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o out/basis.apk; fi
"$JBR/javac" --release 8 -nowarn -classpath "$JAR" -d out/classes $(find src -name '*.java')
"$BT/d8.bat" --min-api 26 --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/basis.apk out/roh.apk
"$JBR/jar" --update --no-compress --no-manifest --file out/roh.apk -C out/dex classes.dex
"$BT/zipalign" -f 4 out/roh.apk out/a.apk
"$BT/apksigner.bat" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --out "$1.apk" out/a.apk
rm -rf out
echo "fertig: $(pwd)/$1.apk"
