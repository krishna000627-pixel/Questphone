# QuestPhone Roadmap

> Single developer · Personal use · No store publishing  
> Current: **v3.2 build 21** | Completion: **100%** (excluding companion apps)

---

## ✅ Complete — All Features Done

### Core Launcher
- [x] App drawer, stranger mode, hidden apps, app rename on long-press
- [x] Home screen clock, widgets (HeatMap, Neural Mesh)
- [x] Boot receiver restores all locks and alarms
- [x] New day receiver resets daily quest states
- [x] Widget management screen (AppWidgetHost)

### Quest System
- [x] SwiftMark, DeepFocus, AiSnap, ExternalIntegration quest types
- [x] Hard-lock overlay blocks distracting apps until quest done
- [x] JSON bulk quest import + AiSnap quest (camera + AI verification)
- [x] Per-quest stat allocation (Strength / Intelligence / Focus / Discipline)
- [x] Quest skip dialog with coin cost
- [x] Quest completion animation skeleton
- [x] Category badges — color-coded pill on every quest card
- [x] Per-quest streak counter (🔥N)
- [x] Overdue markers — red ⚠ badge when time window passed
- [x] Sort & filter bar — by category, streak, time window, stat

### Kai — AI Companion
- [x] Gemma 4 31B via Google AI Studio (free tier)
- [x] 11 economy-enforced tools (create/edit/delete quests, open apps, settings, memory)
- [x] Persistent memory across sessions
- [x] Pixel art avatar (20 characters, Canvas-drawn)
- [x] Heartbeat system
- [x] Voice wake word → TTS response
- [x] Quest Plan Generator — goal → full week-by-week schedule
- [x] AI Model Selector — 5 models (Gemma 3 4B/12B/27B, Gemini Flash, Gemini Pro)
- [x] Weekly Sunday summary notification (WorkManager)
- [x] Daily briefing notification at configurable hour

### App Blocker & Focus
- [x] Accessibility service foreground monitoring
- [x] Stranger Mode whitelist
- [x] Study Quota — blocks distractions until study app time met
- [x] Block schedule — full lock until daily study hour goal reached
- [x] YouTube allowance — earn minutes by studying
- [x] Per-app daily time limits
- [x] Focus Timer (Pomodoro) with coin reward per session
- [x] Focus session history — full log per day

### Notification Blocker  ← NEW v3.2
- [x] NotificationListenerService — cancels notifications from blocked apps
- [x] Block ALL notifications during Focus Mode (configurable)
- [x] Block only distracting-app notifications (configurable)
- [x] DND (Do Not Disturb) auto-enabled on Focus start, disabled on Focus end
- [x] Notification Access permission prompt with live status
- [x] DND Access permission prompt with live status

### Device Admin  ← NEW v3.2
- [x] Optional Device Admin activation prevents uninstallation
- [x] One-tap activate/deactivate from Settings
- [x] Clear explanation + deactivation path shown to user
- [x] device_admin.xml policy registered in Manifest

### Screen Time
- [x] Per-app foreground stats with icons
- [x] 7-day screen time trend chart (top of page)
- [x] Quest Activity heatmap
- [x] Top 3 apps (gold/silver/bronze)
- [x] Date picker for historical view
- [x] Bottom nav → Focus Mode
- [x] App time limits enforced

### Economy & Store
- [x] Coins, XP, levels, cultivation realms
- [x] Inventory items: Quest Editor, Deleter, Streak Freezer, XP Booster, etc.
- [x] Store: buy themes with coins (purchaseTheme wired)
- [x] Store: buy home widgets with coins
- [x] Store: buy consumable items (Streak Saver, Full Free Day, etc.)
- [x] Item shop expansion — all InventoryItem entries in Store
- [x] Coin Transaction Log — full earn/spend history, auto-logged via UserRepository
- [x] CoinTransactionLogger wired into UserRepository.addCoins and useCoins

