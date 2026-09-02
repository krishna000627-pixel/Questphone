#!/data/data/com.termux/files/usr/bin/bash
# ╔══════════════════════════════════════════════════════════════╗
# ║  QuestPhone — Universal Push Script                          ║
# ║  Generates keystore ONCE, embeds it in CI so every build    ║
# ║  uses the SAME signing key → updates always work            ║
# ╚══════════════════════════════════════════════════════════════╝
set -e

TOKEN_FILE="$HOME/questphone_token"
GH_USER="krishna000627-pixel"
REPO="Questphone"
PROJ_DIR="$HOME/push_workspace"
KEYSTORE_FILE="$HOME/questphone_release.jks"
KEY_ALIAS="questphone"
KEY_PASS="QuestPhone2025"
STORE_PASS="QuestPhone2025"
KEYSTORE_B64_FILE="$HOME/questphone_ks.b64"

# ── Token ─────────────────────────────────────────────────────
if [ ! -f "$TOKEN_FILE" ]; then
  echo ""
  echo "First time: enter your GitHub personal access token"
  read -p "Token: " INPUT_TOKEN
  echo "$INPUT_TOKEN" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
fi
GH_TOKEN=$(cat "$TOKEN_FILE" | tr -d '[:space:]')

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  QuestPhone Universal Push                       ║"
echo "║  Repo: $GH_USER/$REPO                            ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# ── Find zip ──────────────────────────────────────────────────
echo "[1/6] Finding zip..."
ZIP_PATH=""
for candidate in "$1" "/sdcard/Download/$1"; do
  if [ -n "$candidate" ] && [ -f "$candidate" ]; then
    ZIP_PATH="$candidate"; break
  fi
done
if [ -z "$ZIP_PATH" ]; then
  # Auto-find newest zip in Downloads
  ZIP_PATH=$(ls -t /sdcard/Download/questphone*.zip 2>/dev/null | head -1)
fi
if [ -z "$ZIP_PATH" ]; then
  read -p "Zip path or filename: " Z
  [ -f "$Z" ] && ZIP_PATH="$Z" || ZIP_PATH="/sdcard/Download/$Z"
fi
[ ! -f "$ZIP_PATH" ] && echo "Not found: $ZIP_PATH" && exit 1
echo "     $ZIP_PATH"

# ── Generate keystore ONCE ────────────────────────────────────
echo "[2/6] Keystore..."
if [ ! -f "$KEYSTORE_FILE" ]; then
  echo "     Generating keystore (only done once — stored at $KEYSTORE_FILE)"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=Krishna Tiwari,OU=QuestPhone,O=Personal,L=India,ST=UP,C=IN" \
    2>/dev/null
  echo "     ✅ Keystore created: $KEYSTORE_FILE"
  echo "     ⚠  BACK THIS UP: cp $KEYSTORE_FILE /sdcard/Download/"
else
  echo "     Using existing keystore: $KEYSTORE_FILE"
fi

# Encode to base64 — stored in file so we can embed in CI
base64 "$KEYSTORE_FILE" > "$KEYSTORE_B64_FILE" 2>/dev/null || \
base64 -w 0 "$KEYSTORE_FILE" > "$KEYSTORE_B64_FILE" 2>/dev/null
KEYSTORE_B64=$(cat "$KEYSTORE_B64_FILE" | tr -d '\n')
echo "     Keystore encoded (${#KEYSTORE_B64} chars)"

# ── Extract zip ───────────────────────────────────────────────
echo "[3/6] Extracting..."
rm -rf "$PROJ_DIR" && mkdir -p "$PROJ_DIR"
unzip -o "$ZIP_PATH" -d "$PROJ_DIR" > /dev/null
GRADLE_DIR=$(find "$PROJ_DIR" -name "gradlew" -printf "%h\n" 2>/dev/null | head -1)
[ -z "$GRADLE_DIR" ] && echo "ERROR: gradlew not found" && exit 1
cd "$GRADLE_DIR"
echo "     $(pwd)"

# ── local.properties ──────────────────────────────────────────
cat > local.properties << LOCALEOF
KEYSTORE_FILE=questphone_release.jks
KEYSTORE_PASSWORD=$STORE_PASS
KEY_ALIAS=$KEY_ALIAS
KEY_PASSWORD=$KEY_PASS
KAI_API_KEY=
LOCALEOF

