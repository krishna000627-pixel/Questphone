# QuestPhone Roadmap

> Single developer · 3-5 users · No store publishing  
> Current: **v2.7.1 build20** | Completion: **~82%**

---

## Done (builds 1-20)

### Core
- [x] Launcher with app drawer, stranger mode, app blocker
- [x] Quest system (SWIFT_MARK) with time windows, hard lock
- [x] RPG economy: coins, XP, level up, inventory items, streak
- [x] Cultivation Realm progression (Mortal Body to True Immortal)
- [x] Stat points: Strength / Intelligence / Focus / Discipline per quest
- [x] **Quest stat rewards** applied on completion (builds 18-20)
- [x] **Quest categories + color** (data model ready, build 20)
- [x] **Quest completion streak per quest** (data model ready, build 20)
- [x] AppBlockerService with overlay hard-lock
- [x] AutoDestruct mode (streak-fail consequence)
- [x] BacklogWall activity shown on blocked app open
- [x] Boot receiver restores all active locks and alarms
- [x] New day receiver resets daily quest states
- [x] Widget management screen (AppWidgetHost)

### Kai AI
- [x] Kai AI companion (Gemma 4 31B, Google AI Studio free tier)
- [x] 11 economy-enforced tools (create/edit/delete quests, settings, memory)
- [x] Chat history sessions with persistent memory
- [x] Markdown formatter (bold, italic, code, lists)
- [x] Pixel art avatars (20 RPG characters, Canvas-drawn)
- [x] Heartbeat system + coin cost per message
- [x] **App opening via fuzzy name resolution** (build 20)
- [x] **Installed app list injected into AI context** (build 20)
- [x] **Screen time data visible to Kai** (build 20)
- [x] **Live coin balance injected on every message** (build 19-20)
- [x] KaiMemoryManager — persistent facts across sessions
- [x] Low coins dialog gates Kai usage
- [x] Model download dialog for on-device model flow

### Quest Types
- [x] SwiftMark quest (tap-to-complete, time-windowed)
- [x] DeepFocus quest (app whitelist + focus timer)
- [x] AiSnap quest (camera photo verified by AI evaluation)
- [x] ExternalIntegration quest (webhook-based third-party triggers)
- [x] Quest skip dialog + skip cost in coins
- [x] Quest completion dialog + reward dialog
- [x] Streak up / streak failed / streak freezers dialogs
- [x] Hard lock dialog (blocks dismissal until window passes)
- [x] Quick reward dialog for bonus coin events
- [x] Level up dialog + cultivation realm upgrade dialog

### Settings & UI
- [x] 10 themes (Cherry Blossoms, Hacker, Bonsai, Pitch Black + 6 new)
- [x] JSON bulk quest import with stat fields
- [x] GitHub sync + WiFi sync
- [x] API key secured in KaiPrefs (never synced)
- [x] **Settings search bar** (build 20)
- [x] **Custom stat names** via StatSettings screen (build 20)
- [x] **Focus Timer / Pomodoro** with coin rewards (build 20)
- [x] **Notification settings screen** (build 20)
- [x] **Focus Timer side panel entry** (build 20)
- [x] Customize screen (icon packs, clock style, grid size)
- [x] Tracker settings screen (coin reward ratio, thresholds)
- [x] Stranger Mode settings (whitelist apps, toggle)
- [x] Hidden apps settings screen
- [x] Store screen (coin-gated theme/item shop)
- [x] Donations dialog
- [x] Socials screen + Document viewer screen
- [x] Onboarding flow (OnBoarderView + OnboardActivity)
- [x] Privacy policy activity + terms screen
- [x] Review dialog (post-streak milestone prompt)
- [x] Profile screen + profile settings + profile sync service
- [x] Theme preview screen
- [x] MdPad (in-app markdown notepad)

### Screen Time
- [x] Per-app foreground usage stats with icon, label, time
- [x] Total screen time header card with date picker
- [x] Top 3 apps visualization with gold/silver/bronze rank + progress bars
- [x] Ignored apps list (persisted via SharedPreferences)
- [x] Date-range navigation (earliest available → today)
- [x] Usage permission prompt dialog
- [x] HeatMapChart composable (per-app daily grid, built)
- [x] **Bottom nav bar on Screen Time page** — one-tap entry to Focus Mode (build 20)

### Focus Mode
- [x] Scheduled focus windows via AlarmManager (07:50–16:00, Mon–Fri)
- [x] Focus mode toggle persisted in SharedPreferences
- [x] Study whitelist (PhysicsWallah, YouTube, Chrome, Wikipedia, QuestPhone)
- [x] Stranger Mode whitelist integration
- [x] Boot-restore: re-activates focus if device reboots mid-window
- [x] Focus scheduling enable/disable toggle with alarm cancellation
- [x] Pomodoro card composable with session timer UI
- [x] Focus timer screen with coin reward on completion

### Voice
- [x] Jarvis voice wake word with TTS responses
- [x] Fuzzy app opening by name from voice
- [x] TTS feedback loop fix (mic stops before speaking)
- [x] Custom Voice Actions settings screen

### Study Quota
- [x] Study Quota settings screen (StudyQuotaSettings)
- [x] Warning banner + Save disabled when no prime study app selected

### Notifications
- [x] AlarmHelper + AlarmReceiver infrastructure
- [x] GenerateQuestReminder + GenerateStreakReminder
- [x] StreakReminderReceiver
- [x] NotificationScheduler + ScheduleDailyNotification
- [x] QuestReminderActivity (opens from notification tap)
- [x] FCM handler (FcmHandler) wired up