### Profile & Stats
- [x] Cultivation realm timeline
- [x] Stat History charts — 30-day real data from quest completions
- [x] Stat chips in LazyRow (no text wrap)
- [x] Stat History / Coin Log — compact side-by-side buttons in Profile

### Backup & Sync
- [x] Backup to JSON file (SAF export)
- [x] Restore from JSON file (SAF import, merge with conflict handling)
- [x] GitHub sync (push/pull)
- [x] WiFi sync (local LAN)
- [x] Supabase cloud profile sync

### Onboarding  ← COMPLETED v3.2
- [x] Name entry step (saves to full_name + username)
- [x] Kai API key step (saves via KaiPrefs, skippable)
- [x] First quest picker — 6 templates, skippable
- [x] Usage access permission step
- [x] Overlay permission step
- [x] Notification permission step
- [x] Exact alarm permission step
- [x] Select distraction apps step
- [x] Tutorial step
- [x] Socials step
- [x] Terms & conditions gate

### Notifications
- [x] Quest reminders (AlarmManager per quest time_start)
- [x] Streak warning at 9 PM
- [x] Kai daily briefing (8 AM, configurable)
- [x] Weekly Kai summary (WorkManager, every Sunday)

### Voice
- [x] Jarvis wake word → TTS response
- [x] Open any app by name
- [x] Quick queries (coins, streak, level)
- [x] TTS audio focus fix

### Gameplay Systems  ← NEW
- [x] **Boss Battle** — weekly boss per ISO week, HP reduces as quests are completed, coins+XP on victory, penalty on failure
- [x] **Quest Chains** — 3 preset chains (Morning Warrior, Deep Focus, Strength Arc), unlocks next step on completion, bonus coins on chain complete
- [x] **Rival System** — simulated shadow rival at 60-90% efficiency, daily battle comparison, taunt messages, rival levels up if it beats you
- [x] **Productivity Score** — daily 0-100 score (Quests 40pt + Study 30pt + Screen Time 20pt + Streak 10pt), S/A/B/C/D/F grade
- [x] **Weekly Report Card** — Kai-generated weekly grade with best/worst day, stat totals, personality-aware commentary
- [x] **Lockdown Escalation** — 3 missed days → Stranger Mode auto-activates 24h, 60s panic button cooldown
- [x] **Kai Personality** — 5 modes: Friendly Coach, Strict Sensei, Competitive Rival, Stoic Philosopher, Anime Mentor; injected into system prompt

### Developer / CI
- [x] Two-CI push script (versioned + F-Droid)
- [x] GitHub Actions signed APK build
- [x] Crash log viewer
- [x] JSON Quest Converter
- [x] ErrorLogger
- [x] BroadcastCentre

---

## 🔮 Companion Apps (Separate Projects — Future)

- [ ] **QuestWatch** — Wear OS: streak/coins/quests, one-tap complete
- [ ] **QuestWidget** — Android widget (4×1, 2×2)
- [ ] **QuestBuddy** — Second device: friend sees your streak
- [ ] **QuestBoard** — Tablet dashboard: heatmap, bulk editor

---

## Final Progress

| Category | Status |
|---|---|
| Quest system | ✅ 100% |
| Kai AI | ✅ 100% |
| App blocker | ✅ 100% |
| Notification blocker | ✅ 100% |
| Device admin | ✅ 100% |
| Screen time | ✅ 100% |
| Study integration | ✅ 100% |
| Economy + store | ✅ 100% |
| Backup / restore | ✅ 100% |
| Onboarding | ✅ 100% |
| Notifications | ✅ 100% |
| Voice | ✅ 100% |
| Boss Battle system | ✅ 100% |
| Quest Chains | ✅ 100% |
| Rival system | ✅ 100% |
| Productivity Score | ✅ 100% |
| Weekly Report Card | ✅ 100% |
| Lockdown Escalation | ✅ 100% |
| Kai Personality (5 modes) | ✅ 100% |
| Weekly screen time chart | ✅ Fixed |
| **Total (excl. companion apps)** | **✅ 100%** |
