# QuestPhone Patch — v3.3

## 🐛 Build Fixes

### `AppInfo.kt` — `activityName` field removed
The data class `AppInfo` only has `name` and `packageName`.
`AppListViewModel.kt` was constructing `AppInfo(name, packageName, activityName = "")` at lines 86 and 205 — a parameter that doesn't exist.
**Fix:** both `peopleEntry` constructions now use only the two valid fields.

### `PeopleDatabaseScreen.kt` — Multiple Kotlin type errors (lines 416–619)
Root cause: `var customFields by remember { mutableStateOf(... mutableListOf()) }`
Kotlin inferred the type as `MutableList<Nothing>` (empty literal) causing every collection operation (`filter`, `+`, `forEachIndexed`, `copy`, etc.) to fail with "Unresolved reference" and "Cannot infer type" errors.
The `by` delegate also failed because `MutableList` doesn't implement `setValue`.

**Fix:**
- Changed to `var customFields by remember { mutableStateOf<List<CustomField>>(...) }`
- All mutations now use `toMutableList().apply { ... }` and reassign back to the immutable `List<CustomField>` var
- `forEachIndexed` is called directly in the `@Composable` body — no lambda context issues

---

## ✨ New Features

### 🎨 People DB — 3 Style Themes
Tap the palette icon in the People screen top bar to switch between:
- **Grimoire** — deep indigo + arcane gold. Dark mystical tome.
- **Chronicle** — warm amber on charcoal. Field journal aesthetic.
- **Roster** — teal on near-black. Bold command register.

Style is persisted per-device via SharedPreferences.

### 📋 Routines
Story-based daily quests that can be turned on or off.

**Files added:**
- `app/src/main/java/neth/iecal/questphone/app/screens/routine/RoutineDatabase.kt`
- `app/src/main/java/neth/iecal/questphone/app/screens/routine/RoutineScreen.kt`

**Route added:** `RootRoute.Routines` → `"routines/"`

**Features:**
- Create routines with a name, emoji, story intro, and ordered steps
- Each step has a title, flavour text, and optional duration in minutes
- Toggle routines on/off without deleting them
- "Begin Quest" runner view with step-by-step progress and story intro card
- Mark complete — resets steps for tomorrow, increments completion counter
- Ships with two default routines so the screen isn't empty on install

### 🔐 Data Vault
AES-256-GCM encrypted local storage for secrets and sensitive data.

**Files added:**
- `app/src/main/java/neth/iecal/questphone/app/screens/vault/VaultDatabase.kt`
- `app/src/main/java/neth/iecal/questphone/app/screens/vault/VaultScreen.kt`

**Route added:** `RootRoute.DataVault` → `"data_vault/"`

**Features:**
- PIN-protected (min 4 digits), hashed with PBKDF2 (100k iterations)
- All content encrypted with AES-256-GCM; key derived fresh per session
- Six categories: Password, Secret Note, Card/Account, ID/Document, Key/Code, Other
- Reveal/hide toggle so content is never visible by default
- Category filter chips and full-text search
- Lock button — clears session key from memory immediately
- No cloud, no analytics, no recovery (by design)

---

## 📌 Search Pinning Fix
**Problem:** People entry (`👥 People`) was always prepended to filtered results even
when searching for something completely unrelated (e.g. "Chrome").
**Fix:** People only appears at the top when the query is blank OR the query
matches "people" / "👥 People". This is now handled inside `setFilteredApps(list, query)`.

---

## 🗺️ Navigation additions
See `NAVIGATION_ADDITIONS.kt` for the exact composable blocks to paste into `MainActivity.kt`.

---

## 📖 Future Ideas
See `FUTURE_IDEAS.md` for 100 directions future contributors can explore.
