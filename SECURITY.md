# Security Policy

Fitness For You uses Firebase Authentication and Cloud Firestore.
Public releases and real deployments should protect user data carefully.

## Do Not Commit

Do not commit these files to a public repository:

- `local.properties`.
- Production `app/google-services.json`.
- Release signing files such as `.jks`, `.keystore`, `.p12`, or `.pem`.
- `.env` files.
- Firestore exports or real user data.

## Firebase

Firebase API keys in Android apps are not server-side secrets, but they
should still be restricted in Google Cloud Console.

Recommendations:

- Use a demo Firebase project for public repositories.
- Enable Email/Password Authentication for the current app flow.
- Write Firestore Rules so users can only access their own `users/{uid}` data.
- Do not use real user data in screenshots, demo videos, or sample exports.

## Reporting Security Issues

If you find a vulnerability that could expose user data, report it privately
to the repository owner before publishing details.

Please include:

- A clear description of the issue.
- Reproduction steps.
- Potential impact.
- Suggested mitigation, if available.
