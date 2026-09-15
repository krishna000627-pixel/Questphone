#!/data/data/com.termux/files/usr/bin/bash

# ============================================================
#  QuestPhone Push Tool v2 - Termux
#  Token is read from ~/questphone_token (never hardcoded)
#  Usage:
#    ./push_v2.sh                   <- prompts for zip name
#    ./push_v2.sh myfile.zip        <- direct
# ============================================================

TOKEN_FILE="$HOME/questphone_token"
GH_USER="krishna000627-pixel"
REPO="Questphone"

# ---- Load token -----------------------------------------------
if [ ! -f "$TOKEN_FILE" ]; then
  echo ""
  echo "First-time setup: enter your GitHub token"
  echo "(It will be stored in $TOKEN_FILE - never in the script)"
  read -p "GitHub token: " INPUT_TOKEN
  echo "$INPUT_TOKEN" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  echo "Token saved."
fi
GH_TOKEN=$(cat "$TOKEN_FILE" | tr -d '[:space:]')

# ---- Banner ---------------------------------------------------
echo ""
echo "  QuestPhone Push Tool"
echo "  Repo: $GH_USER/$REPO"
echo ""

# ---- Zip file -------------------------------------------------
if [ -n "$1" ]; then
  ZIP_NAME="$1"
else
  read -p "Zip filename (in /sdcard/Download/): " ZIP_NAME
fi

if [ -f "$ZIP_NAME" ]; then
  ZIP_PATH="$ZIP_NAME"
else
  ZIP_PATH="/sdcard/Download/$ZIP_NAME"
fi

if [ ! -f "$ZIP_PATH" ]; then
  echo "Not found: $ZIP_PATH"
  exit 1
fi

# ---- Extract --------------------------------------------------
PROJ_DIR="$HOME/push_workspace"
echo "Extracting..."
rm -rf "$PROJ_DIR" && mkdir -p "$PROJ_DIR"
unzip -o "$ZIP_PATH" -d "$PROJ_DIR" > /dev/null

cd "$PROJ_DIR"

# Flatten nested folder if present
ROOT=$(find . -maxdepth 3 -name "gradlew" | head -1 | xargs dirname 2>/dev/null)
if [ -n "$ROOT" ] && [ "$ROOT" != "." ]; then
  echo "Flattening..."
  cp -a "$ROOT"/. .
  NESTED=$(echo "$ROOT" | cut -d'/' -f2)
  rm -rf "./$NESTED"
fi

# ---- Write local.properties (from env or prompt) --------------
if [ ! -f "local.properties" ]; then
  echo "Creating local.properties..."
  cat > local.properties << 'LOCALEOF'
KEYSTORE_FILE=questphone_release.jks
KEYSTORE_PASSWORD=QuestPhone2025
KEY_ALIAS=questphone
KEY_PASSWORD=QuestPhone2025
KAI_API_KEY=AIzaSyDzcK1yHPBF9fY-DHUqkQ86WMZcreUzw1o
LOCALEOF
fi

# ---- GitHub Actions workflow ----------------------------------
echo "Writing workflow..."
mkdir -p .github/workflows
WF=".github/workflows/build.yml"

cat > "$WF" << 'WFEOF'
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

      - name: Setup Keystore
        env:
          EXISTING_KS: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          mkdir -p app
          if [ -n "$EXISTING_KS" ]; then
            echo "Using stored keystore"
            echo "$EXISTING_KS" | base64 --decode > app/questphone_release.jks
          else
            echo "Generating keystore"
            keytool -genkeypair -alias questphone -keyalg RSA -keysize 2048 \
              -validity 10000 -keystore app/questphone_release.jks \
              -storepass QuestPhone2025 -keypass QuestPhone2025 \
              -dname "CN=QuestPhone,O=Krishna,C=IN" -noprompt
          fi
          echo "KEYSTORE_FILE=questphone_release.jks" >> local.properties
          echo "KEYSTORE_PASSWORD=QuestPhone2025" >> local.properties
          echo "KEY_ALIAS=questphone" >> local.properties
          echo "KEY_PASSWORD=QuestPhone2025" >> local.properties
          echo "KAI_API_KEY=${{ secrets.KAI_API_KEY }}" >> local.properties

      - name: Build
        run: |
          chmod +x gradlew
          ./gradlew assembleFdroidRelease

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: QuestPhone-${{ github.run_number }}
          path: app/build/outputs/apk/fdroid/release/*.apk
          retention-days: 30
WFEOF

# ---- Git push -------------------------------------------------
echo "Setting up git..."
rm -rf .git
git init -q
git config user.email "build@questphone.app"
git config user.name "QuestPhone Build"
git remote add origin "https://$GH_USER:$GH_TOKEN@github.com/$GH_USER/$REPO.git"
git branch -M main

# Never commit secrets
cat >> .gitignore << 'GIEOF'
local.properties
*.jks
*.keystore
GIEOF

git add .
git commit -q -m "build: $(date '+%Y-%m-%d %H:%M')"

echo "Pushing..."
git push -u -f origin main

echo ""
echo "Done! https://github.com/$GH_USER/$REPO/actions"
echo ""
