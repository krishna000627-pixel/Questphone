# QuestPhone — Future Ideas for Developers

> A living document. These are directions worth exploring,
> ranging from small polish to major arcs.

---

## 🧠 AI / Kai Expansions

1. **Kai narrates your day** — at day end, Kai generates a short story recap of what you completed
2. **Adaptive quest difficulty** — Kai adjusts quest challenge based on your recent completion rate
3. **Kai coaching mode** — Kai checks in mid-session ("You've been on Instagram 20 mins. Ready to refocus?")
4. **Quest generation from journal entries** — Kai reads your notes and surfaces quests you implicitly want
5. **Personality-aware failure messages** — Kai reacts to missed quests differently based on selected personality
6. **Kai-generated routine stories** — One tap: Kai writes the story intro for a new routine
7. **AI-powered People insights** — Kai notices you haven't interacted with someone in months and surfaces a nudge
8. **Weekly quest letter** — Kai writes a motivational letter based on your week's performance
9. **Voice journaling summary** — Record a voice note, Kai transcribes and extracts action items as quests
10. **Rival AI persona** — An adversarial version of Kai that challenges you ("You think that's enough?")

---

## ⚔️ RPG / Quest System

11. **Guild system** — Form a party with friends; shared quests everyone must complete
12. **Quest chains with branching** — Completing Quest A unlocks Quest B or C based on choice
13. **Boss battles for long-term goals** — A 30-day challenge that grows harder each week
14. **Prestige system** — Reach max level, reset, unlock a new class title
15. **Quest lore pages** — Each quest type has a short lore description (fighting a "Sloth Goblin")
16. **Class selection** — Warrior (physical), Mage (study), Rogue (stealth productivity), Sage (wisdom)
17. **Attribute system** — Quests build specific stats (Strength, Focus, Discipline, Social)
18. **Equipment slots** — Earning items that passively boost rewards ("Focus Relic = +10% coins")
19. **World map progression** — Complete enough quests to unlock new biomes / regions on a visual map
20. **Side quests from random events** — Unexpected prompts: "Someone helps you. Add them to People. +5 coins."

---

## 📊 Stats & Tracking

21. **Heatmap calendar** — GitHub-style activity grid for quest completions
22. **Focus score™** — A composite daily score from screen time, quests, routines
23. **Mood tracking** — Log mood 1–5 each day; correlate with productivity
24. **Sleep tracking integration** — Input wake/sleep time; show patterns vs. quest performance
25. **Session replay** — Show a timeline of which apps were opened when
26. **Streak recovery quests** — Break your streak? A special quest to redeem it
27. **Month-end report card** — A full PDF-style summary of the month
28. **App guilt score** — Rank apps by "regret score" (time spent vs. satisfaction rating you gave)
29. **Productive ratio meter** — % of screen time spent in "intentional" apps vs. distractions
30. **Discipline points** — A second currency earned purely for refusing to unlock blocked apps

---

## 👥 People Database Extensions

31. **Interaction log** — Log when you last talked to someone and what about
32. **Birthdays widget** — Home screen widget showing upcoming birthdays
33. **"Reach out" nudges** — Remind you to contact people you haven't in N days
34. **Relationship health score** — Based on interaction frequency and notes
35. **Voice notes per person** — Record a quick voice memo attached to a person
36. **Photo attachment** — Attach an image to a person's profile
37. **People groups** — Tag people into groups (Work, College, Family) and view by group
38. **Gift ideas field** — A dedicated field to note gift ideas for someone
39. **People timeline** — A chronological log of your history with someone
40. **Import from contacts** — One-tap import basic info from Android Contacts

---

## 🌙 Routines

41. **Routine streaks** — Track consecutive days a routine was completed
42. **Routine XP** — Completing a routine earns XP toward leveling up
43. **Shared routines** — Export/import routines as JSON to share with friends
44. **Conditional steps** — "If raining, skip the run step"
45. **Routine templates** — Pre-built packs (Monk Morning, Deep Work, Athlete PM)
46. **Animated story cutscene** — A simple lottie animation plays when you begin a routine
47. **Step timer with sound** — Each step can have a bell that sounds when time is up
48. **Routine calendar integration** — Mark days when a routine was done on a calendar view
49. **Routine suggestions from Kai** — Based on your stats, Kai proposes a custom routine
50. **Routine pause and resume** — Step away mid-routine and resume where you left off

