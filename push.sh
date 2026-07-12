#!/data/data/com.termux/files/usr/bin/bash
# QuestPhone Push — uses keystore from GitHub Secrets (KEYSTORE_B64)
set -e

TOKEN_FILE="$HOME/questphone_token"
GH_USER="krishna000627-pixel"
REPO="Questphone"
WORK="$HOME/qp_push"

# Token — ask once, save forever
if [ ! -f "$TOKEN_FILE" ]; then
  read -p "GitHub token (saved once): " T
  echo "$T" > "$TOKEN_FILE" && chmod 600 "$TOKEN_FILE"
fi
TOKEN=$(cat "$TOKEN_FILE" | tr -d '[:space:]')

# Find zip
ZIP="${1:-}"
[ -z "$ZIP" ] && ZIP=$(ls -t /sdcard/Download/questphone*.zip 2>/dev/null | head -1)
[ -z "$ZIP" ] && read -p "Zip path: " ZIP
[ ! -f "$ZIP" ] && echo "Not found: $ZIP" && exit 1
echo "Using: $ZIP"

# Extract
rm -rf "$WORK" && mkdir -p "$WORK"
unzip -o "$ZIP" -d "$WORK" > /dev/null
ROOT=$(find "$WORK" -name "gradlew" -printf "%h\n" | head -1)
[ -z "$ROOT" ] && echo "gradlew not found" && exit 1
cd "$ROOT"

# Push
rm -rf .git
git init -q
git config user.email "build@questphone.app"
git config user.name "QuestPhone"
git remote add origin "https://$GH_USER:$TOKEN@github.com/$GH_USER/$REPO.git"
git branch -M main
git add .
git commit -q -m "build: $(date '+%Y-%m-%d %H:%M') push_v4"
git push -u -f origin main

echo ""
echo "✅ Pushed! CI building at:"
echo "   https://github.com/$GH_USER/$REPO/actions"
