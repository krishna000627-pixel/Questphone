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

### Voice
- [x] Voice wake word with TTS responses
- [x] Fuzzy app opening by name from voice
- [x] TTS feedback loop fix (mic stops before speaking)

---

## v2.8 — Quest Intelligence
*~2 sessions*

- [ ] **Quest completion animation** - particle burst + sound when ticking off
- [ ] **Smart quest suggestions** - Kai analyzes patterns and suggests better times/days
- [ ] **Overdue quest markers** - quests past their time window get visual warning
- [ ] **Quest category UI** - color-coded category badge in quest list and setup
- [ ] **Per-quest streak display** - show individual quest streak next to each quest

---

## v2.9 — Notifications Live
*~1-2 sessions*

- [ ] **Quest reminders actually firing** - AlarmManager scheduled per quest time_start
- [ ] **Streak warning at 9 PM** - fires if today's quests not done
- [ ] **Kai daily briefing** - morning notification with Kai-generated summary via API
- [ ] **YouTube allowance mode** - 10 min YT per 1h study app time

---

## v3.0 — Study Integration
*~3 sessions*

- [ ] **Study app time tracker** - minutes spent auto-completes time-based quests
- [ ] **Block schedule** - blocked until X hours studied (not per-quest)
- [ ] **App usage heatmap** - per-app daily usage grid (not just global)
- [ ] **Focus session stats** - Pomodoro history, sessions per day chart

---

## v3.1 — Kai Upgrades
*~2 sessions*

- [ ] **Quest plan generator** - "I have physics exam in 3 weeks" -> full quest schedule
- [ ] **Context-aware suggestions** - Kai notices no study app opened, suggests quest
- [ ] **Weekly summary** - Sunday Kai message: what completed, what missed, stats gained
- [ ] **Multi-model comparison** - run same prompt on 2 models, pick better answer

---

## v3.2 — Polish
*~1-2 sessions*

- [ ] **Theme purchase flow** - buy themes from Store with coins (backend mostly ready)
- [ ] **Smooth onboarding** - guided first-run: name -> quest -> wake word -> done
- [ ] **Backup to file** - export full data as encrypted JSON to Downloads
- [ ] **Stat history graph** - 30-day line chart of each stat

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

- [ ] **QuestWatch** - Wear OS: streak/coins/quests, one-tap complete
- [ ] **QuestWidget** - Android widget (4x1, 2x2) for home screen
- [ ] **QuestBuddy** - Second device: friend/parent sees your streak
- [ ] **QuestBoard** - Tablet dashboard: heatmap, bulk editor, charts
