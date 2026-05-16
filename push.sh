#!/data/data/com.termux/files/usr/bin/bash
# QuestPhone Quick Push — shortcut to push_universal.sh
set -e
cd "$(dirname "$0")"
exec ./push_universal.sh "$@"
