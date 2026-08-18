# Pull Request Guide

Tài liệu này dùng để hướng dẫn **developer hoặc coding agent** khi tạo Pull Request.

Khi tạo Pull Request, sử dụng template:

```text
.github/pull_request_template.md
```

Mục tiêu là mô tả Pull Request **ngắn gọn, đúng nội dung thay đổi và dễ review**.

---

## 1. Links

Dùng để liên kết Pull Request với Issue/Task liên quan.

Ví dụ:

```md
## Links

- #123
```

Quy tắc:

- Chỉ thêm Issue/Task thực sự liên quan.
- Nếu không có Issue/Task thì **bỏ phần này hoặc để trống**.
- Không tự tạo hoặc đoán Issue ID.

---

## 2. Tiêu đề ngắn gọn

Viết một câu ngắn mô tả mục đích chính của Pull Request.

Ví dụ:

```md
## Tiêu đề ngắn gọn

Thêm chức năng hủy booking
```

Quy tắc:

- Chỉ mô tả **mục đích chính** của Pull Request.
- Ngắn gọn, dễ hiểu.
- Không ghi chi tiết implementation.
- Không dùng các tiêu đề mơ hồ như:
  - Update code
  - Fix something
  - Change files

Nếu phù hợp, PR title có thể theo Git Commit Convention:

```text
feat(booking): add booking cancellation
```

---

## 3. Nội dung đã làm / sửa đổi

Liệt kê ngắn gọn các chức năng hoặc hành vi đã được thêm, sửa hoặc thay đổi.

Ví dụ:

```md
## Nội dung đã làm / sửa đổi

- Thêm chức năng hủy booking.
- Kiểm tra điều kiện hủy theo thời gian trước check-in.
- Cập nhật trạng thái booking sau khi hủy.
```

Quy tắc:

- Viết dưới dạng bullet list.
- Chỉ ghi những thay đổi **thực sự có trong Pull Request**.
- Ưu tiên mô tả theo góc nhìn chức năng/hành vi.
- Không cần liệt kê từng file đã sửa.
- Không mô tả dài dòng cách code hoạt động nếu không cần thiết.
- Các thay đổi nhỏ liên quan có thể gộp thành một bullet.

---

# Quy tắc dành cho Agent

Khi Agent tạo Pull Request:

1. Đọc toàn bộ thay đổi của branch so với branch đích.
2. Xác định mục đích chính của Pull Request.
3. Nếu có Issue/Task được cung cấp, thêm vào `Links`.
4. Không tự suy đoán hoặc tạo Issue ID.
5. Viết `Tiêu đề ngắn gọn` dựa trên mục đích chính.
6. Liệt kê các thay đổi quan trọng trong `Nội dung đã làm / sửa đổi`.
7. Không liệt kê các thay đổi không tồn tại trong diff.
8. Không ghi các chi tiết kỹ thuật nhỏ không cần thiết.
9. Không ghi nội dung chung chung như `update code`, `fix code`, `change logic`.
10. Giữ nội dung ngắn gọn để reviewer có thể hiểu nhanh Pull Request làm gì.

---

# Format mong muốn

```md
## Links

- #123

## Tiêu đề ngắn gọn

Thêm chức năng hủy booking

## Nội dung đã làm / sửa đổi

- Thêm chức năng hủy booking.
- Kiểm tra điều kiện hủy theo thời gian trước check-in.
- Cập nhật trạng thái booking sau khi hủy.
```

Nếu không có Issue:

```md
## Tiêu đề ngắn gọn

Sửa lỗi tính tổng tiền invoice

## Nội dung đã làm / sửa đổi

- Sửa cách tính tổng tiền phòng.
- Cập nhật lại discount trước khi tính tổng cuối cùng.
```