# Copy keystore into project (gitignored but used by CI inline)
cp "$KEYSTORE_FILE" app/questphone_release.jks 2>/dev/null || true

# ── Write CI workflow with EMBEDDED keystore ──────────────────
echo "[4/6] Writing CI workflow..."
mkdir -p .github/workflows Fragment/fdroid

# The keystore is base64-embedded directly in the workflow.
# This means EVERY CI run uses the EXACT SAME signing key.
# No secrets needed. No key mismatch. Updates always work.
cat > .github/workflows/build.yml << WFEOF
name: QuestPhone CI
on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Restore keystore from embedded base64
        run: |
          mkdir -p app
          # Keystore embedded at push time — same key every build = updates work
          cat > /tmp/ks.b64 << 'KS_EOF'
$KEYSTORE_B64
KS_EOF
          base64 --decode /tmp/ks.b64 > app/questphone_release.jks
          echo "Keystore restored ($(du -sh app/questphone_release.jks | cut -f1))"

      - name: Write local.properties
        run: |
          cat > local.properties << LEOF
          KEYSTORE_FILE=questphone_release.jks
          KEYSTORE_PASSWORD=$STORE_PASS
          KEY_ALIAS=$KEY_ALIAS
          KEY_PASSWORD=$KEY_PASS
          KAI_API_KEY=\${{ secrets.KAI_API_KEY }}
          LEOF

      - name: Build Universal APK
        run: |
          chmod +x gradlew
          ./gradlew assembleFdroidRelease

      - name: Rename APK
        run: |
          APK=\$(find app/build/outputs/apk -name "*.apk" | grep -v test | head -1)
          DEST="QuestPhone_v\${{ github.run_number }}_universal.apk"
          cp "\$APK" "\$DEST"
          SIZE=\$(du -sh "\$DEST" | cut -f1)
          echo "APK_PATH=\$DEST" >> \$GITHUB_ENV
          echo "APK_SIZE=\$SIZE" >> \$GITHUB_ENV

      - name: Upload
        uses: actions/upload-artifact@v4
        with:
          name: QuestPhone-v\${{ github.run_number }}-universal
          path: \${{ env.APK_PATH }}
          retention-days: 365

      - name: Summary
        run: |
          echo "## ✅ Build #\${{ github.run_number }}" >> \$GITHUB_STEP_SUMMARY
          echo "| | |" >> \$GITHUB_STEP_SUMMARY
          echo "|---|---|" >> \$GITHUB_STEP_SUMMARY
          echo "| APK | \`\${{ env.APK_PATH }}\` |" >> \$GITHUB_STEP_SUMMARY
          echo "| Size | \${{ env.APK_SIZE }} |" >> \$GITHUB_STEP_SUMMARY
          echo "| Signed | ✅ Same key every build |" >> \$GITHUB_STEP_SUMMARY
WFEOF

echo "     .github/workflows/build.yml written"

# ── Push ──────────────────────────────────────────────────────
echo "[5/6] Committing..."

# gitignore — never commit local.properties but DO commit the workflow
cat >> .gitignore << 'GIEOF'
local.properties
*.jks
*.keystore
GIEOF

echo "# QuestPhone fdroid builds" > Fragment/fdroid/.gitkeep

rm -rf .git
git init -q
git config user.email "build@questphone.app"
git config user.name "QuestPhone Build"
git remote add origin "https://$GH_USER:$GH_TOKEN@github.com/$GH_USER/$REPO.git"
git branch -M main
git add .
DATE=$(date '+%Y-%m-%d %H:%M')
git commit -q -m "build: $DATE"

echo "[6/6] Pushing..."
git push -u -f origin main

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  ✅ PUSHED! CI running at:                       ║"
echo "║  github.com/$GH_USER/$REPO/actions               "
echo "╚══════════════════════════════════════════════════╝"
echo ""
echo "Your keystore is at: $KEYSTORE_FILE"
echo "Back it up: cp $KEYSTORE_FILE /sdcard/Download/"
echo ""
echo "Every future push will use the SAME signing key."
echo "Updates on device will work correctly."
