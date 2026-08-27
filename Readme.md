# QuestPhone 🎮📱

**A gamified Android launcher that turns your productivity into an RPG.**

Complete quests → earn coins → unlock distraction apps. Built by Krishna, running on his own phone.

---

## Latest Features (v2.8 – v3.2)

### v2.8 — Quest Intelligence
- **Category badges** — color-coded pill (Study / Health / Personal / Work / Custom) on every quest card
- **Per-quest streak counter** — 🔥N displayed next to each quest
- **Overdue markers** — red ⚠ badge when a quest's time window passed today without completion
- **Sort & filter bar** — sort quests by category, streak, time window, or stat type
- **Category picker** — reusable composable embedded in all quest setup screens

### v2.9 — Notifications Live
- **Quest reminders** — AlarmManager fires per quest `time_start`, uses existing AlarmHelper
- **Streak warning at 9 PM** — StreakReminderReceiver fires if today's quests aren't all done
- **Kai daily briefing** — 8 AM notification listing today's quests, tapping opens Kai chat
- **YouTube allowance** — earn YouTube minutes by studying (configurable ratio); counter shown in card
- **Focus mode notification** — silent notification when focus window activates/deactivates

### v3.0 — Study Integration
- **Study Tracker screen** — per-study-app usage, progress bar toward daily quota, block schedule status
- **Block schedule mode** — device locks distracting apps until daily study hour goal is met
- **Focus Session History** — full Pomodoro log: sessions per day, total focus time, coins earned
- **Weekly screen time trend** — 7-day line chart at the top of Screen Time page (with 0m for empty days)
- **App Time Limits** — set a daily cap per app; once hit, AppBlockerService blocks it for the day
- **HeatMap** wired into Screen Time page as "Quest Activity" grid

### v3.1 — Kai Upgrades
- **Quest Plan Generator** — describe a goal + timeline → Kai generates week-by-week quest schedule
- **AI Model Selector** — switch between Gemma 4 31B (default), Gemini 2.0 Flash, Gemini 1.5 Flash/Pro, Gemma 3 12B/4B inline in Settings
- **Weekly Kai Summary** — WorkManager fires every Sunday; Kai writes a recap + fires a notification
- **Kai daily briefing scheduler** — wired into MainActivity LaunchedEffect on first launch

### v3.2 — Polish
- **Coin Transaction Log** — every earn/spend event logged with timestamp, reason, and running balance
- **Backup & Restore** — export full data (quests + profile) to JSON via SAF file picker; restore merges with conflict handling
- **Stat History charts** — 30-day line chart per stat using real quest completion data; empty state when no completions yet
- **Stat chips fix** — LazyRow prevents "Discipline" wrapping in Stat History screen
- **Profile screen cleanup** — "Stat History" and "Coin Log" are compact side-by-side buttons, not floating links

---

## All Features

### 🎯 Quest System
- SwiftMark, DeepFocus, AiSnap, ExternalIntegration quest types
- Hard-lock overlay blocks distracting apps until quest done
- JSON bulk import (50 at once) with AI generation via Kai
- Per-quest stat allocation (Strength / Intelligence / Focus / Discipline)
- Quest skip dialog with coin cost
- Category badges, streak counter, overdue markers, sort/filter bar

### 🤖 Kai — AI Companion
- Gemma 4 31B via Google AI Studio (free tier)
- 11 economy-enforced tools: create/edit/delete quests, open apps, toggle settings, write to memory
- Persistent memory across sessions (KaiMemoryManager)
- Pixel art avatar (20 characters, Canvas-drawn)
- Heartbeat system — Kai talks unprompted occasionally
- Voice wake word → TTS response
- Quest Plan Generator — goal input → full week-by-week schedule
- AI Model Selector — 5 models switchable without restarting
- Daily briefing notification at 8 AM
- Weekly Sunday summary notification

