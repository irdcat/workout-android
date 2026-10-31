# Workout

An Android app for following a training plan kept in a Google Sheets spreadsheet. It reads
the plan, guides you through each workout set by set with rest timers, and writes your
results back into the sheet.

## Features

- **Week view:** the workouts planned for the current week, read from the spreadsheet.
- **Browse:** every month, week and workout in the plan.
- **Workout session:** step through the exercises with warm-up sets and a result editor
  (weight, reps, RPE). It also shows what you did last time and the tempo for each exercise.
  Every result needs an RPE. It's written to the sheet as `…, RPE 9`, and left out when it
  matches the RPE the row asks for.
- **Rest timer:** starts after each set that has a rest time. A row with no rest of its own
  takes the longest rest from the exercise's other rows. You pick the rest yourself only when
  none of them gives one. The timer vibrates and/or plays a sound when the rest ends, even
  with the app in the background.
- **Summary:** total workout time and what was logged.
- **Analysis:** planned against actual, week by week, for groups of exercises: volume, top
  set weight and RPE, over 8 weeks, 16 weeks or everything. Each group lists the names that
  count toward it, ignoring case, accents and notes in brackets. The defaults are Squat,
  Bench and Deadlift by their English and Polish names, e.g. `Squat` / `Przysiad` or
  `Deadlift` / `Martwy ciąg`. In Settings → Exercise groups you can add, rename, reorder and
  remove groups and their exercises, with names from your sheet suggested as you type.
- **Scores:** DOTS, Wilks and Wilks 2020 from your bodyweight and total.
- **Offline cache:** the plan is cached in Room, so the app works without a connection.
  Results wait until the next sync and are then written to the sheet.

## Spreadsheet format

Each tab is one month, for example `CZERWIEC`. In Setup you can blacklist tabs so they
aren't loaded. Rows use columns A–H:

| Row type          | A                          | B           | C    | D    | E          | F     | G    | H     |
|-------------------|----------------------------|-------------|------|------|------------|-------|------|-------|
| Week              | `dd.MM.yyyy - dd.MM.yyyy`  |             |      |      |            |       |      |       |
| Workout header    | workout code, e.g. `T1`    | `ĆWICZENIE` |      |      |            |       |      |       |
| Exercise          | number (`5`, `5a`…)        | name        | sets | reps | weight/RPE | tempo | rest | WYNIK |
| Continuation      | *(empty)*                  | note        | sets | reps | weight/RPE | tempo | rest | WYNIK |

The date range can be in column A or B. A week row with `WOLNE` in a later cell marks a rest
week. A continuation row adds another set group to the exercise above it.

Accepted cell notation (see `SheetNotation.kt`):

- **Sets:** a number, `x` (open-ended), or empty (unlimited).
- **Reps:** `8`, `8-12`, `30s` / `30-45 sek`, `10/str` (per side), `20 kroków` (steps),
  `2 długości` (lengths), `<10` (up to).
- **Weight:** `60`, `60 kg`, `60-70 kg`, `20/22,5 kg` (choice), `2x16 kg` (implements),
  `RPE 8`, or `-`/empty for bodyweight.
- **Tempo:** four characters from `0-9`, `X` and `-`, e.g. `30X1`.
- **Rest:** `90`, `90s`, `2 min`. If a row leaves it empty, it uses the longest rest from
  the exercise's other rows.
- **Time limit:** written in the exercise text, e.g. `Czas: 10:00` or `czas 12 min`.

Text that can't be parsed is kept and shown as written.

## Setup

### Requirements

- Android Studio with JDK 21 (the code targets Java 17)
- Android SDK with compile SDK 37
- A device or emulator with Google Play Services (API 24+)

### Google Cloud

Sign-in uses Credential Manager. Sheets access goes through `AuthorizationClient` with the
`spreadsheets` and `drive` scopes. In the Google Cloud Console:

1. Enable the **Google Sheets API** and the **Google Drive API**.
2. Configure the OAuth consent screen and add your account as a test user.
3. Create an **Android** OAuth client for package `com.github.irdcat.workout` with your
   signing key's SHA-1 (`./gradlew signingReport`).
4. Create a **Web application** OAuth client and copy its client ID.

### `local.properties`

Put the Web client ID in `local.properties`. That file is gitignored, so the ID stays out of
the repository:

```properties
google.serverClientId=1234567890-abc123.apps.googleusercontent.com
```

The build turns it into the `google_server_client_id` string resource. Without it the app
still builds, but Sign in with Google won't work. Gradle prints a warning when it's missing.

### First run

Sign in with Google, paste the spreadsheet link (`https://docs.google.com/spreadsheets/d/<id>/edit`),
and optionally blacklist tabs. The plan syncs and the week view opens.

## Building and testing

```bash
./gradlew :app:assembleDebug                        # debug APK
./gradlew :app:testDebugUnitTest                    # JVM tests (Robolectric for Compose UI)
./gradlew :app:connectedDebugAndroidTest            # device tests
./gradlew :app:createDebugUnitTestCoverageReport    # JaCoCo coverage for the JVM tests
./gradlew detekt                                    # static analysis
```

Detekt is configured in `config/detekt/detekt.yml`, and any finding fails the build. The
config leaves composables out of `LongMethod` and `TooManyFunctions`, and Room DAO queries out
of `TooManyFunctions`.

The sheet parser tests run against real month exports in `app/src/test/resources/test-data/`.
Fakes and the navigation tests in `app/src/sharedTest/` run both on the JVM and on a device.

## Architecture

Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, Navigation Compose with type-safe
routes, and kotlinx.serialization.

```
app/src/main/kotlin/com/github/irdcat/workout/
├── application/ui/    # Compose screens, view models, navigation, theme
├── domain/            # plan model, sheet notation, session, results, analysis, settings
└── infrastructure/
    ├── cache/         # Room database: plan cache, saved results, session log
    ├── google/        # sign-in, Sheets API, sheet parser, repositories
    ├── session/       # active session (DataStore) and session log (Room)
    ├── settings/      # settings, rest alarms and notifications
    └── time/, coroutines/
```

The domain layer defines repository interfaces. Infrastructure implements them and binds them
with Hilt modules.

## Licenses

The bundled fonts, Space Grotesk and JetBrains Mono, are under the SIL Open Font License.
See `app/src/main/assets/licenses/`.
