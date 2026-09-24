# Stat Up v4 — Comprehension, Classifier, Progression

## Context

Tester feedback on v3.1.6 surfaced three blocking problems. All three were confirmed by
measurement, not opinion.

1. **Nobody knew what to do.** Onboarding explains the concept, then drops users into an
   empty Tasks tab. Nothing in the app defines the six stats. The `?` button exists on three
   screens; testers never noticed it.
2. **Nobody knew which stat a task belongs to.** Both pickers default to a hardcoded
   `StatType.STR`. Unlabeled Todoist tasks pass `statType = null` — points hit the balance
   and **no stat at all**.
3. **Rank raced ahead of stats.** Measured on the live build: **S rank on day 25 at average
   stat 8.8.** Decay took −6 per idle day against +1 per active day, so break-even
   consistency was ~86%.

**No DB migration. `DB_VERSION` stays 5.**

### Target after the change

| player | D | C | B | A | S | EX |
|---|---|---|---|---|---|---|
| all 6 default missions daily | d9 | d23 | d42 | d76 | **d144 (4.7mo)** | **d292 (9.6mo)** |
| committed (10/day, 90%) | d11 | d34 | d64 | d105 | d163 (5.4mo) | d659 |
| typical (6/day, 80%) | d7 | d59 | d148 | d247 | d331 | d1178 |

Even maximum effort cannot reach EX before month 9.

---

## Part 1 — First-run comprehension

### 1.1 Guided tutorial (must finish, ~30 seconds)

Runs after onboarding, before the main app unlocks. Only the highlighted element is
interactive at each step.

1. "Tap this task to complete it" → one seeded sample task, nothing else tappable
2. "+5 points earned" → balance animates
3. "Your STR went 5 → 6" → stat bar animates
4. "6 more active days to D rank" → rank progress highlighted
5. "Tap **?** on any screen for help" → the help icon pulses once

> The tutorial task must be worth **5 points**. At 5 points per stat point, a 4-point task
> leaves the bar at 4/5 and the stat does not move — exactly the anticlimax this exists to
> prevent.

