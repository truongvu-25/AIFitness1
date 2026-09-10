# Tri Force security

## Reporting

Use GitHub's **Security → Advisories → Report a vulnerability** when private vulnerability reporting is enabled for this repository. Include the affected commit, reproduction steps using test data, and likely impact.

If that option is unavailable, open a minimal issue requesting a private reporting channel without disclosing exploit details, credentials, or user data. This repository does not currently publish a dedicated security email or a release support schedule.

## Configuration and access

Real Firebase configuration stays in an ignored `google-services.json`; the tracked `.example` file contains dummy values. Firebase client configuration contains project/app identifiers rather than service-account private credentials. Keeping it out of this repository separates developers' backends; access still depends on deployed Firebase rules and authentication. See [Firebase Android configuration](https://firebase.google.com/docs/android/setup).

Never commit service-account private keys, signing keys, passwords, access tokens, or user exports. If a real credential is exposed, revoke/rotate it at its provider; deleting a file in a later commit does not remove it from Git history.

Firestore data is scoped under `users/{uid}`. Deploy and test ownership rules for the profile and every descendant collection. The [setup example](docs/SETUP.md#firebase) is a development starting point; deployed rules and backend restrictions cannot be verified from this repository.

## Data handling in the current code

- Camera pose inference runs locally; the camera workout path stores session metrics and has no Firebase frame-upload path.
- Firebase Authentication handles accounts. Firestore stores profiles, workout plans, sessions, and exercise history.
- Room stores workout sessions with a user ID. The background worker selects pending sessions for the signed-in account.
- Health Connect reads steps and writes exercise sessions when the required permissions are available.
- SharedPreferences cache language, reminders, sensor state, and some plan/goal settings.
- Logout stops the step service and cancels reminders, but does not clear all local data. Some plan preferences are shared at the installation level.
- The manifest currently enables Android backup and does not define custom backup exclusions.

These implementation facts should inform testing on shared devices and any deployment privacy notice. Account/data deletion and retention policies are not defined by this repository documentation.

## Before exposing an existing repository

Ignoring a file only affects future untracked files. Review all branches/tags and historical blobs before publishing existing history. See [publication notes](docs/PUBLISHING.md) for the configuration history found during this preparation.

Dependency, build, and lint checks are not a penetration test or verification of a deployed Firebase backend.

