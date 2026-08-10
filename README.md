# Fitness For You

Fitness For You là ứng dụng Android hỗ trợ tập luyện cá nhân hóa.
Ứng dụng tính BMI, tạo lộ trình 30 ngày, xem video hướng dẫn,
nhận diện tư thế bằng AI qua camera và theo dõi vận động hằng ngày.

![Fitness For You](app/src/main/res/drawable/fitness_for_you_banner.png)

## Tính năng chính

- Đăng ký và đăng nhập bằng Firebase Authentication.
- Nhập hồ sơ sức khỏe: họ tên, tuổi, chiều cao và cân nặng.
- Tự động tính BMI và phân loại người dùng theo thể trạng.
- Sinh lộ trình tập luyện 30 ngày theo nhóm BMI.
- Hiển thị lịch tập, ngày nghỉ, tiến độ từng ngày và bài chưa hoàn thành.
- Phát video hướng dẫn bài tập từ thư mục assets.
- Dùng CameraX và MediaPipe để nhận diện khung xương theo thời gian thực.
- Đếm số lần hoặc số giây giữ tư thế cho từng bài tập.
- Lưu trạng thái hoàn thành lên Cloud Firestore.
- Chạy bộ đếm nghỉ 5 phút giữa các bài bằng Foreground Service.
- Đếm bước chân và calo tiêu thụ bằng Foreground Service.
- Nhắc nhở tập luyện hằng ngày lúc 8:00 sáng.
- Yêu cầu cập nhật BMI sau 7 ngày để làm mới dữ liệu sức khỏe.

## Điểm nổi bật kỹ thuật

- Kết hợp CameraX với MediaPipe Pose Landmarker để xử lý frame camera
  theo thời gian thực ngay trên thiết bị.
- Tách logic phân tích động tác thành nhiều analyzer riêng,
  dễ mở rộng thêm bài tập mới.
- Dùng Firebase Auth và Cloud Firestore cho luồng tài khoản,
  hồ sơ sức khỏe và tiến độ tập luyện.
- Dùng Foreground Service cho các tác vụ cần chạy nền:
  đếm bước chân và đếm giờ nghỉ giữa bài.
- Dùng AlarmManager và BroadcastReceiver để nhắc tập hằng ngày
  và khôi phục lịch sau khi thiết bị khởi động lại.
- Repository đã loại bỏ file cấu hình local và khóa Firebase hardcode
  để phù hợp khi public trên GitHub.

## Công nghệ sử dụng

- Kotlin.
- Android Native SDK.
- XML Layout.
- Android Jetpack Navigation.
- View Binding.
- CameraX.
- MediaPipe Tasks Vision.
- Firebase Authentication.
- Cloud Firestore.
- Foreground Service.
- AlarmManager và BroadcastReceiver.
- Material Components.

## Cấu trúc thư mục

```text
AIFitness1/
├── app/
│   ├── src/main/java/.../poselandmarker/
│   │   ├── fragment/                 # Các màn hình chính.
│   │   ├── ExerciseAnalyzer.kt        # Phân tích từng động tác.
│   │   ├── PoseLandmarkerHelper.kt    # Cấu hình MediaPipe.
│   │   ├── OverlayView.kt             # Vẽ khung xương lên màn hình.
│   │   ├── StepCounterService.kt      # Đếm bước chân và calo.
│   │   ├── RestTimerService.kt        # Đếm giờ nghỉ giữa các bài.
│   │   ├── NotificationHelper.kt      # Lập lịch nhắc tập luyện.
│   │   ├── FitnessApplication.kt      # Khởi tạo Firebase và dữ liệu.
│   │   └── Models.kt                  # Data class cho Firestore.
│   ├── src/main/assets/
│   │   ├── pose_landmarker_*.task     # Model AI nhận diện tư thế.
│   │   └── videos/                    # Video hướng dẫn bài tập.
│   └── src/main/res/                  # Layout, ảnh, màu, menu, navigation.
├── docs/
│   ├── TECHNICAL_OVERVIEW.md          # Tổng quan kỹ thuật cho reviewer.
│   └── ARCHITECTURE.md                # Kiến trúc, service và dữ liệu.
├── README.md
├── CONTRIBUTING.md
├── SECURITY.md
└── LICENSE
```

