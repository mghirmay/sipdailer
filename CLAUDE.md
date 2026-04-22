# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

An Android SIP/VoIP dialer app built on the Linphone SDK, targeting the `sinitpower.de` SIP server. Package: `com.example.android.sip`, minSdk 26, targetSdk 34.

## Build & Deploy

Standard Gradle project. No tests, no CI/CD.

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
# or
adb install app/build/outputs/apk/debug/app-debug.apk
```

The root `build.gradle`, `app/build.gradle`, and `settings.gradle` configure the Linphone Maven repo (`https://linphone.org/maven_repository`) and declare `org.linphone:linphone-sdk-android:5.2.+`. Supported ABIs: arm64-v8a, armeabi-v7a.

## Architecture

Active source files are under `app/src/main/java/com/example/android/sip/`:

- **`LinphoneService.java`** — Foreground service (type: microphone) that owns the Linphone `Core` for the app lifetime. Handles account registration, outgoing/incoming calls, and broadcasts state changes (`REGISTRATION_STATE_CHANGED`, `CALL_STATE_CHANGED`) via local intents. Public API: `registerAccount()`, `makeCall(address)`, `answerCall()`, `hangUp()`, `isInCall()`.

- **`MainActivity.java`** — Entry point. Bottom navigation (Dialpad / Contacts / History / Settings tabs), binds to `LinphoneService`, requests runtime permissions (RECORD_AUDIO, READ_CONTACTS, POST_NOTIFICATIONS), and shows a colored status indicator reflecting registration state. Listens for preference changes to trigger re-registration automatically.

- **`WalkieTalkieActivity.java`** — Alternative single-button UI. Large image button opens a dialog with an inline dialpad to enter a callee address. Binds the same `LinphoneService`; used as a simpler call-initiation path.

- **`IncomingCallActivity.java`** — Full-screen incoming call UI, shown over the lock screen. Displays caller info with Answer/Decline buttons. Finishes automatically when a `CALL_ENDED` broadcast is received.

- **`SipSettings.java`** — `PreferenceFragmentCompat`-based settings activity. Preference keys: `namePref`, `authIdPref`, `passPref`, `domainPref` (default: `sinitpower.de`), `portPref` (default: `5060`), `transportPref` (UDP/TCP/TLS), `enabledPref`, `reconnectPref`.

- **`DialpadFragment.java`** / **`ContactsFragment.java`** / **`PlaceholderFragment.java`** — Fragments hosted by `MainActivity`. `ContactsFragment` reads the device contacts via `ContentResolver` and calls `LinphoneService.makeCall()` on tap.

**Call flow:** `LinphoneService.onCreate()` creates a Linphone `Core` and calls `registerAccount()` → `MainActivity` binds and monitors broadcasts → user dials via `DialpadFragment` or `ContactsFragment` → `LinphoneService.makeCall()` → `Core.inviteAddressWithParams()`. Incoming calls arrive via `CoreListenerStub.onCallStateChanged()`; `LinphoneService` launches `IncomingCallActivity` and notifies.

## Key Implementation Notes

- SIP address completion: if the entered address lacks `@`, the domain from `domainPref` is appended before calling Linphone.
- Speaker mode is enabled by default when a call starts.
- `LinphoneService` must remain running for incoming calls to be received; it is started in `MainActivity.onStart()` and should not be stopped while the app is in use.
- Media encryption is disabled (`NONE`); SRTP or ZRTP can be enabled via `CallParams`.

## Legacy Code

`src/com/example/android/sip/` (root-level, not the `app/` module) contains the original Android SipDemo code using the deprecated `android.net.sip.*` APIs (`WalkieTalkieActivity`, `IncomingCallReceiver`, `SipSettings`). These files are **not compiled** in the current Gradle build and are kept only for historical reference.
