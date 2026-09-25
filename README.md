# Stat Up

An offline-first Android app that turns daily tasks into RPG progression. Complete tasks to earn
points, level six character stats, hold a streak to climb the rank ladder, and spend points on
rewards you define yourself.

![Stat Up](asset/STAT_UP.webp)

Demo: https://www.youtube.com/watch?v=gvXfM7x2DlU

## Features

**Progression**
- Six stats — Strength, Intelligence, Wisdom, Dexterity, Charisma, Vitality — shown on a hexagon
  radar chart, range 5–100.
- Points convert to stats at 5 points per stat point; the remainder is carried in a per-stat
  accumulator.
- Rank ladder E → D → C → B → A → S → EX gated on **both** cumulative Work Days and average
  stat: D 7/6, C 15/14, B 30/24, A 60/36, S 120/50, EX 240/80. Work Days are +1 per active day,
  −1 per idle day, floored at 0, and **never reset on promotion**.
- Demotion is Work Days only — losing a stat point never costs a rank.
- Daily decay at midnight: an idle day costs 1 Work Day and 1 point from the single highest stat.
- Streak Freeze Shield — 30 points, max 3. An idle day consumes one instead of decaying stats.
- First-run guided tutorial (real task → stat → reward → achievement) and a plain-language
  "How It Works" screen under Settings → About.

**Tasks**
- Manual missions with priority P1–P4 and a stat assignment. P1=4pts, P2=3pts, P3=2pts, P4=1pt.
- Offline stat classifier (96 KB, on-device, no network) pre-selects the stat for a typed task
  name and fills in the stat for unlabelled Todoist tasks. Never overwrites a category you set.
- Daily missions reset at local midnight, including when the app is never opened.
- Optional Todoist sync — label-to-stat routing, deduplicated by task ID, runs every 15 minutes.

**Rewards and achievements**
- User-defined rewards with preset or custom costs up to 999,999, editable after creation.
- 12 built-in achievements plus custom ones; unlocked titles can be equipped under your name.
- Full transaction history with filtering and pagination.

**Other**
- Mood check-in, once per local day, +2 Wisdom.
- Daily quote — offline pack by default, or Animechan / ZenQuotes / mixed.
- 4x2 home-screen widget showing rank, balance, streak and today's points.
- Optional Gemini-backed AI coach that reads your current stats to give grounded advice.
- Dark-only Material 3 UI with GPU backdrop blur on API 31+.

## Building

Requires JDK 17 (Gradle 8.13 does not run on JDK 24+) and the Android SDK with platforms
`android-35` and `android-36`, build-tools `35.0.0` and `36.0.0`.

```bash
git clone https://github.com/itsDigvijaysing/Stat-Up.git
cd Stat-Up

./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # report -> app/build/reports/
./gradlew bundleRelease          # Play-ready AAB
```

Release builds fall back to the debug keystore if `keystore.properties` is missing, and print a
warning when they do. Copy `keystore.properties.template` to `keystore.properties` and fill in real
values before producing anything for Play.

Testing on Waydroid:

```bash
waydroid prop set persist.waydroid.multi_windows true
waydroid session stop && waydroid session start

waydroid app install app/build/outputs/apk/debug/app-debug.apk
waydroid app launch dev.statup.app.debug
```

## Architecture

MVVM in explicit layers, wired through Koin in `di/AppModule.kt`.

```
UI (Compose)          ui/screen/*, ui/components/*
ViewModel             ui/screen/<feature>/<Feature>ViewModel.kt
Repository            data/repository/*
Room DAO + DataStore  data/local/db/*, data/local/datastore/*
RPG engines           rpg/StatsEngine, DecayEngine, RankLogic, StatRecomputer, AchievementTracker
Workers               sync/DecayWorker, sync/TodoistSyncWorker
```

- Kotlin 2.1.20, Compose BOM 2025.03.01, Material 3
- Room 2.7.1 (schema v5, migrations exported to `app/schemas/`)
- DataStore 1.1.4 for preferences; AndroidX Security 1.1.0 (`EncryptedSharedPreferences`,
  AES-256-GCM) for the Todoist token and Gemini key
- Ktor 3.1.2, Koin 3.5.6, WorkManager 2.10.1, Haze 1.7.2
- minSdk 26, targetSdk 36, compileSdk 36

Every multi-write earn or redeem path runs inside a single Room transaction, and daily decay is
idempotent per local day so a retried worker cannot double-apply it.

### Design tokens

```
BackgroundBase  #0A0A0F        GlassFill    white @ 10%
SurfaceBase     #12121A        GlassBorder  white @ 18%
AccentPrimary   #7C4DFF        GlassRadius  24.dp
PointsGold      #FFD740
```

## Privacy

All gameplay data stays on the device in a local Room database. There are no accounts, no
analytics, no telemetry, no crash reporting and no ads.

The app makes no network requests until you enable one of four optional integrations in Settings:

| Integration | Endpoint | Enabled by |
| --- | --- | --- |
| Todoist sync | `api.todoist.com` | Your Todoist API token |
| AI coach | `generativelanguage.googleapis.com` | Your Gemini API key |
| Anime quotes | `api.animechan.io` | Choosing the Anime or Mixed quote source |
| Motivation quotes | `zenquotes.io` | Choosing the Motivation or Mixed quote source |

The daily quote defaults to a bundled offline pack, so a fresh install is fully offline. The AI
coach sends a snapshot of your current stats, rank, streak and recent activity with each message —
Settings shows exactly what is sent before you enable it. API tokens are stored encrypted and are
excluded from Android backups.

Full policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md), also readable in-app at Settings → About.

## Roadmap

Shipped through v3.1.4: the full stat and rank system, Todoist sync, decay and shields,
achievements and titles, the AI coach, the home-screen widget, notifications for sync results and
rank changes, onboarding, and daily quotes.

Not built yet:

- Evening "no points yet today" reminder — needs its own scheduled worker.
- Widget size variants and a configuration activity.
- Persisted AI conversations. The `ai_conversations` table exists but has no DAO or consumer.
- Dependency refresh — Compose BOM and Koin are both well behind current.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
