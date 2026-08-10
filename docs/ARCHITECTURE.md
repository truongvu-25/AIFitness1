# Kiến trúc hệ thống

Tài liệu này mô tả cấu trúc kỹ thuật của Fitness For You ở mức tổng quan.
Chi tiết flow kỹ thuật nằm trong `docs/TECHNICAL_OVERVIEW.md`.

## Thành phần Android

```text
FitnessApplication
└── MainActivity
    └── NavHostFragment
        ├── LoginFragment
        ├── RegisterFragment
        ├── UserInfoFragment
        ├── WorkoutCalendarFragment
        ├── CameraFragment
        ├── ProfileFragment
        ├── UpdateBmiFragment
        └── GalleryFragment
```

Các thành phần chạy nền:

- `WorkoutReminderReceiver`: nhận alarm nhắc tập luyện hằng ngày.
- `BootReceiver`: đặt lại alarm sau khi thiết bị khởi động lại.
- `StepCounterService`: Foreground Service đếm bước và calo.
- `RestTimerService`: Foreground Service đếm 5 phút nghỉ giữa bài.

## Luồng dữ liệu chính

```text
Firebase Auth
└── uid
    └── users/{uid}
        └── workouts/day_N
```

```text
FitnessApplication
└── exercises/{exerciseId}
```

`exercises` là dữ liệu bài tập gốc.
`users/{uid}` là hồ sơ cá nhân.
`users/{uid}/workouts` là lịch tập và tiến độ riêng của người dùng.

## Luồng AI nhận diện tư thế

```text
CameraX ImageAnalysis
└── CameraFragment.detectPose()
    └── PoseLandmarkerHelper.detectLiveStream()
        └── MediaPipe Pose Landmarker
            ├── OverlayView.setResults()
            └── ExerciseAnalyzer.analyze()
```

CameraX lấy từng frame camera.
MediaPipe trả landmark cơ thể.
OverlayView vẽ khung xương.
ExerciseAnalyzer kiểm tra động tác và trả tiến độ.

## Phân tích bài tập

Factory trong `ExerciseAnalyzer.kt` chọn analyzer theo `exerciseId`.

```text
pushup      -> PushupAnalyzer
squat       -> SquatAnalyzer
jumpingjack -> JumpingJackAnalyzer
situp       -> SitupAnalyzer
plank       -> PlankAnalyzer
sideplank   -> SidePlankAnalyzer
splitsquat  -> SplitSquatAnalyzer
```

Các bài theo số lần dùng trạng thái lên/xuống hoặc mở/khép.
Các bài Plank và Side Plank dùng thời gian giữ tư thế hợp lệ.

## Service và thông báo

`NotificationHelper` dùng `AlarmManager` để đặt lịch lúc 8:00 sáng.
`WorkoutReminderReceiver` kiểm tra bài chưa hoàn thành rồi mới báo.

`StepCounterService` dùng `Sensor.TYPE_STEP_COUNTER`.
Service lưu số bước vào `SharedPreferences`, tính calo theo công thức:

```text
calories = steps * 0.04
```

`RestTimerService` chạy `CountDownTimer` 5 phút.
Nếu hết giờ nghỉ, service phát thông báo ưu tiên cao để người dùng quay lại.

## Cấu hình public

Repository public không theo dõi:

- `.idea/`.
- `local.properties`.
- `app/google-services.json`.
- File ký release.
- Output build.

Người clone project cần tự thêm `app/google-services.json` từ Firebase.
