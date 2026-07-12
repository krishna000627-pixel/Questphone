#!/data/data/com.termux/files/usr/bin/bash
# QuestPhone — The Indie Revers Push Script
# Run this from Termux AFTER unzipping questphone_the_indie_revers_fixed.zip
# to /sdcard/Download/ and copying it to $HOME/questphone_indie/
#
# Two CIs triggered on push:
#   CI 1 (android.yml)           — versioned APK archive (90-day retention)
#   CI 2 (fdroid-universal.yml)  — F-Droid universal APK in Fragment/fdroid/ (365-day)
set -e

TOKEN=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
USER=krishna000627-pixel
REPO=Questphone
ZIP_PATH=/sdcard/Download/questphone_the_indie_revers_fixed.zip
PROJ_DIR=$HOME/questphone_indie
KEYSTORE=$HOME/questphone.keystore
KEY_ALIAS=questphone
KEY_PASS=questphone123
STORE_PASS=questphone123

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║   QuestPhone — The Indie Revers                  ║"
echo "║   CI 1: Versioned APK archive                    ║"
echo "║   CI 2: F-Droid Universal → Fragment/fdroid/     ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# ── 1. Find zip ───────────────────────────────────────────────────────────────
echo "[1/6] Checking zip..."
if [ ! -f "$ZIP_PATH" ]; then
  # Also try old name
  OLD=/sdcard/Download/questphone_the_indie_revers.zip
  if [ -f "$OLD" ]; then
    ZIP_PATH=$OLD
    echo "     Using: $OLD"
  else
    echo "ERROR: Move questphone_the_indie_revers_fixed.zip to /sdcard/Download/"
    exit 1
  fi
fi
echo "     OK: $ZIP_PATH"

# ── 2. Clean old build ────────────────────────────────────────────────────────
echo "[2/6] Cleaning..."
pkill -9 git 2>/dev/null || true
rm -rf "$PROJ_DIR" "$HOME/.git" "$HOME/questphone_v13" "$HOME/questphone_indie_old" 2>/dev/null || true
echo "     OK"

# ── 3. Extract ────────────────────────────────────────────────────────────────
echo "[3/6] Extracting..."
mkdir -p "$PROJ_DIR"
unzip -o "$ZIP_PATH" -d "$PROJ_DIR" > /dev/null
# Handle nested questphone/ folder from zip
GRADLE_DIR=$(find "$PROJ_DIR" -name "gradlew" -printf "%h\n" | head -n 1)
[ -z "$GRADLE_DIR" ] && echo "ERROR: gradlew not found in zip" && exit 1
cd "$GRADLE_DIR"
echo "     OK: $(pwd)"

# ── 4. Keystore ───────────────────────────────────────────────────────────────
echo "[4/6] Keystore..."
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=Krishna Tiwari,OU=QuestPhone,O=Personal,L=India,ST=India,C=IN" \
    2>/dev/null && echo "     Created keystore" || echo "     keytool unavailable — CI will build unsigned"
fi
[ -f "$KEYSTORE" ] && \
  KEYSTORE_B64=$(base64 "$KEYSTORE" 2>/dev/null || base64 -w 0 "$KEYSTORE" 2>/dev/null || echo "")
echo "     OK"

# ── 5. Write both CI workflows ────────────────────────────────────────────────
echo "[5/6] Writing CI workflows..."
mkdir -p .github/workflows

# CI 1 — Versioned archive (every push, 90-day retention)
cat > .github/workflows/android.yml << 'WORKFLOW1'
name: Android CI — Versioned Archive
on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
jobs:
  build-versioned:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
      with: { submodules: false }
    - uses: actions/setup-java@v4
      with: { java-version: '17', distribution: 'temurin', cache: gradle }
    - name: Build APK
      run: |
        chmod +x gradlew
        ./gradlew assembleFdroidRelease 2>/dev/null || \
        ./gradlew assembleRelease       2>/dev/null || \
        ./gradlew assembleDebug
    - name: Sign APK
      run: |
        APK=$(find . -name "*release*.apk" 2>/dev/null | grep -v unsigned | head -n 1)
        [ -z "$APK" ] && APK=$(find . -name "*.apk" 2>/dev/null | head -n 1)
        echo "Signing: $APK"
        if [ -n "${{ secrets.KEYSTORE_B64 }}" ]; then
          echo "${{ secrets.KEYSTORE_B64 }}" | base64 -d > ks.jks
          jarsigner -keystore ks.jks \
            -storepass "${{ secrets.STORE_PASS }}" \
            -keypass   "${{ secrets.KEY_PASS }}"   \
            -signedjar QuestPhone_v${{ github.run_number }}.apk \
            "$APK" questphone
          echo "APK_FILE=QuestPhone_v${{ github.run_number }}.apk" >> $GITHUB_ENV
        else
          cp "$APK" QuestPhone_v${{ github.run_number }}_unsigned.apk
          echo "APK_FILE=QuestPhone_v${{ github.run_number }}_unsigned.apk" >> $GITHUB_ENV
        fi
    - uses: actions/upload-artifact@v4
      with:
        name: QuestPhone-v${{ github.run_number }}
        path: ${{ env.APK_FILE }}
        retention-days: 90
