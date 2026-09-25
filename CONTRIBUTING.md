# Contributing to Stat Up

## Principles

These constrain what gets merged, so check a proposal against them first.

- **Offline first.** Core gameplay works with no network. Every network surface is opt-in and
  gated behind a credential or setting the user supplies. Do not add a call that runs by default.
- **Privacy.** No analytics, telemetry, crash reporting or ads. No accounts.
- **FOSS dependencies only.**
- **API 26+.** Anything newer needs a runtime guard and a working fallback.
- **Never break existing saves.** Updates must not wipe user data - see Database below.

## Setup

- JDK 17. Gradle 8.13 does not run on JDK 24+.
- Android SDK: platforms `android-35` and `android-36`, build-tools `35.0.0` and `36.0.0`,
  plus platform-tools.
- Android Studio Iguana or newer, or just Gradle and the SDK command-line tools.

```bash
git clone https://github.com/itsDigvijaysing/Stat-Up.git
cd Stat-Up
./gradlew assembleDebug
```

The project targets minSdk 26, targetSdk 36, compileSdk 36. compileSdk 36 is required, not
optional - transitive AndroidX dependencies fail the build below it.

## Architecture

- MVVM: Screen → ViewModel → Repository → Room/DataStore/Ktor.
- All dependency wiring goes through Koin in `di/AppModule.kt`. A new ViewModel, repository or
  engine that is not registered there will fail at runtime, not compile time.
- Keep point math, decay and rank transitions inside the engines in `rpg/`, not in ViewModels.
  `StatsEngine.calculateTaskPoints` is the only priority-to-points mapping; call it, don't
  reimplement it.
- Multi-write earn and redeem paths must run inside a single `database.withTransaction { }`.
- Secrets go in `SecretStorage` (`EncryptedSharedPreferences`), never plain DataStore.

## Database

Schema changes are the highest-risk change in this codebase. To bump the schema:

1. Increment `DB_VERSION` in `AppDatabase.kt`.
2. Add a `Migration` to `AppDatabase.ALL_MIGRATIONS`.
3. Run `./gradlew connectedDebugAndroidTest` on a device or emulator.

`MigrationTest` builds an old database and migrates it forward against the exported schemas in
`app/schemas/`. It fails if a migration is missing or wrong. The database builder deliberately uses
`fallbackToDestructiveMigrationOnDowngrade(false)`, so a forgotten migration throws at startup
instead of silently deleting the user's progress. Do not replace it with
`fallbackToDestructiveMigration`.

Do not rename the package, the Room database name, the DataStore name or the encrypted prefs file
without a migration plan. Existing installs would orphan their data.

## Testing

```bash
./gradlew testDebugUnitTest       # JVM tests
./gradlew connectedDebugAndroidTest  # instrumented, needs a device or emulator
./gradlew lintDebug
```

CI runs the unit tests, lint and a debug build on every push and pull request. Before opening a PR,
confirm the build is clean, and manually verify offline behaviour with airplane mode if you touched
anything network-adjacent.

## Pull requests

- Branch from `main`, one logical change per commit, specific commit messages.
- Do not add AI attribution or generated-by trailers to commits.
- Include screenshots for UI changes.
- Update `README.md` when you change behaviour it documents. If you change what the AI coach sends
  to Gemini, update the in-app disclosure in `SettingsScreen.kt` in the same commit - Play's user
  data policy requires it to be accurate.

## Security

Report vulnerabilities privately via a
[GitHub security advisory](https://github.com/itsDigvijaysing/Stat-Up/security/advisories/new)
rather than a public issue.

- Never log tokens, API keys or chat transcripts.
- New secrets go through `SecretStorage`. `KEY_TODOIST_TOKEN` and `KEY_GEMINI_API_KEY` are the
  model to follow.
- All traffic is HTTPS; `network_security_config.xml` trusts system anchors only and disables
  cleartext. Keep it that way.
- The `INTERNET` permission exists for the opt-in integrations only. Discuss before adding a new
  network call.
