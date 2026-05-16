#!/data/data/com.termux/files/usr/bin/bash
# ╔══════════════════════════════════════════════════════════════╗
# ║  QuestPhone — Universal Push Script                          ║
# ║  Uses your EXISTING app signing keystore                     ║
# ║  Same key every build = Play Store updates work              ║
# ╚══════════════════════════════════════════════════════════════╝
set -e

TOKEN_FILE="$HOME/questphone_token"
GH_USER="krishna000627-pixel"
REPO="Questphone"
PROJ_DIR="$HOME/push_workspace"

# These must match whatever you used when first signing the app
KEYSTORE_FILE="$HOME/questphone_release.jks"
KEY_ALIAS="questphone"
KEY_PASS="QuestPhone2025"
STORE_PASS="QuestPhone2025"

# ── Token ──────────────────────────────────────────────────────
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
echo "║  Repo : $GH_USER/$REPO"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# ── Find zip ──────────────────────────────────────────────────
echo "[1/6] Finding zip..."
ZIP_PATH=""
if [ -n "$1" ]; then
  [ -f "$1" ] && ZIP_PATH="$1" || ZIP_PATH="/sdcard/Download/$1"
fi
if [ -z "$ZIP_PATH" ] || [ ! -f "$ZIP_PATH" ]; then
  ZIP_PATH=$(ls -t /sdcard/Download/questphone*.zip 2>/dev/null | head -1)
fi
if [ -z "$ZIP_PATH" ] || [ ! -f "$ZIP_PATH" ]; then
  read -p "Zip path or filename in Downloads: " Z
  [ -f "$Z" ] && ZIP_PATH="$Z" || ZIP_PATH="/sdcard/Download/$Z"
fi
[ ! -f "$ZIP_PATH" ] && echo "Not found: $ZIP_PATH" && exit 1
echo "     $ZIP_PATH"

# ── Keystore check ─────────────────────────────────────────────
echo "[2/6] Checking app signing keystore..."
if [ ! -f "$KEYSTORE_FILE" ]; then
  echo ""
  echo "  Keystore not found at: $KEYSTORE_FILE"
  echo ""
  echo "  Options:"
  echo "  a) Copy your keystore here:"
  echo "     cp /sdcard/Download/your_keystore.jks $KEYSTORE_FILE"
  echo ""
  echo "  b) Or generate a new one (only if app is NOT on Play Store yet):"
  echo "     keytool -genkeypair -alias questphone -keyalg RSA -keysize 2048 \\"
  echo "       -validity 36500 -keystore $KEYSTORE_FILE \\"
  echo "       -storepass QuestPhone2025 -keypass QuestPhone2025 \\"
  echo "       -dname 'CN=Krishna,O=QuestPhone,C=IN'"
  echo ""
  read -p "Press Enter once keystore is in place, or Ctrl+C to abort..."
fi
[ ! -f "$KEYSTORE_FILE" ] && echo "Still not found: $KEYSTORE_FILE" && exit 1
echo "     Found: $KEYSTORE_FILE ($(du -sh $KEYSTORE_FILE | cut -f1))"

# ── Extract zip ────────────────────────────────────────────────
echo "[3/6] Extracting..."
rm -rf "$PROJ_DIR" && mkdir -p "$PROJ_DIR"
unzip -o "$ZIP_PATH" -d "$PROJ_DIR" > /dev/null
GRADLE_DIR=$(find "$PROJ_DIR" -name "gradlew" -printf "%h\n" 2>/dev/null | head -1)
[ -z "$GRADLE_DIR" ] && echo "ERROR: gradlew not found in zip" && exit 1
cd "$GRADLE_DIR"
echo "     $(pwd)"

# ── local.properties ───────────────────────────────────────────
echo "[4/6] Writing local.properties..."
cat > local.properties << LEOF
KEYSTORE_FILE=questphone_release.jks
KEYSTORE_PASSWORD=$STORE_PASS
KEY_ALIAS=$KEY_ALIAS
KEY_PASSWORD=$KEY_PASS
KAI_API_KEY=
LEOF

# Copy keystore into app/ so Gradle can find it
mkdir -p app
cp "$KEYSTORE_FILE" app/questphone_release.jks
echo "     Done"

# ── gitignore ──────────────────────────────────────────────────
# Never commit secrets — keystore + local.properties stay local only
grep -qxF "local.properties" .gitignore 2>/dev/null || echo "local.properties" >> .gitignore
grep -qxF "*.jks"            .gitignore 2>/dev/null || echo "*.jks"            >> .gitignore
grep -qxF "*.keystore"       .gitignore 2>/dev/null || echo "*.keystore"       >> .gitignore

# ── Push ───────────────────────────────────────────────────────
echo "[5/6] Committing..."
rm -rf .git
git init -q
git config user.email "build@questphone.app"
git config user.name  "QuestPhone Build"
git remote add origin "https://$GH_USER:$GH_TOKEN@github.com/$GH_USER/$REPO.git"
git branch -M main
git add .
DATE=$(date '+%Y-%m-%d %H:%M')
git commit -q -m "build: $DATE"

echo "[6/6] Pushing..."
git push -u -f origin main

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  ✅ PUSHED! CI building at:                      ║"
echo "║  github.com/$GH_USER/$REPO/actions"
echo "╚══════════════════════════════════════════════════╝"
echo ""
echo "Required GitHub Secrets (Settings → Secrets → Actions):"
echo "  KEYSTORE_B64      → base64 -w 0 $KEYSTORE_FILE"
echo "  KEYSTORE_PASSWORD → $STORE_PASS"
echo "  KEY_ALIAS         → $KEY_ALIAS"
echo "  KEY_PASSWORD      → $KEY_PASS"
echo "  KAI_API_KEY       → your Gemini API key"
echo ""