WORKFLOW1

# CI 2 — F-Droid Universal → collected in Fragment/fdroid/ (365-day retention)
cat > .github/workflows/fdroid-universal.yml << 'WORKFLOW2'
name: Android CI — F-Droid Universal (Fragment)
on:
  push:
    branches: [ "main", "master" ]
jobs:
  build-fdroid:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
      with: { submodules: false }
    - uses: actions/setup-java@v4
      with: { java-version: '17', distribution: 'temurin', cache: gradle }
    - name: Build F-Droid Universal APK
      run: |
        chmod +x gradlew
        ./gradlew assembleFdroidRelease 2>/dev/null || \
        ./gradlew assembleRelease       2>/dev/null || \
        ./gradlew assembleDebug
    - name: Collect into Fragment/fdroid
      run: |
        mkdir -p Fragment/fdroid
        APK=$(find . -path "*/fdroid*" -name "*.apk" 2>/dev/null | grep -v "test" | head -n 1)
        [ -z "$APK" ] && APK=$(find . -name "*release*.apk" 2>/dev/null | grep -v unsigned | head -n 1)
        [ -z "$APK" ] && APK=$(find . -name "*.apk" 2>/dev/null | grep -v test | head -n 1)
        DEST="Fragment/fdroid/QuestPhone_fdroid_v${{ github.run_number }}.apk"
        cp "$APK" "$DEST"
        echo "APK_FILE=$DEST" >> $GITHUB_ENV
        echo "Collected: $DEST"
    - name: Sign F-Droid APK
      run: |
        if [ -n "${{ secrets.KEYSTORE_B64 }}" ]; then
          echo "${{ secrets.KEYSTORE_B64 }}" | base64 -d > ks.jks
          SIGNED="${{ env.APK_FILE %.apk }}_signed.apk"
          jarsigner -keystore ks.jks \
            -storepass "${{ secrets.STORE_PASS }}" \
            -keypass   "${{ secrets.KEY_PASS }}"   \
            -signedjar "$SIGNED" "${{ env.APK_FILE }}" questphone
          echo "APK_FILE=$SIGNED" >> $GITHUB_ENV
        fi
    - uses: actions/upload-artifact@v4
      with:
        name: QuestPhone-fdroid-v${{ github.run_number }}
        path: Fragment/fdroid/
        retention-days: 365
WORKFLOW2

echo "     OK: 2 workflows written"

# ── 6. Push to GitHub ─────────────────────────────────────────────────────────
echo "[6/6] Pushing to GitHub..."
mkdir -p Fragment/fdroid
echo "# F-Droid builds collected by CI 2" > Fragment/fdroid/.gitkeep

rm -rf .git
git init -q
git config user.email "build@questphone.app"
git config user.name "QuestPhone Build"
git remote add origin "https://$USER:$TOKEN@github.com/$USER/$REPO.git"
git branch -M main
git add .
git commit -q -m "The Indie Revers: 12 fixes — Jarvis audio, home btn, stranger mode whitelist, app rename+info, study quota guard, hidden apps sub-page, custom voice actions, JSON quest converter, WiFi sync fix, 2x CI, remove Pomodoro"
git push -u -f origin main

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║   ✅  PUSHED! Both CIs running on GitHub.        ║"
echo "║   https://github.com/$USER/$REPO/actions         "
echo "╚══════════════════════════════════════════════════╝"

# Print secrets if keystore was generated
if [ -n "$KEYSTORE_B64" ]; then
  echo ""
  echo "┌─ Add these to GitHub → Settings → Secrets ───────┐"
  echo "│  KEYSTORE_B64  (long base64 string below)         │"
  echo "│  STORE_PASS  = $STORE_PASS                        │"
  echo "│  KEY_PASS    = $KEY_PASS                          │"
  echo "└───────────────────────────────────────────────────┘"
  echo ""
  echo "KEYSTORE_B64:"
  echo "$KEYSTORE_B64"
fi