### 🔒 App Blocker & Focus
- Accessibility service monitors foreground
- Stranger Mode — only whitelisted apps visible
- Study Quota — blocks distractions until prime study app time met
- Block schedule — full lock until daily study hour goal reached
- YouTube allowance — earn minutes by studying
- Per-app daily time limits
- Focus Timer (Pomodoro) with coin reward per completed session

### 📊 Screen Time
- Screen Time page layout: Total → 7-day trend → Quest activity heatmap → Top 3 → Per-app list
- Bottom nav: Screen Time ↔ Focus Mode
- Per-app foreground usage with icon
- Ignored apps list
- Date picker for historical view

### 💰 Economy & Profile
- Coins, XP, levels, cultivation realms (Mortal Body → True Immortal)
- Inventory items: Quest Editor, Deleter, Streak Freezer, XP Booster
- Coin Transaction Log — full history of every earn/spend event
- Stat History charts — real 30-day growth from quest completions
- Backup/Restore — full JSON export + import

### 🎨 Themes (10 total)
Cherry Blossoms · Hacker · Pitch Black · Bonsai · Deep Ocean · Crimson Dusk · Void Purple · Forest Monk · Neon Tokyo · Desert Gold

### 🔄 Sync
- GitHub sync (push/pull JSON)
- WiFi sync (local LAN, no cloud needed)
- Supabase cloud profile sync

---

## Setup

### Prerequisites
- Android API 26+ (Android 8.0+)
- Google AI Studio API key (free): [aistudio.google.com](https://aistudio.google.com)

### Build
```
# Clone repo
git clone https://github.com/krishna000627-pixel/Questphone

# Add local.properties
KAI_API_KEY=your_key_here
KEYSTORE_FILE=questphone_release.jks
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=questphone
KEY_PASSWORD=your_password

# Build
./gradlew assembleFdroidRelease
```

---

## Architecture

```
app/
  screens/
    launcher/     HomeScreen, AppList, Widgets
    etc/          LauncherSettings, GemmaChat, FocusTimer,
                  StudyTracker, FocusSessionHistory, AppTimeLimit,
                  KaiModelSelector, QuestPlanGenerator,
                  ScreentimeStats, WeeklyScreenTimeTrendCard,
                  YouTubeAllowanceCard, StatSettings, HeatMapChart
    game/         Store, Inventory, CoinTransactionLog
    quest/        ViewQuest, ListAllQuests, Setup screens,
                  QuestCategoryBadge, QuestStreakBadge, OverdueBadge
    account/      ProfileScreen, BackupRestore, StatHistory
  core/
    ai/           GemmaRepository, KaiMemoryManager
    services/     AppBlockerService, JarvisListenerService
    utils/
      reminder/   NotificationScheduler, KaiBriefingScheduler,
                  AlarmHelper, StreakReminderReceiver
    youtube/      YouTubeAllowanceManager
  backed/
    repositories/ UserRepository, QuestRepository, StatsRepository
workers/          KaiWeeklySummaryWorker, StatsSyncWorker, QuestSyncWorker
data/             UserInfo, CommonQuestInfo, StatsInfo, InventoryItem, StatPoints
core/             Shared utils, animations, ScreenUsageStatsHelper
```

---

## Progress: ~93% complete

| Area | Status |
|---|---|
| Quest system (all types) | ✅ Done |
| Kai AI + all tools | ✅ Done |
| App blocker + Focus | ✅ Done |
| Screen Time + charts | ✅ Done |
| Study integration | ✅ Done |
| Notifications | ✅ Done |
| Economy + inventory | ✅ Done |
| Backup / Restore | ✅ Done |
| Stat History charts | ✅ Done |
| Coin Transaction Log | ✅ Done |
| Theme purchase flow | ⏳ Remaining |
| Smooth onboarding | ⏳ Remaining |
| Companion apps | 🔮 Future (separate projects) |

---

*Built in AIDE + Termux on Android*
