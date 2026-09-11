# Security policy

Security fixes are tracked on the default branch (`Nam2`). No separate long-term support commitment is documented for older builds.

## Reporting

Do not publish exploitable details, credentials or personal data in public issues. Use GitHub private vulnerability reporting if enabled, or a private contact method listed on the maintainer's GitHub profile. Include commit/device, reproduction and impact. This repository does not publish a dedicated security email or response-time guarantee.

## Configuration and access

- Keep real Firebase configuration, signing material, service-account keys and local environment files outside version control.
- Firebase Android configuration is not an authorization boundary. Enforce ownership with Firestore rules and relevant API restrictions.
- `firestore.rules` is a reviewable owner-access template; its presence does not prove deployment in any live project.
- Mobile clients read the shared catalogue; trusted administrative tooling owns updates.
- Workout-session queries use UID scope. Some templates/settings remain device-level preferences.
- Camera frames stay in device memory. Firestore stores profile metrics, schedules and workout results. Health Connect is optional and permission-controlled.

Before distribution, validate cross-account/unauthenticated access, backup behavior, retention/deletion requirements and shared-device use. Use an isolated Firebase project for tests.
