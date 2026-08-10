# Security Policy

Security and data privacy are top priorities for Fitness For You. This document outlines security policies, open-source rules, and vulnerability reporting procedures.

## Supported Versions

We provide security updates for the code on the `main` and `master` branches of this repository.

| Version / Branch | Supported | Notes |
| :--- | :---: | :--- |
| `main` / `master` | YES | Active development branch |
| Older releases | NO | Upgrade to the latest commit |

## Restricted Files & Sensitive Data

To protect cloud infrastructure and maintain security, do not commit the following items to the public repository:

- **Firebase Configuration**: `app/google-services.json`
- **IDE & Local Properties**: `local.properties`, `.idea/`, `.gradle/`
- **Keystores & Credentials**: `.jks`, `.keystore`, `.p12`, `.pem`
- **Environment Files**: `.env`, private API keys
- **User Data**: Real user profiles, personal identifiers, or exported Firestore collections

All sensitive files must remain listed in `.gitignore`.

## Firebase & Security Best Practices

1. **Firestore Security Rules**: Ensure Cloud Firestore rules restrict users so they can only read and write their own documents:
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /exercises/{exerciseId} {
         allow read: if request.auth != null;
         allow write: if false;
       }
       match /users/{userId}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```
2. **Google Cloud Console Restrictions**: Restrict Android API keys in Google Cloud Console by package name (`com.google.mediapipe.examples.poselandmarker`) and SHA-1 fingerprint.
3. **Isolated Demo Environment**: Use a dedicated demo Firebase project for public repositories.

## Reporting a Vulnerability

If you discover a security vulnerability or potential privacy issue:

1. Do not create a public GitHub issue.
2. Email a detailed vulnerability report to the repository maintainer.
3. Include step-by-step reproduction instructions, potential impact, and suggested mitigation.
