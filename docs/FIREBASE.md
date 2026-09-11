# Firebase setup

The app uses Firebase Authentication and Firestore directly; there is no Node/Python backend to start.

1. Register package `com.google.mediapipe.examples.poselandmarker`.
2. Download Android configuration into ignored `app/google-services.json`.
3. Enable Email/Password sign-in and create Firestore.
4. Review/test `firestore.rules` against your schema in an isolated project.

## Collections

```text
exercises/{exerciseId}                 optional shared catalogue overrides
users/{uid}                           profile, metrics and active plan metadata
users/{uid}/workouts/day_{1..30}        active schedule
users/{uid}/workout_sessions/{uuid}    completed sessions and difficulty
users/{uid}/exercise_history/{id}      aggregates and latest result
users/{uid}/custom_plans/{planId}      weekly-plan templates
```

Built-in catalogue metadata and videos ship in the app. An empty `exercises` collection is supported. Administrative updates must use trusted tooling; clients do not seed the catalogue at startup.

Onboarding writes profile plus schedule atomically. Completion sync uses a transaction. Separate clients must not create difficulty-only session documents.

## Rules

The template permits authenticated users to access their own profile subtree, allows authenticated catalogue reads and rejects catalogue writes. It does not add field validation/rate limits and is not evidence of current live rules.

With Firebase CLI installed and authenticated, review the target project before deploying:

```bash
firebase deploy --only firestore:rules --project YOUR_TEST_PROJECT_ID
```

No default project alias or production credentials are committed. Local build does not deploy rules.

Validate unauthenticated denial, cross-user denial, own-profile plus 30-day batch, completion transaction, catalogue write denial, and offline/reconnect deduplication against an isolated project.