### Sync & Cloud
- [x] GitHub sync manager (push/pull JSON data)
- [x] WiFi sync (local LAN, WifiSyncServer + WifiSyncClient)
- [x] Supabase manager (SupabaseManager for cloud profile sync)
- [x] ProfileSyncService + StatsSyncWorker + QuestSyncWorker
- [x] FileDownloadWorker for remote asset fetch

### Developer / CI
- [x] Two-CI push script: versioned APK archive + F-Droid universal APK
- [x] App rename + App Info on long-press in app drawer
- [x] JSON Quest Converter screen
- [x] Crash log viewer screen
- [x] GitHub Actions signed APK build
- [x] ErrorLogger utility (structured crash capture)
- [x] BroadcastCentre (centralized intent routing)

---

## v2.8 — Quest Intelligence
*~2 sessions*

### Quest List & Completion
- [ ] **Quest completion animation** — particle burst + sound when ticking off a quest
- [ ] **Overdue quest markers** — red/orange badge on quests past their time window
- [ ] **Quest category badge UI** — color-coded pill shown in quest list rows and setup screens
- [ ] **Per-quest streak display** — show `🔥N` streak counter next to each quest card
- [ ] **Quest sort/filter bar** — sort by category, stat type, time window, or streak

### Kai Smart Features
- [ ] **Smart quest suggestions** — Kai reads completion patterns, proposes better time windows
- [ ] **Streak at-risk alert** — Kai warns in chat if a quest with active streak is not done by 8 PM
- [ ] **Quest difficulty auto-tag** — Kai labels quests Easy/Medium/Hard based on historical completion rate

---

## v2.9 — Notifications Live
*~1-2 sessions*

### Reminders
- [ ] **Quest reminders actually firing** — AlarmManager scheduled per quest `time_start`, using existing AlarmHelper
- [ ] **Streak warning at 9 PM** — fires via StreakReminderReceiver if today's quests not all done
- [ ] **Kai daily briefing** — morning notification (8 AM) with Kai-generated summary of today's quests via API
- [ ] **Focus start/end notification** — silent notification when focus mode activates or deactivates

### YouTube Allowance
- [ ] **YouTube allowance mode** — 10 min YouTube unlocked per 1h in whitelisted study app; resets daily
- [ ] **Allowance counter UI** — small chip on home screen showing remaining YouTube minutes today

---

## v3.0 — Study Integration
*~3 sessions*

### Auto-completion
- [ ] **Study app time tracker** — UsageStatsHelper polls every 15 min; minutes in study app auto-fill time-based quests
- [ ] **Block schedule mode** — device locks distracting apps until X hours of study app time is logged
- [ ] **Study session summary card** — end-of-day card: hours studied, quests auto-completed, coins earned

### Screen Time Deep Dive
- [ ] **App usage heatmap activated** — wire HeatMapChart.kt into ScreentimeStatsScreen per-app detail view
- [ ] **Focus session stats screen** — Pomodoro session history: sessions per day bar chart, total focus hours, coins from focus
- [ ] **Weekly screen time trend** — 7-day line chart of total daily screen time on the Screen Time page
- [ ] **App time limit setter** — set a daily cap per app; AppBlockerService enforces after limit is hit

---

## v3.1 — Kai Upgrades
*~2 sessions*

### Planning
- [ ] **Quest plan generator** — "I have physics exam in 3 weeks" → Kai generates a full week-by-week quest schedule
- [ ] **Context-aware nudges** — Kai detects no study app opened by noon on a weekday and sends an in-app nudge
- [ ] **Weekly summary message** — every Sunday Kai sends a chat message: quests completed, missed, stats gained, streak health

### Multi-model
- [ ] **Model selector in Kai settings** — choose between Gemma 4 31B, Flash, or Pro for each session
- [ ] **Multi-model comparison mode** — run same prompt on 2 models side-by-side, user picks better answer
- [ ] **Kai response rating** — thumbs up/down on each message feeds a local preference log

---

## v3.2 — Polish
*~1-2 sessions*

### Store & Economy
- [ ] **Theme purchase flow** — buy locked themes from Store with coins; StoreScreen already wired to backend
- [ ] **Item shop expansion** — streak freezers, XP boosters, coin multipliers purchasable from Store
- [ ] **Coin transaction log** — view history of every earn/spend event with timestamp and reason

### Onboarding & Backup
- [ ] **Smooth onboarding** — guided first-run: name → create first quest → set wake word → done; skip allowed
- [ ] **Backup to file** — export full app data (quests, stats, economy, settings) as encrypted JSON to Downloads
- [ ] **Restore from file** — import backup JSON, merges with existing data, shows conflict summary

### Stats & Graphs
- [ ] **Stat history graph** — 30-day line chart per stat (Strength, Intelligence, Focus, Discipline) on Profile screen
- [ ] **Cultivation milestone timeline** — visual timeline of realm unlocks with dates reached

---

## Completion Estimate

| Version | Status | % Complete |
|---------|--------|------------|
| v2.7.1 build 20 | **Current** | **82%** |
| After v2.8 | Quest intelligence | 86% |
| After v2.9 | Notifications live | 89% |
| After v3.0 | Study integration | 93% |
| After v3.1 | Kai upgrades | 96% |
| After v3.2 | Polish | **99%** |

At 2-3 messages/day: **~4-5 weeks** to reach 99%.

---

## Companion Apps (Separate Projects, Future)

- [ ] **QuestWatch** — Wear OS: streak/coins/quests glanceable, one-tap complete
- [ ] **QuestWidget** — Android widget (4×1, 2×2) showing today's quests + streak
- [ ] **QuestBuddy** — Second device: friend/parent sees your streak live
- [ ] **QuestBoard** — Tablet dashboard: heatmap, bulk quest editor, stat charts
