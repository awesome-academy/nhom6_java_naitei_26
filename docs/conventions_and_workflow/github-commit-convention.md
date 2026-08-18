# Git Commit Convention

## Format

```text
<type>(<scope>): <description>
```

Ví dụ:

```text
feat(auth): add Google login
fix(booking): prevent duplicate booking
docs(readme): update setup guide
```

---

## Các `type` thường dùng

| Type         | Dùng khi nào?                                                                          |
| ------------ | ---------------------------------------------------------------------------------------- |
| `feat`     | Thêm**chức năng mới**                                                          |
| `fix`      | **Sửa bug**                                                                       |
| `docs`     | Thay đổi **tài liệu** như README, API docs                                   |
| `refactor` | Chỉnh sửa cấu trúc code nhưng**không thêm chức năng và không sửa bug** |
| `test`     | Thêm hoặc chỉnh sửa**test**                                                    |
| `style`    | Chỉnh format/code style, không thay đổi logic                                        |
| `perf`     | Cải thiện**hiệu năng**                                                         |
| `build`    | Thay đổi build system, dependency, Maven, Gradle, Docker build...                      |
| `ci`       | Thay đổi**CI/CD** như GitHub Actions                                            |
| `chore`    | Các công việc bảo trì nhỏ không thuộc các nhóm trên                           |
| `revert`   | Hoàn tác một commit trước đó                                                      |

---

## `(scope)` là gì?

Phần trong ngoặc `()` cho biết **khu vực/module bị thay đổi**.

Ví dụ:

```text
feat(auth): add Google login
```

Ở đây:

- `feat` → thêm chức năng mới
- `auth` → thay đổi nằm trong module authentication
- `add Google login` → nội dung thay đổi

Một số scope thường gặp:

```text
auth
user
room
booking
payment
invoice
api
database
ui
```

`scope` **không bắt buộc**.

Có thể viết:

```text
docs: update README
```

hoặc:

```text
docs(api): update booking API documentation
```

---

## Quy tắc ngắn gọn

Commit nên theo dạng:

```text
type(scope): mô tả ngắn gọn thay đổi
```

Ưu tiên:

```text
feat(booking): add cancellation feature
fix(payment): handle failed payment
refactor(auth): simplify token validation
docs: update README
```

Tránh các commit message mơ hồ như:

```text
update
fix
change
done
final
```
