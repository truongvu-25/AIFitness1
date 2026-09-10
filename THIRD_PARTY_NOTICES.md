# Third-party code and assets

[Overview](README.md) · [License](LICENSE)

## Source code

This repository contains code derived from the Google MediaPipe Pose Landmarker Android example. Existing files including `MainActivity.kt`, `analysis/PoseLandmarkerHelper.kt`, and the app Gradle scripts retain **Copyright 2023 The TensorFlow Authors** and Apache-2.0 headers.

The repository's [Apache License 2.0](LICENSE) is retained. Preserve upstream headers when modifying or redistributing those files. Dependency versions and coordinates are recorded in [app/build.gradle](app/build.gradle); their upstream licenses and notices still apply.

## Pose models

The bundled assets are:

- `app/src/main/assets/pose_landmarker_full.task`
- `app/src/main/assets/pose_landmarker_lite.task`
- `app/src/main/assets/pose_landmarker_heavy.task`

[download_tasks.gradle](app/download_tasks.gradle) records the Google-hosted MediaPipe model URLs used to obtain these files (float16, version 1). Keep model provenance with any redistributed copy and review the applicable upstream model terms before distributing a release. This document does not assign a new license to the model binaries.

## App branding

The README uses `app/src/main/res/drawable/app_logo.png`, the same file referenced by the Android manifest. Existing Tri Force logos, backgrounds, and welcome artwork remain as supplied by the project.

The repository currently has no separate author/source records for these image assets. Their inclusion here preserves the requested current app identity; it does not independently establish ownership or permission to redistribute them.

## Tutorial videos

The seven bundled videos are `push_up.mp4`, `sit_up.mp4`, `squat.mp4`, `plank.mp4`, `side_plank.mp4`, `jumping_jack.mp4`, and `split_squat.mp4` under `app/src/main/assets/videos/`.

No per-video source, creator, or license record was found in the tracked documentation. Before distributing these media files publicly, the maintainer should confirm redistribution permission and record the creator/source and license here. A repository code license alone does not establish the provenance of these videos.
