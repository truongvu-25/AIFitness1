# Security Policy

Security and data privacy are top priorities for **Fitness For You**. This document outlines our security policies, rules for open-source contributions, and vulnerability reporting procedures.

---

## 🛡️ Supported Versions

We provide security updates for the latest code on the `main` branch of this repository.

| Version / Branch | Supported | Notes |
| :--- | :---: | :--- |
| `main` | YES | Active development branch |
| Older releases | NO | Please upgrade to `main` |

---

## 🚫 Restricted Files & Sensitive Data

To protect cloud infrastructure and maintain open-source compliance, **NEVER** commit the following items to the public repository:

- **Firebase Configuration**: `app/google-services.json`
- **IDE & Local Properties**: `local.properties`, `.idea/`, `.gradle/`
- **Keystores & Credentials**: `.jks`, `.keystore`, `.p12`, `.pem`
- **Environment Files**: `.env`, private API keys
- **User Data**: Real user profiles, personal identifiers, or exported Firestore collections

All sensitive files must remain listed in [.gitignore](.gitignore).

---

## 🔑 Firebase & Security Best Practices

While Firebase API keys embedded in client applications are not secret keys, security must be enforced on the backend:

1. **Firestore Security Rules**:
   Ensure Cloud Firestore rules restrict users so they can only read and write their own documents:
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       // Shared exercise metadata is readable by authenticated users
       match /exercises/{exerciseId} {
         allow read: if request.auth != null;
         allow write: if false; // Admin only
       }
       // Users can only access their own profile and workouts
       match /users/{userId}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```

2. **Google Cloud Console API Restrictions**:
   Restrict Android API keys in Google Cloud Console by package name (`com.google.mediapipe.examples.poselandmarker`) and SHA-1 fingerprint.

3. **Isolated Demo Environment**:
   Use a separate Firebase project for development and testing. Do not connect production databases to public test builds.

---

## 📩 Reporting a Vulnerability

If you discover a security vulnerability or potential privacy issue in this repository:

1. **DO NOT** create a public GitHub issue.
2. Email a detailed vulnerability report to the project owner/maintainer.
3. Include the following details in your report:
   - Type of issue (e.g., credential exposure, insecure Firestore rule, permission flaw).
   - Step-by-step reproduction instructions.
   - Potential impact and affected components.
   - Suggested mitigation or fix, if available.

We will acknowledge receipt of your report promptly and work on a fix before public disclosure.
