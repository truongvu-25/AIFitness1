# Hướng dẫn đóng góp

Cảm ơn bạn quan tâm tới Fitness For You.
Tài liệu này giúp project giữ cấu trúc rõ ràng khi phát triển tiếp.

## Chạy project

```powershell
.\gradlew.bat assembleDebug
```

Mở project bằng Android Studio, sync Gradle và chạy module `app`
trên thiết bị Android có camera.

## Quy ước code

- Viết Kotlin theo phong cách hiện có trong project.
- Giữ logic từng màn hình trong Fragment tương ứng.
- Giữ model dữ liệu dùng chung trong `Models.kt`.
- Nếu thêm bài tập mới, cập nhật dữ liệu mẫu trong `FitnessApplication.kt`.
- Nếu bài tập cần nhận diện AI, thêm analyzer trong `ExerciseAnalyzer.kt`.
- Không commit file local, file ký release hoặc cấu hình Firebase cá nhân.

## Quy trình đề xuất

1. Tạo branch mới từ branch chính.
2. Sửa code hoặc tài liệu đúng phạm vi.
3. Chạy build debug trước khi gửi thay đổi.
4. Ghi chú nếu thay đổi cần dữ liệu Firestore hoặc Firebase Rules mới.

## Checklist pull request

- Build debug chạy được.
- Không có file cấu hình cá nhân bị commit.
- README hoặc tài liệu trong `docs/` được cập nhật nếu đổi luồng app.
- Ảnh, video hoặc model mới được đặt đúng thư mục assets/res.
- Thay đổi không làm sai luồng đăng nhập, tạo lịch và hoàn thành bài tập.
