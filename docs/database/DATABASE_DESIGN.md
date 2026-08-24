# Hotel Management — Thiết kế Database Production

Link: [dbdiagram.io/d/hotel_management_database_design-6a80285cc6a866c907722415](https://dbdiagram.io/d/hotel_management_database_design-6a80285cc6a866c907722415) 

Nguồn yêu cầu: `Hotel_Management_Project_Specification.docx` (mục 2 Chức năng, mục 3 Trang, mục 5 Business Rules, mục 7 Luồng nghiệp vụ).

DBMS mục tiêu: **MySQL 8.0+**. Lý do: cộng đồng yêu cầu MySQL ( PROJECT_PLAN.md ), dễ triển khai trong môi trường học tập. BR-002 (chống overlap booking) được thực thi bằng trigger kiểm tra tại application layer; BR-004/BR-015 tương tự. `JSON` (MySQL 8) thay thế `JSONB` cho snapshot policy. Không dùng `daterange`/`EXCLUDE USING gist`/`CITEXT`/partial index — xem chi tiết từng feature ở mục 11.4.

File DBML để dán vào https://dbdiagram.io: [`hotel_management_for_dbdiagram.dbml`](./hotel_management_for_dbdiagram.dbml)

Bản này được review theo ba mục tiêu: **tách rõ master/config data với transaction snapshot**, **mỗi giá trị có đúng một source of truth**, và **thay đổi giá/chính sách trong tương lai không hồi tố xuống giao dịch đã chốt**. Danh sách thay đổi ở mục 13, các điểm xung đột với requirement gốc ở mục 14.

---

## 1. Nguyên tắc thiết kế

| #   | Nguyên tắc                                                                                                                                                                                                                                                                                               | Lý do                                                                                                                                                 |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| P1  | Khóa chính`BIGINT NOT NULL AUTO_INCREMENT`, kèm `public_id CHAR(36)` hoặc `*_code` để lộ ra ngoài (URL, API, email, hóa đơn)                                                                                                                                               | PK tự tăng giúp index nhỏ và join nhanh; không lộ ID tuần tự ra ngoài để tránh enumeration attack                                         |
| P2  | Tiền tệ dùng`NUMERIC(14,2)` + cột `currency CHAR(3)`, **không dùng** `FLOAT/DOUBLE`                                                                                                                                                                                                      | Float làm sai lệch phép cộng tiền.`14,2` đủ cho VND                                                                                           |
| P3  | Thời điểm dùng`TIMESTAMPTZ`; ngày lưu trú dùng `DATE`                                                                                                                                                                                                                                          | Đêm khách sạn là đơn vị**ngày**; còn `checked_in_at` thực tế là thời điểm nên phải có timezone                              |
| P4  | Khoảng ngày lưu trú luôn là nửa mở`[check_in, check_out)`                                                                                                                                                                                                                                        | Khách A trả phòng 17/08 thì khách B nhận phòng đúng 17/08. Khoảng đóng`[]` sẽ báo trùng phòng sai và mất doanh thu một đêm      |
| P5  | Soft delete bằng`deleted_at` cho Room/RoomType/User/Staff; FK từ dữ liệu lịch sử dùng `ON DELETE RESTRICT`                                                                                                                                                                                      | BR-008: không hard delete nếu làm mất liên kết booking/invoice/audit                                                                             |
| P6  | Mọi bảng nghiệp vụ có`created_at`, `updated_at`; bảng có hành vi người dùng thêm `created_by`                                                                                                                                                                                            | Truy vết vận hành, phục vụ tranh chấp với khách                                                                                                |
| P7  | **Ba tầng dữ liệu tách biệt**: master/config hiện tại (RoomType, RateOverride, ServiceItem, CancellationPolicy) → transaction snapshot (BookingRoomNight, FolioCharge) → chứng từ bất biến (Invoice, InvoiceItem). Không dùng config hiện tại để tính lại giao dịch đã chốt | Sửa giá phòng hôm nay không được làm đổi hóa đơn đã in tháng trước. Xem sơ đồ ở QĐ-5                                           |
| P8  | **Không dùng SCD Type 2** cho giá RoomType/ServiceItem                                                                                                                                                                                                                                            | Snapshot tại transaction đã giải quyết trọn vấn đề giữ giá cũ. SCD2 thêm bảng version, thêm join, thêm lỗi mà không thêm giá trị |
| P9  | **Một giá trị chỉ có một source of truth.** Cột aggregate ở cấp cha được phép tồn tại nhưng phải ghi rõ tổng hợp từ đâu và cập nhật trong cùng transaction                                                                                                                | Xem bảng nguồn dữ liệu ở mục 11.1                                                                                                                |
| P10 | Trạng thái dùng`ENUM` khi tập giá trị do code quyết định; dùng **bảng lookup** khi vận hành cần tự thêm giá trị                                                                                                                                                                  | `booking_status` là logic code → enum. `booking_sources` (thêm OTA mới) → bảng                                                               |
| P11 | Ràng buộc nghiệp vụ bất biến đặt ở DB (CHECK/UNIQUE/trigger) hoặc application layer (pessimistic locking), không chỉ ở application đơn lẻ                                                                                                                                  | Nhiều instance backend + job + query tay đều ghi được. Trigger và stored procedure đảm bảo toàn vẹn ngay cả khi truy vấn trực tiếp vào DB                          |

---

## 2. Conceptual Design

### 2.1. Bảy nhóm nghiệp vụ (bounded context)

| Nhóm                              | Trả lời câu hỏi                                         | Chức năng nguồn                                                                 |
| ---------------------------------- | ----------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| **Identity & Access**        | Ai đang dùng hệ thống, được làm gì                 | 2.1 Đăng ký/đăng nhập/OAuth/reset password, 2.2 Profile, 2.4 Quản lý Staff |
| **Inventory**                | Khách sạn có gì để bán                               | 2.3 Quản lý phòng, loại phòng, ảnh, trạng thái vận hành                  |
| **Pricing & Policy**         | Bán giá bao nhiêu, hủy thì hoàn bao nhiêu            | 2.2 Xem giá, tính tổng tiền; 2.2 Hủy booking có hoàn tiền                  |
| **Booking & Availability**   | Ai giữ phòng nào, trong khoảng nào                     | 2.2 Đặt phòng, 2.3 Booking ngoài hệ thống, BR-002/003/009                    |
| **Stay & Billing**           | Khách đang ở, phải trả bao nhiêu                      | 2.3 Check-in, check-out, phát sinh, invoice                                       |
| **Payment**                  | Tiền đã thực nhận chưa, có xác minh chưa           | 2.2 Internet Banking, BR-012                                                       |
| **Feedback / Comms / Audit** | Khách nói gì, hệ thống đã gửi gì, ai đã sửa gì | 2.2 Review, 2.4 Compose email, 2.5 System email                                    |

### 2.2. Các thực thể khái niệm và lý do tồn tại

Mỗi thực thể dưới đây tồn tại vì một câu trong tài liệu yêu cầu, không phải vì thói quen thiết kế.

**Identity & Access**

- **User** — chủ thể đăng nhập. Tách riêng khỏi Customer/Staff vì một người có thể vừa là nhân viên vừa là khách lưu trú, và vì OAuth (dòng 46-50) gắn với danh tính đăng nhập chứ không gắn với vai trò.
- **Role, Permission** — dòng 159-161 nói "Admin có toàn bộ quyền của Staff cộng thêm quyền quản trị". Nếu dùng một cột `role VARCHAR` thì mỗi lần thêm quyền phải sửa code và deploy. Tách Role–Permission cho phép cấu hình bằng dữ liệu. Quan hệ User–Role là N–N để một người kiêm nhiệm được.
- **CustomerProfile / StaffProfile** — hai vai trò có thuộc tính không giao nhau (khách cần địa chỉ, ngày sinh; nhân viên cần mã NV, ngày vào làm, phòng ban). Nhồi chung một bảng sẽ tạo hàng loạt cột NULL và không thể ràng buộc NOT NULL cho bên nào.
- **SocialAccount** — dòng 46-50, một User có thể liên kết Google + Facebook + X cùng lúc → quan hệ 1–N, không phải cột trên User.
- **AuthToken** — dòng 42-45 và 179-184: token activation/reset "có thời hạn và chỉ dùng một lần". Đây là thực thể có vòng đời riêng (phát hành → hết hạn → đã dùng), không phải thuộc tính của User.

**Inventory**

- **RoomType** — dòng 113-116: loại phòng có tên, số giường, sức chứa, giá cơ bản, tiện nghi, mô tả và được "gán cho từng Room". Tách RoomType khỏi Room để tránh lặp mô tả/giá/tiện nghi trên 50 phòng Deluxe giống nhau. **Đây là master/config data**: `base_price` là giá niêm yết hiện tại, không phải giá của giao dịch cũ (P7).
- **Room** — đơn vị vật lý có số phòng, tầng, view, trạng thái vận hành. Là thực thể bị "khóa" bởi booking nên phải độc lập.
- **Amenity** — dòng 61 và 109: tiện nghi vừa mô tả ở loại phòng vừa dùng để **lọc**. Nếu lưu chuỗi text "wifi, tv, minibar" thì không thể lọc bằng index. Chuẩn hóa thành danh mục + bảng nối.
- **RoomImage / RoomTypeImage** — dòng 110: "upload, cập nhật, xóa và **sắp xếp**" → cần bảng riêng có `sort_order`, không phải mảng URL.
- **RoomStatusBlock** — dòng 117-120 và BR-003/BR-004: bảo trì là **một khoảng thời gian**, không phải một cờ. Cờ `available=false` không trả lời được "phòng này bảo trì 20/08–22/08, có bán được ngày 25/08 không". Đây là lý do tồn tại quan trọng nhất của bảng này.

> View của phòng (dòng 53, 67) là **enum `room_view` trên `rooms.view_type`**, không tách thành bảng riêng. Tập giá trị (SEA/CITY/GARDEN/POOL/MOUNTAIN/NONE) do code quyết định vì nó xuất hiện cứng trên bộ lọc UI, và bảng lookup chỉ có `code`+`name` không mang thêm thông tin nào (P10).

**Pricing & Policy**

- **RateOverride** — giá thực bán khác giá niêm yết theo mùa/cuối tuần/dịp lễ. **Master/config data**: dùng để *tính* giá lúc đặt, không dùng để *tính lại* booking cũ.
- **CancellationPolicy + CancellationPolicyRule** — dòng 97 và BR-005: "hệ thống tính số tiền hoàn dựa trên **thời điểm hủy**". Một mốc thời gian duy nhất không diễn đạt được chính sách thực tế nhiều bậc (72h → 100%, 30h → 50%, 0h → 0%), nên Policy tách thành nhiều Rule. Admin gắn policy cho từng RoomType; toàn bộ policy + rules được snapshot vào từng `booking_rooms` để đổi chính sách không hồi tố khách cũ.

**Booking & Availability**

- **Booking** — đơn đặt: trạng thái, người liên hệ, tổng tiền, nguồn. Là gốc của mọi luồng ở mục 7. **Không giữ khoảng ngày lưu trú** — khoảng ngày thuộc BookingRoom (xem QĐ-6).
- **BookingRoom** — dòng đặt cho **một phòng trong một khoảng ngày**. Đây là quyết định thiết kế then chốt: (a) BR-002 nói ràng buộc overlap là theo **phòng**, nên trigger chống overlap phải nằm trên bảng có `room_id`; (b) khách đặt 2 phòng trong 1 đơn là bình thường; (c) mỗi phòng có thể có ngày trả khác nhau; (d) đổi phòng giữa kỳ lưu trú trở thành thêm dòng, không phải sửa dòng cũ.
- **BookingRoomNight** — một dòng cho mỗi đêm với giá đêm đó. **Đây là source of truth của giá phòng đã bán.** Cần vì: (a) dòng 154-156 yêu cầu doanh thu theo **ngày**, một booking 28/08–02/09 phải phân bổ sang hai tháng; (b) giá từng đêm khác nhau nên không tồn tại một con số "giá phòng" duy nhất cho cả kỳ lưu trú; (c) khóa giá tại thời điểm đặt để đổi `base_price`/`RateOverride` sau này không hồi tố.
- **BookingGuest** — dòng 105 (staff xem *ai* đang ở trong phòng) và dòng 122 (booking walk-in không có tài khoản). **Khách lưu trú ≠ người đặt**: người đặt là `bookings.contact_*`, khách thực tế ở là BookingGuest. Khách sạn ở Việt Nam phải khai báo lưu trú theo CCCD/passport → cần lưu giấy tờ tùy thân, có mã hóa.
- **BookingSource** — dòng 124 liệt kê WEBSITE, WALK_IN, PHONE, BOOKING_COM, AGODA, STAFF_MANUAL và dòng 152 yêu cầu thống kê theo nguồn. Là bảng lookup vì OTA mới phát sinh theo hợp đồng thương mại. `commission_percent` là **config hiện tại**, phải snapshot vào booking (mục 6.2).
- **BookingStatusHistory** — mục 7 định nghĩa các luồng chuyển trạng thái. Đây là **business timeline** (`PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT`), khác AuditLog (log kỹ thuật mọi thao tác sửa dữ liệu). Khi khách khiếu nại "tôi không hủy đơn này", cần biết ai đổi, lúc nào, do tác nhân nào.

**Stay & Billing**

- **ServiceItem** — danh mục dịch vụ với giá **hiện tại**. Master/config data.
- **FolioCharge** — dòng 132: "các khoản phát sinh nếu có" (minibar, giặt ủi, late check-out). **Snapshot giá dịch vụ tại thời điểm phát sinh**: đổi giá Laundry hôm nay không làm đổi khoản đã ghi hôm qua. Không thể gộp vào tổng tiền booking vì phát sinh sau khi đặt và phải hiện thành dòng riêng trên hóa đơn.
- **Invoice / InvoiceItem** — dòng 130-136: tạo hóa đơn khi check-out, in/tải về. Tách khỏi Booking vì hóa đơn là **chứng từ bất biến sau khi ISSUED** (BR-013): tên khách, mã số thuế, từng dòng tiền đóng băng tại thời điểm phát hành. Booking còn đổi trạng thái, hóa đơn thì không.

**Payment**

- **Payment** — dòng 79-83: mỗi lần giao dịch với gateway. Booking 1–N Payment vì có đặt cọc rồi trả phần còn lại khi check-out (dòng 134), và vì lần trả thất bại rồi trả lại là hai giao dịch. **Payment/Refund là ledger — source of truth của dòng tiền.**
- **PaymentEvent** — BR-012 và dòng 83: "kết quả thanh toán phải được xác minh từ phía gateway". Lưu nguyên payload callback để đối soát, chống replay (gateway gửi lại IPN nhiều lần là chuyện thường), và làm chứng cứ khi tranh chấp.
- **Refund** — dòng 81 có trạng thái `Refunded` và dòng 97 tính tiền hoàn. Tách khỏi Payment vì một lần thu có thể hoàn nhiều lần/hoàn một phần.

**Feedback, Comms, Audit**

- **Review** — dòng 98-101: chỉ khách đã hoàn tất lưu trú mới được đánh giá; đánh giá phòng, chất lượng, nhân viên/dịch vụ; **mỗi booking tối đa một review** (BR-007) → `booking_id UNIQUE`.
- **EmailMessage** — dòng 174-177 và 185-193: Admin soạn email, System gửi, "lưu trạng thái gửi và lịch sử email". Cần cho retry khi gửi lỗi và cho việc chứng minh đã gửi email xác nhận booking.
- **AuditLog** — BR-008 nhắc "audit", dòng 167 nhắc "audit history". Ghi thao tác đổi giá, hủy booking, void hóa đơn.

### 2.3. Quan hệ giữa các thực thể

```
User 1─N UserRole N─1 Role N─N Permission
User 1─1 CustomerProfile          User 1─1 StaffProfile
User 1─N SocialAccount            User 1─N AuthToken

RoomType 1─N Room                 RoomType N─N Amenity
RoomType 1─N RoomTypeBed          RoomType 1─N RoomTypeImage
Room     N─N Amenity              Room     1─N RoomImage
Room     1─N RoomStatusBlock
RoomType 1─N RateOverride  (hoặc gắn trực tiếp Room)

CancellationPolicy 1─N CancellationPolicyRule

CustomerProfile 1─N Booking  (nullable: walk-in không có account)
BookingSource   1─N Booking
CancellationPolicy 1─N RoomType
CancellationPolicy 1─N BookingRoom  (+ snapshot JSONB toàn bộ policy vào từng booking room)

Booking 1─N BookingRoom ────── N─1 Room       ← overlap check bằng trigger BR-002
BookingRoom 1─N BookingRoomNight              ← 1 dòng / 1 đêm, source of truth giá phòng
BookingRoom 0─1 BookingRoom (moved_from)      ← chuỗi đổi phòng giữa kỳ
Booking 1─N BookingGuest ───── N─1 BookingRoom (nullable, composite FK)
Booking 1─N BookingStatusHistory
Booking 1─N FolioCharge  N─1 ServiceItem
Booking 1─N Payment 1─N PaymentEvent
Payment 1─N Refund
Booking 1─N Invoice 1─N InvoiceItem
Invoice 0─1 Invoice (replaced_by)             ← VOID + hóa đơn thay thế
Booking 1─1 Review                            ← UNIQUE(booking_id) = BR-007

User 1─N EmailMessage (recipient)             User 1─N AuditLog (actor)
```

### 2.4. Sáu quyết định thiết kế cần thống nhất trước khi code

**QĐ-1. Availability là kết quả tính toán, không phải cột lưu sẵn.**
Tài liệu nói thẳng ở dòng 119 và BR-003. Một phòng khả dụng cho khoảng `[in, out)` khi và chỉ khi: `rooms.operational_status = 'ACTIVE'` **và** không có `booking_rooms` đang hiệu lực nào overlap **và** không có `room_status_blocks` nào overlap. Không có cột `is_available` trong schema — cố tình như vậy, vì một cột như thế sai ngay khi có booking đầu tiên.

**QĐ-2. Chống trùng phòng bằng ràng buộc DB, không bằng "check rồi insert".**
Dòng 74 yêu cầu "kiểm tra lại availability trước khi tạo booking". Nhưng kiểm tra ở application rồi mới insert vẫn để lọt double-booking: hai request đồng thời đều đọc thấy trống rồi đều ghi thành công. Giải pháp là trigger BEFORE INSERT/UPDATE trên `booking_rooms` — request thứ hai bị DB từ chối ngay tại trigger, không phụ thuộc timing. Kết hợp `SELECT ... FOR UPDATE` trên `rooms` trong transaction tạo booking để giảm contention ở tầng trigger.

**QĐ-3. Booking PENDING phải có thời điểm hết hạn.**
Dòng 76-78: booking tạo ở trạng thái PENDING rồi mới thanh toán. Nếu khách bỏ giữa đường, phòng bị giữ vô thời hạn và không bán được. Cần `hold_expires_at` + job giải phóng. Thiếu cột này là lỗi thất thoát doanh thu.

**QĐ-4. Snapshot mọi thứ ảnh hưởng tới tiền, đúng một lần, tại đúng thời điểm.**
Giá đêm (`booking_room_nights.price`), chính sách hủy (`booking_rooms.cancellation_policy_snapshot`), hoa hồng OTA (`bookings.source_commission_percent_snapshot`), giá dịch vụ (`folio_charges.unit_price`), thông tin người mua trên hóa đơn (`invoices.buyer_*`) đều được copy tại thời điểm giao dịch. Nếu join sang bảng gốc để hiển thị, việc Staff sửa giá hôm nay sẽ làm đổi hóa đơn đã in tháng trước.

**QĐ-5. Ba mốc snapshot — thay đổi master data không hồi tố xuống dưới.**

```
  RoomType.base_price + RateOverride          ServiceItem.unit_price
  (master/config hiện tại)                    (master/config hiện tại)
            │                                           │
            │ booking được chốt                         │ dịch vụ được sử dụng
            ▼                                           ▼
  BookingRoomNight.price                      FolioCharge.unit_price
  = giá phòng thực bán từng đêm               = giá dịch vụ lúc phát sinh
  (transaction snapshot, bất biến)            (transaction snapshot, bất biến)
            │                                           │
            └─────────────────┬─────────────────────────┘
                              │ Staff lập hóa đơn / checkout
                              ▼
                      InvoiceItem (DRAFT: có thể regenerate)
                              │ Staff nhấn Issue
                              ▼
                      Invoice ISSUED + InvoiceItem
                      = chứng từ bất biến (BR-013)
```

Sau mỗi mũi tên, sửa dữ liệu ở tầng trên **không** được làm đổi số ở tầng dưới. Đây là lý do không cần SCD Type 2 cho giá RoomType/ServiceItem (P8): giá cũ đã nằm an toàn trong transaction snapshot.

**QĐ-6. Khoảng ngày lưu trú chỉ tồn tại ở `booking_rooms`.**
`bookings` **không** có `check_in_date`/`check_out_date`/`nights`. Lý do: một booking có nhiều phòng, mỗi phòng có thể khác ngày trả, khách có thể đổi phòng giữa kỳ, và overlap được kiểm theo từng `room_id`. Cần khoảng chung của booking thì derive `MIN(check_in_date)`, `MAX(check_out_date)` từ các `booking_rooms` đang hiệu lực — có view `v_booking_stay_range` ở mục 9.7 cho việc này.

---

## 3. Logical Design — Identity & Access

### 3.1. `users`

Tài khoản đăng nhập. Không chứa thuộc tính riêng của khách hay nhân viên.

| Cột                            | Kiểu        | Ràng buộc                                    | Giải thích                                                                                                                                                        |
| ------------------------------- | ------------ | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                          | BIGINT       | PK, AUTO_INCREMENT                             |                                                                                                                                                                     |
| `public_id`                   | CHAR(36)     | NOT NULL, UNIQUE, default`UUID()`              | Dùng trong URL/API thay cho id (P1)                                                                                                                                |
| `email`                       | VARCHAR(255) | NOT NULL, UNIQUE                               | Dùng collation`utf8mb4_0900_ai_ci` để`An@x.com` = `an@x.com` (MySQL 8). Dòng 37 yêu cầu kiểm tra email chưa được dùng                    |
| `email_verified_at`           | TIMESTAMPTZ  | NULL                                           | Dòng 38, 41: NULL = chờ xác thực, chưa cho truy cập chức năng                                                                                               |
| `password_hash`               | TEXT         | NULL                                           | NULL hợp lệ với tài khoản tạo từ OAuth (dòng 50). Lưu bcrypt cost ≥ 12 hoặc argon2id                                                                     |
| `phone`                       | VARCHAR(20)  | NULL, UNIQUE khi không NULL                   | Dòng 36 thu số điện thoại                                                                                                                                      |
| `full_name`                   | VARCHAR(150) | NOT NULL                                       |                                                                                                                                                                     |
| `avatar_url`                  | TEXT         | NULL                                           | Dòng 58                                                                                                                                                            |
| `status`                      | user_status  | NOT NULL, default`PENDING_VERIFICATION`      | `PENDING_VERIFICATION / ACTIVE / SUSPENDED / DEACTIVATED`. Dòng 166, dòng 59 (khách không tự đổi được)                                                  |
| `failed_login_count`          | SMALLINT     | NOT NULL, default 0                            | Chống brute force                                                                                                                                                  |
| `locked_until`                | TIMESTAMPTZ  | NULL                                           | Khóa tạm sau nhiều lần sai                                                                                                                                      |
| `last_login_at`               | TIMESTAMPTZ  | NULL                                           |                                                                                                                                                                     |
| `created_at` / `updated_at` | TIMESTAMPTZ  | NOT NULL, default now()                        |                                                                                                                                                                     |
| `deleted_at`                  | TIMESTAMPTZ  | NULL                                           | Soft delete (P5, BR-008)                                                                                                                                            |

Điều kiện "có `password_hash` hoặc có social account" không cưỡng chế được bằng CHECK (phải đọc bảng khác) → kiểm ở service layer.

### 3.2. `roles`, `permissions`, `role_permissions`, `user_roles`

| Bảng                | Cột chính                                                 | Ràng buộc & giải thích                                                                                                |
| -------------------- | ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `roles`            | `id`, `code`, `name`, `description`, `is_system`  | `code` UNIQUE (`CUSTOMER`/`STAFF`/`ADMIN`). `is_system=true` chặn Admin xóa role gốc làm hỏng phân quyền |
| `permissions`      | `id`, `code`, `resource`, `action`, `description` | `code` UNIQUE dạng `room:create`, `booking:cancel_any`, `staff:manage`. UNIQUE(`resource`,`action`)          |
| `role_permissions` | `role_id`, `permission_id`                              | PK kép; FK`ON DELETE CASCADE` (xóa permission thì bỏ khỏi role là đúng)                                         |
| `user_roles`       | `user_id`, `role_id`, `assigned_at`, `assigned_by`  | PK kép. N–N để một User kiêm nhiệm;`assigned_by` để audit ai cấp quyền                                       |

Vì sao không dùng `users.role VARCHAR`: dòng 160-161 mô tả Admin ⊃ Staff. Với enum một cột, mỗi lần thêm quyền cho Staff phải sửa code kiểm tra ở mọi endpoint. Với RBAC, thêm dòng vào `role_permissions` là xong.

### 3.3. `customer_profiles`

| Cột                                    | Kiểu       | Ràng buộc                                   | Giải thích                                                |
| --------------------------------------- | ----------- | --------------------------------------------- | ----------------------------------------------------------- |
| `id`                                  | BIGINT      | PK                                            |                                                             |
| `user_id`                             | BIGINT      | NOT NULL,**UNIQUE**, FK→users RESTRICT | UNIQUE tạo quan hệ 1–1                                   |
| `date_of_birth`                       | DATE        | NULL, CHECK`< CURRENT_DATE`                 |                                                             |
| `gender`                              | gender_enum | NULL                                          |                                                             |
| `nationality`                         | CHAR(2)     | NULL                                          | ISO 3166-1                                                  |
| `address_line`, `city`, `country` | VARCHAR     | NULL                                          | Dòng 58 cho sửa địa chỉ                                |
| `loyalty_points`                      | INT         | NOT NULL default 0, CHECK`>= 0`             | Dòng 148: mở rộng khách quay lại                       |
| `total_stays`                         | INT         | NOT NULL default 0                            | Aggregate từ`bookings` có `status='CHECKED_OUT'` (P9) |
| `notes`                               | TEXT        | NULL                                          | Ghi chú nội bộ (khách VIP, dị ứng)                    |

### 3.4. `staff_profiles`

| Cột                  | Kiểu             | Ràng buộc                          | Giải thích                                                                          |
| --------------------- | ----------------- | ------------------------------------ | ------------------------------------------------------------------------------------- |
| `id`                | BIGINT            | PK                                   |                                                                                       |
| `user_id`           | BIGINT            | NOT NULL, UNIQUE, FK→users RESTRICT | RESTRICT vì BR-008/dòng 167: Staff đã xử lý booking/invoice không được xóa |
| `employee_code`     | VARCHAR(20)       | NOT NULL, UNIQUE                     | Mã nhân viên nội bộ                                                              |
| `position`          | VARCHAR(80)       | NOT NULL                             | Receptionist, Housekeeping, Manager                                                   |
| `department`        | VARCHAR(80)       | NULL                                 |                                                                                       |
| `hired_at`          | DATE              | NOT NULL                             |                                                                                       |
| `terminated_at`     | DATE              | NULL, CHECK`>= hired_at`           | Dòng 165 "deactivate/fire" → ghi ngày, không xóa dòng                           |
| `employment_status` | employment_status | NOT NULL default`ACTIVE`           | `ACTIVE / ON_LEAVE / TERMINATED`                                                    |
| `base_salary`       | NUMERIC(14,2)     | NULL                                 | Nhạy cảm: chỉ Admin đọc, cần view riêng hoặc column-level grant               |

### 3.5. `user_social_accounts`

| Cột                 | Kiểu          | Ràng buộc                 | Giải thích                                                      |
| -------------------- | -------------- | --------------------------- | ----------------------------------------------------------------- |
| `id`               | BIGINT         | PK                          |                                                                   |
| `user_id`          | BIGINT         | NOT NULL, FK→users CASCADE |                                                                   |
| `provider`         | oauth_provider | NOT NULL                    | `GOOGLE / FACEBOOK / TWITTER` (dòng 47-49)                     |
| `provider_user_id` | VARCHAR(191)   | NOT NULL                    | ID do provider trả về                                           |
| `provider_email`   | CITEXT         | NULL                        | Email tại provider, có thể khác email hệ thống              |
| `raw_profile`      | JSONB          | NULL                        | Payload gốc, để về sau lấy thêm field không cần migration |
| `linked_at`        | TIMESTAMPTZ    | NOT NULL default now()      |                                                                   |

- UNIQUE(`provider`, `provider_user_id`) — chặn hai User cùng gắn một tài khoản Google (đường chiếm tài khoản bằng cách link trùng).
- UNIQUE(`user_id`, `provider`) — mỗi User chỉ gắn một tài khoản mỗi provider.

### 3.6. `auth_tokens`

Dùng chung cho activation (dòng 179-181) và reset password (dòng 42-45, 182-184).

| Cột             | Kiểu           | Ràng buộc                     | Giải thích                                                                                                                                                                                                                          |
| ---------------- | --------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`           | BIGINT          | PK                              |                                                                                                                                                                                                                                       |
| `user_id`      | BIGINT          | NOT NULL, FK→users CASCADE     |                                                                                                                                                                                                                                       |
| `token_type`   | auth_token_type | NOT NULL                        | `EMAIL_VERIFICATION / PASSWORD_RESET / EMAIL_CHANGE`                                                                                                                                                                                |
| `token_hash`   | CHAR(64)        | NOT NULL, UNIQUE                | **Lưu SHA-256 của token, không lưu token gốc.** Token là chuỗi random entropy cao nên SHA-256 ở đây là đủ (khác trường hợp CCCD ở mục 6.5). Nếu DB bị lộ, kẻ tấn công không dùng lại được token |
| `expires_at`   | TIMESTAMPTZ     | NOT NULL, CHECK`> created_at` | "Đường dẫn có thời hạn" (dòng 45)                                                                                                                                                                                             |
| `used_at`      | TIMESTAMPTZ     | NULL                            | "chỉ được sử dụng một lần" (dòng 45): hợp lệ khi`used_at IS NULL AND expires_at > now()`                                                                                                                                 |
| `requested_ip` | INET            | NULL                            | Điều tra khi có dấu hiệu lạm dụng                                                                                                                                                                                              |
| `created_at`   | TIMESTAMPTZ     | NOT NULL default now()          |                                                                                                                                                                                                                                       |

Index: `(user_id, token_type)` với điều kiện `used_at IS NULL`. Job xóa token hết hạn quá 30 ngày.

### 3.7. `auth_refresh_tokens`

Lưu trạng thái refresh token cho JWT session. Bảng này tách khỏi `auth_tokens` vì `auth_tokens` là token một lần cho email verification/reset password, còn refresh token có vòng đời đăng nhập: phát hành → dùng để rotate → revoke/logout → hết hạn.

| Cột               | Kiểu        | Ràng buộc                 | Giải thích                                                                                                        |
| ------------------ | ------------ | --------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `id`             | BIGINT       | PK                          |                                                                                                                     |
| `user_id`        | BIGINT       | NOT NULL, FK→users CASCADE | User sở hữu refresh token                                                                                          |
| `jwt_id`         | VARCHAR(36)  | NOT NULL, UNIQUE            | Giá trị claim `jti` trong refresh JWT. **Không lưu raw JWT**, nên DB lộ cũng không có token gốc để dùng lại |
| `expires_at`     | TIMESTAMPTZ  | NOT NULL                    | Hết hạn theo `app.jwt.refresh-token-ttl`                                                                           |
| `revoked_at`     | TIMESTAMPTZ  | NULL                        | Có giá trị khi logout hoặc khi token bị rotate                                                                      |
| `rotated_to_jti` | VARCHAR(36)  | NULL                        | Khi refresh token cũ được rotate, lưu `jti` của refresh token mới để audit/debug                                  |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL default now() |                                                                                                                     |

Index:

- UNIQUE(`jwt_id`) — một refresh JWT chỉ có một trạng thái trong DB.
- `(user_id, revoked_at, expires_at)` — tìm token còn hiệu lực của một user.
- `(expires_at)` — job dọn token hết hạn.

Luồng kiểm tra hợp lệ: parse refresh JWT bằng secret → lấy `sub` và `jti` → lock dòng `auth_refresh_tokens.jwt_id` → hợp lệ khi `user.public_id = sub`, `revoked_at IS NULL`, `expires_at > now()`.

### 3.8. `shifts` và `shift_assignments`

Hai bảng phục vụ **quản lý ca trực** (dòng 168-173, dòng 268-271). Đây là phần "có thể triển khai thêm" trong spec, nhưng schema cần chuẩn bị sẵn để thêm sau không phải thiết kế lại.

#### `shifts` — định nghĩa ca

| Cột                 | Kiểu       | Ràng buộc            | Giải thích                                                                        |
| -------------------- | ----------- | ---------------------- | ----------------------------------------------------------------------------------- |
| `id`               | SMALLINT    | PK                     |                                                                                     |
| `code`             | VARCHAR(30) | NOT NULL, UNIQUE       | `MORNING / AFTERNOON / NIGHT`                                                     |
| `name`             | VARCHAR(80) | NOT NULL               | Ca sáng, Ca chiều, Ca đêm                                                       |
| `start_time`       | TIME        | NOT NULL               | Giờ bắt đầu                                                                     |
| `end_time`         | TIME        | NOT NULL               | Giờ kết thúc                                                                     |
| `crosses_midnight` | BOOLEAN     | NOT NULL default false | Ca đêm 22:00–06:00 vượt qua ngày. Cần để tính`shift_end_at` chính xác ở application layer |
| `is_active`        | BOOLEAN     | NOT NULL default true  | Không xóa ca đang dùng, chỉ vô hiệu hóa                                     |

`TIME` không có timezone — giờ làm việc luôn theo múi giờ địa phương của khách sạn, không phải UTC.

#### `shift_assignments` — phân công Staff vào ca theo ngày

| Cột                            | Kiểu             | Ràng buộc                           | Giải thích                                                                                                                    |
| ------------------------------- | ----------------- | ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `id`                          | BIGINT            | PK                                    |                                                                                                                                 |
| `staff_id`                    | BIGINT            | NOT NULL, FK→staff_profiles RESTRICT |                                                                                                                                 |
| `shift_id`                    | SMALLINT          | NOT NULL, FK→shifts RESTRICT         |                                                                                                                                 |
| `work_date`                   | DATE              | NOT NULL                              | Dòng 170: gán Staff vào ca**theo ngày**                                                                               |
| `shift_start_at`              | DATETIME          | NOT NULL                              | Thời điểm bắt đầu ca tại ngày làm việc. Tính từ`work_date` + giờ bắt đầu ca. Vượt nửa đêm xử lý ở application layer |
| `shift_end_at`                | DATETIME          | NOT NULL                              | Thời điểm kết thúc ca. Nếu ca vượt nửa đêm (Night 22:00–06:00), giá trị rơi vào ngày hôm sau — xử lý ở application layer |
| `status`                      | assignment_status | NOT NULL default`SCHEDULED`         | `SCHEDULED / COMPLETED / ABSENT / CANCELLED`                                                                                  |
| `note`                        | TEXT              | NULL                                  | Ghi chú cho Staff khác hoặc lý do vắng                                                                                     |
| `assigned_by`                 | BIGINT            | NOT NULL, FK→users RESTRICT          |                                                                                                                                 |
| `created_at` / `updated_at` | TIMESTAMPTZ       | NOT NULL default now()                |                                                                                                                                 |

**BR-015 / dòng 173 — không trùng ca:**

```sql
-- MySQL: sử dụng BEFORE INSERT/UPDATE trigger thay vì EXCLUDE constraint
-- PostgreSQL (tham khảo, không chạy trên MySQL):
-- ALTER TABLE shift_assignments
--   ADD CONSTRAINT shift_no_overlap
--   EXCLUDE USING gist (staff_id WITH =, shift_period WITH &&)
--   WHERE (status IN ('SCHEDULED', 'COMPLETED'));
```

MySQL không có kiểu `TSTZRANGE` hay `EXCLUDE ... USING gist`, nên ràng buộc không trùng ca được triển khai qua `BEFORE INSERT/UPDATE` trigger kiểm tra overlap giữa `shift_start_at` và `shift_end_at` cho cùng `staff_id` và `work_date` khi `status IN ('SCHEDULED', 'COMPLETED')`. Trigger xử lý đúng cả khi ca vượt nửa đêm (ví dụ: ca Night 22:00–06:00, `shift_end_at` nằm ở ngày hôm sau).

UNIQUE(`staff_id`, `shift_id`, `work_date`) — không gán cùng một Staff vào cùng một ca trong cùng ngày (chặt hơn EXCLUDE vì không có khoảng giao nhau nào được phép).

Index: `(work_date, shift_id)` cho xem lịch trực ngày nào.

#### Tính `shift_start_at` / `shift_end_at`

`start_time` và `end_time` là `TIME` không có timezone, nên logic tính datetime thực tế ở application layer hoặc trigger. Giờ timezone của khách sạn nên lấy từ `hotel_settings` nếu có, thay vì hard-code.

- **Ca thường** (Morning 06:00–14:00): `end_time > start_time`, `shift_end_at` cùng ngày với `work_date`.
- **Ca đêm** (Night 22:00–06:00): `crosses_midnight = true` và `end_time <= start_time`, nên `shift_end_at` cộng thêm 1 ngày so với `work_date` → `shift_end_at` rơi vào ngày hôm sau, đúng 8 tiếng.

**MySQL trigger / application logic:**

```sql
-- Pseudo-code (điều chỉnh theo ngôn ngữ lập trình dùng)
SET NEW.shift_start_at = NEW.work_date + INTERVAL HOUR(shift.start_time) HOUR
                           + INTERVAL MINUTE(shift.start_time) MINUTE;
SET NEW.shift_end_at = NEW.work_date + INTERVAL HOUR(shift.end_time) HOUR
                         + INTERVAL MINUTE(shift.end_time) MINUTE;
-- Ca đêm: nếu crosses_midnight = true, cộng thêm 1 ngày
IF shift.crosses_midnight AND shift.end_time <= shift.start_time THEN
  SET NEW.shift_end_at = NEW.shift_end_at + INTERVAL 1 DAY;
END IF;
```

Nếu bỏ shift management, hai bảng này có thể để trống mà không ảnh hưởng luồng chính.

---

## 4. Logical Design — Inventory

### 4.1. `room_types`

**Master/config data** (P7): các cột giá ở đây là giá niêm yết **hiện tại**, dùng để tính giá lúc đặt. Không dùng để tính lại booking cũ.

| Cột                                             | Kiểu         | Ràng buộc                                  | Giải thích                                                                                                                    |
| ------------------------------------------------ | ------------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `id`                                           | BIGINT        | PK                                           |                                                                                                                                 |
| `code`                                         | VARCHAR(30)   | NOT NULL, UNIQUE                             | `STD`, `DLX`, `SUITE` — dùng trong báo cáo                                                                            |
| `name`                                         | VARCHAR(120)  | NOT NULL                                     | Dòng 115                                                                                                                       |
| `slug`                                         | VARCHAR(140)  | NOT NULL, UNIQUE                             | URL SEO cho trang Rooms (dòng 200)                                                                                             |
| `description`                                  | TEXT          | NULL                                         |                                                                                                                                 |
| `bed_count`                                    | SMALLINT      | NOT NULL, CHECK`BETWEEN 1 AND 10`          | Dòng 53, 65: lọc theo**số giường** → phải là số, không phải text                                               |
| `max_occupancy`                                | SMALLINT      | NOT NULL, CHECK`>= 1`                      | Dòng 61 "sức chứa", dòng 198 "số khách"                                                                                   |
| `max_adults`                                   | SMALLINT      | NOT NULL, CHECK`>= 1 AND <= max_occupancy` |                                                                                                                                 |
| `max_children`                                 | SMALLINT      | NOT NULL default 0, CHECK`>= 0`            |                                                                                                                                 |
| `base_price`                                   | NUMERIC(14,2) | NOT NULL, CHECK`>= 0`                      | Giá niêm yết 1 đêm, dòng 115                                                                                              |
| `currency`                                     | CHAR(3)       | NOT NULL default`VND`                      | P2                                                                                                                              |
| `extra_bed_price`                              | NUMERIC(14,2) | NULL, CHECK`>= 0`                          | Vượt sức chứa cơ bản                                                                                                      |
| `size_sqm`                                     | NUMERIC(6,2)  | NULL                                         |                                                                                                                                 |
| `is_active`                                    | BOOLEAN       | NOT NULL default true                        | Ngừng bán loại phòng mà không xóa                                                                                        |
| `sort_order`                                   | SMALLINT      | NOT NULL default 0                           | Dòng 199 phòng nổi bật                                                                                                      |
| `cancellation_policy_id`                       | BIGINT        | NULL, FK→cancellation_policies RESTRICT      | Admin chọn chính sách hủy áp dụng cho loại phòng này. Bắt buộc khi `is_active=true`; inactive được phép trống để cấu hình sau. RoomType cũ được backfill tạm bằng `NON_REFUND` |
| `created_at` / `updated_at` / `deleted_at` | TIMESTAMPTZ   |                                              |                                                                                                                                 |

### 4.2. `room_type_beds`

Chi tiết cấu hình giường (2 giường Queen + 1 sofa bed). `bed_count` ở trên là tổng để lọc nhanh; bảng này để hiển thị chi tiết.

| Cột             | Kiểu    | Ràng buộc                                                     |
| ---------------- | -------- | --------------------------------------------------------------- |
| `id`           | BIGINT   | PK                                                              |
| `room_type_id` | BIGINT   | NOT NULL, FK→room_types CASCADE                                |
| `bed_type`     | bed_type | NOT NULL —`SINGLE / DOUBLE / QUEEN / KING / SOFA_BED / BUNK` |
| `quantity`     | SMALLINT | NOT NULL, CHECK`>= 1`                                         |

UNIQUE(`room_type_id`, `bed_type`) — không tạo hai dòng KING cho cùng loại phòng, phải cộng vào `quantity`.

### 4.3. `amenities`, `room_type_amenities`, `room_amenities`

| Bảng                   | Cột                                                                                         | Ràng buộc & giải thích                                                                                                                   |
| ----------------------- | -------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `amenities`           | `id`, `code` UNIQUE, `name`, `icon`, `category`, `is_filterable`, `sort_order` | `category`: `ROOM / BATHROOM / TECH / SERVICE` để nhóm trên UI. `is_filterable` đánh dấu tiện nghi được đưa lên bộ lọc |
| `room_type_amenities` | `room_type_id`, `amenity_id`                                                             | PK kép. Tiện nghi mặc định của loại phòng (dòng 115)                                                                                |
| `room_amenities`      | `room_id`, `amenity_id`                                                                  | PK kép. Tiện nghi riêng của phòng cụ thể (dòng 109) — ví dụ một phòng Deluxe có ban công, các phòng Deluxe khác không     |

Tiện nghi hiệu lực của một phòng = hợp của hai bảng. Cách này tránh nhân bản 20 dòng tiện nghi cho từng phòng trong khi vẫn cho phép ngoại lệ theo phòng.

### 4.4. `rooms`

| Cột                                                              | Kiểu                   | Ràng buộc                                                    | Giải thích                                                                                                                                                                                           |
| ----------------------------------------------------------------- | ----------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `id`                                                            | BIGINT                  | PK                                                             |                                                                                                                                                                                                        |
| `room_number`                                                   | VARCHAR(20)             | NOT NULL,**partial UNIQUE** `WHERE deleted_at IS NULL` | Partial index vì: phòng 301 bị soft delete, sau này khách sạn mở lại phòng 301 → UNIQUE thường sẽ chặn sai                                                                               |
| `room_type_id`                                                  | BIGINT                  | NOT NULL, FK→room_types**RESTRICT**                     | Dòng 116 "gán Room Type cho từng Room". RESTRICT để không xóa loại phòng đang có phòng                                                                                                     |
| `view_type`                                                     | room_view               | NOT NULL default`NONE`                                       | Dòng 53, 67 lọc theo view. Enum`SEA / CITY / GARDEN / POOL / MOUNTAIN / NONE` — giá trị có kiểm soát để "Sea view" và "sea-view" không thành hai nhóm. Không tách bảng riêng (P10) |
| `floor`                                                         | SMALLINT                | NULL                                                           |                                                                                                                                                                                                        |
| `operational_status`                                            | room_operational_status | NOT NULL default`ACTIVE`                                     | `ACTIVE / MAINTENANCE / OUT_OF_SERVICE / RENOVATION`. Dòng 117-118. **Đây không phải availability** (QĐ-1) — chỉ là trạng thái dài hạn                                            |
| `housekeeping_status`                                           | housekeeping_status     | NOT NULL default`CLEAN`                                      | `CLEAN / DIRTY / CLEANING / INSPECTED`. Sau check-out phòng chuyển DIRTY; lễ tân không nên gán phòng DIRTY cho khách mới                                                                   |
| `price_override`                                                | NUMERIC(14,2)           | NULL, CHECK`>= 0`                                            | Phòng góc cùng loại nhưng giá khác. NULL = dùng`room_types.base_price`. Master/config data                                                                                                   |
| `max_occupancy_override`                                        | SMALLINT                | NULL, CHECK`>= 1`                                            |                                                                                                                                                                                                        |
| `description`                                                   | TEXT                    | NULL                                                           | Dòng 109                                                                                                                                                                                              |
| `is_active`                                                     | BOOLEAN                 | NOT NULL default true                                          | Dòng 111-112: ưu tiên vô hiệu hóa thay vì hard delete                                                                                                                                           |
| `created_at` / `updated_at` / `deleted_at` / `created_by` |                         |                                                                |                                                                                                                                                                                                        |

Index: `(room_type_id)`, `(view_type)`, và `(operational_status, is_active)` với `WHERE deleted_at IS NULL`.

### 4.5. `room_images` và `room_type_images`

Hai bảng cùng cấu trúc, tách ra để FK rõ ràng (một bảng dùng chung với `owner_type` sẽ mất khả năng ràng buộc FK).

| Cột                           | Kiểu        | Ràng buộc            | Giải thích                                                                     |
| ------------------------------ | ------------ | ---------------------- | -------------------------------------------------------------------------------- |
| `id`                         | BIGINT       | PK                     |                                                                                  |
| `room_id` / `room_type_id` | BIGINT       | NOT NULL, FK CASCADE   | Xóa phòng thì xóa ảnh là đúng (ảnh không phải dữ liệu tài chính)  |
| `url`                        | TEXT         | NOT NULL               |                                                                                  |
| `storage_key`                | TEXT         | NULL                   | Key trên S3/Cloudinary để xóa file thật khi xóa record                     |
| `alt_text`                   | VARCHAR(200) | NULL                   | **Bắt buộc về accessibility** — screen reader cần alt cho ảnh phòng |
| `is_primary`                 | BOOLEAN      | NOT NULL default false | Ảnh đại diện trong danh sách                                                |
| `sort_order`                 | SMALLINT     | NOT NULL default 0     | Dòng 110 "sắp xếp hình ảnh"                                                 |
| `created_at`                 | TIMESTAMPTZ  | NOT NULL default now() |                                                                                  |

Partial UNIQUE(`room_id`) `WHERE is_primary` — chỉ một ảnh chính mỗi phòng. Không đặt UNIQUE trên `sort_order` vì kéo-thả sắp xếp lại sẽ vi phạm tạm thời giữa các bước update.

### 4.6. `room_status_blocks`

Bảng then chốt cho BR-003/BR-004. Ghi lại khoảng phòng không bán được vì lý do vận hành.

| Cột            | Kiểu           | Ràng buộc                                                | Giải thích                                                                 |
| --------------- | --------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `id`          | BIGINT          | PK                                                         |                                                                              |
| `room_id`     | BIGINT          | NOT NULL, FK→rooms RESTRICT                               |                                                                              |
| `block_type`  | room_block_type | NOT NULL                                                   | `MAINTENANCE / RENOVATION / OUT_OF_SERVICE / INTERNAL_USE / DEEP_CLEANING` |
| `start_date`  | DATE            | NOT NULL                                                   |                                                                              |
| `end_date`    | DATE            | NOT NULL, CHECK`> start_date`                            | Nửa mở`[start, end)` theo P4                                             |
| `reason`      | TEXT            | NULL                                                       | Ghi chú cho Staff                                                           |
| `created_by`  | BIGINT          | NOT NULL, FK→users RESTRICT                               | Ai chặn phòng                                                              |
| `created_at`  | TIMESTAMPTZ     | NOT NULL default now()                                     |                                                                              |

**Không có `block_range` / `DATERANGE`.** MySQL không có kiểu `DATERANGE`. Overlap kiểm tra bằng trigger tương tự BR-002:

```sql
CREATE TRIGGER trg_block_no_overlap
BEFORE INSERT OR UPDATE OF room_id, start_date, end_date
ON room_status_blocks
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1 FROM room_status_blocks
    WHERE room_id = NEW.room_id
      AND id <> COALESCE(NEW.id, 0)
      AND start_date < NEW.end_date
      AND end_date > NEW.start_date
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Room status block overlaps with an existing block';
  END IF;
END;
```

Ràng buộc này chặn hai lệnh bảo trì trùng nhau trên cùng phòng. Kiểm tra chéo giữa block và booking được thực hiện bằng trigger đối xứng (mục 8.3).

---

## 5. Logical Design — Pricing & Policy

Toàn bộ mục này là **master/config data** (P7). Dữ liệu ở đây dùng để *tính* giá và tiền hoàn tại thời điểm giao dịch, rồi được snapshot xuống transaction. Sửa dữ liệu ở đây **không** hồi tố xuống booking đã chốt.

### 5.1. `rate_overrides`

Giá theo mùa/cuối tuần/dịp lễ. Dòng 75 yêu cầu "tính số đêm lưu trú và tổng tiền dự kiến" — với giá thay đổi theo ngày, tổng tiền không thể là `base_price × số đêm`.

| Cột                          | Kiểu         | Ràng buộc                    | Giải thích                                                                                                                      |
| ----------------------------- | ------------- | ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `id`                        | BIGINT        | PK                             |                                                                                                                                   |
| `room_type_id`              | BIGINT        | NULL, FK→room_types CASCADE   |                                                                                                                                   |
| `room_id`                   | BIGINT        | NULL, FK→rooms CASCADE        |                                                                                                                                   |
| `name`                      | VARCHAR(120)  | NOT NULL                       | "Tết 2027", "Cuối tuần hè"                                                                                                    |
| `start_date` / `end_date` | DATE          | NOT NULL, CHECK`end > start` |                                                                                                                                   |
| `price`                     | NUMERIC(14,2) | NOT NULL, CHECK`>= 0`        | Giá tuyệt đối cho một đêm                                                                                                  |
| `weekdays`                  | SMALLINT[]    | NULL                           | `{6,7}` = chỉ áp dụng T7, CN. NULL = mọi ngày                                                                              |
| `priority`                  | SMALLINT      | NOT NULL default 0             | Khi nhiều rule trùng ngày, lấy`priority` cao nhất. Bắt buộc phải có, nếu không kết quả tính giá là ngẫu nhiên |
| `is_active`                 | BOOLEAN       | NOT NULL default true          |                                                                                                                                   |

CHECK: `(room_type_id IS NOT NULL) <> (room_id IS NOT NULL)` — đúng một trong hai, tránh dòng vừa gắn loại phòng vừa gắn phòng gây nhập nhằng.

Thứ tự tính giá một đêm: `rate_override` (priority cao nhất) → `rooms.price_override` → `room_types.base_price`.

**Bản ghi này có thể bị UPDATE.** Vì vậy `booking_room_nights.rate_override_id` chỉ là **trace reference**, không phải nguồn tính giá — chi tiết ở mục 6.4.

### 5.2. `cancellation_policies`

Dòng 97: "hệ thống tính số tiền hoàn dựa trên thời điểm hủy". Policy chỉ giữ phần **không phụ thuộc mốc thời gian**; các bậc hoàn tiền nằm ở `cancellation_policy_rules`.

| Cột                            | Kiểu        | Ràng buộc                                      | Giải thích                                                                                                |
| ------------------------------- | ------------ | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| `id`                          | BIGINT       | PK                                               |                                                                                                             |
| `code`                        | VARCHAR(30)  | NOT NULL, UNIQUE                                 | `FLEXIBLE`, `MODERATE`, `NON_REFUND`                                                                  |
| `name`                        | VARCHAR(120) | NOT NULL                                         | Hiện cho khách trước khi đặt                                                                          |
| `description`                 | TEXT         | NULL                                             |                                                                                                             |
| `no_show_charge_percent`      | NUMERIC(5,2) | NOT NULL default 100, CHECK`BETWEEN 0 AND 100` | Dòng 151 có trạng thái no-show.**No-show không phải một bậc hủy** — xem giải thích dưới |
| `is_default`                  | BOOLEAN      | NOT NULL default false                           | Partial UNIQUE`WHERE is_default` — chỉ một chính sách mặc định                                    |
| `is_active`                   | BOOLEAN      | NOT NULL default true                            |                                                                                                             |
| `created_at` / `updated_at` | TIMESTAMPTZ  | NOT NULL default now()                           |                                                                                                             |

**Vì sao `no_show_charge_percent` tách khỏi các bậc hủy:** hủy là hành động khách chủ động làm ở một thời điểm cụ thể, nên tính theo "còn bao nhiêu giờ tới check-in". No-show là trường hợp khách **không hủy và không xuất hiện** — không có thời điểm hủy nào để so, nên không thể biểu diễn bằng một rule `min_hours_before`. Đây là hai nhánh nghiệp vụ khác nhau.

Nếu toàn khách sạn luôn dùng một mức no-show duy nhất, nên chuyển cột này sang bảng cấu hình chung của khách sạn thay vì lặp trên mọi policy. Giữ ở đây vì mỗi policy có thể có mức khác nhau (`NON_REFUND` thu 100%, `FLEXIBLE` có thể chỉ thu một đêm).

### 5.3. `cancellation_policy_rules`

Các bậc hoàn tiền của một policy. Bảng này thay thế cặp cột `free_cancel_hours` + `refund_percent_after` ở bản trước — cặp cột đó chỉ biểu diễn được **đúng một mốc**, không diễn đạt được chính sách thực tế nhiều bậc.

| Cột                 | Kiểu        | Ràng buộc                                          | Giải thích                                                                                                                            |
| -------------------- | ------------ | ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `id`               | BIGINT       | PK                                                   |                                                                                                                                         |
| `policy_id`        | BIGINT       | NOT NULL, FK→cancellation_policies**CASCADE** | CASCADE vì rule là phần thân của policy (config data). Booking cũ không bị ảnh hưởng vì đã có snapshot riêng — xem 5.4 |
| `min_hours_before` | INT          | NOT NULL, CHECK`>= 0`                              | Số giờ tối thiểu trước check-in để bậc này áp dụng                                                                          |
| `refund_percent`   | NUMERIC(5,2) | NOT NULL, CHECK`BETWEEN 0 AND 100`                 | Phần trăm hoàn của bậc này                                                                                                        |
| `created_at`       | TIMESTAMPTZ  | NOT NULL default now()                               |                                                                                                                                         |

- UNIQUE(`policy_id`, `min_hours_before`) — không có hai bậc cùng mốc giờ gây nhập nhằng.
- Service layer phải bảo đảm mỗi policy có ít nhất một rule `min_hours_before = 0` làm bậc mặc định, để mọi thời điểm hủy đều khớp đúng một bậc. Không cưỡng chế được bằng CHECK (ràng buộc mức tập hợp), nên kiểm khi tạo/sửa policy.

Ví dụ policy `FLEXIBLE`:

| min_hours_before | refund_percent |
| ---------------- | -------------- |
| 72               | 100            |
| 30               | 50             |
| 0                | 0              |

**Cách chọn rule:**

```
hours_before_cancel = scheduled_check_in_time - cancelled_at
rule áp dụng = rule có min_hours_before LỚN NHẤT nhưng <= hours_before_cancel
```

| hours_before_cancel | rule khớp | refund |
| ------------------- | ---------- | ------ |
| 80 giờ             | 72         | 100%   |
| 50 giờ             | 30         | 50%    |
| 10 giờ             | 0          | 0%     |

Lợi ích so với hard-code `if >= 72 ... else if >= 30 ...`: business thêm bậc mới (`168h → 100%`, `72h → 80%`, `24h → 50%`, `0h → 0%`) bằng cách thêm dòng dữ liệu, không sửa code và không deploy.

Trong đó `scheduled_check_in_time` = `MIN(booking_rooms.check_in_date)` của booking kết hợp giờ nhận phòng chuẩn của khách sạn. Query tính refund cụ thể ở mục 9.6.

### 5.4. Snapshot chính sách hủy vào từng booking room

`booking_rooms.cancellation_policy_id` chỉ là **reference tới policy gốc** (để biết từng dòng phòng dùng policy nào, phục vụ báo cáo). Số tiền hoàn **phải** tính từ `booking_rooms.cancellation_policy_snapshot JSONB` — bản chụp toàn bộ policy **kèm rules** tại thời điểm điều khoản được chốt với khách:

```json
{
  "code": "FLEXIBLE",
  "name": "Flexible Cancellation",
  "no_show_charge_percent": 100,
  "rules": [
    { "min_hours_before": 72, "refund_percent": 100 },
    { "min_hours_before": 30, "refund_percent": 50 },
    { "min_hours_before": 0,  "refund_percent": 0 }
  ]
}
```

Ba điểm cần đúng:

1. Snapshot **đúng policy của RoomType tại thời điểm tạo booking_room**, không copy toàn bộ bảng `cancellation_policies`.
2. Thời điểm snapshot: khi điều khoản thương mại được khách chấp nhận. Với luồng ở mục 7 của spec (tạo booking → thanh toán → xác nhận), điều khoản hủy được hiển thị và chấp nhận ngay ở bước tạo booking, nên snapshot **tại lúc tạo booking**. Nếu team đổi flow sang "chấp nhận điều khoản ở bước thanh toán", chuyển sang snapshot lúc `CONFIRMED` — chỉ cần thống nhất một chỗ và ghi vào code comment.
3. Một booking có thể gồm nhiều RoomType với nhiều policy khác nhau; khi hủy, refund tính theo từng `booking_rooms.room_subtotal` và snapshot của dòng phòng đó rồi cộng lại.
4. Sau khi snapshot, Admin sửa `room_types.cancellation_policy_id`, `cancellation_policies`/`cancellation_policy_rules` (kể cả xóa policy làm CASCADE mất rules) **không** ảnh hưởng booking cũ, vì phép tính refund đọc từ JSONB chứ không join sang bảng gốc. Đây là lý do không cần versioning hay SCD Type 2 cho policy (P8).

---

## 6. Logical Design — Booking & Availability

### 6.1. `booking_sources`

| Cột                   | Kiểu        | Ràng buộc                                    | Giải thích                                                                                                                                                                       |
| ---------------------- | ------------ | ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                 | SMALLINT     | PK                                             |                                                                                                                                                                                    |
| `code`               | VARCHAR(30)  | NOT NULL, UNIQUE                               | Dòng 124:`WEBSITE / WALK_IN / PHONE / BOOKING_COM / AGODA / STAFF_MANUAL`                                                                                                       |
| `name`               | VARCHAR(80)  | NOT NULL                                       |                                                                                                                                                                                    |
| `is_external`        | BOOLEAN      | NOT NULL default false                         | Phân biệt booking ngoài hệ thống (dòng 121-124)                                                                                                                              |
| `requires_account`   | BOOLEAN      | NOT NULL default false                         | `WEBSITE` = true (khách phải đăng nhập mới đặt được, dòng 206). Dùng để validate `bookings.customer_id` ở service layer                                        |
| `commission_percent` | NUMERIC(5,2) | NOT NULL default 0, CHECK`BETWEEN 0 AND 100` | **Config hiện tại**, đổi theo hợp đồng. OTA thu 15-20%; doanh thu thuần (dòng 153-156) phải trừ phần này. Booking snapshot giá trị này lúc chốt — xem 6.2 |
| `is_active`          | BOOLEAN      | NOT NULL default true                          |                                                                                                                                                                                    |

### 6.2. `bookings`

Giữ thông tin **đơn đặt** và **người liên hệ**. Không giữ khoảng ngày lưu trú (QĐ-6).

| Cột                                   | Kiểu                  | Ràng buộc                                    | Giải thích                                                                                                                                                                                                                                        |
| -------------------------------------- | ---------------------- | ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                                 | BIGINT                 | PK                                             | Dùng nội bộ DB/FK                                                                                                                                                                                                                                |
| `public_id`                          | CHAR(36)               | NOT NULL, UNIQUE, default`UUID()` | Dùng cho URL/API (P1). Không dùng PK tuần tự làm public identifier. MySQL `UUID()` trả chuỗi 36 ký tự có dấu `-`; nếu cần chuỗi ngắn hơn dùng application-side UUID v4                                                                                              |
| `booking_code`                       | VARCHAR(20)            | NOT NULL, UNIQUE                               | `BK-2026-000123`. Khách đọc mã này qua điện thoại/email; không đọc UUID                                                                                                                                                                |
| `customer_id`                        | BIGINT                 | **NULL**, FK→customer_profiles RESTRICT | NULL hợp lệ và cần thiết: dòng 122 booking walk-in/OTA không có tài khoản. Validate theo`booking_sources.requires_account` ở service layer                                                                                             |
| `source_id`                          | SMALLINT               | NOT NULL, FK→booking_sources RESTRICT         | Dòng 124, 152                                                                                                                                                                                                                                      |
| `source_commission_percent_snapshot` | NUMERIC(5,2)           | NULL, CHECK`BETWEEN 0 AND 100`               | **Snapshot** hoa hồng OTA lúc booking được xác nhận. Đổi hợp đồng Agoda từ 18% sang 20% không được làm đổi doanh thu thuần của booking cũ. NULL với nguồn không có hoa hồng                                       |
| `external_reference`                 | VARCHAR(100)           | NULL                                           | Mã đơn bên Booking.com/Agoda để đối soát                                                                                                                                                                                                   |
| `status`                             | booking_status         | NOT NULL default`PENDING`                    | `PENDING / CONFIRMED / CHECKED_IN / CHECKED_OUT / CANCELLED / NO_SHOW / EXPIRED` (dòng 76, 151, mục 7)                                                                                                                                          |
| `contact_name`                       | VARCHAR(150)           | NOT NULL                                       | **Người đặt / đầu mối liên hệ**, không nhất thiết là người lưu trú. Snapshot: khách đổi tên trong profile không làm đổi booking cũ                                                                                  |
| `contact_email`                      | CITEXT                 | NULL                                           | Nullable: booking walk-in thường không có email. Ràng buộc "phải có ít nhất một cách liên lạc" kiểm ở service theo`booking_sources`                                                                                               |
| `contact_phone`                      | VARCHAR(20)            | NULL                                           | Nullable vì lý do trên                                                                                                                                                                                                                           |
| `adults`                             | SMALLINT               | NOT NULL default 1, CHECK`>= 1`              | Số khách theo yêu cầu đặt (dòng 198), dùng để khớp`max_occupancy`                                                                                                                                                                      |
| `children`                           | SMALLINT               | NOT NULL default 0, CHECK`>= 0`              |                                                                                                                                                                                                                                                     |
| `rooms_total`                        | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`              | Aggregate =`SUM(booking_rooms.room_subtotal)`. Tiền phòng trước thuế                                                                                                                                                                         |
| `services_total`                     | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`              | Aggregate =`SUM(folio_charges.line_subtotal)` của các khoản chưa void. Tiền dịch vụ trước thuế                                                                                                                                          |
| `discount_total`                     | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`              | Giảm giá mức booking                                                                                                                                                                                                                             |
| `tax_total`                          | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`              | VAT + phí dịch vụ                                                                                                                                                                                                                                |
| `total_amount`                       | NUMERIC(14,2)          | NOT NULL, CHECK`>= 0`                        | Số phải trả cuối cùng                                                                                                                                                                                                                          |
| `paid_amount`                        | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`              | **Tổng payment SUCCEEDED đã nhận. Không bị giảm khi refund** — xem 7.6                                                                                                                                                                |
| `refunded_amount`                    | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`              | Tổng refund COMPLETED.`net_received = paid_amount - refunded_amount`                                                                                                                                                                             |
| `room_tax_percent_snapshot`          | NUMERIC(5,2)           | NOT NULL default 0, CHECK`BETWEEN 0 AND 100` | Thuế suất áp cho tiền phòng, chốt lúc tạo booking. Cần để`tax_total` và dòng ROOM trên hóa đơn tính lại được từ snapshot mà không đọc config hiện tại. **Cần business xác nhận thuế suất** — xem mục 14 |
| `currency`                           | CHAR(3)                | NOT NULL default`VND`                        |                                                                                                                                                                                                                                                     |
| `payment_status`                     | booking_payment_status | NOT NULL default`UNPAID`                     | `UNPAID / PARTIALLY_PAID / PAID / PARTIALLY_REFUNDED / REFUNDED`                                                                                                                                                                                  |
| `hold_expires_at`                    | TIMESTAMPTZ            | NULL                                           | QĐ-3: mốc hết hạn giữ phòng khi`status='PENDING'`                                                                                                                                                                                           |
| `special_requests`                   | TEXT                   | NULL                                           |                                                                                                                                                                                                                                                     |
| `internal_notes`                     | TEXT                   | NULL                                           | Chỉ Staff thấy                                                                                                                                                                                                                                    |
| `confirmed_at`                       | TIMESTAMPTZ            | NULL                                           |                                                                                                                                                                                                                                                     |
| `checked_in_at`                      | TIMESTAMPTZ            | NULL                                           | Dòng 129: thời gian check-in**thực tế**                                                                                                                                                                                                   |
| `checked_in_by`                      | BIGINT                 | NULL, FK→staff_profiles RESTRICT              | Dòng 129: nhân viên thực hiện                                                                                                                                                                                                                  |
| `checked_out_at`                     | TIMESTAMPTZ            | NULL                                           |                                                                                                                                                                                                                                                     |
| `checked_out_by`                     | BIGINT                 | NULL, FK→staff_profiles RESTRICT              |                                                                                                                                                                                                                                                     |
| `cancelled_at`                       | TIMESTAMPTZ            | NULL                                           | Mốc thời gian để chọn rule hoàn tiền (5.3)                                                                                                                                                                                                   |
| `cancelled_by`                       | BIGINT                 | NULL, FK→users RESTRICT                       | Phân biệt khách tự hủy hay Staff hủy                                                                                                                                                                                                          |
| `cancellation_reason`                | TEXT                   | NULL                                           |                                                                                                                                                                                                                                                     |
| `created_by`                         | BIGINT                 | NULL, FK→users RESTRICT                       | NULL khi khách tự đặt online                                                                                                                                                                                                                    |
| `created_at` / `updated_at`        | TIMESTAMPTZ            | NOT NULL default now()                         |                                                                                                                                                                                                                                                     |

**Đổi tên so với bản trước** (để mỗi tên chỉ mang một nghĩa):

| Cũ                                                     | Mới                                                     | Lý do                                                                                                                                                                                                                        |
| ------------------------------------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `room_subtotal`                                       | `rooms_total`                                          | Phân biệt với`booking_rooms.room_subtotal`. `BookingRoom.room_subtotal` = tổng của **một dòng phòng**; `Booking.rooms_total` = SUM của **tất cả** dòng phòng                                   |
| `service_total`                                       | `services_total`                                       | Đối xứng với`rooms_total`                                                                                                                                                                                               |
| `guest_full_name` / `guest_email` / `guest_phone` | `contact_name` / `contact_email` / `contact_phone` | Tránh nhầm với`booking_guests`. Contact = người đặt/đầu mối; BookingGuest = người thực tế lưu trú. Người đặt có thể không tới khách sạn (đặt cho người khác, công ty đặt cho nhân viên) |

Ràng buộc mức bảng:

```sql
CHECK (total_amount = rooms_total + services_total + tax_total - discount_total)
CHECK (paid_amount     <= total_amount + 0.01)   -- chặn thu quá
CHECK (refunded_amount <= paid_amount)           -- không hoàn quá số đã thu
CHECK (status <> 'CHECKED_IN'  OR checked_in_at  IS NOT NULL)   -- BR-010
CHECK (status <> 'CHECKED_OUT' OR checked_out_at IS NOT NULL)   -- BR-011
CHECK (status <> 'CANCELLED'   OR cancelled_at   IS NOT NULL)
CHECK (status <> 'PENDING'     OR hold_expires_at IS NOT NULL)  -- QĐ-3

-- Chống import trùng một đơn OTA nhiều lần
CREATE UNIQUE INDEX bookings_external_ref_uniq
  ON bookings (source_id, external_reference)
  WHERE external_reference IS NOT NULL;
```

BR-001 **không** còn ở bảng này (không còn cột ngày) — đã chuyển hoàn toàn về `booking_rooms`.

Index: `(public_id)` unique, `(booking_code)` unique, `(customer_id, status)` cho trang My Bookings, `(status, created_at DESC)`, `(source_id)`, và partial `(hold_expires_at)` `WHERE status='PENDING'` cho job dọn hold. Không còn index trên `check_in_date` — truy vấn "cần check-in hôm nay" chuyển sang `booking_rooms` (mục 9.5).

### 6.3. `booking_rooms` — bảng quan trọng nhất của hệ thống

Nơi BR-001, BR-002, BR-003 và BR-009 được thực thi. Đây cũng là nơi duy nhất giữ khoảng ngày lưu trú (QĐ-6).

| Cột                            | Kiểu               | Ràng buộc                                                         | Giải thích                                                                                                                                                                               |
| ------------------------------- | ------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `id`                          | BIGINT              | PK                                                                  |                                                                                                                                                                                            |
| `booking_id`                  | BIGINT              | NOT NULL, FK→bookings**RESTRICT**                            | RESTRICT, không CASCADE — xem mục 6.7                                                                                                                                                   |
| `room_id`                     | BIGINT              | NOT NULL, FK→rooms**RESTRICT**                               | BR-008: không xóa phòng đang có lịch sử booking                                                                                                                                     |
| `room_type_id`                | BIGINT              | NOT NULL, FK→room_types RESTRICT                                   | **Chỉ là reference**, dùng để join lấy thông tin loại phòng hiện tại và cho báo cáo                                                                                    |
| `room_type_code_snapshot`     | VARCHAR(30)         | NOT NULL                                                            | Snapshot code loại phòng lúc đặt                                                                                                                                                      |
| `room_type_name_snapshot`     | VARCHAR(120)        | NOT NULL                                                            | Snapshot tên loại phòng lúc đặt. Cần vì Staff có thể đổi tên "Deluxe" thành "Deluxe Garden"; hóa đơn và lịch sử của khách cũ phải giữ tên tại thời điểm bán |
| `cancellation_policy_id`      | BIGINT              | NULL, FK→cancellation_policies RESTRICT                             | Reference policy gốc của dòng phòng, không dùng để tính refund                                                                                                                   |
| `cancellation_policy_snapshot`| JSONB               | NULL                                                                | **Nguồn tính refund** cho dòng phòng này (mục 5.4)                                                                                                                              |
| `check_in_date`               | DATE                | NOT NULL                                                            |                                                                                                                                                                                            |
| `check_out_date`              | DATE                | NOT NULL,**CHECK `> check_in_date`**                        | **BR-001** cưỡng chế tại DB                                                                                                                                                      |
| `nights`                      | INT                 | GENERATED ALWAYS AS (`DATEDIFF(check_out_date, check_in_date)`) STORED | Số đêm lưu trú. MySQL 8 hỗ trợ generated columns                                                                                                                                                              |

**Không có `stay_range` / `DATERANGE`.** MySQL không có kiểu `DATERANGE`. Khoảng ngày lưu trú half-open `[check_in, check_out)` được biểu diễn bằng hai cột DATE đã có. Overlap kiểm tra bằng trigger (xem bên dưới).
| `room_subtotal`               | NUMERIC(14,2)       | NOT NULL default 0, CHECK`>= 0`                                   | **Aggregate** = `SUM(booking_room_nights.price)` của dòng này. Cập nhật trong cùng transaction với nights (P9)                                                              |
| `status`                      | booking_room_status | NOT NULL default`RESERVED`                                        | 5 giá trị, xem bảng dưới                                                                                                                                                              |
| `guest_count`                 | SMALLINT            | NOT NULL default 1, CHECK`>= 1`                                   |                                                                                                                                                                                            |
| `moved_from_booking_room_id`  | BIGINT              | NULL, FK→booking_rooms RESTRICT                                    | Dòng này nhận khách từ dòng nào khi đổi phòng giữa kỳ. Cho phép truy vết chuỗi chuyển phòng                                                                               |
| `assigned_at`                 | TIMESTAMPTZ         | NULL                                                                | Dòng 128: gán phòng khi check-in                                                                                                                                                        |
| `assigned_by`                 | BIGINT              | NULL, FK→staff_profiles RESTRICT                                   |                                                                                                                                                                                            |
| `created_at` / `updated_at` | TIMESTAMPTZ         | NOT NULL default now()                                              |                                                                                                                                                                                            |

**Đã bỏ `room_rate_snapshot`.** Một kỳ lưu trú có thể có giá khác nhau từng đêm (15/08 = 1.000.000, 16/08 = 1.200.000, 17/08 = 1.500.000) nên không tồn tại một con số duy nhất đại diện chính xác cho cả dòng phòng. Giá thực bán từng đêm nằm ở `booking_room_nights.price` (source of truth); `room_subtotal` là aggregate của chúng.

**Đã bỏ `is_active`.** Trigger BR-002 dùng trực tiếp `status IN ('RESERVED','OCCUPIED')`, nên một cột derived song song chỉ tạo nguy cơ lệch dữ liệu. Không vừa có `is_active` vừa dùng `status` ở trigger.

#### Ý nghĩa `booking_room_status`

| Giá trị     | Ý nghĩa                                                                         | Chiếm phòng? |
| ------------- | --------------------------------------------------------------------------------- | -------------- |
| `RESERVED`  | Đã giữ phòng, khách chưa ở                                                 | active         |
| `OCCUPIED`  | Khách đang ở                                                                   | active         |
| `COMPLETED` | Đã ở và checkout bình thường                                               | inactive       |
| `RELEASED`  | Reservation được giải phóng trước khi hoàn tất lưu trú (cancel/expire) | inactive       |
| `MOVED_OUT` | Khách đã rời phòng này vì được chuyển sang phòng khác                | inactive       |

`COMPLETED` là giá trị mới. Bản trước để dòng đã checkout ở `OCCUPIED`, khiến nó vẫn nằm trong tập active và tiếp tục chặn phòng vô ích. Tách `COMPLETED` (kết thúc bình thường) khỏi `RELEASED` (kết thúc bất thường) cũng cần cho báo cáo: đêm đã bán thực tế chỉ tính dòng `COMPLETED`/`OCCUPIED`.

**Ràng buộc chống trùng phòng (BR-002) — MySQL:**

MySQL không có `EXCLUDE USING gist`. BR-002 được thực thi bằng `BEFORE INSERT/UPDATE` trigger:

```sql
CREATE TRIGGER trg_booking_room_no_overlap
BEFORE INSERT OR UPDATE OF room_id, check_in_date, check_out_date, status
ON booking_rooms
FOR EACH ROW
BEGIN
  IF NEW.status IN ('RESERVED', 'OCCUPIED') THEN
    IF EXISTS (
      SELECT 1 FROM booking_rooms
      WHERE room_id = NEW.room_id
        AND id <> COALESCE(NEW.id, 0)
        AND status IN ('RESERVED', 'OCCUPIED')
        AND check_in_date < NEW.check_out_date
        AND check_out_date > NEW.check_in_date
    ) THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Room is already booked for overlapping dates (BR-002)';
    END IF;
  END IF;
END;
```

Cách đọc: không được tồn tại hai dòng **cùng `room_id`** mà khoảng ngày giao nhau, xét trong các dòng còn hiệu lực (`status IN ('RESERVED','OCCUPIED')`). Ba điểm cần lưu ý:

1. `status IN (...)` là bắt buộc. Không có nó, booking đã hủy vẫn chặn khách mới đặt lại phòng đó — phòng bị "chết" vĩnh viễn sau một lần hủy.
2. Điều kiện `check_in_date < NEW.check_out_date AND check_out_date > NEW.check_in_date` xử lý đúng trường hợp back-to-back: `[15/08, 17/08)` và `[17/08, 19/08)` **không** giao nhau, nên khách mới nhận phòng đúng ngày khách cũ trả phòng (P4).
3. Trigger chạy ở tầng DB nên chặn được cả hai request đồng thời (QĐ-2), cả booking do Staff nhập tay (BR-009), và cả `INSERT` chạy tay lúc xử lý sự cố.
4. Với nhiều instance backend, nên dùng `SELECT ... FOR UPDATE` trên hàng phòng trước khi INSERT trong cùng transaction để tránh race giữa trigger kiểm tra và commit — trigger không thay thế được pessimistic locking ở tầng transaction khi tải đồng thời cực cao.

```sql
-- Cho composite FK từ booking_guests (mục 6.5)
ALTER TABLE booking_rooms
  ADD CONSTRAINT booking_rooms_id_booking_uniq UNIQUE (id, booking_id);
```

**Index:** GiST index không tồn tại trên MySQL. Chỉ giữ các B-tree thực sự dùng:

```sql
CREATE INDEX booking_rooms_booking     ON booking_rooms (booking_id);
CREATE INDEX booking_rooms_room_status ON booking_rooms (room_id, status);
CREATE INDEX booking_rooms_dates       ON booking_rooms (room_id, check_in_date, check_out_date, status);
CREATE INDEX booking_rooms_arrivals    ON booking_rooms (check_in_date, status);
```

`booking_rooms_arrivals` phục vụ danh sách khách đến hôm nay (dòng 247) — truy vấn này trước đây chạy trên `bookings.check_in_date`, giờ chuyển về đây.

#### Đổi phòng giữa kỳ lưu trú

Khách đặt Room 101 từ 15/08 → 19/08. Ngày 17/08 chuyển sang Room 305. Kết quả **hai dòng**, không sửa `room_id` của dòng cũ:

| # | room     | khoảng        | status        | moved_from |
| - | -------- | -------------- | ------------- | ---------- |
| 1 | Room 101 | 15/08 → 17/08 | `MOVED_OUT` | NULL       |
| 2 | Room 305 | 17/08 → 19/08 | `OCCUPIED`  | 1          |

Quy tắc:

- **Không UPDATE `room_id` từ 101 thành 305** — làm vậy mất lịch sử ai đã ở phòng nào, và phá dữ liệu vận hành (housekeeping, khiếu nại, khai báo lưu trú).
- Dòng 1 thu hẹp `check_out_date` về 17/08 và chuyển `MOVED_OUT`; các `booking_room_nights` từ 17/08 trở đi được **chuyển sang** dòng 2 (đổi `booking_room_id`), không tạo lại giá mới.
- **Không tự động re-price** khi đổi phòng do lỗi khách sạn. Giá đã cam kết với khách phải giữ nguyên — đây là lý do chuyển night rows sang thay vì tính lại theo giá Room 305. Nếu khách chủ động yêu cầu nâng cấp có phụ phí, phần chênh ghi thành `folio_charges` để thấy rõ trên hóa đơn.
- Sau khi chuyển, `room_subtotal` của cả hai dòng được tính lại từ night rows thuộc về mình; `bookings.rooms_total` không đổi.

### 6.4. `booking_room_nights`

**Source of truth của giá phòng đã bán theo từng đêm.** Một dòng cho mỗi đêm của mỗi phòng.

| Cột                 | Kiểu         | Ràng buộc                          | Giải thích                                                                                                                 |
| -------------------- | ------------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| `id`               | BIGINT        | PK                                   |                                                                                                                              |
| `booking_room_id`  | BIGINT        | NOT NULL, FK→booking_rooms RESTRICT | RESTRICT — xem 6.7                                                                                                          |
| `stay_date`        | DATE          | NOT NULL                             | Đêm ngủ (đêm 15/08 = từ 15/08 sang 16/08)                                                                              |
| `price`            | NUMERIC(14,2) | NOT NULL, CHECK`>= 0`              | **Giá thực bán của đêm đó** sau khi áp `base_price` / `price_override` / `rate_override`. Trước thuế |
| `rate_override_id` | BIGINT        | NULL, FK→rate_overrides RESTRICT    | **Chỉ là trace field** — xem giải thích dưới                                                                    |

- UNIQUE(`booking_room_id`, `stay_date`) — không tính tiền hai lần cho một đêm.
- Index `(stay_date)` cho báo cáo doanh thu theo ngày.

**Tính bất biến:** sau khi booking được chốt, thay đổi `room_types.base_price`, `rooms.price_override` hay `rate_overrides` **không** được thay đổi `price` ở đây. Không có job nào, không có trigger nào tính lại cột này.

**Vì sao bảng này tồn tại:** booking 28/08–02/09 tổng 5.000.000₫. Câu hỏi "doanh thu tháng 8 là bao nhiêu" chỉ trả lời được nếu biết đêm nào thuộc tháng 8. Chia tổng cho số đêm là sai khi giá cuối tuần khác ngày thường. Đây cũng là chuẩn kế toán khách sạn — ADR và RevPAR (mục 9.4) đều tính theo đêm.

#### Ràng buộc toàn vẹn với dòng phòng cha

Phải bảo đảm mọi đêm nằm trong khoảng lưu trú của `booking_rooms`:

```
booking_rooms.check_in_date <= booking_room_nights.stay_date < booking_rooms.check_out_date
```

Không cho phép dữ liệu kiểu `BookingRoom: 15/08 → 18/08` mà có `BookingRoomNight: 20/08`. `CHECK` không tham chiếu được bảng cha, nên dùng trigger trong cùng transaction:

- **BEFORE INSERT/UPDATE trên `booking_room_nights`**: đọc `booking_rooms` (`FOR SHARE`) và từ chối nếu `stay_date` nằm ngoài `[check_in_date, check_out_date)`.
- **BEFORE UPDATE trên `booking_rooms`** khi `check_in_date`/`check_out_date` đổi: từ chối nếu còn night row nằm ngoài khoảng mới. Buộc thao tác đổi phòng/đổi ngày phải xử lý night rows trước.
- **Khi booking chuyển sang `CONFIRMED`**: kiểm `COUNT(night rows) = booking_rooms.nights` cho từng dòng phòng, và `SUM(price) = room_subtotal`. Đặt ở trigger trên `bookings` hoặc ở service trong cùng transaction — không để booking đã xác nhận mà thiếu đêm hoặc lệch tổng.

#### `rate_override_id` chỉ là trace field

Cột này ghi lại "giá đêm này sinh ra từ rule nào", phục vụ giải thích khi khách thắc mắc. **Không được dùng `rate_overrides` hiện tại để tính lại giá cũ** vì bản ghi rate override có thể bị UPDATE hoặc xóa sau đó.

Nếu về sau team cần tái hiện chính xác rule đã áp dụng (không chỉ id), có hai lựa chọn, chọn theo nhu cầu thực tế:

- Chỉ giữ `price` và bỏ `rate_override_id` — đủ cho mọi tính toán tiền, mất khả năng truy vết nguồn giá.
- Snapshot phần rule quan trọng vào một cột JSONB nhỏ (`{name, price, priority}`) nếu việc giải thích giá cho khách là yêu cầu thường xuyên.

Không cần version hóa toàn bộ bảng `rate_overrides` (P8) — giá đã bán nằm an toàn ở `price`.

### 6.5. `booking_guests`

**Người thực tế lưu trú**, không nhất thiết là User/Customer và không nhất thiết là người đặt (`bookings.contact_*`). Phục vụ dòng 103-106 (Staff xem ai đang ở phòng nào) và khai báo lưu trú.

| Cột                             | Kiểu        | Ràng buộc                     | Giải thích                                                                                                                   |
| -------------------------------- | ------------ | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `id`                           | BIGINT       | PK                              |                                                                                                                                |
| `booking_id`                   | BIGINT       | NOT NULL, FK→bookings RESTRICT |                                                                                                                                |
| `booking_room_id`              | BIGINT       | NULL, FK composite (xem dưới) | NULL khi khách đã được khai báo lúc đặt nhưng chưa gán phòng cụ thể (phòng chỉ gán khi check-in, dòng 128) |
| `full_name`                    | VARCHAR(150) | NOT NULL                        |                                                                                                                                |
| `nationality`                  | CHAR(2)      | NULL                            | ISO 3166-1. Cần cho khai báo lưu trú với khách nước ngoài và cho thống kê khách theo quốc tịch                  |
| `id_document_type`             | id_doc_type  | NULL                            | `NATIONAL_ID / PASSPORT / DRIVER_LICENSE`                                                                                    |
| `id_document_number_encrypted` | BYTEA        | NULL                            | Ciphertext AES-256-GCM, key ở KMS. Chỉ giải mã khi user có quyền`guest:read_id`                                        |
| `id_document_lookup_hash`      | BYTEA        | NULL                            | HMAC-SHA256 với key riêng (pepper) để tìm chính xác theo số giấy tờ mà không cần giải mã                        |
| `date_of_birth`                | DATE         | NULL                            |                                                                                                                                |
| `created_at`                   | TIMESTAMPTZ  | NOT NULL default now()          |                                                                                                                                |

**Giấy tờ tùy thân — vì sao hai cột:** số CCCD/passport có không gian giá trị hẹp và định dạng đoán được, nên SHA-256 thuần bị vét cạn trong vài giây (khác token random ở mục 3.6). Do đó:

- `id_document_number_encrypted` giữ giá trị thật để hiển thị khi có quyền — **không lưu plaintext**.
- `id_document_lookup_hash` dùng HMAC có key bí mật, cho phép Staff tìm chính xác `WHERE id_document_lookup_hash = hmac($1)` mà kẻ tấn công lấy được DB không dò ngược ra được.
- CHECK: `(id_document_number_encrypted IS NULL) = (id_document_lookup_hash IS NULL)` — hai cột luôn đi cùng nhau.
- Index `(id_document_lookup_hash)` cho tra cứu; **không** index cột encrypted.

**Toàn vẹn `booking_id` ↔ `booking_room_id`:** phải chặn dữ liệu kiểu `booking_id = 10` nhưng `booking_room_id` lại thuộc booking 20. Giữ cả hai cột (vì khách có thể tồn tại trước khi gán phòng) và dùng **composite FK**:

```sql
FOREIGN KEY (booking_room_id, booking_id)
  REFERENCES booking_rooms (id, booking_id) ON DELETE RESTRICT
```

FK này hợp lệ nhờ `UNIQUE (id, booking_id)` đã tạo ở mục 6.3. Khi `booking_room_id IS NULL`, composite FK không kiểm (chuẩn SQL: FK có cột NULL được bỏ qua với `MATCH SIMPLE`) — đúng ý muốn cho khách chưa gán phòng.

**Đã bỏ `is_primary`** và partial UNIQUE tương ứng. Spec không có chức năng nào dùng khái niệm "khách chính của phòng"; người đứng tên/đầu mối liên hệ của đơn đã là `bookings.contact_name`. Nếu sau này có yêu cầu "khách đại diện từng phòng", thêm lại `is_primary` + partial UNIQUE(`booking_room_id`) `WHERE is_primary`.

Index: `(booking_id)`, `(booking_room_id)`, `(id_document_lookup_hash)`.

### 6.6. `booking_status_history`

**Business timeline** của booking (`PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT`). Khác `audit_logs`: bảng này ghi *chuyển trạng thái nghiệp vụ* để trả lời khách hàng và vận hành; `audit_logs` ghi *mọi thao tác sửa dữ liệu* để điều tra kỹ thuật.

| Cột            | Kiểu                | Ràng buộc                              | Giải thích                                                                                                                                       |
| --------------- | -------------------- | ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`          | BIGINT               | PK                                       |                                                                                                                                                    |
| `booking_id`  | BIGINT               | NOT NULL, FK→bookings**RESTRICT** | RESTRICT để không có đường nào xóa mất timeline (6.7)                                                                                    |
| `from_status` | booking_status       | NULL                                     | NULL ở lần tạo đầu                                                                                                                            |
| `to_status`   | booking_status       | NOT NULL                                 |                                                                                                                                                    |
| `actor_type`  | actor_type           | NOT NULL                                 | `USER / SYSTEM`. Tách khỏi `changed_by` để `changed_by IS NULL` không còn mang hai nghĩa "hệ thống làm" và "không biết ai làm" |
| `changed_by`  | BIGINT               | NULL, FK→users RESTRICT                 |                                                                                                                                                    |
| `source`      | status_change_source | NOT NULL                                 | `MANUAL / PAYMENT_CALLBACK / HOLD_EXPIRY_JOB / NO_SHOW_JOB / OTA_IMPORT / SYSTEM_OTHER` — biết chính xác cơ chế nào đổi trạng thái    |
| `reason`      | TEXT                 | NULL                                     |                                                                                                                                                    |
| `metadata`    | JSONB                | NULL                                     | Số tiền hoàn, mã giao dịch liên quan                                                                                                         |
| `created_at`  | TIMESTAMPTZ          | NOT NULL default now()                   |                                                                                                                                                    |

```sql
CHECK ((actor_type = 'USER'   AND changed_by IS NOT NULL)
    OR (actor_type = 'SYSTEM' AND changed_by IS NULL))
```

**Append-only.** Chặn bằng trigger, không chỉ bằng quy ước:

```sql
DELIMITER $$

CREATE TRIGGER bsh_no_update BEFORE UPDATE ON booking_status_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_status_history is append-only';
END$$

CREATE TRIGGER bsh_no_delete BEFORE DELETE ON booking_status_history
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'booking_status_history is append-only';
END$$

DELIMITER ;
```

Index: `(booking_id, created_at)`.

### 6.7. Không hard delete booking đã phát sinh nghiệp vụ

Booking ở trạng thái `CONFIRMED / CHECKED_IN / CHECKED_OUT / CANCELLED / NO_SHOW` **không được hard delete**, và các dữ liệu con phải được giữ: `booking_rooms`, `booking_room_nights`, `booking_guests`, `booking_status_history`, `invoices`, `payments`.

Vì vậy **mọi FK từ bảng con của booking dùng `ON DELETE RESTRICT`, không dùng CASCADE.** Bản trước dùng CASCADE cho `booking_rooms`/`booking_guests`/`booking_status_history` — một lệnh `DELETE FROM bookings WHERE id = ...` chạy tay sẽ xóa sạch cả timeline và lịch sử giá, vi phạm BR-008/BR-013. RESTRICT làm lệnh đó thất bại, đúng như mong muốn.

Trường hợp duy nhất được xóa vật lý: booking **chưa từng chốt** (`PENDING` hoặc `EXPIRED`, chưa có payment thành công, chưa có invoice) — ví dụ dọn rác các hold bị bỏ sau nhiều tháng. Việc này làm qua một stored procedure ghi rõ giới hạn, xóa con trước cha trong một transaction:

```sql
CREATE PROCEDURE purge_abandoned_booking(IN p_booking_id BIGINT)
BEGIN
  DECLARE v_status VARCHAR(20);
  DECLARE v_has_payment INT DEFAULT 0;
  DECLARE v_has_invoice INT DEFAULT 0;

  -- chỉ cho phép với booking chưa từng phát sinh nghiệp vụ
  SELECT status INTO v_status FROM bookings WHERE id = p_booking_id;
  IF v_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Booking not found';
  END IF;
  IF v_status NOT IN ('PENDING','EXPIRED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Booking has business activity, cannot delete';
  END IF;

  SELECT COUNT(*) INTO v_has_payment FROM payments
   WHERE booking_id = p_booking_id AND status = 'SUCCEEDED';
  SELECT COUNT(*) INTO v_has_invoice FROM invoices WHERE booking_id = p_booking_id;
  IF v_has_payment > 0 OR v_has_invoice > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Booking has payment or invoice, cannot delete';
  END IF;

  -- Xóa con trước cha trong transaction
  DELETE FROM booking_room_nights
   WHERE booking_room_id IN (SELECT id FROM booking_rooms WHERE booking_id = p_booking_id);
  DELETE FROM booking_guests         WHERE booking_id = p_booking_id;
  DELETE FROM booking_rooms          WHERE booking_id = p_booking_id;
  DELETE FROM booking_status_history WHERE booking_id = p_booking_id;
  DELETE FROM bookings               WHERE id = p_booking_id;
END;
```

Lưu ý `booking_status_history` có trigger chặn DELETE, nên procedure này cần chạy với quyền cho phép tạm vô hiệu trigger (`session_replication_role` hoặc một cờ trong trigger function). Nếu team muốn tuyệt đối không xóa gì, bỏ hai dòng cuối và chỉ giữ booking ở `EXPIRED` — tốn dung lượng nhưng đơn giản hơn.

---

## 7. Logical Design — Stay, Billing, Payment, Feedback

### 7.1. `service_items`

**Master/config data**: `unit_price` là giá **hiện tại** của dịch vụ.

| Cột                            | Kiểu            | Ràng buộc                                                                |
| ------------------------------- | ---------------- | -------------------------------------------------------------------------- |
| `id`                          | BIGINT           | PK                                                                         |
| `code`                        | VARCHAR(40)      | NOT NULL, UNIQUE                                                           |
| `name`                        | VARCHAR(120)     | NOT NULL                                                                   |
| `category`                    | service_category | NOT NULL —`FNB / LAUNDRY / SPA / TRANSPORT / MINIBAR / PENALTY / OTHER` |
| `unit_price`                  | NUMERIC(14,2)    | NOT NULL, CHECK`>= 0` — giá hiện tại                                 |
| `tax_percent`                 | NUMERIC(5,2)     | NOT NULL default 0, CHECK`BETWEEN 0 AND 100`                             |
| `is_active`                   | BOOLEAN          | NOT NULL default true                                                      |
| `created_at` / `updated_at` | TIMESTAMPTZ      | NOT NULL default now()                                                     |

Đổi giá ở đây chỉ ảnh hưởng các khoản phát sinh **từ lúc đổi trở đi**:

```
16/08  Laundry = 100k  →  folio_charges.unit_price = 100k
17/08  Admin đổi Laundry = 150k
       →  khoản ghi ngày 16/08 vẫn là 100k
       →  khoản ghi ngày 17/08 trở đi là 150k
```

Không cần SCD Type 2 cho `service_items` chỉ để giữ giá cũ (P8) — snapshot ở `folio_charges` đã đủ.

### 7.2. `folio_charges`

Các khoản phát sinh ghi vào tài khoản của booking (dòng 132: "các khoản phát sinh nếu có"). **Snapshot giá dịch vụ tại thời điểm phát sinh.**

| Cột                | Kiểu         | Ràng buộc                                    | Giải thích                                                                                                           |
| ------------------- | ------------- | ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `id`              | BIGINT        | PK                                             |                                                                                                                        |
| `booking_id`      | BIGINT        | NOT NULL, FK→bookings RESTRICT                |                                                                                                                        |
| `service_item_id` | BIGINT        | NULL, FK→service_items RESTRICT               | NULL cho khoản nhập tay. Chỉ là trace reference                                                                    |
| `description`     | VARCHAR(200)  | NOT NULL                                       | **Snapshot tên dịch vụ** — luôn có, kể cả khi có `service_item_id`, vì tên dịch vụ có thể đổi |
| `quantity`        | NUMERIC(10,2) | NOT NULL default 1, CHECK`> 0`               |                                                                                                                        |
| `unit_price`      | NUMERIC(14,2) | NOT NULL, CHECK`>= 0`                        | **Snapshot giá** lúc phát sinh                                                                                |
| `line_subtotal`   | NUMERIC(14,2) | NOT NULL, CHECK`>= 0`                        | `= quantity × unit_price`                                                                                           |
| `discount_amount` | NUMERIC(14,2) | NOT NULL default 0, CHECK`>= 0`              |                                                                                                                        |
| `tax_percent`     | NUMERIC(5,2)  | NOT NULL default 0, CHECK`BETWEEN 0 AND 100` | Snapshot thuế suất                                                                                                   |
| `tax_amount`      | NUMERIC(14,2) | NOT NULL default 0, CHECK`>= 0`              | Snapshot số thuế của khoản này                                                                                    |
| `line_total`      | NUMERIC(14,2) | NOT NULL, CHECK`>= 0`                        | `= line_subtotal - discount_amount + tax_amount`                                                                     |
| `charged_at`      | TIMESTAMPTZ   | NOT NULL default now()                         |                                                                                                                        |
| `charged_by`      | BIGINT        | NULL, FK→staff_profiles RESTRICT              | Ai ghi khoản này                                                                                                     |
| `is_voided`       | BOOLEAN       | NOT NULL default false                         | **Không xóa** khoản ghi sai, chỉ void                                                                        |
| `voided_at`       | TIMESTAMPTZ   | NULL                                           |                                                                                                                        |
| `voided_by`       | BIGINT        | NULL, FK→staff_profiles RESTRICT              |                                                                                                                        |
| `void_reason`     | TEXT          | NULL                                           |                                                                                                                        |

**Vì sao tách nhỏ các cột tiền:** bản trước có một cột `amount` mang nghĩa "quantity × unit_price + thuế" nhưng không thấy phần thuế cụ thể — không đối chiếu được với hóa đơn, không tách được thuế cho báo cáo, và không kiểm tra được bằng CHECK. Giờ mỗi thành phần hiện rõ và ràng buộc được:

```sql
CHECK (line_subtotal = ROUND(quantity * unit_price, 2))
CHECK (line_total    = line_subtotal - discount_amount + tax_amount)
CHECK (discount_amount <= line_subtotal)
-- void phải đủ bộ ba thông tin
CHECK (is_voided = false
       OR (voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL))
```

`discount_amount` được giữ ở đây (mặc định 0) để cấu trúc dòng tiền của `folio_charges` khớp 1–1 với `invoice_items`, giúp bước tạo hóa đơn là phép copy trực tiếp. Nếu project không cần giảm giá theo từng dịch vụ thì để nguyên 0 và chỉ xử lý giảm giá ở mức Booking/Invoice.

Index: `(booking_id, is_voided)`, `(charged_at)`.

### 7.3. Vòng đời hóa đơn

Tách **trạng thái chứng từ** khỏi **trạng thái thanh toán**. Bản trước trộn hai state machine vào một enum (`DRAFT/ISSUED/PAID/PARTIALLY_PAID/VOID`), khiến không biểu diễn được "hóa đơn đã phát hành và đã thu một phần" hay "đã phát hành, đã hoàn tiền".

```
invoice_status          (chứng từ):  DRAFT → ISSUED → VOID
invoice_payment_status  (tiền):      UNPAID → PARTIALLY_PAID → PAID
                                            → PARTIALLY_REFUNDED → REFUNDED
```

Hai trạng thái này biến đổi độc lập: một hóa đơn `ISSUED` có thể lần lượt `UNPAID → PARTIALLY_PAID → PAID → PARTIALLY_REFUNDED` mà `invoice_status` không đổi.

**Các bước theo luồng checkout (dòng 130-138):**

| Bước                           | Việc xảy ra                                                                                                                                                                   |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Booking được chốt            | `booking_room_nights` được tạo, snapshot giá phòng từng đêm                                                                                                          |
| Khách dùng dịch vụ           | `folio_charges` được tạo, snapshot giá dịch vụ                                                                                                                         |
| Staff lập hóa đơn / checkout | Tạo`invoices` ở `DRAFT` + `invoice_items` copy từ `booking_room_nights` và `folio_charges`. `invoice_number = NULL`                                             |
| Staff chỉnh charge (nếu cần)  | Trong`DRAFT` được phép xóa và regenerate toàn bộ `invoice_items`                                                                                                    |
| Staff nhấn Issue                | Cấp`invoice_number`, đặt `issued_at = now()`, `invoice_status = ISSUED`. Từ đây header + items + buyer info + thuế + giảm giá + tổng tiền **bất biến** |
| Hóa đơn sai sau khi ISSUED    | `VOID` + phát hành hóa đơn thay thế (`replaces_invoice_id`). **Không UPDATE âm thầm** hóa đơn cũ                                                         |

### 7.4. `invoices`

| Cột                              | Kiểu                  | Ràng buộc                              | Giải thích                                                                                                                                                                                                                                                  |
| --------------------------------- | ---------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                            | BIGINT                 | PK                                       |                                                                                                                                                                                                                                                               |
| `public_id`                     | UUID                   | NOT NULL, UNIQUE                         | Dùng cho URL tải hóa đơn (P1)                                                                                                                                                                                                                            |
| `invoice_number`                | VARCHAR(30)            | **NULL**, UNIQUE khi không NULL   | NULL khi`DRAFT`; được cấp tại thời điểm Issue. Đây là số chứng từ do hệ thống cấp khi phát hành — spec không nêu yêu cầu pháp lý cụ thể nào về tính liên tục, nên không khẳng định điều đó ở đây (xem mục 14) |
| `booking_id`                    | BIGINT                 | NOT NULL, FK→bookings**RESTRICT** | BR-013                                                                                                                                                                                                                                                        |
| `status`                        | invoice_status         | NOT NULL default`DRAFT`                | `DRAFT / ISSUED / VOID`                                                                                                                                                                                                                                     |
| `payment_status`                | invoice_payment_status | NOT NULL default`UNPAID`               | `UNPAID / PARTIALLY_PAID / PAID / PARTIALLY_REFUNDED / REFUNDED`                                                                                                                                                                                            |
| `issued_at`                     | TIMESTAMPTZ            | NULL                                     |                                                                                                                                                                                                                                                               |
| `issued_by`                     | BIGINT                 | NULL, FK→staff_profiles RESTRICT        |                                                                                                                                                                                                                                                               |
| `buyer_name`                    | VARCHAR(150)           | NOT NULL                                 | Snapshot — sửa profile khách không đổi hóa đơn đã in                                                                                                                                                                                               |
| `buyer_address`                 | TEXT                   | NULL                                     |                                                                                                                                                                                                                                                               |
| `buyer_tax_code`                | VARCHAR(20)            | NULL                                     | Khách công ty cần hóa đơn VAT                                                                                                                                                                                                                           |
| `buyer_email`                   | CITEXT                 | NULL                                     |                                                                                                                                                                                                                                                               |
| `subtotal`                      | NUMERIC(14,2)          | NOT NULL, CHECK`>= 0`                  | `= SUM(invoice_items.line_subtotal)`                                                                                                                                                                                                                        |
| `discount_total`                | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`        | `= SUM(invoice_items.discount_amount)`                                                                                                                                                                                                                      |
| `tax_total`                     | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`        | `= SUM(invoice_items.tax_amount)`                                                                                                                                                                                                                           |
| `total_amount`                  | NUMERIC(14,2)          | NOT NULL                                 | `= subtotal - discount_total + tax_total`                                                                                                                                                                                                                   |
| `paid_amount`                   | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`        | Tổng payment SUCCEEDED gắn hóa đơn này.**Không giảm khi refund**                                                                                                                                                                                |
| `refunded_amount`               | NUMERIC(14,2)          | NOT NULL default 0, CHECK`>= 0`        | Tổng refund COMPLETED                                                                                                                                                                                                                                        |
| `currency`                      | CHAR(3)                | NOT NULL default`VND`                  |                                                                                                                                                                                                                                                               |
| `pdf_url` / `pdf_storage_key` | TEXT                   | NULL                                     | Chỉ render bản chính thức khi`ISSUED`. Lưu file đã render                                                                                                                                                                                            |
| `replaces_invoice_id`           | BIGINT                 | NULL, FK→invoices RESTRICT              | Hóa đơn này thay thế hóa đơn nào (sau VOID)                                                                                                                                                                                                          |
| `voided_at`                     | TIMESTAMPTZ            | NULL                                     |                                                                                                                                                                                                                                                               |
| `voided_by`                     | BIGINT                 | NULL, FK→staff_profiles RESTRICT        |                                                                                                                                                                                                                                                               |
| `void_reason`                   | TEXT                   | NULL                                     |                                                                                                                                                                                                                                                               |
| `created_at` / `updated_at`   | TIMESTAMPTZ            | NOT NULL default now()                   |                                                                                                                                                                                                                                                               |

```sql
CHECK (total_amount = subtotal - discount_total + tax_total)
CHECK (refunded_amount <= paid_amount)
CHECK (status <> 'ISSUED' OR (invoice_number IS NOT NULL AND issued_at IS NOT NULL
                              AND issued_by IS NOT NULL))
CHECK (status <> 'DRAFT'  OR (invoice_number IS NULL AND issued_at IS NULL))
CHECK (status <> 'VOID'   OR (voided_at IS NOT NULL AND void_reason IS NOT NULL))
```

**Trigger bảo vệ tính bất biến (BR-013):** sau khi `status = 'ISSUED'`, chặn UPDATE lên `invoice_number`, `booking_id`, `buyer_*`, `subtotal`, `discount_total`, `tax_total`, `total_amount`, `issued_at`. Chỉ cho phép đổi `payment_status`, `paid_amount`, `refunded_amount`, `pdf_*`, và bộ `void*` khi chuyển sang VOID. Chặn DELETE ở mọi trạng thái khác `DRAFT`.

Index: `(booking_id)`, `(status, issued_at)`, `(payment_status)`, unique `(invoice_number)` khi không NULL.

### 7.5. `invoice_items`

Snapshot cuối cùng của **các dòng tiền thực sự xuất hiện trên hóa đơn**. Không phải view, không đọc ngược giá hiện tại từ `room_types` / `service_items` / `rate_overrides` để render hóa đơn cũ.

| Cột                | Kiểu             | Ràng buộc                                    | Giải thích                                                                                                                                                                          |
| ------------------- | ----------------- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`              | BIGINT            | PK                                             |                                                                                                                                                                                       |
| `invoice_id`      | BIGINT            | NOT NULL, FK→invoices CASCADE                 | CASCADE chỉ để phục vụ regenerate khi hóa đơn còn`DRAFT`; trigger chặn xóa khi đã `ISSUED`                                                                           |
| `line_type`       | invoice_line_type | NOT NULL                                       | `ROOM / SERVICE / ADJUSTMENT`                                                                                                                                                       |
| `description`     | VARCHAR(200)      | NOT NULL                                       | **Snapshot**: `"Deluxe Sea View — 15/08/2026"`, `"Laundry Service"`, `"Minibar — Coca Cola"`. Đổi tên RoomType/ServiceItem sau này không làm đổi hóa đơn cũ |
| `quantity`        | NUMERIC(10,2)     | NOT NULL default 1, CHECK`<> 0`              |                                                                                                                                                                                       |
| `unit_price`      | NUMERIC(14,2)     | NOT NULL                                       | Snapshot                                                                                                                                                                              |
| `line_subtotal`   | NUMERIC(14,2)     | NOT NULL                                       | `= quantity × unit_price`                                                                                                                                                          |
| `discount_amount` | NUMERIC(14,2)     | NOT NULL default 0, CHECK`>= 0`              | Snapshot                                                                                                                                                                              |
| `tax_percent`     | NUMERIC(5,2)      | NOT NULL default 0, CHECK`BETWEEN 0 AND 100` | Snapshot                                                                                                                                                                              |
| `tax_amount`      | NUMERIC(14,2)     | NOT NULL default 0                             | Snapshot                                                                                                                                                                              |
| `line_total`      | NUMERIC(14,2)     | NOT NULL                                       | `= line_subtotal - discount_amount + tax_amount`                                                                                                                                    |
| `reference_type`  | VARCHAR(40)       | NULL                                           | `BOOKING_ROOM_NIGHT / FOLIO_CHARGE`                                                                                                                                                 |
| `reference_id`    | BIGINT            | NULL                                           |                                                                                                                                                                                       |
| `sort_order`      | SMALLINT          | NOT NULL default 0                             |                                                                                                                                                                                       |

```sql
CHECK (line_subtotal = ROUND(quantity * unit_price, 2))
CHECK (line_total    = line_subtotal - discount_amount + tax_amount)
-- chỉ ADJUSTMENT được mang giá trị âm
CHECK (line_type = 'ADJUSTMENT' OR (line_subtotal >= 0 AND line_total >= 0))
```

**`line_type` chỉ còn ba giá trị.** Bản trước có thêm `TAX` và `DISCOUNT` thành dòng riêng, trong khi thuế và giảm giá đã là thuộc tính tiền của từng dòng và lại được tổng hợp ở header — rất dễ đếm hai lần. Nay:

- `ROOM` — copy từ `booking_room_nights`, một dòng mỗi đêm (hoặc gộp các đêm cùng giá tùy cách trình bày). `tax_percent` lấy từ `bookings.room_tax_percent_snapshot`.
- `SERVICE` — copy từ `folio_charges` chưa void, một dòng mỗi khoản.
- `ADJUSTMENT` — điều chỉnh do Staff nhập tại thời điểm lập hóa đơn (làm tròn, bù trừ thiện chí, phụ phí thỏa thuận). **Semantics rõ ràng: giá trị dương làm tăng tổng, giá trị âm làm giảm tổng**, giống mọi dòng khác trong công thức. Đây là `line_type` duy nhất được phép âm. Mọi ADJUSTMENT bắt buộc có `description` giải thích lý do.

**Công thức nhất quán hai cấp:**

```
line_subtotal  = quantity × unit_price
line_total     = line_subtotal - discount_amount + tax_amount

Invoice.subtotal       = SUM(invoice_items.line_subtotal)
Invoice.discount_total = SUM(invoice_items.discount_amount)
Invoice.tax_total      = SUM(invoice_items.tax_amount)
Invoice.total_amount   = subtotal - discount_total + tax_total
                       = SUM(invoice_items.line_total)
```

Thuế và giảm giá **không** được cộng thêm lần nữa qua `line_type` — hai cách tính `total_amount` ở trên phải cho cùng kết quả, dùng làm phép kiểm tra khi phát hành.

**`reference_type` / `reference_id` là polymorphic trace reference**, chỉ dùng để truy vết dòng này sinh ra từ đâu. Không đặt FK vì trỏ tới nhiều bảng khác nhau — và cố ý không đặt, vì bản chất là "nguồn tham chiếu lịch sử", không phải quan hệ cần toàn vẹn. **Không dùng nguồn đó để tính lại hóa đơn sau khi `ISSUED`.**

**Trigger:** chặn INSERT/UPDATE/DELETE trên `invoice_items` khi hóa đơn cha có `status <> 'DRAFT'`.

### 7.6. `payments`

Dòng 79-83, BR-012. **Payment + Refund là ledger — source of truth của dòng tiền.** Các cột `paid_amount`/`refunded_amount` ở `bookings` và `invoices` là aggregate cache, cập nhật trong cùng transaction với ledger (P9).

| Cột                                   | Kiểu          | Ràng buộc                                     | Giải thích                                                                                                 |
| -------------------------------------- | -------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `id`                                 | BIGINT         | PK                                              |                                                                                                              |
| `payment_code`                       | VARCHAR(30)    | NOT NULL, UNIQUE                                | Mã nội bộ gửi sang gateway làm`orderId`                                                               |
| `booking_id`                         | BIGINT         | NOT NULL, FK→bookings RESTRICT                 | Dòng 82: thanh toán phải liên kết booking                                                               |
| `invoice_id`                         | BIGINT         | NULL, FK→invoices RESTRICT                     | Có khi thu tiền lúc checkout                                                                              |
| `method`                             | payment_method | NOT NULL                                        | `INTERNET_BANKING / CARD / CASH / BANK_TRANSFER / E_WALLET`                                                |
| `provider`                           | VARCHAR(40)    | NULL                                            | `VNPAY / MOMO / STRIPE`. NULL khi thu tiền mặt                                                           |
| `amount`                             | NUMERIC(14,2)  | NOT NULL, CHECK`> 0`                          |                                                                                                              |
| `currency`                           | CHAR(3)        | NOT NULL default`VND`                         |                                                                                                              |
| `status`                             | payment_status | NOT NULL default`PENDING`                     | Dòng 81:`PENDING / PROCESSING / SUCCEEDED / FAILED / CANCELLED / EXPIRED / REFUNDED / PARTIALLY_REFUNDED` |
| `provider_txn_id`                    | VARCHAR(120)   | NULL, UNIQUE khi không NULL                    | Mã giao dịch từ gateway. UNIQUE để callback gửi lại nhiều lần không cộng tiền hai lần           |
| `provider_bank_code`                 | VARCHAR(40)    | NULL                                            |                                                                                                              |
| `idempotency_key`                    | VARCHAR(80)    | NULL, UNIQUE                                    | Khách bấm "Thanh toán" hai lần không tạo hai giao dịch                                                |
| `paid_at`                            | TIMESTAMPTZ    | NULL                                            |                                                                                                              |
| `verified_at`                        | TIMESTAMPTZ    | NULL                                            | **BR-012**: chỉ khi cột này có giá trị mới được cập nhật booking sang CONFIRMED            |
| `failure_code` / `failure_message` |                | NULL                                            | Hiện lý do cho khách (dòng 224)                                                                          |
| `refunded_amount`                    | NUMERIC(14,2)  | NOT NULL default 0, CHECK`>= 0 AND <= amount` | Tổng refund COMPLETED của payment này                                                                     |
| `expires_at`                         | TIMESTAMPTZ    | NULL                                            | Link thanh toán hết hạn                                                                                   |
| `created_by`                         | BIGINT         | NULL, FK→users RESTRICT                        | Staff thu tiền mặt                                                                                         |
| `created_at` / `updated_at`        | TIMESTAMPTZ    | NOT NULL default now()                          |                                                                                                              |

```sql
-- BR-012 cưỡng chế tại DB: không cho đánh dấu thành công mà chưa xác minh
CHECK (status <> 'SUCCEEDED' OR (paid_at IS NOT NULL AND verified_at IS NOT NULL))
```

**Ngữ nghĩa `paid_amount` không giảm khi refund.** Ở cả `bookings` và `invoices`:

```
paid_amount     = 5.000.000     -- tổng đã thực nhận
refunded_amount = 2.000.000     -- tổng đã hoàn
net_received    = 3.000.000     -- suy ra, không lưu
```

Nếu trừ trực tiếp vào `paid_amount` thì mất thông tin "đã từng thu bao nhiêu", không đối soát được với sao kê ngân hàng và không báo cáo được doanh thu gộp/hoàn. `net_received` là giá trị dẫn xuất, tính khi cần chứ không lưu thành cột.

Index: `(booking_id, status)`, `(status, created_at)`, unique `(provider_txn_id)`, unique `(idempotency_key)`.

### 7.7. `payment_events`

Log thô mọi lần gateway gọi về. Chỉ ghi thêm.

| Cột                  | Kiểu        | Ràng buộc                 | Giải thích                                                                                                       |
| --------------------- | ------------ | --------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `id`                | BIGINT       | PK                          |                                                                                                                    |
| `payment_id`        | BIGINT       | NULL, FK→payments RESTRICT | NULL khi callback không khớp payment nào (vẫn phải lưu để điều tra)                                      |
| `event_type`        | VARCHAR(60)  | NOT NULL                    | `IPN_RECEIVED`, `RETURN_URL`, `QUERY_RESULT`                                                                 |
| `provider`          | VARCHAR(40)  | NOT NULL                    |                                                                                                                    |
| `provider_event_id` | VARCHAR(120) | NULL                        | UNIQUE(`provider`,`provider_event_id`) — **chống replay**: gateway gửi lại IPN cùng id thì bỏ qua |
| `signature_valid`   | BOOLEAN      | NULL                        | Kết quả kiểm chữ ký.`false` = nghi vấn giả mạo, cần cảnh báo                                          |
| `http_status`       | SMALLINT     | NULL                        |                                                                                                                    |
| `raw_payload`       | JSONB        | NOT NULL                    | Nguyên văn từ gateway — chứng cứ đối soát                                                                 |
| `received_ip`       | INET         | NULL                        | Kiểm tra IP có thuộc dải của gateway                                                                          |
| `processed_at`      | TIMESTAMPTZ  | NULL                        | NULL = chưa xử lý, dùng để retry                                                                             |
| `created_at`        | TIMESTAMPTZ  | NOT NULL default now()      |                                                                                                                    |

### 7.8. `refunds`

| Cột                            | Kiểu         | Ràng buộc                     | Giải thích                                                                                                                     |
| ------------------------------- | ------------- | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `id`                          | BIGINT        | PK                              |                                                                                                                                  |
| `payment_id`                  | BIGINT        | NOT NULL, FK→payments RESTRICT |                                                                                                                                  |
| `booking_id`                  | BIGINT        | NOT NULL, FK→bookings RESTRICT |                                                                                                                                  |
| `amount`                      | NUMERIC(14,2) | NOT NULL, CHECK`> 0`          | Tính từ `booking_rooms.cancellation_policy_snapshot` (mục 9.6), không từ policy hiện tại                                |
| `reason`                      | refund_reason | NOT NULL                        | `CUSTOMER_CANCEL / HOTEL_CANCEL / OVERCHARGE / NO_SHOW_ADJUST / OTHER`                                                         |
| `status`                      | refund_status | NOT NULL default`PENDING`     | `PENDING / PROCESSING / COMPLETED / FAILED / REJECTED`                                                                         |
| `policy_applied`              | JSONB         | NULL                            | Snapshot phép tính: bậc rule nào khớp,`hours_before_cancel`, phần trăm áp dụng. Cần khi khách tranh luận số tiền |
| `provider_refund_id`          | VARCHAR(120)  | NULL, UNIQUE khi không NULL    |                                                                                                                                  |
| `requested_by`                | BIGINT        | NOT NULL, FK→users RESTRICT    |                                                                                                                                  |
| `approved_by`                 | BIGINT        | NULL, FK→users RESTRICT        | Hoàn tiền nên có người phê duyệt                                                                                         |
| `processed_at`                | TIMESTAMPTZ   | NULL                            |                                                                                                                                  |
| `created_at` / `updated_at` | TIMESTAMPTZ   | NOT NULL default now()          |                                                                                                                                  |

Trigger: `SUM(refunds.amount WHERE status='COMPLETED') <= payments.amount` cho mỗi payment; đồng thời cập nhật `payments.refunded_amount`, `bookings.refunded_amount`, `invoices.refunded_amount` trong cùng transaction.

### 7.9. `reviews`

Dòng 98-101, BR-006, BR-007.

| Cột                                      | Kiểu         | Ràng buộc                                      | Giải thích                                                                          |
| ----------------------------------------- | ------------- | ------------------------------------------------ | ------------------------------------------------------------------------------------- |
| `id`                                    | BIGINT        | PK                                               |                                                                                       |
| `booking_id`                            | BIGINT        | NOT NULL,**UNIQUE**, FK→bookings RESTRICT | **BR-007** — một booking tối đa một review                                 |
| `customer_id`                           | BIGINT        | NOT NULL, FK→customer_profiles RESTRICT         |                                                                                       |
| `room_id`                               | BIGINT        | NULL, FK→rooms RESTRICT                         | Hiện review trên trang chi tiết phòng (dòng 62)                                  |
| `room_type_id`                          | BIGINT        | NULL, FK→room_types RESTRICT                    | Gộp review theo loại phòng                                                         |
| `overall_rating`                        | SMALLINT      | NOT NULL, CHECK`BETWEEN 1 AND 5`               |                                                                                       |
| `room_rating`                           | SMALLINT      | NULL, CHECK`BETWEEN 1 AND 5`                   | Dòng 100: đánh giá phòng                                                         |
| `cleanliness_rating`                    | SMALLINT      | NULL, CHECK`BETWEEN 1 AND 5`                   | Dòng 100: chất lượng                                                              |
| `service_rating`                        | SMALLINT      | NULL, CHECK`BETWEEN 1 AND 5`                   | Dòng 100: nhân viên/dịch vụ                                                      |
| `value_rating`                          | SMALLINT      | NULL, CHECK`BETWEEN 1 AND 5`                   |                                                                                       |
| `title`                                 | VARCHAR(200)  | NULL                                             |                                                                                       |
| `comment`                               | TEXT          | NULL                                             | Dòng 100: bình luận                                                                |
| `status`                                | review_status | NOT NULL default`PUBLISHED`                    | `PENDING / PUBLISHED / HIDDEN / REJECTED` — cần kiểm duyệt nội dung xúc phạm |
| `staff_reply`                           | TEXT          | NULL                                             | Khách sạn phản hồi                                                                |
| `staff_reply_by` / `staff_replied_at` |               | NULL                                             |                                                                                       |
| `created_at` / `updated_at`           | TIMESTAMPTZ   | NOT NULL default now()                           |                                                                                       |

Trigger cưỡng chế **BR-006**: chỉ cho INSERT khi booking tương ứng có `status='CHECKED_OUT'` và `customer_id` khớp chủ booking. Không thể diễn đạt bằng CHECK vì phải đọc bảng khác.

### 7.10. `email_messages`

Dòng 174-177 (Admin soạn), 178-193 (System gửi).

| Cột                           | Kiểu        | Ràng buộc                 | Giải thích                                                                                             |
| ------------------------------ | ------------ | --------------------------- | -------------------------------------------------------------------------------------------------------- |
| `id`                         | BIGINT       | PK                          |                                                                                                          |
| `template_code`              | VARCHAR(60)  | NULL                        | `ACTIVATION / PASSWORD_RESET / BOOKING_CONFIRMED / BOOKING_CANCELLED / PAYMENT_SUCCESS / ADMIN_CUSTOM` |
| `to_email`                   | CITEXT       | NOT NULL                    | Lưu địa chỉ thật, không chỉ`user_id` — user đổi email sau này thì lịch sử vẫn đúng    |
| `to_user_id`                 | BIGINT       | NULL, FK→users SET NULL    |                                                                                                          |
| `cc` / `bcc`               | TEXT         | NULL                        |                                                                                                          |
| `subject`                    | VARCHAR(300) | NOT NULL                    | Dòng 175                                                                                                |
| `body_html` / `body_text`  | TEXT         | NULL                        |                                                                                                          |
| `status`                     | email_status | NOT NULL default`QUEUED`  | `QUEUED / SENDING / SENT / FAILED / BOUNCED` (dòng 188)                                               |
| `provider`                   | VARCHAR(40)  | NULL                        | SES/SendGrid                                                                                             |
| `provider_message_id`        | VARCHAR(150) | NULL                        | Tra cứu khi khách báo không nhận được                                                            |
| `attempt_count`              | SMALLINT     | NOT NULL default 0          | Retry có kiểm soát                                                                                    |
| `last_error`                 | TEXT         | NULL                        |                                                                                                          |
| `scheduled_at` / `sent_at` | TIMESTAMPTZ  | NULL                        |                                                                                                          |
| `related_booking_id`         | BIGINT       | NULL, FK→bookings SET NULL | Nối email với booking (dòng 190-192)                                                                  |
| `created_by`                 | BIGINT       | NULL, FK→users RESTRICT    | Admin nào soạn; NULL = hệ thống tự gửi                                                             |
| `created_at`                 | TIMESTAMPTZ  | NOT NULL default now()      |                                                                                                          |

Index: `(status, scheduled_at)` cho worker lấy việc; `(to_email)`, `(related_booking_id)`.

### 7.11. `audit_logs`

| Cột                             | Kiểu       | Ràng buộc              | Giải thích                                                                 |
| -------------------------------- | ----------- | ------------------------ | ---------------------------------------------------------------------------- |
| `id`                           | BIGINT      | PK                       |                                                                              |
| `actor_user_id`                | BIGINT      | NULL, FK→users RESTRICT | NULL = tiến trình hệ thống                                               |
| `action`                       | VARCHAR(60) | NOT NULL                 | `BOOKING_CANCEL`, `PRICE_UPDATE`, `STAFF_DEACTIVATE`, `INVOICE_VOID` |
| `entity_type`                  | VARCHAR(60) | NOT NULL                 |                                                                              |
| `entity_id`                    | BIGINT      | NULL                     | Không đặt FK vì trỏ tới nhiều bảng khác nhau                        |
| `before_data` / `after_data` | JSONB       | NULL                     | So sánh trước/sau                                                         |
| `ip_address`                   | INET        | NULL                     |                                                                              |
| `user_agent`                   | TEXT        | NULL                     |                                                                              |
| `created_at`                   | TIMESTAMPTZ | NOT NULL default now()   |                                                                              |

Index `(entity_type, entity_id, created_at DESC)`, `(actor_user_id, created_at DESC)`. Cân nhắc partition theo tháng khi vượt vài chục triệu dòng.

---

## 8. Ánh xạ Business Rules sang cơ chế database

| BR     | Yêu cầu                                                        | Cơ chế thực thi                                                                                                                                  | Ở đâu                                                                        |
| ------ | ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| BR-001 | Check-out > check-in                                             | `CHECK (check_out_date > check_in_date)`                                                                                                          | `booking_rooms` (chỉ ở đây — `bookings` không còn cột ngày, QĐ-6) |
| BR-002 | Không hai booking hiệu lực overlap trên một phòng          | Trigger BEFORE INSERT/UPDATE (MySQL): `SELECT ... IF EXISTS` kiểm tra `check_in_date < NEW.check_out_date AND check_out_date > NEW.check_in_date` với `room_id = NEW.room_id AND status IN ('RESERVED','OCCUPIED')` | `booking_rooms`                                                               |
| BR-003 | Availability = trạng thái vận hành + booking + khoảng ngày | Không có cột`is_available`; tính bằng query mục 9.1                                                                                         | Query                                                                           |
| BR-004 | Phòng bảo trì không được booking                          | Trigger trên `booking_rooms` chặn INSERT/UPDATE nếu phòng có `room_status_blocks` overlap (8.3); trigger trên `room_status_blocks` chặn nếu phòng đã có booking hiệu lực                   | Query + trigger 8.3                                                             |
| BR-005 | Khách chỉ hủy booking của mình, ở trạng thái cho phép   | Trigger kiểm`bookings.customer_id = actor` và `status IN ('PENDING','CONFIRMED')`; kiểm quyền ở service                                    | Trigger + service                                                               |
| BR-006 | Chỉ review sau check-out                                        | Trigger BEFORE INSERT kiểm`bookings.status='CHECKED_OUT'`                                                                                        | `reviews`                                                                     |
| BR-007 | Một booking tối đa một review                                | `UNIQUE (booking_id)`                                                                                                                             | `reviews`                                                                     |
| BR-008 | Không hard delete Room/Staff nếu mất liên kết               | `deleted_at` + FK `ON DELETE RESTRICT` từ `booking_rooms`, `invoices`, `folio_charges`; mọi bảng con của booking dùng RESTRICT (6.7) | Toàn schema                                                                    |
| BR-009 | Booking ngoài hệ thống phải chặn ngày                      | Booking ngoài dùng chung`bookings`/`booking_rooms` nên tự động chịu trigger BR-002                                                  | `booking_sources` + `booking_rooms`                                         |
| BR-010 | Chỉ booking hợp lệ mới check-in                              | `CHECK (status<>'CHECKED_IN' OR checked_in_at IS NOT NULL)` + trigger state machine chỉ cho `CONFIRMED → CHECKED_IN`                          | `bookings`                                                                    |
| BR-011 | Chỉ CHECKED_IN mới checkout                                    | Trigger state machine chỉ cho`CHECKED_IN → CHECKED_OUT`                                                                                         | `bookings`                                                                    |
| BR-012 | Payment phải xác minh từ gateway                              | `CHECK (status<>'SUCCEEDED' OR verified_at IS NOT NULL)` + `payment_events.signature_valid` + UNIQUE `provider_txn_id`                        | `payments`, `payment_events`                                                |
| BR-013 | Invoice liên kết booking, giữ lịch sử                       | FK RESTRICT + trigger chặn UPDATE các cột chứng từ sau`ISSUED` + snapshot `buyer_*`/`invoice_items` + VOID thay vì sửa                 | `invoices`, `invoice_items`                                                 |
| BR-014 | Chỉ Admin được quản lý Staff và ca trực                  | RBAC: permission`staff:manage` và`shift:manage` chỉ gán cho role ADMIN. Trigger không Staff có 2 ca overlap cùng lúc                     | `role_permissions`, `shift_assignments`                                     |
| BR-015 | Một Staff không được trùng ca trong cùng khoảng giờ     | Trigger BEFORE INSERT/UPDATE: kiểm tra overlap bằng điều kiện `NOT (existing.shift_end <= NEW.shift_start OR existing.shift_start >= NEW.shift_end)` với `staff_id = NEW.staff_id AND status IN ('SCHEDULED','COMPLETED')` | `shift_assignments`                                                           |

### 8.1. State machine của booking

Trigger kiểm tra mọi lần đổi `bookings.status`, chỉ cho phép các bước sau:

```
PENDING  ──> CONFIRMED   (payment verified: BR-012)
PENDING  ──> CANCELLED   (khách bỏ / Staff hủy)
PENDING  ──> EXPIRED     (job, khi hold_expires_at < now)
CONFIRMED──> CHECKED_IN  (BR-010)
CONFIRMED──> CANCELLED   (BR-005, tính refund từ snapshot)
CONFIRMED──> NO_SHOW     (job cuối ngày, khách không đến)
CHECKED_IN ─> CHECKED_OUT(BR-011)
```

Mọi bước khác bị từ chối. Đặt ở DB vì dòng 96 nói rõ booking đã check-in/check-out không được hủy theo luồng thường — nếu chỉ chặn ở app, một endpoint mới viết sau sẽ vô tình phá quy tắc. Mỗi lần chuyển thành công, trigger ghi một dòng `booking_status_history` với `actor_type` và `source` tương ứng.

MySQL trigger (ví dụ cho `trg_booking_state_machine`):

```sql
DELIMITER $$

CREATE TRIGGER trg_booking_state_machine
BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
  DECLARE allowed INT DEFAULT 0;

  IF OLD.status = 'PENDING' AND NEW.status IN ('CONFIRMED','CANCELLED','EXPIRED') THEN
    SET allowed = 1;
  ELSEIF OLD.status = 'CONFIRMED' AND NEW.status IN ('CHECKED_IN','CANCELLED','NO_SHOW') THEN
    SET allowed = 1;
  ELSEIF OLD.status = 'CHECKED_IN' AND NEW.status = 'CHECKED_OUT' THEN
    SET allowed = 1;
  END IF;

  IF allowed = 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Invalid booking status transition';
  END IF;

  -- Ghi lịch sử chuyển trạng thái
  INSERT INTO booking_status_history (booking_id, from_status, to_status, actor_type, changed_by, source)
  VALUES (NEW.id, OLD.status, NEW.status, 'USER', NEW.updated_by, 'TRIGGER');
END$$

DELIMITER ;
```

### 8.2. Đồng bộ `booking_room_status` với `booking_status`

Trạng thái dòng phòng phải theo trạng thái đơn, do trigger đảm nhiệm:

| Booking chuyển sang                      | `booking_rooms` đang `RESERVED`/`OCCUPIED` chuyển thành |
| ----------------------------------------- | ---------------------------------------------------------------- |
| `CHECKED_IN`                            | `OCCUPIED`                                                     |
| `CHECKED_OUT`                           | `COMPLETED`                                                    |
| `CANCELLED` / `EXPIRED` / `NO_SHOW` | `RELEASED`                                                     |

Chuyển `MOVED_OUT` là thao tác riêng của luồng đổi phòng (mục 6.3), không do trigger này sinh ra.

### 8.3. Trigger kiểm tra chéo block ↔ booking (BR-004)

MySQL không có `EXCLUDE` cross-table. Cần trigger kiểm tra chéo:

- BEFORE INSERT/UPDATE trên `booking_rooms`: từ chối nếu tồn tại `room_status_blocks` cùng `room_id` có overlap (`start_date < NEW.check_out_date AND end_date > NEW.check_in_date`), hoặc nếu `rooms.operational_status <> 'ACTIVE'`.
- BEFORE INSERT/UPDATE trên `room_status_blocks`: từ chối nếu đã có `booking_rooms` hiệu lực overlap (Staff không được đặt bảo trì lên phòng đã bán) — hoặc cho phép nhưng cảnh báo và buộc chuyển phòng cho khách theo luồng ở 6.3.

Cả hai trigger nên dùng `SELECT ... FROM rooms WHERE id = NEW.room_id FOR UPDATE` để khóa hàng phòng trước khi kiểm tra overlap, tránh race giữa hai bảng. Thứ tự khóa phải nhất quán (ví dụ: luôn khóa `rooms` trước) để tránh deadlock.

### 8.4. Danh sách trigger cần viết (MySQL)

| Trigger                        | Bảng                                          | Việc                                                                                             |
| ------------------------------ | ---------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `trg_booking_state_machine`  | `bookings`                                   | `CREATE TRIGGER ... BEFORE UPDATE ON bookings FOR EACH ROW BEGIN ... SIGNAL ... END` — chặn chuyển trạng thái sai (8.1), ghi`booking_status_history` |
| `trg_booking_sync_rooms`     | `bookings`                                   | `CREATE TRIGGER ... AFTER UPDATE ON bookings FOR EACH ROW BEGIN UPDATE booking_rooms SET ... END` — đồng bộ`booking_room_status` (8.2) |
| `trg_booking_room_vs_block`  | `booking_rooms`                              | `CREATE TRIGGER ... BEFORE INSERT/UPDATE ON booking_rooms FOR EACH ROW BEGIN SELECT ... SIGNAL ... END` — BR-004 (8.3) |
| `trg_block_vs_booking_room`  | `room_status_blocks`                         | `CREATE TRIGGER ... BEFORE INSERT/UPDATE ON room_status_blocks FOR EACH ROW BEGIN SELECT ... SIGNAL ... END` — BR-004 (8.3) |
| `trg_night_within_stay`      | `booking_room_nights`                        | `CREATE TRIGGER ... BEFORE INSERT ON booking_room_nights FOR EACH ROW BEGIN ... SIGNAL ... END` — đêm phải nằm trong khoảng lưu trú (6.4) |
| `trg_stay_range_vs_nights`   | `booking_rooms`                              | `CREATE TRIGGER ... AFTER UPDATE ON booking_rooms FOR EACH ROW BEGIN ... END` — đổi ngày không được để night row lạc ngoài khoảng (6.4) |
| `trg_booking_totals`         | `booking_room_nights`, `folio_charges`     | `CREATE TRIGGER ... AFTER INSERT/UPDATE/DELETE ON booking_room_nights/folio_charges FOR EACH ROW BEGIN UPDATE bookings SET ... END` — cập nhật totals |
| `trg_confirm_completeness`   | `bookings`                                   | `CREATE TRIGGER ... BEFORE UPDATE ON bookings FOR EACH ROW BEGIN ... SIGNAL ... END` — khi`→ CONFIRMED`: đủ night rows, tổng khớp, mọi `booking_rooms` có policy snapshot |
| `trg_payment_ledger`         | `payments`, `refunds`                      | `CREATE TRIGGER ... AFTER INSERT/UPDATE ON payments/refunds FOR EACH ROW BEGIN UPDATE bookings SET ... END` — cập nhật paid/refunded/payment_status |
| `trg_invoice_immutable`      | `invoices`                                   | `CREATE TRIGGER ... BEFORE UPDATE/DELETE ON invoices FOR EACH ROW BEGIN SIGNAL ... END` — chặn sửa sau`ISSUED`, chặn delete (7.4) |
| `trg_invoice_item_immutable` | `invoice_items`                              | `CREATE TRIGGER ... BEFORE INSERT/UPDATE/DELETE ON invoice_items FOR EACH ROW BEGIN ... END` — chặn khi hóa đơn cha`<> DRAFT` (7.5) |
| `trg_review_after_checkout`  | `reviews`                                    | `CREATE TRIGGER ... BEFORE INSERT ON reviews FOR EACH ROW BEGIN SELECT ... SIGNAL ... END` — BR-006 |
| `trg_append_only`            | `booking_status_history`, `payment_events` | `CREATE TRIGGER ... BEFORE UPDATE/DELETE ON ... FOR EACH ROW BEGIN SIGNAL ... END` — chặn UPDATE/DELETE |

---

## 9. Truy vấn chính

### 9.1. Tìm phòng khả dụng (dòng 51-54, 64-70, BR-003)

```sql
-- $1 check_in, $2 check_out, $3 số khách, $4 số giường tối thiểu,
-- $5 cần điều hòa, $6 view codes, $7 room_type_ids, $8 giá min, $9 giá max.
-- Điều hòa được biểu diễn duy nhất bởi amenity code AC.
SELECT r.id, r.room_number, r.view_type, rt.name AS room_type,
       COALESCE(r.price_override, rt.base_price) AS price_per_night
FROM rooms r
JOIN room_types rt ON rt.id = r.room_type_id
WHERE r.deleted_at IS NULL
  AND r.is_active
  AND r.operational_status = 'ACTIVE'                    -- BR-004
  AND rt.is_active
  AND COALESCE(r.max_occupancy_override, rt.max_occupancy) >= $3
  AND rt.bed_count >= $4
  AND (
        $5 IS NULL
        OR ($5 = TRUE AND EXISTS (
              SELECT 1
              FROM room_type_amenities rta
              JOIN amenities a ON a.id = rta.amenity_id
              WHERE rta.room_type_id = rt.id AND a.code = 'AC'
            ))
        OR ($5 = FALSE AND NOT EXISTS (
              SELECT 1
              FROM room_type_amenities rta
              JOIN amenities a ON a.id = rta.amenity_id
              WHERE rta.room_type_id = rt.id AND a.code = 'AC'
            ))
      )
  AND ($6 IS NULL OR JSON_CONTAINS($6, CONCAT('"', r.view_type, '"')))
  AND ($7 IS NULL OR rt.id = ANY($7))
  AND COALESCE(r.price_override, rt.base_price) BETWEEN $8 AND $9
  -- không trùng booking đang hiệu lực (BR-002)
  AND NOT EXISTS (
        SELECT 1 FROM booking_rooms br
        WHERE br.room_id = r.id
          AND br.status IN ('RESERVED','OCCUPIED')
          AND br.check_in_date < $2
          AND br.check_out_date > $1
      )
  -- không trùng lệnh chặn vận hành (BR-003/BR-004)
  AND NOT EXISTS (
        SELECT 1 FROM room_status_blocks b
        WHERE b.room_id = r.id
          AND b.start_date < $2
          AND b.end_date > $1
      )
ORDER BY price_per_night, r.room_number
LIMIT 20 OFFSET 0;                                        -- dòng 201: phân trang
```

Hai `NOT EXISTS` dùng index `(room_id, check_in_date, check_out_date, status)` trên `booking_rooms` và `(room_id, start_date, end_date)` trên `room_status_blocks` nên vẫn nhanh khi bảng booking lớn. Giá hiển thị ở đây là giá niêm yết để sắp xếp/lọc; giá thực tính cho từng đêm áp `rate_overrides` ở bước tạo booking.

### 9.2. Tạo booking an toàn dưới tải đồng thời (dòng 71-78, QĐ-2)

```sql
START TRANSACTION;
  -- Khóa hàng phòng trước để tránh race với trigger BR-002
  SELECT 1 FROM rooms WHERE id = $16 FOR UPDATE;

	  INSERT INTO bookings (
	      public_id, booking_code, customer_id, source_id,
	      source_commission_percent_snapshot, status,
	      contact_name, contact_email, contact_phone,
	      adults, children, rooms_total, room_tax_percent_snapshot,
	      tax_total, total_amount, hold_expires_at, currency)
	  SELECT UUID(), $1, $2, $3,
	         s.commission_percent, 'PENDING',
	         $4, $5, $6, $7, $8, $9, $10,
	         $11, $12, DATE_ADD(NOW(), INTERVAL 15 MINUTE), 'VND'
	  FROM booking_sources s
	  WHERE s.id = $3;

  -- Nếu phòng đã bị người khác giữ: trigger BR-002 ném lỗi SQLSTATE '45000'
  -- Backend bắt 45000 → trả 409 "Phòng vừa được đặt, vui lòng chọn phòng khác"
	  INSERT INTO booking_rooms (
	      booking_id, room_id, room_type_id,
	      room_type_code_snapshot, room_type_name_snapshot,
	      cancellation_policy_id, cancellation_policy_snapshot,
	      check_in_date, check_out_date, room_subtotal, status)
	  SELECT LAST_INSERT_ID(), r.id, rt.id, rt.code, rt.name,
	         p.id,
	         JSON_OBJECT(
	           'code', p.code, 'name', p.name,
	           'no_show_charge_percent', p.no_show_charge_percent,
	           'rules', (SELECT JSON_EXTRACT(CONCAT('[', COALESCE(GROUP_CONCAT(JSON_OBJECT(
	                              'min_hours_before', r.min_hours_before,
	                              'refund_percent',   r.refund_percent)
	                            ORDER BY r.min_hours_before DESC SEPARATOR ','), ''), ']'), '$')
	                     FROM cancellation_policy_rules r WHERE r.policy_id = p.id)),
	         $14, $15, 0, 'RESERVED'
	  FROM rooms r JOIN room_types rt ON rt.id = r.room_type_id
	  JOIN cancellation_policies p ON p.id = rt.cancellation_policy_id
	  WHERE r.id = $16;

  -- Giá từng đêm: rate_override (priority cao nhất) → price_override → base_price
  -- MySQL không có generate_series → tạo dãy ngày bằng application loop hoặc recursive CTE
  -- Ví dụ dùng recursive CTE (MySQL 8.0+):
  INSERT INTO booking_room_nights (booking_room_id, stay_date, price, rate_override_id)
  WITH RECURSIVE nights AS (
      SELECT $14 AS stay_date
      UNION ALL
      SELECT DATE_ADD(stay_date, INTERVAL 1 DAY) FROM nights
      WHERE stay_date < $15 - INTERVAL 1 DAY
  )
  SELECT LAST_INSERT_ID(), n.stay_date,
         COALESCE(
           (SELECT o.price FROM rate_overrides o
            WHERE o.is_active
              AND (o.room_id = $16 OR o.room_type_id = rt.id)
              AND n.stay_date BETWEEN o.start_date AND o.end_date - INTERVAL 1 DAY
              AND (o.weekdays IS NULL
                   OR JSON_CONTAINS(o.weekdays, CAST(DAYOFWEEK(n.stay_date) - 1 AS JSON)))
            ORDER BY o.priority DESC, o.room_id IS NOT NULL DESC
            LIMIT 1),
           r.price_override, rt.base_price)
  FROM nights n
  CROSS JOIN rooms r
  JOIN room_types rt ON rt.id = r.room_type_id
  WHERE r.id = $16;

  -- room_subtotal / rooms_total do trg_booking_totals cập nhật
  INSERT INTO booking_status_history (booking_id, from_status, to_status,
                                      actor_type, changed_by, source)
  VALUES (LAST_INSERT_ID(), NULL, 'PENDING', 'USER', $17, 'MANUAL');
COMMIT;
```

Điểm cốt lõi: không cần `SELECT ... check ... INSERT`. Cứ INSERT, để DB phán quyết — đây là cách duy nhất đúng khi có nhiều instance backend. Trigger BR-002 chạy BEFORE nên overlap bị từ chối ngay tại DB. Nếu tải đồng thời rất cao, `SELECT ... FOR UPDATE` trên `rooms` trước khi INSERT giảm contention giữa các request cùng phòng.

### 9.3. Khách đang ở trong phòng (dòng 103-106, 241-243)

```sql
SELECT r.room_number, b.booking_code, b.contact_name,
       b.checked_in_at, br.check_out_date AS expected_check_out,
       g.full_name AS guest_name, g.nationality, g.id_document_type
FROM booking_rooms br
JOIN bookings b ON b.id = br.booking_id
JOIN rooms r    ON r.id = br.room_id
LEFT JOIN booking_guests g ON g.booking_room_id = br.id
WHERE b.status  = 'CHECKED_IN'                -- dòng 106: nguồn dữ liệu là booking CHECKED_IN
  AND br.status = 'OCCUPIED'
  AND (CAST($1 AS CHAR) IS NULL OR r.room_number = $1)
ORDER BY r.room_number;
```

Không trả `id_document_number_encrypted` trong query danh sách. Số giấy tờ chỉ giải mã ở endpoint riêng, khi user có quyền `guest:read_id`, và ghi `audit_logs` mỗi lần đọc.

### 9.4. Doanh thu theo ngày (dòng 153-157)

```sql
SELECT n.stay_date,
       SUM(n.price)                              AS room_revenue,
       COUNT(DISTINCT br.room_id)                AS rooms_sold,
       ROUND(AVG(n.price), 0)                    AS adr,
       ROUND(SUM(n.price) / NULLIF((SELECT COUNT(*) FROM rooms
                                     WHERE deleted_at IS NULL AND is_active), 0), 0) AS revpar,
       -- doanh thu thuần sau hoa hồng OTA, dùng snapshot chứ không dùng config hiện tại
       SUM(n.price * (1 - COALESCE(b.source_commission_percent_snapshot, 0) / 100)) AS net_revenue
FROM booking_room_nights n
JOIN booking_rooms br ON br.id = n.booking_room_id
JOIN bookings b       ON b.id = br.booking_id
WHERE br.status IN ('OCCUPIED','COMPLETED','MOVED_OUT')   -- đêm đã thực bán
  AND b.status IN ('CHECKED_IN','CHECKED_OUT')
  AND n.stay_date BETWEEN $1 AND $2
GROUP BY n.stay_date
ORDER BY n.stay_date;
```

Đây là lý do `booking_room_nights` tồn tại: ADR và RevPAR không tính được từ tổng tiền booking. Điều kiện `br.status` gồm cả `MOVED_OUT` để đêm khách đã ở phòng cũ trước khi chuyển phòng không bị mất khỏi doanh thu.

### 9.5. Danh sách cần check-in / check-out hôm nay (dòng 247)

Truy vấn này chạy trên `booking_rooms` vì `bookings` không còn cột ngày (QĐ-6).

```sql
-- Khách đến hôm nay
SELECT b.booking_code, b.contact_name, b.contact_phone,
       r.room_number, br.check_in_date, br.check_out_date, br.nights
FROM booking_rooms br
JOIN bookings b ON b.id = br.booking_id
JOIN rooms r    ON r.id = br.room_id
WHERE br.check_in_date = CURRENT_DATE
  AND br.status = 'RESERVED'
  AND b.status  = 'CONFIRMED'                 -- BR-010
ORDER BY r.room_number;

-- Khách trả phòng hôm nay
SELECT b.booking_code, b.contact_name, r.room_number,
       b.total_amount, b.paid_amount,
       b.total_amount - (b.paid_amount - b.refunded_amount) AS balance_due
FROM booking_rooms br
JOIN bookings b ON b.id = br.booking_id
JOIN rooms r    ON r.id = br.room_id
WHERE br.check_out_date = CURRENT_DATE
  AND br.status = 'OCCUPIED'
  AND b.status  = 'CHECKED_IN'                -- BR-011
ORDER BY r.room_number;
```

### 9.6. Tính tiền hoàn khi hủy (dòng 97, BR-005, mục 5.3)

Đọc **từ snapshot trên từng booking room**, không join sang `cancellation_policy_rules`.

```sql
WITH room_ctx AS (
  SELECT br.id,
         br.room_subtotal,
         br.cancellation_policy_snapshot AS snap,
         TIMESTAMPDIFF(HOUR,
           NOW(),
           TIMESTAMP(CAST(br.check_in_date AS DATE), '14:00:00')
         ) AS hours_before_cancel
  FROM booking_rooms br
  WHERE br.booking_id = $1 AND br.status IN ('RESERVED','OCCUPIED')
),
matched AS (
  SELECT rc.id, j.refund_percent, j.min_hours_before,
         ROW_NUMBER() OVER (PARTITION BY rc.id ORDER BY j.min_hours_before DESC) AS rn
  FROM room_ctx rc,
       JSON_TABLE(
         rc.snap, '$.rules[*]' COLUMNS (
           refund_percent   NUMERIC(5,2) PATH '$.refund_percent',
           min_hours_before INT         PATH '$.min_hours_before'
         )
       ) AS j
  WHERE j.min_hours_before <= rc.hours_before_cancel
)
SELECT ROUND(SUM(rc.room_subtotal * m.refund_percent / 100), 2) AS refund_amount,
       JSON_ARRAYAGG(JSON_OBJECT(
         'booking_room_id', rc.id,
         'hours_before_cancel', rc.hours_before_cancel,
         'min_hours_before', m.min_hours_before,
         'refund_percent', m.refund_percent
       )) AS policy_applied
FROM room_ctx rc
JOIN matched m ON m.id = rc.id AND m.rn = 1;
```

Kết quả theo từng dòng phòng (`booking_room_id`, `hours_before_cancel`, rule khớp, phần trăm) được ghi vào `refunds.policy_applied` để giải thích cho khách. Giờ nhận phòng chuẩn `14:00` nên đưa vào bảng cấu hình khách sạn thay vì hard-code — spec không nêu con số này (mục 14).

### 9.7. View khoảng lưu trú của booking (QĐ-6)

Vì `bookings` không lưu khoảng ngày, dùng view cho các màn hình cần hiển thị "booking từ ngày … đến ngày …":

```sql
CREATE VIEW v_booking_stay_range AS
SELECT br.booking_id,
       MIN(br.check_in_date)  AS check_in_date,
       MAX(br.check_out_date) AS check_out_date,
       DATEDIFF(MAX(br.check_out_date), MIN(br.check_in_date)) AS span_days,
       SUM(br.nights)         AS total_room_nights,
       COUNT(*)               AS room_count
FROM booking_rooms br
WHERE br.status IN ('RESERVED','OCCUPIED','COMPLETED','MOVED_OUT')
GROUP BY br.booking_id;
```

`span_days` (khoảng cách lịch) khác `total_room_nights` (tổng đêm-phòng đã bán): đơn 2 phòng × 3 đêm có `span_days = 3` nhưng `total_room_nights = 6`. Dùng đúng cột cho đúng mục đích — hiển thị cho khách dùng `span_days`, báo cáo doanh thu dùng `total_room_nights`.

### 9.8. Thống kê phòng tại một thời điểm (dòng 139-144)

```sql
SELECT
  COUNT(*)                                                          AS total_rooms,
  COUNT(*) FILTER (WHERE occ.room_id IS NOT NULL)                   AS occupied,
  COUNT(*) FILTER (WHERE r.operational_status = 'ACTIVE'
                     AND occ.room_id IS NULL)                       AS available,
  COUNT(*) FILTER (WHERE r.operational_status = 'MAINTENANCE')      AS maintenance,
  COUNT(*) FILTER (WHERE r.operational_status = 'OUT_OF_SERVICE')   AS out_of_service
FROM rooms r
LEFT JOIN (
  SELECT DISTINCT br.room_id
  FROM booking_rooms br JOIN bookings b ON b.id = br.booking_id
  WHERE b.status = 'CHECKED_IN' AND br.status = 'OCCUPIED'
) occ ON occ.room_id = r.id
WHERE r.deleted_at IS NULL;
```

---

## 10. Index và hiệu năng

| Bảng                   | Index                                                                                                        | Phục vụ                                              |
| ----------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------ |
| `booking_rooms`       | `(room_id, check_in_date, check_out_date, status)`                                                       | Tìm phòng trống (9.1) và chống double-booking     |
| `booking_rooms`       | `(booking_id)`                                                                                             | Lấy các phòng của một đơn                       |
| `booking_rooms`       | `(room_id, status)`                                                                                        | Phòng này đang có ai ở                            |
| `booking_rooms`       | `(check_in_date, status)`                                                                                  | Danh sách khách đến hôm nay (9.5)                 |
| `room_status_blocks` | `(room_id, start_date, end_date)`                                                                         | Chặn phòng trùng block (9.1 BR-004)             |
| `bookings`            | unique`(public_id)`, unique `(booking_code)`                                                             | Tra cứu từ URL/API và từ mã khách đọc          |
| `bookings`            | `(customer_id, status)`                                                                                    | My Bookings (dòng 225-227)                            |
| `bookings`            | `(status, created_at DESC)`                                                                                | Danh sách vận hành                                  |
| `bookings`            | partial`(hold_expires_at)` WHERE `status='PENDING'`                                                      | Job giải phóng hold                                  |
| `bookings`            | partial unique`(source_id, external_reference)` WHERE not null                                             | Chống import trùng đơn OTA                         |
| `booking_room_nights` | unique`(booking_room_id, stay_date)`, `(stay_date)`                                                      | Chống tính hai lần một đêm; báo cáo doanh thu  |
| `booking_guests`      | `(booking_id)`, `(booking_room_id)`, `(id_document_lookup_hash)`                                       | Tra khách theo đơn/phòng/giấy tờ                 |
| `rooms`               | partial`(operational_status, is_active)` WHERE `deleted_at IS NULL`, `(view_type)`, `(room_type_id)` | Lọc phòng bán được                               |
| `folio_charges`       | `(booking_id, is_voided)`, `(charged_at)`                                                                | Tổng hợp phát sinh khi checkout                     |
| `invoices`            | `(booking_id)`, `(status, issued_at)`, partial unique `(invoice_number)`                               | Tra hóa đơn của đơn, danh sách đã phát hành |
| `reviews`             | `(room_type_id, status, created_at DESC)`                                                                  | Review trên trang phòng                              |
| `payments`            | `(booking_id, status)`, unique `(provider_txn_id)`, unique `(idempotency_key)`                         | Đối soát, idempotency                               |
| `payment_events`      | unique`(provider, provider_event_id)`, `(processed_at)`                                                  | Chống replay, retry                                   |
| `email_messages`      | `(status, scheduled_at)`                                                                                   | Worker gửi email                                      |
| `audit_logs`          | `(entity_type, entity_id, created_at DESC)`                                                                | Tra lịch sử                                          |

Ghi chú vận hành:

- **Index `(room_id, check_in_date, check_out_date, status)`** phục vụ trigger BR-002 và query 9.1. MySQL không có GiST, nên khai báo B-tree composite để cover trigger overlap check và NOT EXISTS scan.
- **Dashboard không query trực tiếp bảng giao dịch.** Dòng 234-236 và 254-258 yêu cầu nhiều biểu đồ. Khi dữ liệu lớn, dùng view hoặc job định kỳ tạo bảng tổng hợp (`mv_daily_revenue`, `mv_daily_occupancy`) refresh mỗi 15-30 phút. MySQL không có materialized view tự động refresh; cân nhắc dùng event scheduler hoặc job bên ngoài.
- **Partition** `audit_logs`, `payment_events`, `email_messages` theo tháng khi mỗi bảng vượt ~20 triệu dòng (MySQL `PARTITION BY RANGE`).
- **Connection pool** (ProxySQL hoặc MySQL Router) đặt trước DB vì booking dùng transaction ngắn nhưng lượng request cao lúc khuyến mãi.
- **Isolation level**: `READ COMMITTED` đủ cho phần lớn thao tác vì BR-002 trigger chạy BEFORE. Luồng checkout (đọc nhiều bảng để tính tiền rồi phát hành invoice) nên chạy `SERIALIZABLE`.

---

## 11. Source of truth, bảo mật, triển khai

### 11.1. Bảng nguồn dữ liệu (P9)

Mỗi giá trị tiền chỉ có một nơi sinh ra. Các cột aggregate được phép tồn tại nhưng phải ghi rõ nguồn và cập nhật trong cùng transaction.

| Giá trị                   | Source of truth                                 | Aggregate/cache ở                                                                       | Cập nhật bởi                               |
| --------------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------- |
| Giá phòng một đêm      | `booking_room_nights.price`                   | —                                                                                       | Ghi một lần lúc tạo booking, không đổi |
| Tiền một dòng phòng     | —                                              | `booking_rooms.room_subtotal` = SUM night rows                                         | `trg_booking_totals`                        |
| Tiền phòng cả đơn      | —                                              | `bookings.rooms_total` = SUM `booking_rooms.room_subtotal`                           | `trg_booking_totals`                        |
| Giá dịch vụ đã dùng   | `folio_charges.unit_price` / `line_total`   | `bookings.services_total`                                                              | `trg_booking_totals`                        |
| Tiền trên chứng từ      | `invoice_items.line_total`                    | `invoices.subtotal`/`tax_total`/`total_amount`                                     | Lúc Issue, sau đó bất biến               |
| Tiền đã thu              | `payments` (status SUCCEEDED)                 | `bookings.paid_amount`, `invoices.paid_amount`                                       | `trg_payment_ledger`                        |
| Tiền đã hoàn            | `refunds` (status COMPLETED)                  | `payments.refunded_amount`, `bookings.refunded_amount`, `invoices.refunded_amount` | `trg_payment_ledger`                        |
| Khoảng ngày lưu trú     | `booking_rooms.check_in_date/check_out_date`  | View`v_booking_stay_range`                                                             | Không lưu, derive                           |
| Chính sách hủy áp dụng | `booking_rooms.cancellation_policy_snapshot`  | —                                                                                       | Ghi một lần cho từng phòng lúc tạo booking |
| Hoa hồng OTA của đơn    | `bookings.source_commission_percent_snapshot` | —                                                                                       | Ghi lúc xác nhận booking                   |

Job đối soát hằng đêm so lại toàn bộ cột aggregate với nguồn và báo lệch — trigger có thể sai do bug, dữ liệu tiền thì không được sai âm thầm.

### 11.2. Chuẩn hóa và các điểm cố ý phi chuẩn

Schema ở dạng 3NF. Các chỗ cố ý denormalize:

| Chỗ denormalize                                                                                           | Lý do                                                                                            | Giữ nhất quán bằng                                                                                                                      |
| ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `bookings.rooms_total`, `services_total`, `total_amount`, `paid_amount`, `refunded_amount`       | Tránh SUM nhiều bảng ở mọi lần hiển thị danh sách booking                                | `trg_booking_totals` / `trg_payment_ledger` + job đối soát (11.1)                                                                    |
| `booking_rooms.room_type_code_snapshot`, `room_type_name_snapshot`                                     | Snapshot theo P7 —**bắt buộc**, không phải tối ưu                                    | Không đồng bộ; cố ý đứng yên                                                                                                       |
| `bookings.contact_*`, `invoices.buyer_*`, `folio_charges.description`, `invoice_items.description` | Snapshot theo P7                                                                                  | Không đồng bộ; cố ý đứng yên                                                                                                       |

Điểm khác biệt cần nhớ: hai nhóm cuối **không phải** denormalize để tối ưu — chúng là dữ liệu độc lập, trùng giá trị với master data chỉ tại thời điểm tạo.

Điều hòa có đúng một source of truth là amenity code `AC`. Loại phòng có điều hòa khi tồn tại dòng tương ứng trong `room_type_amenities`; không lưu thêm cột boolean trên `room_types`.

### 11.3. Bảo mật dữ liệu

- **Giấy tờ tùy thân** (`booking_guests`): lưu `id_document_number_encrypted` (AES-256-GCM, key ở KMS) + `id_document_lookup_hash` (HMAC-SHA256 với pepper riêng). Không lưu plaintext, không dùng SHA-256 thuần cho số CCCD/passport vì không gian giá trị hẹp nên bị vét cạn. Chi tiết ở mục 6.5.
- `staff_profiles.base_salary`: chỉ Admin đọc, qua view riêng hoặc column-level grant.
- `auth_tokens.token_hash` lưu SHA-256 của token random — ở đây SHA-256 là đủ vì token có entropy cao, khác trường hợp CCCD.
- `password_hash` dùng bcrypt cost ≥ 12 hoặc argon2id.
- Tài khoản DB cho ứng dụng chỉ cần DML; không cấp `SUPERUSER`/`CREATE`. Migration chạy bằng tài khoản riêng.
- Bật `pgaudit` cho `payments`, `invoices`, `refunds`, và cho mọi lần đọc `id_document_number_encrypted`.
- Backup: WAL archiving + PITR. Dữ liệu booking/payment mất là không khôi phục được bằng cách khác.
- GDPR/PDPA: khi khách yêu cầu xóa dữ liệu, ẩn danh hóa (`contact_name` → `'Deleted'`, xóa `contact_email`/`contact_phone`, xóa hai cột giấy tờ) chứ không DELETE — vì BR-013 buộc giữ hóa đơn.

### 11.4. Triển khai theo phase

**Phase 1 — MVP (23 bảng)**
`users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `customer_profiles`, `staff_profiles`, `auth_tokens`, `user_social_accounts`, `amenities`, `room_types`, `room_type_beds`, `room_type_amenities`, `room_type_images`, `rooms`, `room_amenities`, `room_images`, `room_status_blocks`, `rate_overrides`, `booking_sources`, `bookings`, `booking_rooms`, `booking_room_nights`.
Đủ cho luồng: đăng ký → tìm phòng → đặt phòng → chặn trùng phòng.

**Phase 2 — Thanh toán & vận hành (10 bảng)**
`cancellation_policies`, `cancellation_policy_rules`, `booking_guests`, `booking_status_history`, `payments`, `payment_events`, `refunds`, `invoices`, `invoice_items`, `service_items`.
Đủ cho: thanh toán có xác minh, hủy có hoàn tiền theo bậc, check-in/check-out, hóa đơn.

**Phase 3 — Mở rộng (6 bảng)**
`shifts`, `shift_assignments`, `folio_charges`, `reviews`, `email_messages`, `audit_logs`, cùng materialized view cho dashboard.

Nếu tiến độ gấp, hai chỗ có thể cắt mà không phá kiến trúc: bỏ `permissions`/`role_permissions` (dùng tạm role code) và bỏ `rate_overrides` (chỉ dùng `base_price`/`price_override`, `booking_room_nights` vẫn ghi giá từng đêm nên thêm lại sau không cần migration dữ liệu).

**Không được cắt**: trigger BR-002 trên `booking_rooms`, `booking_room_nights`, `room_status_blocks`, `bookings.hold_expires_at`, `payments.verified_at`, và các cột snapshot. Thiếu bất kỳ thứ nào trong số này là lỗi tiền hoặc lỗi trùng phòng, và khắc phục sau đòi hỏi backfill dữ liệu không thể tái tạo.

### 11.5. MySQL Compatibility Notes

Các PostgreSQL features dưới đây đã được thay thế bằng MySQL equivalents để đảm bảo tương thích:

| PostgreSQL feature                                      | MySQL 8 equivalent used in this design                                                                                                                                                                    |
| ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EXCLUDE USING gist` cho BR-002                       | Trigger `BEFORE INSERT/UPDATE` trên `booking_rooms` kiểm tra overlap bằng điều kiện trên DATE columns + `SIGNAL SQLSTATE '45000'` để từ chối. Không dùng pessimistic lock ở table-level |
| `daterange` + `&&` overlap operator                   | Hai cột DATE riêng (`check_in_date`, `check_out_date`) + điều kiện overlap `NOT (existing.check_out <= NEW.check_in_date OR existing.check_in_date >= NEW.check_out_date)`                               |
| `CITEXT` case-insensitive text                        | Collation`utf8mb4_0900_ai_ci` trên cột email                                                                                                                                                             |
| Partial UNIQUE `WHERE deleted_at IS NULL`             | Generated column `room_number_active` = `IF(deleted_at IS NULL, room_number, NULL)` rồi UNIQUE trên cột sinh đó                                                                                           |
| Partial unique `(source_id, external_reference)`        | Tương tự: generated column gộp hai giá trị, NULL khi `external_reference` NULL                                                                                                                            |
| `ENUM` type dùng chung (`room_view`, `booking_status`…) | `ENUM(...)` trên từng cột (ít linh hoạt hơn; thay đổi giá trị enum cần ALTER TABLE) hoặc bảng lookup                                                                                              |
| `JSONB` (snapshot policy)                             | `JSON` — `JSON_EXTRACT`/`JSON_UNQUOTE` để đọc. Không có index GIN nhưng snapshot policy luôn tra theo `booking_id` nên không ảnh hưởng performance                                               |
| Composite FK cho `booking_guests`                      | Hỗ trợ đầy đủ, không cần thay thế                                                                                                                                                                         |
| `EXCLUDE` trên `room_status_blocks`                  | Trigger `BEFORE INSERT/UPDATE` trên `room_status_blocks` tương tự BR-002 (8.3)                                                                                                                            |

**Không dùng pessimistic lock (`SELECT ... FOR UPDATE`) ở table-level** vì làm giảm throughput. Thay vào đó, tất cả overlap check được đặt trong trigger body với `SIGNAL` error — đảm bảo chặn ở DB mà không cần giữ lock lâu.

**Không dùng**: `EXCLUDE USING gist`, `daterange`, `LANGUAGE plpgsql`, `CREATE FUNCTION ... RETURNS trigger`.

---

## 12. DBML cho dbdiagram.io

Xem file [`hotel_management_for_dbdiagram.dbml`](./hotel_management_for_dbdiagram.dbml) — dán trực tiếp vào https://dbdiagram.io.

Tổng: **40 bảng**, 27 enum. File DBML gồm seed data tối thiểu (roles, booking_sources, cancellation_policies + rules, room_types, shifts) để dán vào là thấy được ngay.

---

## 13. Changes Made

Thay đổi schema so với bản đầu, theo nhóm.

**Thêm bảng**

| Bảng                         | Lý do                                                                                                     |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `shifts`                    | Quản lý ca trực (dòng 168-173, 268-271): tạo ca, gán Staff, xem lịch. Chuẩn bị sẵn cho phase sau |
| `shift_assignments`         | Gán Staff vào ca theo ngày. BR-015: trigger `BEFORE INSERT/UPDATE` chặn Staff có 2 ca trùng giờ              |
| `cancellation_policy_rules` | Thay cặp cột một-mốc`free_cancel_hours` + `refund_percent_after` bằng mô hình nhiều bậc (5.3) |

**Bỏ bảng**

| Bảng          | Lý do                                                                                                                                                           |
| -------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `room_views` | Thay bằng enum`room_view` trên `rooms.view_type`. Bảng chỉ có `code`+`name`, không mang thêm thông tin; tập giá trị cố định trên UI (P10) |

**Thêm bảng**

| Bảng                         | Lý do                                                                                                     |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `cancellation_policy_rules` | Thay cặp cột một-mốc`free_cancel_hours` + `refund_percent_after` bằng mô hình nhiều bậc (5.3) |

**`bookings`**

| Thay đổi                                                           | Chi tiết                                                                                                    |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Bỏ`check_in_date`, `check_out_date`, `nights`                 | Khoảng ngày chỉ thuộc`booking_rooms` (QĐ-6). Derive qua `v_booking_stay_range` (9.7)                |
| Đổi`room_subtotal` → `rooms_total`                            | Phân biệt với`booking_rooms.room_subtotal`                                                              |
| Đổi`service_total` → `services_total`                         | Đối xứng với`rooms_total`                                                                              |
| Đổi`guest_full_name/email/phone` → `contact_name/email/phone` | Tránh nhầm với`booking_guests`; email và phone nay nullable, validate theo source ở service           |
| Thêm`public_id UUID`                                              | Không dùng PK tuần tự làm public identifier (P1)                                                        |
| Thêm`source_commission_percent_snapshot`                          | Đổi hợp đồng OTA không hồi tố doanh thu thuần booking cũ                                           |
| Thêm`refunded_amount`                                             | `paid_amount` không giảm khi refund; `net_received` là giá trị dẫn xuất (7.6)                     |
| Thêm`room_tax_percent_snapshot`                                   | Để`tax_total` và dòng ROOM trên hóa đơn tính được từ snapshot, không đọc config hiện tại |
| Thêm partial unique`(source_id, external_reference)`              | Chống import trùng đơn OTA                                                                               |
| Bỏ CHECK BR-001                                                     | Không còn cột ngày; BR-001 chỉ ở`booking_rooms`                                                      |
| Đổi CHECK tổng tiền                                              | `total_amount = rooms_total + services_total + tax_total - discount_total`                                 |
| Bỏ index trên`check_in_date`                                     | Chuyển sang`booking_rooms(check_in_date, status)`                                                         |

**`booking_rooms`**

| Thay đổi                                                    | Chi tiết                                                                                                                                                     |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bỏ`room_rate_snapshot`                                     | Giá khác nhau từng đêm nên không có một con số đại diện. Nguồn giá là`booking_room_nights.price`                                            |
| Bỏ`is_active`                                              | Trigger BR-002 dùng trực tiếp`status`; cột derived song song chỉ gây lệch                                                                                   |
| Thêm`COMPLETED` vào enum status                           | 5 giá trị:`RESERVED / OCCUPIED / COMPLETED / RELEASED / MOVED_OUT`. Bản trước để dòng đã checkout ở `OCCUPIED` nên vẫn chặn phòng vô ích |
| Thêm`room_type_code_snapshot`, `room_type_name_snapshot` | `room_type_id` chỉ là reference, không phải snapshot                                                                                                    |
| Thêm`moved_from_booking_room_id`                           | Truy vết chuỗi đổi phòng giữa kỳ (6.3)                                                                                                                 |
| Thêm UNIQUE`(id, booking_id)`                              | Làm đích cho composite FK từ`booking_guests`                                                                                                            |
| Đổi FK`booking_id` sang RESTRICT                          | Không cho CASCADE xóa mất lịch sử (6.7)                                                                                                                  |
| Thêm trigger BR-002 + index`(room_id, check_in_date, check_out_date, status)` | Trigger BEFORE INSERT/UPDATE chống overlap thay cho `EXCLUDE USING gist`; index B-tree phục vụ trigger và query 9.1                                                                                                              |
| Thêm index`(check_in_date, status)`                        | Danh sách khách đến hôm nay                                                                                                                              |

**`booking_room_nights`**

| Thay đổi                                       | Chi tiết                                                                                                         |
| ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| Ghi rõ là source of truth của giá phòng     | `price` là giá thực bán từng đêm, bất biến sau khi chốt                                               |
| Thêm trigger toàn vẹn với dòng cha          | `check_in_date <= stay_date < check_out_date`; đủ số đêm và khớp tổng khi CONFIRMED (6.4)               |
| Ghi rõ`rate_override_id` chỉ là trace field | Không dùng`rate_overrides` hiện tại để tính lại giá cũ; nêu hai lựa chọn nếu cần tái hiện rule |
| Đổi FK sang RESTRICT                           | Giữ lịch sử giá (6.7)                                                                                         |

**`booking_guests`**

| Thay đổi                                                                                                              | Chi tiết                                                                                                         |
| ----------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Thêm`nationality CHAR(2)`                                                                                            | Theo yêu cầu; cần cho khai báo lưu trú khách nước ngoài                                                 |
| Bỏ`is_primary` + partial UNIQUE                                                                                      | Spec không có chức năng dùng khái niệm "khách chính"; đầu mối đơn đã là`bookings.contact_name` |
| Thay`id_document_number VARCHAR(50)` bằng `id_document_number_encrypted BYTEA` + `id_document_lookup_hash BYTEA` | Không mô tả plaintext rồi nói sẽ mã hóa. HMAC cho exact search; không dùng SHA-256 thuần cho CCCD      |
| Thêm composite FK`(booking_room_id, booking_id)`                                                                     | Chặn dữ liệu trỏ chéo sang booking khác                                                                     |

**`booking_status_history`**

| Thay đổi                                               | Chi tiết                                                                          |
| -------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Thêm`actor_type` (`USER`/`SYSTEM`) và `source` | `changed_by IS NULL` không còn mang hai nghĩa "hệ thống" và "không biết" |
| Thêm CHECK gắn`actor_type` với `changed_by`       |                                                                                    |
| Đổi FK sang RESTRICT + trigger chặn UPDATE/DELETE     | Append-only thực sự, không chỉ theo quy ước                                  |

**`cancellation_policies`**

| Thay đổi                                         | Chi tiết                                                                                                                                                     |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bỏ`free_cancel_hours`, `refund_percent_after` | Chỉ biểu diễn được một mốc; thay bằng`cancellation_policy_rules` nhiều bậc                                                                       |
| Giữ`no_show_charge_percent`                     | No-show không phải một bậc hủy (không có thời điểm hủy để so). Ghi rõ nên chuyển sang cấu hình chung nếu toàn khách sạn dùng một mức |
| Snapshot vào booking room gồm cả rules          | `booking_rooms.cancellation_policy_snapshot` chứa `code`, `name`, `no_show_charge_percent`, và mảng `rules` (5.4)                                |

**`folio_charges`**

| Thay đổi                                                                                                             | Chi tiết                                                                                                 |
| ---------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Thay một cột`amount` bằng `line_subtotal`, `discount_amount`, `tax_percent`, `tax_amount`, `line_total` | Cột`amount` gộp cả thuế mà không thấy phần thuế; không đối chiếu và không CHECK được  |
| Thêm CHECK công thức                                                                                                | `line_subtotal = quantity × unit_price`, `line_total = line_subtotal - discount_amount + tax_amount` |
| Thêm`voided_at`                                                                                                     | Void phải có thời điểm, không chỉ`voided_by`/`void_reason`                                     |
| Thêm CHECK bộ ba void                                                                                                | `is_voided` bắt buộc kèm `voided_at`, `voided_by`, `void_reason`                               |

**`invoices`**

| Thay đổi                                                                                                                                   | Chi tiết                                                                                                     |
| -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Tách một enum thành`status` (`DRAFT/ISSUED/VOID`) và `payment_status` (`UNPAID/PARTIALLY_PAID/PAID/PARTIALLY_REFUNDED/REFUNDED`) | Bản trước trộn hai state machine, không biểu diễn được "đã phát hành và đã hoàn một phần" |
| `invoice_number` nay NULLABLE                                                                                                              | NULL khi DRAFT, cấp khi Issue. Bỏ khẳng định về yêu cầu pháp lý tính liên tục (mục 14)          |
| Thêm`public_id UUID`                                                                                                                      | URL tải hóa đơn                                                                                           |
| Thêm`refunded_amount`                                                                                                                     | `paid_amount` không giảm khi refund (7.6)                                                                 |
| Thêm`replaces_invoice_id`, `voided_at`, `voided_by`                                                                                   | Luồng VOID + hóa đơn thay thế thay cho sửa âm thầm                                                    |
| Thêm CHECK theo trạng thái + trigger bất biến                                                                                           | Sau ISSUED chỉ được đổi payment/pdf/void; chặn DELETE                                                  |

**`invoice_items`**

| Thay đổi                                                                                                   | Chi tiết                                                                                                                            |
| ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| Bỏ`TAX` và `DISCOUNT` khỏi `line_type`, còn `ROOM / SERVICE / ADJUSTMENT`                        | Thuế và giảm giá đã là thuộc tính tiền của từng dòng và được tổng hợp ở header → dòng riêng gây double-count |
| Thay`amount` bằng `line_subtotal`, `discount_amount`, `tax_percent`, `tax_amount`, `line_total` | Khớp cấu trúc với`folio_charges` để bước tạo hóa đơn là phép copy                                                    |
| Định nghĩa rõ`ADJUSTMENT`                                                                              | Dương làm tăng, âm làm giảm; là line_type duy nhất được phép âm; bắt buộc có`description`                         |
| Ghi rõ`reference_type`/`reference_id` là polymorphic trace                                             | Không FK, không dùng để tính lại sau ISSUED                                                                                   |
| Thêm trigger chặn ghi khi hóa đơn cha`<> DRAFT`                                                       |                                                                                                                                      |

**`payments` / `refunds`**

| Thay đổi                                    | Chi tiết                                                                                                                     |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Định nghĩa lại ngữ nghĩa`paid_amount` | Là tổng đã thực nhận,**không** giảm khi refund; `net_received = paid_amount - refunded_amount` tính khi cần |
| Ghi rõ ledger là source of truth            | Các cột amount ở`bookings`/`invoices` là aggregate cache, cập nhật transactionally (11.1)                           |
| `refunds.policy_applied` ghi rõ nội dung  | Bậc rule khớp,`hours_before_cancel`, phần trăm — để giải thích với khách                                         |

**Khác**

| Thay đổi                                     | Chi tiết                                                                                             |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Thêm P7–P10 vào nguyên tắc                | Ba tầng dữ liệu; không dùng SCD Type 2; một source of truth; enum vs bảng lookup               |
| Thêm QĐ-5, QĐ-6                             | Sơ đồ ba mốc snapshot; khoảng ngày chỉ ở`booking_rooms`                                     |
| Thêm view`v_booking_stay_range`             | Thay cho cột ngày đã bỏ ở`bookings`                                                           |
| Thêm mục 6.7                                 | Chính sách không hard delete + procedure`purge_abandoned_booking` giới hạn cho PENDING/EXPIRED |
| Thêm mục 8.2, 8.4                            | Đồng bộ`booking_room_status`; danh sách 13 trigger cần viết                                   |
| Thêm mục 11.1                                | Bảng source of truth cho mọi giá trị tiền                                                        |
| Thêm query 9.5, 9.6, 9.7                      | Arrivals/departures theo`booking_rooms`; tính refund từ snapshot; view khoảng lưu trú          |
| Bỏ`updated_by` khỏi P6                     | Không bảng nào dùng;`audit_logs` và `booking_status_history` đã ghi ai sửa gì            |
| `booking_sources` thêm `requires_account` | Validate`customer_id` theo nguồn thay vì CHECK không khả thi                                    |

---

## 14. Conflicts và điểm cần business xác nhận

Bốn điểm dưới đây tôi **không tự quyết**, vì hoặc xung đột với requirement gốc, hoặc spec không có dữ liệu.

**C-1. Bỏ `shifts` / `shift_assignments` xung đột với requirement gốc.**
Spec dòng 168-173 (Quản lý ca trực), dòng 268-271 (Shift Management page) và **BR-014** ("Chỉ Admin được quản lý Staff **và ca trực** nếu chức năng này được đưa vào scope") đều mô tả chức năng ca trực. Spec đánh dấu đây là phần "**có thể triển khai thêm**", nên bỏ khỏi schema là hợp lệ về mặt scope — nhưng đây là **bỏ một chức năng optional, không phải sửa lỗi thiết kế**. Tôi đã bỏ hai bảng theo yêu cầu và điều chỉnh BR-014 trong bảng 8 chỉ còn phần `staff:manage`. Nếu sau này ca trực vào scope, cần thêm lại hai bảng cùng ràng buộc `EXCLUDE USING gist (staff_id WITH =, shift_period WITH &&)` cho dòng 173 ("không cho một Staff có nhiều ca bị trùng thời gian") — đây là ràng buộc overlap thứ ba của hệ thống và không nên tự phát minh lại lúc đó.

**C-2. `room_view` — giữ chức năng lọc, đổi cách lưu.**
Yêu cầu là bỏ bảng `RoomView`. Nhưng spec dòng 53, 67 và 202 yêu cầu **lọc theo view**, nên bản thân thuộc tính view không thể bỏ. Tôi giữ thuộc tính dưới dạng enum `room_view` trên `rooms.view_type` — mất khả năng cho Admin tự thêm giá trị view qua UI, đổi lại schema gọn hơn và vẫn lọc được. Nếu khách sạn thực sự cần tự thêm loại view (mở cánh mới có view khác), phải quay lại dùng bảng lookup.

**C-3. Thuế suất tiền phòng — spec không nêu con số.**
Tôi thêm `bookings.room_tax_percent_snapshot` để dòng ROOM trên hóa đơn có `tax_percent` snapshot và `tax_total` tính lại được không cần config hiện tại. Nhưng spec không nói tiền phòng có VAT bao nhiêu, có phí dịch vụ 5% hay không, và `booking_room_nights.price` là giá trước thuế hay đã gồm thuế. Tôi chọn **`price` là giá trước thuế** cho nhất quán với `folio_charges.unit_price`. Cần business xác nhận: (a) thuế suất áp cho phòng, (b) có phí dịch vụ riêng không, (c) giá niêm yết trên web là gồm thuế hay chưa — câu (c) ảnh hưởng cách hiển thị giá cho khách, không chỉ ảnh hưởng schema.

**C-4. Giờ nhận phòng chuẩn — cần cho phép tính hoàn tiền.**
Rule hủy tính theo "số giờ trước check-in" (5.3), nên cần biết giờ nhận phòng chuẩn. Query 9.6 tạm dùng `14:00 Asia/Ho_Chi_Minh`. Spec không nêu con số này. Nên đưa vào một bảng cấu hình khách sạn (`hotel_settings`) cùng với mức no-show chung nếu C-1 dẫn tới việc lập bảng đó — tôi **không** thêm bảng này vào schema vì nằm ngoài phạm vi sửa consistency/snapshot lần này.
