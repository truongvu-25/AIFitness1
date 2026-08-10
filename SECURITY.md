# Chính sách bảo mật

Fitness For You dùng Firebase Authentication và Cloud Firestore.
Khi public hoặc triển khai thật, cần chú ý bảo vệ dữ liệu người dùng.

## Không commit

Không đưa các file sau lên repository public:

- `local.properties`.
- `app/google-services.json` của project production.
- File ký release như `.jks`, `.keystore`, `.p12`, `.pem`.
- File `.env`, dữ liệu export Firestore hoặc dữ liệu người dùng thật.

## Firebase

Firebase API key trong app Android không phải mật khẩu server-side,
nhưng vẫn cần giới hạn phạm vi sử dụng trong Google Cloud Console.

Khuyến nghị:

- Dùng Firebase project demo cho repository public.
- Bật Email/Password Authentication nếu muốn chạy đúng luồng hiện tại.
- Viết Firestore Rules để người dùng chỉ đọc/ghi `users/{uid}` của họ.
- Không dùng dữ liệu người dùng thật trong ảnh chụp màn hình hoặc demo.

## Báo cáo lỗi bảo mật

Nếu phát hiện lỗi có thể làm lộ dữ liệu người dùng, hãy báo riêng cho
chủ repository trước khi công khai chi tiết.

Khi báo cáo, vui lòng ghi:

- Mô tả lỗi.
- Bước tái hiện.
- Mức ảnh hưởng.
- Gợi ý khắc phục nếu có.
