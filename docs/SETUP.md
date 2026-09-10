# Build and run Tri Force

[Overview](../README.md) · [Contribution guide](../CONTRIBUTING.md)

## Toolchain

Install Android SDK Platform 36, Build Tools 35.0.0, Platform Tools, and JDK 17 or a compatible newer JDK. Use Android Studio with AGP 8.11 support, or the Gradle wrapper from a terminal.

The repository pins AGP 8.11.0 and Gradle 8.14.3. AGP 8.11 requires at least Gradle 8.13 and JDK 17 and supports API 36. See the [AGP compatibility table](https://developer.android.com/build/releases/agp-8-11-0-release-notes).

Set `JAVA_HOME` to your JDK directory. Select the same Gradle JDK in Android Studio. For the Android SDK, either set `ANDROID_HOME` or let Android Studio create `local.properties`. A portable Windows example is:

```properties
sdk.dir=C:/Users/YOUR_USER/AppData/Local/Android/Sdk
```

Keep machine-specific paths out of tracked files. The app's minimum Android version is API 26; `compileSdk` is 36 and `targetSdk` is 34.

## Firebase

1. Create your own Firebase project.
2. Register an Android application with package `com.google.mediapipe.examples.poselandmarker`.
3. Enable **Authentication → Sign-in method → Email/Password**.
4. Create the default Cloud Firestore database.
5. Download the Android config to `app/google-services.json`.
6. Configure Firestore rules before testing account data.

Firebase's [Android setup guide](https://firebase.google.com/docs/android/setup) explains registration and the configuration file. The Gradle Google Services plugin converts that file into Android resources; `FirebaseConfig` initializes Firebase from those resources. There is no manual API-key entry step in Kotlin.

The following rules express the current owner-only data model. They allow a signed-in user to access their profile and its descendants; paths outside that tree remain denied.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null
                        && request.auth.uid == userId;
      match /{document=**} {
        allow read, write: if request.auth != null
                          && request.auth.uid == userId;
      }
    }
  }
}
```

This is a starting point for a development project, not a deployed or tested ruleset. Add field validation for your deployment and verify unauthenticated/cross-account requests are denied. See [Firestore authentication conditions](https://firebase.google.com/docs/firestore/security/rules-conditions).

The catalog is local in `ExerciseCatalog.kt`; no global exercise collection needs to be seeded. New users create their own profile and plan through onboarding.

### Build-only configuration

To compile or run JVM tests without creating a Firebase project, copy the dummy example **only if you do not already have a local configuration**.

Windows PowerShell:

```powershell
if (-not (Test-Path app/google-services.json)) {
    Copy-Item app/google-services.json.example app/google-services.json
}
```

macOS / Linux:

```bash
if [ ! -f app/google-services.json ]; then
  cp app/google-services.json.example app/google-services.json
fi
```

The example has no usable backend. Replace the copied dummy file with a config downloaded from your own Firebase project before running account/cloud flows. Never overwrite another developer's local configuration during setup.

## Models and videos

The repository tracks Full, Lite, and Heavy `pose_landmarker_*.task` models and seven tutorial MP4s in `app/src/main/assets/`.

`app/download_tasks.gradle` attaches model downloads to `preBuild` with `overwrite false`. Existing models are reused; a missing model is fetched from its declared Google-hosted URL. Tutorial videos are local files and have no download task.

Initial builds still download Gradle and Maven dependencies. See [asset provenance](../THIRD_PARTY_NOTICES.md) before redistributing media.

## Build

Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux:

```bash
bash ./gradlew :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on a device, or run the `app` module from Android Studio. Release signing is not configured in this repository.

## Health Connect

The base app runs on API 26+. Health Connect requires Android 9/API 28 or higher with Google Play services; it is integrated into Android 14+ and uses a separate provider on supported earlier versions. See [Health Connect availability](https://developer.android.com/health-and-fitness/health-connect/availability).

In Profile, connect Health Connect and grant read-steps/write-exercise permissions to test the integration. Test the unavailable and denied-permission states too. Vietnamese TextToSpeech voice data and a hardware step sensor are separate device capabilities.

## Verification

Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS / Linux:

```bash
bash ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Reports:

- JVM tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Android lint: `app/build/reports/lint-results-debug.html`

The GitHub Actions workflow performs these checks with dummy Firebase configuration. It does not run account operations or device tests.

With a connected emulator/device, the existing sample instrumentation test can be run using `:app:connectedDebugAndroidTest`. It is a package-name smoke test.

For runtime changes, manually verify the affected scenarios:

1. Register, sign in, complete/edit the questionnaire, and return after BMI update expiry.
2. Open all four navigation tabs; search tutorials and create/activate a custom plan.
3. Calibrate and complete repetition and timed workouts; try poor visibility and rotation.
4. Finish a workout offline, reopen the app, reconnect, and inspect local/cloud results.
5. Change difficulty and apply a recommendation to upcoming days.
6. Test denied camera/notification/activity/Health Connect permissions and an absent step sensor.
7. Change reminder time, run the rest timer, and test recovery after reboot.
8. Switch Vietnamese/English and sign out/in across two test accounts.

Use synthetic profiles. Build success does not establish these runtime results.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `JAVA_HOME` is not set or Java is too old | Point it to JDK 17+ and check Android Studio's Gradle JDK |
| SDK location/platform missing | Configure SDK path and install Platform 36 / Build Tools 35.0.0 |
| `google-services.json` missing | Use your Firebase config, or the dummy example for build checks |
| No matching Firebase client | Package must exactly match `applicationId` |
| Login fails with the example file | Replace dummy config and enable Email/Password Auth |
| Firestore permission denied | Confirm current UID, database, and deployed access rules |
| Gradle cannot download models | Check network access to the URLs in `download_tasks.gradle` |
| Pose counter is inaccurate | Check full-body visibility, camera orientation, lighting, and starting pose |
| Voice guidance is silent | Check voice mode and installed Vietnamese TTS support |
| Health Connect unavailable | Check provider availability, Android version, and permissions |
| A local session has not uploaded | Check signed-in UID, network, sync flags/errors, and WorkManager state |

