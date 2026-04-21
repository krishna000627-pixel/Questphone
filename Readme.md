# QuestPhone 🎮📱

**A gamified Android launcher that turns your productivity into an RPG.**

Complete quests → earn coins → unlock distraction apps. Built by Krishna.

---

## Features

### 🎯 Quest System
- Create, edit, delete daily quests with time windows and stat rewards
- Hard-lock quests block distraction apps until completion
- JSON bulk import (up to 50 quests at once) with AI generation
- Stat allocation per quest (Strength, Intelligence, Focus, Discipline)

### 🤖 Kai — AI Companion
- Powered by Gemma 4 31B (Google AI Studio, free tier)
- Full app control: create/delete/update quests, toggle settings, open apps
- Economy-enforced: deleting a quest costs 100 coins or 1 Quest Deleter item
- Chat history across multiple sessions with persistent memory
- Voice wake word (say "Kai, what's my next quest?") → TTS response
- Heartbeat system: Kai occasionally says something quietly in the background
- Pixel art avatar (20 options) customisable in Settings
- 5 coins per message (configurable)
- Markdown formatting: bold, italic, code, lists all render properly

### 🔒 App Blocker
- Accessibility service monitors foreground apps
- Distraction apps blocked until quest complete or coins spent
- Stranger Mode: only whitelisted apps visible in drawer
- Study quota: tracks usage of prime study app, blocks distractions if quota not met
- Deep Focus mode with countdown overlay

### 🎨 Themes (10 total)
| Theme | Description |
|---|---|
| Cherry Blossoms | Sakura falling animation |
| Hacker | Matrix rain, neon green |
| Pitch Black | Pure black minimal |
| Bonsai | Growing tree animation |
| Deep Ocean | Dark blue, cyan accents |
| Crimson Dusk | Red/orange city night |
| Void Purple | Deep space purple |
| Forest Monk | Dark green, peaceful |
| Neon Tokyo | Pink/cyan cyberpunk |
| Desert Gold | Warm amber/sand tones |

> **How theme wallpapers work:** Themes that have animated backgrounds (Hacker = Matrix rain, Cherry Blossoms = falling sakura, Bonsai = growing tree) render those animations as Compose Canvas overlays drawn behind the UI. The colour themes (Deep Ocean, Neon Tokyo, etc.) use Material3 `darkColorScheme` with carefully chosen colour palettes — no actual wallpaper images, just colour + typography.

### 🗡️ RPG Economy
- Earn coins by completing quests
- Level up by accumulating XP
- Inventory items: Quest Editor, Quest Deleter, Streak Freezer, XP Booster, etc.
- Stat Points allocated per level-up (Strength / Intelligence / Focus / Discipline)
- Quest deletion costs 100 coins or 1 Quest Deleter

### 📊 Stats & Tracking
- Streak tracking with freezers
- Heatmap of completion history
- Screentime stats
- Custom trackers

### 🎙️ Voice (Kai Wake Word)
- Say wake word → Kai responds via TTS
- Quick queries (next quest, coins, streak, level) handled locally — no API call
- Can open any installed app by name: "Kai open YouTube"
- Override with Gemini/ChatGPT package in settings

### 🔐 Security
- API key stored in `local.properties` (gitignored), injected via BuildConfig at build time
- Never committed to Git
- Release keystore: `questphone_release.jks` (also gitignored)

---

## Setup

### Prerequisites
- Android Studio or AIDE + Termux
- Android API 26+ (Android 8.0+)
- Google AI Studio API key (free): [aistudio.google.com](https://aistudio.google.com)

### Build
1. Clone the repo
2. Create `local.properties` in project root:
   ```
   KAI_API_KEY=your_api_key_here
   KEYSTORE_FILE=questphone_release.jks
   KEYSTORE_PASSWORD=your_password
   KEY_ALIAS=questphone
   KEY_PASSWORD=your_password
   ```
3. Build: `./gradlew assembleFdroidDebug`

### Signing
The release keystore file is `questphone_release.jks`.  
**Store securely — losing it means you can't update the app on any store.**

---

## Architecture

```
app/
  screens/
    launcher/     HomeScreen, AppList, Widgets
    etc/          Settings, AI Chat, Stranger Mode, Study Quota
    game/         Store, Inventory, Rewards
    quest/        View, Setup, Templates, Stats
  core/
    ai/           GemmaRepository, KaiMemoryManager
    focus/        JarvisListenerService (TTS + voice), AppBlockerService
    sync/         GitHub sync, WiFi sync
  backed/
    repositories/ UserRepository, QuestRepository, StatsRepository
data/             UserInfo, CommonQuestInfo, InventoryItem, StatPoints
core/             Shared utils, managers, animations
```

---

## Companion App Ideas (Future)
1. **QuestWatch** — Wear OS app showing streak, coins, next quest, quick complete
2. **QuestDesk** — Desktop app (Electron/Tauri) for bulk quest management, heatmap dashboard
3. **QuestFocus** — Browser extension that blocks distracting sites during focus hours
4. **QuestShare** — Social app where friends see each other's streaks and challenge each other

All connected via local socket (WiFi sync already implemented) or optional cloud sync.

---

## Progress: ~68% to perfect productivity app

**Done:** Launcher, app blocker, quest system, AI companion, gamification, themes, voice, sync  
**Remaining:** Study app integration, push notifications polish, widget system, proper onboarding analytics, companion apps, Play Store listing

---

*Built with ❤️ in AIDE + Termux on Android*