## Luồng hoạt động tổng quát

1. Ứng dụng khởi động qua `FitnessApplication`.
2. Firebase được khởi tạo từ cấu hình local của Android project.
3. Danh sách bài tập mẫu được ghi vào collection `exercises`.
4. `MainActivity` nạp `NavHostFragment` và mở màn hình đăng nhập.
5. Người dùng đăng ký hoặc đăng nhập bằng email và mật khẩu.
6. Nếu chưa có hồ sơ, ứng dụng yêu cầu nhập thông tin sức khỏe.
7. Ứng dụng tính BMI, phân loại thể trạng và tạo lịch tập 30 ngày.
8. Người dùng chọn ngày tập, xem video hoặc bắt đầu tập bằng camera.
9. CameraX gửi frame camera sang MediaPipe để lấy landmark cơ thể.
10. `ExerciseAnalyzer` đếm động tác và phản hồi tư thế.
11. Khi đạt mục tiêu, ứng dụng cập nhật Firestore.
12. Nếu còn bài trong ngày, app chạy bộ đếm nghỉ 5 phút.
13. Profile hiển thị hồ sơ, bước chân và calo tiêu thụ trong ngày.

Chi tiết kỹ thuật nằm trong
[docs/TECHNICAL_OVERVIEW.md](docs/TECHNICAL_OVERVIEW.md).

## Cài đặt và chạy project

Yêu cầu:

- Android Studio phiên bản mới.
- JDK đã cấu hình trong Android Studio hoặc biến môi trường `JAVA_HOME`.
- Thiết bị Android thật hoặc emulator có camera.
- Android SDK tối thiểu: API 24.
- Firebase project đã bật Authentication và Cloud Firestore.

Các bước:

```powershell
git clone <repository-url>
cd AIFitness1
.\gradlew.bat assembleDebug
```

Sau đó mở project bằng Android Studio, sync Gradle và chạy module `app`.

## Cấu hình Firebase

Repository public không commit `app/google-services.json`.
Để chạy project, hãy tạo Firebase project riêng rồi tải file cấu hình về:

```text
app/google-services.json
```

Cần bật:

- Email/Password trong Firebase Authentication.
- Cloud Firestore.

Project đã bỏ hardcode Firebase API key trong source.
`FirebaseConfig.kt` chỉ gọi `FirebaseApp.initializeApp(context)` và dựa vào
cấu hình local do `google-services.json` sinh ra khi build.

## Dữ liệu Firestore

```text
exercises/{exerciseId}
users/{uid}
users/{uid}/workouts/day_1
users/{uid}/workouts/day_2
...
users/{uid}/workouts/day_30
```

Ý nghĩa:

- `exercises`: dữ liệu bài tập gốc dùng chung.
- `users/{uid}`: hồ sơ cá nhân, BMI và thời điểm tạo lộ trình.
- `workouts/day_N`: bài tập từng ngày và trạng thái hoàn thành.

## Quyền Android

Ứng dụng cần các quyền chính:

- `CAMERA`: mở camera để nhận diện tư thế.
- `INTERNET`: đăng nhập và đồng bộ Firestore.
- `POST_NOTIFICATIONS`: hiển thị thông báo trên Android 13 trở lên.
- `ACTIVITY_RECOGNITION`: đọc cảm biến bước chân.
- `FOREGROUND_SERVICE`: chạy service đếm bước và đếm giờ nghỉ.
- `RECEIVE_BOOT_COMPLETED`: đặt lại lịch nhắc sau khi máy khởi động.
- `SCHEDULE_EXACT_ALARM`: lập lịch nhắc tập luyện.

## Tài liệu

- [Tổng quan kỹ thuật](docs/TECHNICAL_OVERVIEW.md)
- [Kiến trúc hệ thống](docs/ARCHITECTURE.md)
- [Hướng dẫn đóng góp](CONTRIBUTING.md)
- [Chính sách bảo mật](SECURITY.md)

## Kiểm tra nhanh

```powershell
.\gradlew.bat assembleDebug
```

```powershell
rg --files -g "*.md" -g "*.txt"
```

## Giấy phép

Project kế thừa một phần mã nguồn từ MediaPipe Android sample của TensorFlow,
được phát hành theo Apache License 2.0.

Xem chi tiết trong [LICENSE](LICENSE).