---

## 🔐 Data Vault

51. **PIN change flow** — Re-encrypt all items under a new PIN
52. **Biometric unlock** — Fingerprint / face unlock as alternative to PIN
53. **Auto-lock timer** — Vault auto-locks after N minutes of inactivity
54. **Vault export (encrypted)** — Export a password-protected backup file
55. **Vault import** — Restore from backup file
56. **TOTP generator** — Built-in 2FA code generator per item (Google Authenticator style)
57. **Password strength meter** — Rate the strength of passwords as you type them
58. **Secure notes formatting** — Markdown support inside vault note content
59. **Favourite / star items** — Pin frequently accessed items to the top
60. **Vault breach check** — Check if a stored email appears in known data breaches (HIBP API)

---

## 🎨 UI / UX Polish

61. **Swipe to delete** in lists (People, Vault, Routines)
62. **Haptic feedback** on key interactions (quest complete, unlock)
63. **Custom app icon packs** — Let users set per-app icons in the launcher
64. **Animated coin counter** — Coins tick up visually instead of jumping
65. **Confetti on quest complete** — A small burst of particles
66. **Dark / AMOLED variants per screen** — Let each section choose its aesthetic
67. **Font size system setting** respected throughout
68. **One-handed mode** — Content shifted down for easier thumb reach
69. **Landscape layout support** for key screens
70. **Widget: Today's routines** — Glanceable home screen widget

---

## 🔔 Notifications & Reminders

71. **Smart quest reminders** — Kai picks the best time to remind based on usage patterns
72. **Routine alarm** — "Your Morning Warrior ritual begins in 5 minutes"
73. **Streak at risk** warning — Evening push if no quests completed yet
74. **People nudge notifications** — "You haven't spoken to [Name] in 2 weeks"
75. **Focus session end bell** — Chime when a timed focus block completes
76. **Vault inactivity reminder** — Prompt to update vault if nothing saved in a long time
77. **Weekly review prompt** — Sunday evening reminder to reflect
78. **Notification digest** — Bundle all QuestPhone reminders into one daily summary
79. **Silent hours** — No notifications between user-set hours
80. **Do-not-disturb quest** — A quest type that requires turning on DND for N minutes

---

## 🌐 Sync & Backup

81. **End-to-end encrypted cloud sync** (self-hosted or via Anthropic API key)
82. **GitHub Gist sync** for quest data (already partially in the codebase)
83. **Local Wi-Fi sync** with a desktop companion app
84. **QR code backup** — Encode small data (People entries) as QR for offline backup
85. **Automatic daily local backup** to Downloads folder
86. **Changelog viewer** — In-app version history so users know what changed

---

## 🧩 Integrations

87. **Obsidian sync** — Export People and Journal entries as markdown to an Obsidian vault
88. **Todoist import** — Pull in tasks as quests
89. **Habitica bridge** — Sync quest completions to Habitica
90. **YouTube allowance integration** — Watch time auto-decrements coins
91. **Spotify "focus playlist"** — Launch a playlist when a focus session starts
92. **Health Connect integration** — Import steps, sleep data to inform quest rewards
93. **Calendar read** — Show today's events on HomeScreen status bar
94. **Tasker plugin** — Allow Tasker to trigger quest completions or unlock apps
95. **Logseq export** — Push daily notes to Logseq format

---

## 🏗️ Architecture & Developer Quality

96. **Full unit test coverage for ViewModel logic** (especially coin calculation)
97. **Espresso UI tests for critical flows** (quest complete, vault unlock)
98. **KDoc on all public functions**
99. **Detekt / ktlint CI enforcement** — Fail the build on style violations
100. **Feature flags system** — Remote-toggle new features without a release

---

*Generated alongside the v3.3 patch (Routines, Data Vault, People styles, build fixes).*
*Last updated: May 2026*