Gate on a new `tutorialComplete` DataStore flag, checked alongside `onboardingComplete` in
[AppNavigation.kt:63-83](app/src/main/java/dev/statup/app/ui/navigation/AppNavigation.kt#L63-L83).

### 1.2 Stat meanings

`StatType` holds only `displayName` + `color` ([StatType.kt:6-12](app/src/main/java/dev/statup/app/domain/model/StatType.kt#L6-L12)). Add:

```kotlin
enum class StatType(
    val displayName: String,
    val color: Color,
    val blurb: String,      // "Physical effort and follow-through"
    val examples: String    // "gym, run, chores, early wake-up"
)
```

Colors stay in `ui/theme/Color.kt` (house rule). Show the selected stat's blurb as one
caption line under the picker grid ([TasksScreen.kt:632-662](app/src/main/java/dev/statup/app/ui/screen/tasks/TasksScreen.kt#L632-L662), [StatusScreen.kt:659-663](app/src/main/java/dev/statup/app/ui/screen/status/StatusScreen.kt#L659-L663)) so the
layout is untouched. Use the wording in `database/README.md` — WIS in particular must read
as *"staying on top of your life"*, not just meditation.

### 1.3 Seeded starter content

New `StarterContentSeeder`, modelled on [StatMappingSeeder.kt:24-29](app/src/main/java/dev/statup/app/data/local/db/StatMappingSeeder.kt#L24-L29) — the one existing
seeder doing a proper transactional check-then-insert.

**6 missions**, one per stat, +4 pts, daily. From King's own setup, three generalised:

| stat | name | description |
|---|---|---|
| STR | Workout | Physical effort builds Strength |
| INT | Study or Learn Something | Learning and problem-solving build Intelligence |
| WIS | Book Reading | Reflection and reading build Wisdom |
| DEX | Practice a Skill | Repetition and craft build Dexterity |
| CHA | Talk to Someone | Connecting with people builds Charisma |
| VIT | 7+ hrs Sleep & 2+ Meals | Rest and nutrition build Vitality |

Dropped: "3+ DSA & 5+ ML Concepts" (meaningless to a non-programmer), "Martial Arts" (too
niche), "Social Networking" (reads as scrolling, not contacting a person).

**7 rewards** — the 50 → 1000 ladder teaches the economy by itself. `category` has no
default; pass `"General"` as [RewardsViewModel.kt:48-60](app/src/main/java/dev/statup/app/ui/screen/rewards/RewardsViewModel.kt#L48-L60) does.

| name | cost | description | emoji |
|---|---|---|---|
| Favorite Meal | 50 | — | 🍦 |
| Movie Time | 80 | — | 🎬 |
| Special Outing | 120 | — | 💆 |
| 3hrs Hobby Session | 150 | — | 🎮 |
| Diamond Reward | 250 | A small treat you choose | 🛍️ |
| Mythic Reward | 500 | Something you've been wanting | 🎁 |
| Legendary Reward | 1000 | A trip or a big goal | ✈️ |

Rupee amounts stripped from the top three so they work in any currency.

Gate on a `starterContentSeeded` flag, **not** "is the table empty" — deleting the samples
must be permanent. Call from `StatUpApp.initializeData()` ([StatUpApp.kt:48-66](app/src/main/java/dev/statup/app/StatUpApp.kt#L48-L66)).

### 1.4 "How It Works" screen + help discoverability

- New `Routes.HOW_IT_WORKS`, registered at [AppNavigation.kt:149-175](app/src/main/java/dev/statup/app/ui/navigation/AppNavigation.kt#L149-L175), left out of the
  bottom-bar list at `:100-102` so the bar hides (same as `PRIVACY_POLICY`)
- Reached from **Settings → About** — copy the Privacy Policy row at [SettingsScreen.kt:359-388](app/src/main/java/dev/statup/app/ui/screen/settings/SettingsScreen.kt#L359-L388)
- **Plain language, no RPG jargon.** "daily streak", not "star lines". Read numbers live
  from the constants — no hardcoded values
- Wire the existing `HelpIconButton` ([HelpDialog.kt](app/src/main/java/dev/statup/app/ui/components/HelpDialog.kt)) into **Status** and **Stats**, and make
  the icon visually prominent on all five tabs

### 1.5 Rank progress must show what is missing

Both requirements, always, with the blocker marked. Replaces the hardcoded 5-star display.

```
Rank B → A
  Active days     47 / 60    ← 13 more
  Avg stat        38 / 36    ✓
```

---

## Part 2 — Offline task classifier

**Built and measured.** Not an estimate.

### 2.1 Model

Multinomial logistic regression over hashed character n-grams plus word uni/bigrams — the
fastText shape (Joulin et al. 2016), trained in pure numpy.

- char_wb n-grams 3–6 + word uni/bigrams, `crc32 % 16384` buckets, L2-normalised
- 6 × 16384 int8 weights + scale + bias → **96 KB** at
  `app/src/main/assets/classifier/stat_clf_v1.bin`
- Sub-millisecond, no new dependency, AAB grows 96 KB on 8.0 MB
- Trainer: `scripts/train_stat_classifier.py` — **numpy only, sklearn not required**

| metric | value |
|---|---|
| 5-fold CV | 86.8% (±1.0) |
| **held-out test** | **85.0%** |
| precision @ 0.55 | 89.3% (87.0% coverage) |
| precision @ 0.65 | 92.2% (76.7% coverage) |

### 2.2 Data — `database/`

| file | rows | |
|---|---|---|
| `database/train.csv` | 1800 | 300 per stat |
| `database/test.csv` | 300 | 50 per stat, zero overlap with train |

Both `task,category`. Rules: **minimum 3 words**, ASCII only, no commas, no real personal
names, no near-duplicates. Voice matches King's own Todoist — simple words, plain leading
verb, mixed capitalisation, abbreviations (`imp`, `WA`, `msg`, `hrs`), occasional typos,
Indian context. Full spec in `database/README.md`.

**What the 3-word rule bought:** 1–2 word entries measured at 35–42% accuracy. Removing
them plus re-anchoring on King's vocabulary took held-out accuracy **61.3% → 85.0%**.

### 2.3 Behaviour rules

**Never overwrite an existing category.** Resolution order:
1. Todoist label → `StatMapping`
2. An existing `statType` on the row
3. The classifier
4. `defaultStat`

**Two thresholds**, because the call sites have different costs of being wrong:

| call site | threshold | coverage | precision | why |
|---|---|---|---|---|
| mission / add-points dialog | **0.55** | 87.0% | 89.3% | user sees the chip; a wrong guess costs one tap |
| Todoist sync | **0.65** | 76.7% | 92.2% | nobody is watching; favour precision |

Below threshold the classifier stays silent and behaviour is identical to today — it can
only improve on the status quo, never regress it.

### 2.4 Wiring

Mirror the `QuotePack` shape ([OfflineQuotePack.kt](app/src/main/java/dev/statup/app/quotes/OfflineQuotePack.kt)) — narrow interface, lazy asset load:

```kotlin
interface TaskClassifier { fun classify(text: String): StatSuggestion? }
data class StatSuggestion(val stat: StatType, val confidence: Float)
```

Takes `modelBytes: () -> ByteArray`, not `Context`, following the narrow-ports style of
`DecayEngine` — keeps scoring JVM-testable with no Android dependency.

**The Kotlin reader must match the trainer exactly** or scores are garbage: 16384 buckets,
char n-grams 3–6 over space-padded lowercase words, word uni+bigrams at weight 2.0, CRC32
hashing, L2 normalisation. Blob layout: `"STCL"` magic, `<HHH` version/buckets/classes,
`<f` scale, 6 × float32 bias, then `[class][bucket]` int8 weights.

| call site | change |
|---|---|
| `CreateMissionDialog` ([TasksScreen.kt:552-556](app/src/main/java/dev/statup/app/ui/screen/tasks/TasksScreen.kt#L552-L556)) | debounced classify of the name; pre-select the picker with a "suggested" marker. Replaces the hardcoded `STR` |
| `AddPointsDialog` ([StatusScreen.kt:550-552](app/src/main/java/dev/statup/app/ui/screen/status/StatusScreen.kt#L550-L552)) | same, on the description. Also replaces a hardcoded `STR` |
| `TodoistSyncManager` ([:43-53](app/src/main/java/dev/statup/app/sync/TodoistSyncManager.kt#L43-L53)) | classify `completedTask.content` instead of passing `statType = null` |

`getDefaultStat()` is `private` at [PointsRepository.kt:261](app/src/main/java/dev/statup/app/data/repository/PointsRepository.kt#L261) — widen to `internal`.

### 2.5 Backfill for already-completed tasks

**Settings → "Assign missing categories"**: finds every `EARN` transaction with
`statType IS NULL`, classifies its `description`, writes the stat, and credits the
accumulator so past tasks finally count. One transaction, reports "N tasks categorised",
never touches rows that already have a `statType`.

### 2.6 Accepted limitations

- STR is weakest at 64% recall — manual-labour vocabulary (firewood, plough, wheelbarrow)
  is thin, and a few STR test rows are arguably DEX.
- Scored **5/8** against King's own labelled Todoist tasks. His real labels and this spec
  disagree in places (he files `CA confirmation` as INT; the spec says WIS). Labelling ~100
  real tasks would resolve it — worth doing later, not a blocker.
- Real inboxes contain 1–2 word tasks the training set deliberately excludes.

**Deferred:** persisting user overrides as `StatMapping` rows; Gemini fallback for
low-confidence. Both additive.

---

## Part 3 — Progression rebalance

### 3.1 Conversion: flat 5 points per stat point

Replaces `POINTS_PER_STAT = 10`. **One number, no tiers** — the rank gate does the pacing,
so a difficulty curve would do the same job twice.

Rework the carry in `PointsRepository.updateStatAccumulator` ([:185-241](app/src/main/java/dev/statup/app/data/repository/PointsRepository.kt#L185-L241)); keep the
existing MAX_STAT freeze and remainder-discard at `:211-225`. **Zero test coverage today** —
add tests.

**Maxed stats: leave as-is.** Points routed to a stat at 100 are discarded and stay
discarded — King's call, deferred. No UI or redistribution work.

### 3.2 Seven ranks, two requirements each

Add **EX** above S. `EX` not `SSS` — the badge renders one glyph and two characters fit
where three would crowd. Promotion needs **both** columns. Demotion is **days only** — stat
decay must never demote, or a missed day punishes twice.

| rank | total active days | avg of all six stats |
|---|---|---|
| D | 7 | 6 |
| C | 15 | 14 |
| B | 30 | 24 |
| A | 60 | 36 |
| S | 120 | 50 |
| **EX** | **240** | **80** |

**The day counter is cumulative, not per-rank.** `+1` per active day, `−1` per idle day,
never resets on promotion. This removes the day-after-promotion cliff permanently: under a
resetting counter you sit at maximum demotion risk after *every* promotion, forever. Under
a cumulative one your safety margin grows as you work.

It also deletes logic rather than adding it. Rank becomes a lookup: promote when
`days >= req && avg >= gate`, demote while `days < req(currentRank)`. No reset value, no
bounce-back special case — [RankLogic.kt:28-65](app/src/main/java/dev/statup/app/rpg/RankLogic.kt#L28-L65) gets simpler.

`Rank` gains a seventh entry plus `statsRequired` and `daysRequired`. Rank is stored as a
`String`, so **no DB migration**. `rankUpStreakCounter` now holds cumulative active days —
same column, new meaning. `rankDownBreakCounter` stays dead and unused.

### 3.3 Decay

- **No grace day.** Every missed day costs −1 from the **single highest** stat above base
- Replaces the all-six loop at [DecayEngine.kt:103-109](app/src/main/java/dev/statup/app/rpg/DecayEngine.kt#L103-L109)
- At 5 pts/stat an active day earns +2 to +4, so a miss stings but recovers same-day —
  versus today's six-day hole
- Shield consumption ([:67-75](app/src/main/java/dev/statup/app/rpg/DecayEngine.kt#L67-L75)) unchanged; it still pre-empts decay, which is the only
  thing keeping the shipped `streakShields` feature meaningful

### 3.4 One-time recompute

App is in beta with few installs, so recompute rather than grandfather. **No schema
change** — `transactions.statType` is populated, so lifetime points per stat are pure SQL.
Gate on a `statCurveVersion` DataStore flag, run once from `initializeData()`.

```
earned[s]  = SELECT SUM(points) FROM transactions WHERE type='EARN' AND statType = s
newStat[s] = min(MAX_STAT, BASE_STAT + earned[s] / 5)
```

- Run **after** the category backfill (2.5), so newly-categorised history counts
- Ignore `decay_log`; the old decay is the bug being fixed and its record is incomplete
- Recompute rank from the resulting stats + banked active days
- **Never touch `transactions`, balance, redemptions or achievements**

### 3.5 Hardcoded values that will silently desync

| file:line | what |
|---|---|
| [PlayerStatsTest.kt:30](app/src/test/java/dev/statup/app/rpg/PlayerStatsTest.kt#L30) | `assertEquals(10, POINTS_PER_STAT)` — hard pin |
| [DecayEngineTest.kt:62-73](app/src/test/java/dev/statup/app/rpg/DecayEngineTest.kt#L62-L73) | asserts `statsLost == 1` per stat — pins the old decay |
| [RankCalculatorTest.kt:25-32](app/src/test/java/dev/statup/app/rpg/RankCalculatorTest.kt#L25-L32) | asserts `Rank.S.order == 5` and `S.nextRank() == null` — breaks with a 7th rank |
| [StatsScreen.kt:469](app/src/main/java/dev/statup/app/ui/screen/stats/StatsScreen.kt#L469), [:527](app/src/main/java/dev/statup/app/ui/screen/stats/StatsScreen.kt#L527) | `"Every 10 points earned = +1 stat point"`, `"$accumulator/10"` |
| [StatsScreen.kt:480](app/src/main/java/dev/statup/app/ui/screen/stats/StatsScreen.kt#L480) | unclamped `accumulator / POINTS_PER_STAT` → progress bar > 1f |
| [TasksScreen.kt:835](app/src/main/java/dev/statup/app/ui/screen/tasks/TasksScreen.kt#L835) | help text `"10 points = +1 stat"` |
| `StatusWindow.kt` `:196 :197 :219 :312 :357 :359` | the 5-star display, hardcoded in six places |
| [AgentContextBuilder.kt:82](app/src/main/java/dev/statup/app/ai/AgentContextBuilder.kt#L82) | persona says `"5 active days = rank up"` — the coach gives wrong advice unless updated |
| `Color.kt` | needs a seventh rank colour |
| [README.md:16](README.md#L16), [:20](README.md#L20), `CLAUDE.md` | documented mechanics |

`RankCalculator` and `StatusViewModel.getDaysToRankUp()` are dead — `getDaysToRankUp()` has
no callers. Delete both or repoint them at the new progress block; do not leave a third
source of truth.

---

## Build order

1. **Progression** (Part 3) — independent of everything else, start here
2. **Comprehension** (Part 1) — seeded content, tutorial, stat blurbs, How It Works
3. **Classifier wiring** (Part 2) — the Kotlin reader + three call sites; model and data
   already exist
4. **Backfill then recompute**, in that order, once 2 and 3 are in

## Files

**Already created:** `database/train.csv`, `database/test.csv`, `database/README.md`,
`scripts/train_stat_classifier.py`, `app/src/main/assets/classifier/stat_clf_v1.bin`.

**New:** `StarterContentSeeder.kt`, `ui/screen/help/HowItWorksScreen.kt`,
`ui/screen/tutorial/`, `ai/classifier/TaskClassifier.kt` + `HashedLinearTaskClassifier.kt`,
plus tests.

**Modified:** `StatType.kt`, `PlayerStats.kt`, `Rank.kt`, `RankLogic.kt`, `DecayEngine.kt`,
`PointsRepository.kt`, `PlayerRepository.kt`, `PlayerStatsDao.kt`, `TodoistSyncManager.kt`,
`UserPreferences.kt`, `StatUpApp.kt`, `AppModule.kt`, `Routes.kt`, `AppNavigation.kt`,
`OnboardingScreen.kt`, `TasksScreen.kt`, `StatusScreen.kt`, `StatsScreen.kt`,
`SettingsScreen.kt`, `StatusWindow.kt`, `AgentContextBuilder.kt`, `Color.kt`, 4 test files.

**Not touched:** `AppDatabase.kt` — **`DB_VERSION` stays 5, no new `Migration`.**

## Verification

```bash
./gradlew :app:testDebugUnitTest    # 36 existing + new; 4 files need updating
./gradlew lintDebug
./gradlew assembleDebug
./gradlew bundleRelease             # AAB must stay ≈8.1 MB (+96 KB, not +MB)
./gradlew connectedDebugAndroidTest # MigrationTest — the data-loss guard
python3 scripts/train_stat_classifier.py   # must still report ≥85% held-out
```

New JVM tests, house style (JUnit4 + hand-written fakes, no mocking library —
`StatsEngineTest` for pure mappers, `DecayEngineTest` for anything with ports):

1. **Conversion** — 5 pts = 1 stat; MAX_STAT clamp; remainder carries; maxed stat discards
2. **Decay** — every missed day takes exactly 1 from the highest stat only; floors at base;
   shield still pre-empts
3. **Rank** — promotion needs both days and stats; stats never demote; cumulative counter
   survives promotion; EX reachable; demotion reversible by one active day
4. **Recompute** — known lifetime points land on expected stats; runs once; transactions
   untouched
5. **Classifier** — the Kotlin reader reproduces the Python scores on a fixed sample (guards
   the feature-extraction contract); low confidence returns `null`; never overwrites an
   existing category

Manual pass on Waydroid (`waydroid app launch dev.statup.app.debug`):

- Fresh install → onboarding → **tutorial cannot be skipped** → Tasks and Rewards open
  populated; deleting all samples and restarting does not bring them back
- Type "go for a 5k run" → picker pre-selects STR with a suggestion marker; override sticks
- Complete a mission → the stat moves **visibly** on the first completion
- Rank block shows both requirements and marks which one is blocking
- Settings → "Assign missing categories" → uncategorised Todoist tasks get stats
- Settings → About → How It Works renders; numbers match the shipped constants
- Upgrade in place from the old build → stats jump up, history and balance unchanged
