# Preparing a public Tri Force repository

[Overview](../README.md) · [Security](../SECURITY.md) · [Asset notices](../THIRD_PARTY_NOTICES.md)

## Repository identity

- Display name: **Tri Force**; welcome-screen styling: **TRI FORCE**.
- Tagline: **Sức Mạnh • Kỷ Luật • Bứt Phá**.
- Logo: `app/src/main/res/drawable/app_logo.png`, reused directly in both READMEs.
- Gradle project name: `TriForce`.
- Existing repository URL: `https://github.com/truongvu-25/AIFitness1.git`.
- Android application ID: `com.google.mediapipe.examples.poselandmarker`.

Suggested GitHub description:

> Tri Force — Android fitness app with on-device pose tracking, personalized workout plans, and progress tracking. Kotlin, MediaPipe, CameraX, Firebase, and Room.

Suggested topics: `android`, `kotlin`, `fitness`, `mediapipe`, `pose-estimation`, `camerax`, `firebase`, `room`, `health-connect`.

These are suggested repository settings. Preparing local files does not change GitHub visibility, rename a remote repository, or publish a release.

## What is included

The public-facing files include English and Vietnamese READMEs, source-based architecture/implementation notes, setup instructions, contribution/security guidance, asset notices, issue/PR templates, and an Android build/test/lint workflow.

The workflow copies a dummy Firebase config and uploads check reports only. It needs no real Firebase credentials and does not exercise a deployed backend. Its first actual GitHub-hosted run must still be checked after pushing.

Local IDE/compiler caches and `.artifacts/` working notes are excluded. Previously tracked `.artifacts/` files are removed from the Git index while their local copies are retained. This affects future commits, not earlier history.

## Existing history needs a separate review

The preparation scan found `app/google-services.json` in an earlier commit (`3216a68910e0`). It has the structure of a Firebase Android client configuration, including client/project identifiers, and no top-level service-account private key. The current file is ignored and not tracked.

Firebase client configuration is not equivalent to an admin credential; see [Firebase's explanation](https://firebase.google.com/docs/android/setup). The project owner should still review the historical backend's rules, API restrictions, and exposure before publishing that history. No deployed backend settings were inspected or changed during repository preparation.

A pattern scan of current tracked text files found no matching GitHub tokens, AWS access-key IDs, Google API keys, or private-key blocks. This was a limited pattern check, not an exhaustive scan of every historical blob. Review all refs if publishing the existing history. No history rewrite or force push has been performed.

If creating a separate public repository from a clean snapshot, export only the files intended for publication and initialize that snapshot separately. Preserve required license notices and attribution. Decide how to handle existing history with the repository owner before changing shared refs.

## Assets and release scope

The three models and seven MP4 tutorials total 242,529,646 bytes (approximately 242.5 MB / 231.3 MiB). They remain tracked so the app keeps its existing assets and tutorial behavior.

Image and video authorship/license records still need maintainer confirmation; see [third-party notices](../THIRD_PARTY_NOTICES.md). The current logo is preserved as requested. No replacement imagery or screenshots of a different app are used.

Publishing source is separate from distributing a signed APK or submitting to an app store. This repository has no release-signing setup. Its `targetSdk` is 34, and device/privacy/store requirements should be assessed separately if preparing an app release.

## Verification record

Local checks on 2026-09-10 completed successfully on Windows with Android Studio's bundled JDK:

| Check | Result |
| --- | --- |
| Debug APK assembly | Passed |
| Existing JVM tests | 17 passed; no failures, errors, or skipped tests |
| Android lint | Completed; 398 warnings, no errors |
| Isolated public export using dummy Firebase config | Assembly, JVM tests, and lint passed |
| Local Markdown links and branding | Checked against files and app manifest |

The isolated export excluded the real Firebase file, local SDK configuration, caches, and working notes. See [SETUP.md](SETUP.md#verification) for reproducible commands and report locations. Existing lint warnings and Gradle/Kapt deprecation notices were not suppressed by this documentation preparation.

Camera accuracy, account/cloud flows, Health Connect, notifications, and behavior across devices require the manual scenarios in the setup guide. No GitHub visibility change, push, or release is part of this local verification.
