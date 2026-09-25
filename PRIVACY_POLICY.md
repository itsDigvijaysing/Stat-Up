# Privacy Policy for Stat Up

**Last updated: June 11, 2026**

<!--DEV-NOTE-START-->
> **Maintainer note (stripped from the in-app copy):** this file is the single source of truth
> for the privacy policy. The `syncPrivacyPolicy` Gradle task copies it to
> `app/src/main/assets/privacy_policy.md`, which `PrivacyPolicyScreen` renders at
> Settings → About → Privacy Policy. Edit here only - never edit the asset copy.
<!--DEV-NOTE-END-->

## 1. Summary

Stat Up is an offline-first app. The core features - stats, ranks, streaks, tasks, missions, rewards, achievements, history, and mood check-ins - run entirely on your device and never communicate with any server. Two features are *opt-in* network integrations: Todoist sync and the AI Coach (Google Gemini). Neither runs unless you explicitly add your own API key/token in Settings.

## 2. Data Stored Locally on Your Device

- Reward points earned and spent
- Daily mood feedback
- Achievement records
- Mission and task completion history
- Stat values, rank, work days, streak, and decay logs
- Your username (only what you type into Settings)
- Your preferences (hexagon style, animation toggles, haptics, etc.)
- Your Todoist API token, if you connect Todoist (stored encrypted via AES-256-GCM in `EncryptedSharedPreferences`)
- Your Google Gemini API key, if you connect the AI Coach (stored encrypted via AES-256-GCM in `EncryptedSharedPreferences`)
- Your AI Coach conversation history is **not** written to the database. It lives only in the in-memory `AgentViewModel` for the lifetime of the app process; it is discarded when the OS kills the process or when you full-reset the app. Switching tabs does not on its own erase the transcript (the ViewModel is retained as long as the activity lives), but nothing is ever persisted to disk.

## 3. Optional Network Features

### 3a. Todoist Sync (opt-in)

- Triggered only if you enter your Todoist API token in Settings.
- The app contacts `api.todoist.com` to fetch your active and completed tasks, on app open and every 15 minutes in the background.
- Your token is sent only to Todoist, encrypted in transit (HTTPS). No other server receives it.
- You can disconnect any time in Settings → Integrations → Todoist.

### 3b. AI Coach (opt-in, Google Gemini)

- Triggered only if you enter a Google Gemini API key in Settings and open the Agent tab.
- When you send a message, the app contacts `generativelanguage.googleapis.com` (Google's Generative Language API) and sends:
  - your typed message
  - a short snapshot of your current player state: name, rank, work day count, streak, six stat values and their average, total points earned, your last 5 earn transactions, and up to 4 active missions (used to ground the coach's replies in your data)
- Replies come back from Google. Google's data-handling for this API is governed by Google's own privacy policy at https://policies.google.com/privacy. Google's terms for AI APIs are at https://ai.google.dev/terms.
- Your API key is sent only to Google, encrypted in transit (HTTPS).
- You can disconnect any time in Settings → Integrations → AI Agent.

## 4. What We Do NOT Do

- No accounts, no sign-up, no cloud sync of your data.
- No analytics, telemetry, crash reporting, or fingerprinting.
- No advertising and no ad SDKs.
- No tracking IDs, no third-party SDKs other than the open-source libraries used to talk to Todoist and Gemini when you opt in.
- We (the app developer) do not operate any server and do not receive any of your data.

## 5. Data Retention

On-device data persists until you:

- Use the "Full Reset" button in Settings → Danger Zone, which wipes the database and preferences.
- Clear app data through your device's system settings.
- Uninstall the app.

If you have Android's automatic backup enabled ("Back up to Google Drive"), your on-device app data - the local Room database (transactions, stats, missions, history, etc.) and your app settings and preferences (display name, chosen default stat, quote source, and similar) - may be included in your personal, end-to-end-encrypted Google backup so it can be restored when you reinstall. Your encrypted secrets (Todoist token, Gemini API key) are explicitly excluded from backup. This backup stays between your device and your own Google account; the app developer never receives it.

For data sent to Todoist or Google: those services retain it according to their own policies. The app cannot delete data on their servers; use their respective settings to manage your data there.

## 6. Security

- All locally stored secrets (Todoist token, Gemini API key) use AndroidX Security's `EncryptedSharedPreferences` (AES-256-GCM, master key in Android Keystore).
- Other on-device data is stored in a plain Room SQLite database - protected by Android's per-app sandboxing but not separately encrypted at rest.
- All network traffic uses HTTPS.

## 7. Children's Privacy

Stat Up is intended for users aged 13 and over, and is not directed to children. The app itself does not collect personal information. The optional AI Coach sends your player state to Google's Generative Language API, so it should not be enabled by anyone under 13; see Google's age and consent policies for that API.

## 8. Changes to This Policy

We may update this policy as features change (in particular, if more optional network integrations are added). Material changes will be reflected in the app's release notes.

## 9. Contact

For questions about this privacy policy or the app: itsdigvijaysing@gmail.com

## 10. Compliance

This app is designed to comply with:

- Google Play Store policies
- Samsung Galaxy Store policies
- GDPR (data subject rights - the app stores data only locally; subject access / deletion is achieved via the in-app Full Reset)
- COPPA (no personal information is collected by the app itself; the AI Coach should not be enabled for children without parental review of Google's policies)
