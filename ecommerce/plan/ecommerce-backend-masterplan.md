# MASTER PLAN — E-Commerce Backend (Java + Spring Boot + JWT + MySQL)

> Tài liệu này là **master plan duy nhất** để triển khai toàn bộ Backend REST API monolithic dựa trên database schema hiện có. Không chứa code — chỉ chứa phân tích, kiến trúc, roadmap, business logic, API design, testing plan và checklist triển khai.

---

## MỤC LỤC

- [PHẦN I — Phân tích Database Schema](#phần-i--phân-tích-database-schema)
- [PHẦN II — Kiến trúc Backend](#phần-ii--kiến-trúc-backend)
- [PHẦN III — Thiết kế chuyên sâu theo Domain](#phần-iii--thiết-kế-chuyên-sâu-theo-domain)
- [PHẦN IV — Development Roadmap (Phase 0 → 17)](#phần-iv--development-roadmap)
- [PHẦN V — Master API Catalogue](#phần-v--master-api-catalogue)
- [PHẦN VI — Testing Strategy](#phần-vi--testing-strategy)
- [PHẦN VII — Development Checklist chi tiết](#phần-vii--development-checklist-chi-tiết)

---

# PHẦN I — Phân tích Database Schema

## 1.1. Ghi chú quan trọng trước khi bắt đầu

Schema này rõ ràng được dump ra từ một dự án **Laravel** (có các bảng `cache`, `cache_locks`, `jobs`, `job_batches`, `failed_jobs`, `migrations`, `sessions`, `password_reset_tokens`, cột `remember_token`). Các bảng/cột này **không liên quan đến Spring Boot** và sẽ **không được map thành Entity**:

| Bảng/cột Laravel | Xử lý |
|---|---|
| `cache`, `cache_locks` | Bỏ qua — Spring Boot dùng cơ chế cache riêng (Caffeine/Redis) nếu cần, không cần bảng SQL |
| `jobs`, `job_batches`, `failed_jobs` | Bỏ qua — nếu cần async/queue, Spring dùng `@Async`, `@Scheduled`, hoặc RabbitMQ/Kafka riêng |
| `migrations` | Bỏ qua — Spring Boot dùng Flyway/Liquibase với bảng migration riêng |
| `sessions` | Bỏ qua — dùng JWT stateless, không cần session table |
| `password_reset_tokens` | Không nằm trong scope tính năng hiện tại (mục 1 chỉ yêu cầu Register/Login/JWT). Có thể bổ sung sau nếu cần "Forgot password" |
| `users.remember_token` | Không cần — đây là cơ chế "remember me" của Laravel, JWT không dùng cột này |

→ **17 bảng nghiệp vụ thực sự cần xử lý**: `users`, `vendors`, `categories`, `products`, `product_variants`, `product_images`, `carts`, `cart_items`, `wishlists`, `customer_addresses`, `coupons`, `coupon_users`, `orders`, `order_items`, `payments`, `reviews`, `vendor_reviews`.

---

## 1.2. Phân tích từng bảng nghiệp vụ

### `users`
| Field | Kiểu | Ghi chú |
|---|---|---|
| id | PK bigint | |
| name | varchar NOT NULL | |
| email | varchar NOT NULL, **UNIQUE** | dùng làm username đăng nhập |
| email_verified_at | timestamp NULL | không có tính năng verify email trong scope hiện tại → có thể bỏ qua hoặc để dành |
| password | varchar NOT NULL | lưu **BCrypt hash**, không bao giờ trả về response |
| role | ENUM('admin','vendor','customer') NOT NULL DEFAULT 'customer' | 1 user = đúng 1 role |
| created_at/updated_at | timestamp | |

### `vendors`
PK `id`; FK `user_id → users.id` (ON DELETE CASCADE); `shop_name` NOT NULL; `description`, `logo`, `address` nullable.
⚠️ **Không có UNIQUE trên `user_id`** → về lý thuyết 1 user có thể có nhiều vendor profile, phá vỡ quan hệ 1–1 kỳ vọng (xem mục 1.4/1.5).

### `categories`
PK `id`; `name` NOT NULL; `parent_id → categories.id` (self-reference, ON DELETE CASCADE); `image`, `slug` nullable.
⚠️ `slug` không có UNIQUE dù thường dùng để tạo URL SEO-friendly.

### `products`
PK `id`; FK `vendor_id → vendors.id` NOT NULL (CASCADE); FK `category_id → categories.id` nullable (SET NULL); `name`, `description` NOT NULL; `price DECIMAL(10,2)` NOT NULL; `stock INT` DEFAULT 0; `is_active TINYINT(1)` DEFAULT 1 (soft flag để ẩn sản phẩm — **rất quan trọng**, sẽ dùng thay cho hard delete).

### `product_variants`
PK `id`; FK `product_id → products.id` NOT NULL (CASCADE); `name` NOT NULL (vd: "Đỏ - XL"); `price DECIMAL(12,2)` NOT NULL; `stock INT` DEFAULT 0; `sku` nullable.
⚠️ `price` có precision `(12,2)` khác với `products.price (10,2)` — không gây lỗi nhưng nên nhất quán khi thiết kế Entity (dùng `BigDecimal` cho cả hai, không cần sửa DB).
⚠️ `sku` không có UNIQUE dù SKU thường phải là mã định danh duy nhất.

### `product_images`
PK `id`; FK `product_id → products.id` NOT NULL (CASCADE); `image` nullable; `is_main TINYINT(1)` DEFAULT 0.

### `carts`
PK `id`; FK `user_id → users.id` NOT NULL (CASCADE).
⚠️ Không có UNIQUE trên `user_id` → DB cho phép 1 user có nhiều cart. Nghiệp vụ e-commerce thường chỉ có **1 cart đang hoạt động/user** — phải enforce ở tầng Service (pattern "find-or-create").

### `cart_items`
PK `id`; FK `cart_id → carts.id` NOT NULL (CASCADE); FK `product_id → products.id` NOT NULL (CASCADE); FK `product_variant_id → product_variants.id` **nullable** (SET NULL); `quantity INT` NOT NULL.
⚠️ Không có UNIQUE trên `(cart_id, product_id, product_variant_id)` → có thể phát sinh nhiều dòng trùng cho cùng 1 sản phẩm/variant thay vì cộng dồn quantity.
🔑 **Lưu ý quan trọng**: `product_variant_id` ở đây **nullable** — tức là schema cho phép thêm vào giỏ hàng một sản phẩm **không có variant**.

### `wishlists`
PK `id`; FK `user_id`, `product_id` NOT NULL (CASCADE cả hai).
⚠️ Không có UNIQUE `(user_id, product_id)` → có thể wishlist trùng 1 sản phẩm nhiều lần.

### `customer_addresses`
PK `id`; FK `user_id → users.id` NOT NULL (CASCADE); `type` varchar DEFAULT 'billing' (nên coi là enum ở tầng service: billing/shipping); các field địa chỉ chuẩn; `is_default TINYINT(1)` NOT NULL.
⚠️ Không có ràng buộc DB đảm bảo chỉ 1 địa chỉ `is_default=true`/user → phải xử lý transaction ở Service (khi set 1 địa chỉ default, phải unset các địa chỉ khác).

### `coupons`
PK `id`; `code` **UNIQUE** NOT NULL; `type ENUM('fixed','percent')`; `value DECIMAL(8,2)`; `min_order_amount` nullable; `usage_limit` nullable (null = không giới hạn); `used` DEFAULT 0 (đếm tổng lượt dùng toàn hệ thống); `valid_from`, `valid_until` nullable.

### `coupon_users`
PK `id`; FK `coupon_id`, `user_id` NOT NULL (CASCADE); `times_used` DEFAULT 0.
⚠️ Không có UNIQUE `(coupon_id, user_id)` → có thể tồn tại nhiều dòng cho cùng 1 cặp coupon-user, khiến `times_used` mất ý nghĩa nếu không kiểm soát ở Service.
⚠️ Schema **không có cột giới hạn số lần dùng/user** (chỉ có `usage_limit` toàn hệ thống) — xem mục 1.5, nhóm "cần cân nhắc".

### `orders`
PK `id`; FK `user_id → users.id` NOT NULL (CASCADE); **snapshot địa chỉ đầy đủ** (`address_name`, `address_line1/2`, `city`, `state`, `postal_code`, `country`, `phone_number`) — đây là thiết kế **đúng và nên giữ nguyên**; `status ENUM('pending','processing','shipped','delivered','cancelled')` DEFAULT 'pending'; `total DECIMAL(10,2)`; `coupon_code` varchar nullable (lưu **string**, không phải FK — cũng là thiết kế đúng, tách rời vòng đời coupon khỏi order); `discount_amount DECIMAL(8,2)` DEFAULT 0.

### `order_items`
PK `id`; FK `order_id → orders.id` NOT NULL (CASCADE); FK `product_id → products.id` NOT NULL **(CASCADE)**; FK `product_variant_id → product_variants.id` **NOT NULL (CASCADE)**; **snapshot** `product_name`, `variant_name`, `price` — thiết kế đúng, đáp ứng đúng yêu cầu "order phải lưu giá tại thời điểm mua"; `quantity` NOT NULL.
🔑 **2 vấn đề nghiêm trọng cần lưu ý** (chi tiết ở mục 1.5, nhóm "cần cân nhắc trước khi code"):
1. `product_variant_id` ở đây là **NOT NULL**, trong khi ở `cart_items` nó lại **nullable** → mâu thuẫn logic khi sản phẩm không có variant.
2. FK `product_id` và `product_variant_id` đều **ON DELETE CASCADE** → nếu xoá cứng 1 sản phẩm/variant đã từng được đặt hàng, **toàn bộ order_items lịch sử liên quan sẽ bị xoá theo**, làm hỏng dữ liệu đơn hàng cũ.

### `payments`
PK `id`; FK `order_id → orders.id` NOT NULL (CASCADE) — **không có UNIQUE** trên `order_id`, nghĩa là 1 order có thể có nhiều payment (phù hợp với thực tế: thanh toán thất bại rồi thử lại); `payment_method` NOT NULL; `payment_reference` **UNIQUE** toàn hệ thống (tốt cho việc chống trùng lặp giao dịch/idempotency với payment gateway); `amount`; `status ENUM('pending','paid','failed')`; `paid_at` nullable.

### `reviews` (Product Review)
PK `id`; FK `user_id`, `product_id` NOT NULL (CASCADE); `rating TINYINT` (comment ghi "1-5" nhưng **không có CHECK constraint** ở DB); `comment` nullable.
⚠️ Không có UNIQUE `(user_id, product_id)` → 1 user có thể review 1 sản phẩm nhiều lần.
⚠️ Không có cột liên kết tới `order_items` → **schema không thể tự enforce "verified purchase"** ở tầng DB, phải kiểm tra bằng query ở Service.

### `vendor_reviews`
Tương tự `reviews` nhưng review cho `vendor_id` thay vì `product_id`. Cùng các hạn chế: không UNIQUE, không CHECK rating, không cách nào xác minh đã mua hàng của vendor đó.

---

## 1.3. Bảng quan hệ (Relationship Analysis)

| Quan hệ | Loại | JPA Mapping đề xuất | Ghi chú |
|---|---|---|---|
| User ↔ Vendor | 1–1 (ý định) | `@OneToOne` trên Vendor (owning side, FK `user_id`) | DB chưa UNIQUE → cần bổ sung ràng buộc ở Service (xem 1.5) |
| User → Cart | 1–1 theo nghiệp vụ (find-or-create) | Map như `@OneToOne` phía Cart, hoặc đơn giản là `@ManyToOne` + Service enforce duy nhất 1 cart/user | DB không UNIQUE `user_id` |
| User → Orders | 1–N | `@ManyToOne` ở Order → User (không cần bidirectional) | |
| User → CustomerAddresses | 1–N | `@ManyToOne` ở Address → User | |
| User → Wishlists | 1–N (association entity) | `@ManyToOne` ở Wishlist → User | Không dùng `@ManyToMany` vì có thêm cột `created_at` cần giữ |
| User → Reviews / VendorReviews / CouponUsers | 1–N | `@ManyToOne` mỗi entity → User | |
| Vendor → Products | 1–N | `@OneToMany`(mappedBy="vendor") / `@ManyToOne` | |
| Vendor → VendorReviews | 1–N | `@ManyToOne` ở VendorReview → Vendor | |
| Category → Category (self-reference) | 1–N (cây phân cấp) | `@ManyToOne` "parent" + `@OneToMany` "children" cùng entity | Cẩn thận CASCADE delete (xem 1.5) |
| Category → Products | 1–N, nullable | `@ManyToOne` optional | |
| Product → ProductVariant | 1–N (sở hữu chặt) | `@OneToMany(mappedBy="product", cascade=ALL, orphanRemoval=true)` | Variant không tồn tại độc lập ngoài Product |
| Product → ProductImage | 1–N (sở hữu chặt) | `@OneToMany(cascade=ALL, orphanRemoval=true)` | |
| Product → CartItem / OrderItem / Review / Wishlist | 1–N | `@ManyToOne` phía con | |
| ProductVariant → CartItem (nullable) / OrderItem (not null) | 1–N | `@ManyToOne` phía con | |
| Cart → CartItem | 1–N (sở hữu chặt) | `@OneToMany(cascade=ALL, orphanRemoval=true)` | Xoá cart → xoá luôn cart items |
| Order → OrderItem | 1–N (bất biến sau khi tạo) | `@OneToMany(cascade=PERSIST)` **không** dùng `orphanRemoval` | Order item không nên bị xoá/sửa sau khi order đã tạo |
| Order → Payment | **1–N** (không phải 1–1!) | `@OneToMany(mappedBy="order")` | Vì DB cho phép nhiều payment attempt/order (retry sau khi fail) |
| Coupon → CouponUser | 1–N | `@ManyToOne` phía CouponUser → Coupon | |

**Không có quan hệ N–N thuần túy nào cần `@ManyToMany` + `@JoinTable`.** Mọi mối quan hệ "nhiều-nhiều" trong hệ thống này (User↔Product qua Wishlist, User↔Coupon qua CouponUser) đều có bảng trung gian mang thêm dữ liệu riêng (timestamps, `times_used`) → nên luôn model thành **entity độc lập** (association entity) thay vì `@ManyToMany`, vì `@ManyToMany` của JPA không hỗ trợ tốt cột phụ trên bảng join.

---

## 1.4. Phát hiện vấn đề Schema

### Nhóm 1 — Có thể giữ nguyên (thiết kế đã tốt)
- Snapshot địa chỉ đầy đủ trong `orders` — đảm bảo đơn hàng cũ không bị ảnh hưởng khi khách sửa/xoá địa chỉ.
- Snapshot `product_name`, `variant_name`, `price` trong `order_items` — đã giải quyết đúng yêu cầu "lưu giá tại thời điểm mua".
- `orders.coupon_code` lưu dạng string thay vì FK — tách rời vòng đời Order khỏi Coupon, đúng.
- `payments.payment_reference` UNIQUE — hỗ trợ idempotency khi tích hợp payment gateway sau này.
- `products.is_active` — cơ chế soft-hide sẵn có, sẽ dùng thay cho hard delete.
- `role` enum trên `users` — đủ dùng cho 3 vai trò cố định hiện tại (không cần bảng `roles` riêng vì không có yêu cầu multi-role/user).

### Nhóm 2 — Nên cải thiện (bổ sung ràng buộc/index, không phá vỡ nghiệp vụ hiện tại; có thể làm bằng migration nhỏ hoặc trước mắt enforce ở tầng Service nếu chưa muốn đổi DB)
| Vấn đề | Đề xuất |
|---|---|
| `vendors.user_id` không UNIQUE | Thêm UNIQUE index, hoặc tối thiểu check "user đã có vendor chưa" ở Service trước khi tạo |
| `categories.slug` không UNIQUE | Thêm UNIQUE index để dùng cho URL và tránh trùng |
| `product_variants.sku` không UNIQUE | Thêm UNIQUE index (cho phép NULL nhiều lần nếu MySQL) |
| `cart_items` không UNIQUE `(cart_id, product_id, product_variant_id)` | Enforce ở Service: nếu đã tồn tại dòng trùng thì cộng dồn `quantity` thay vì tạo dòng mới |
| `wishlists` không UNIQUE `(user_id, product_id)` | Enforce ở Service: kiểm tra tồn tại trước khi insert |
| `coupon_users` không UNIQUE `(coupon_id, user_id)` | Enforce ở Service: dùng pattern "find-or-create" khi tăng `times_used` |
| `customer_addresses.is_default` không có ràng buộc "chỉ 1 default/user" | Xử lý trong transaction: set default mới → unset toàn bộ default cũ của cùng user |
| Thiếu index trên `orders.status`, `products.is_active` | Cân nhắc thêm composite index khi có dữ liệu lớn (không khẩn cấp ở giai đoạn đầu) |
| Không có CHECK constraint cho `rating` (1–5) trên `reviews`/`vendor_reviews` | Enforce bằng Bean Validation `@Min(1) @Max(5)` ở tầng DTO/Entity |

### Nhóm 3 — Cần cân nhắc kỹ trước khi code (ảnh hưởng lớn đến business logic, KHÔNG tự sửa DB nếu chưa xác nhận)
1. **`order_items.product_variant_id` NOT NULL vs `cart_items.product_variant_id` nullable.**
   Đây là mâu thuẫn cốt lõi cần quyết định trước khi code Product/Cart/Order:
   - **Phương án A (khuyến nghị, không cần đổi schema)**: Quy ước **mọi Product đều phải có ít nhất 1 ProductVariant**. Khi Vendor tạo sản phẩm "đơn giản" (không có option màu/size), hệ thống **tự động sinh 1 variant mặc định** (`name="Default"`, `price` = giá product, `stock` = tồn kho product). Từ đó, `product.price`/`product.stock` chỉ còn vai trò hiển thị/giá trị mặc định ban đầu, còn **ProductVariant luôn là nguồn sự thật (source of truth) cho giá & tồn kho khi thêm giỏ hàng và đặt hàng**. `cart_items.product_variant_id` vẫn nullable ở DB nhưng ứng dụng **luôn** gán variant id thực tế.
   - **Phương án B (cần migration)**: Đổi `order_items.product_variant_id` thành nullable để khớp với `cart_items`. Không thực hiện nếu chưa được xác nhận.
   → *Plan này triển khai theo Phương án A.*

2. **FK `order_items.product_id`/`product_variant_id` là ON DELETE CASCADE.** Nếu Vendor/Admin xoá cứng 1 sản phẩm hoặc variant đã từng bán, lịch sử đơn hàng liên quan sẽ mất. **Quy tắc bắt buộc ở tầng Service**: **không bao giờ hard-delete** Product/ProductVariant đã từng xuất hiện trong `order_items` — chỉ cho phép set `is_active = false` (ẩn khỏi storefront). API xoá thật (nếu có) chỉ áp dụng cho sản phẩm/variant chưa từng phát sinh đơn hàng.

3. **Quan hệ Order–Payment nên là 1–N chứ không phải 1–1.** Cần quyết định chính sách: mỗi lần thanh toán thất bại có tạo bản ghi `payment` mới hay update lại bản ghi cũ? → Khuyến nghị: **tạo bản ghi mới mỗi lần thử thanh toán** để giữ lịch sử/audit trail đầy đủ; Order sẽ có 1 payment "hiện hành" (mới nhất hoặc `status=paid`).

4. **Không thể enforce "verified purchase" ở tầng DB** cho `reviews`/`vendor_reviews` (không có FK tới `order_items`). Phải kiểm tra bằng query nghiệp vụ ở Service: user có đơn hàng `status=delivered` chứa `product_id`/vendor đó hay không.

5. **`coupons` chỉ có `usage_limit` toàn hệ thống, không có giới hạn số lần dùng/user riêng biệt** (`coupon_users.times_used` chỉ để đếm, không có cột giới hạn). Nếu nghiệp vụ cần "mỗi user chỉ dùng coupon này tối đa N lần", schema hiện tại chưa hỗ trợ trực tiếp → **mặc định trong plan này: mỗi user chỉ được dùng 1 coupon 1 lần** (`times_used < 1`), có thể điều chỉnh khi có thêm cột `per_user_limit` sau này.

6. **`users` không có cột trạng thái (`is_active`/`status`)** — khác với `products` có `is_active`. Nếu cần tính năng Admin khoá tài khoản, schema hiện tại **chưa hỗ trợ** → tính năng này sẽ **không** nằm trong MVP trừ khi bổ sung cột (đã loại khỏi roadmap Phase Admin, ghi chú rõ trong phase tương ứng).

7. **1 user – nhiều Cart** (`carts.user_id` không UNIQUE) và **1 user – nhiều Vendor profile** (`vendors.user_id` không UNIQUE) đều là rủi ro logic. Plan này enforce "chỉ 1" ở tầng Service (find-or-create / check-before-create) cho tới khi có thể bổ sung UNIQUE index.

---

# PHẦN II — Kiến trúc Backend

## 2.1. Package Structure đề xuất

Chọn **package-by-feature** (đóng gói theo domain nghiệp vụ) thay vì package-by-layer thuần tuý, vì hệ thống có 12+ domain nghiệp vụ tương đối độc lập (Product, Cart, Order, Coupon...) — cách tổ chức này giúp mỗi domain tự chứa (Entity/DTO/Repository/Service/Controller riêng), dễ điều hướng, dễ mở rộng, và nếu sau này cần tách microservices thì ranh giới đã rõ sẵn.

```
com.example.ecommerce
├── EcommerceApplication.java
│
├── config/                 # AppConfig, CorsConfig, OpenApiConfig, JpaAuditingConfig
├── security/               # JwtService, JwtAuthenticationFilter, SecurityConfig,
│                           # CustomUserDetailsService, AuthenticationEntryPoint, AccessDeniedHandler
├── common/                 # BaseEntity (id, createdAt, updatedAt), ApiResponse wrapper,
│                           # PageResponse<T>, shared enums (nếu không thuộc riêng 1 domain)
├── exception/              # GlobalExceptionHandler + toàn bộ custom exceptions, ErrorResponse
│
├── auth/                   # AuthController, AuthService, RegisterRequest, LoginRequest, JwtResponse
├── user/                   # User entity, Role enum, UserRepository, UserService, UserController
├── vendor/                 # Vendor entity + CRUD stack
├── category/               # Category entity (self-reference) + CRUD stack
├── product/
│   ├── entity/              # Product, ProductVariant, ProductImage
│   ├── dto/
│   ├── repository/
│   ├── service/
│   └── controller/
├── cart/                   # Cart, CartItem + stack
├── wishlist/                # Wishlist + stack
├── address/                 # CustomerAddress + stack
├── coupon/                  # Coupon, CouponUser + stack
├── order/                   # Order, OrderItem + CheckoutService + stack
├── payment/                 # Payment + stack (chuẩn bị sẵn interface cho gateway sau này)
├── review/                  # Review, VendorReview + stack
└── admin/                   # AdminDashboardController (tổng hợp, không có entity riêng)
```

**Lý do chọn cấu trúc này:**
- Mỗi domain (vd `product/`) tự chứa đủ layer riêng → giảm phụ thuộc chéo, dễ code song song nhiều module.
- `product/` được chia thêm layer con vì có 3 entity liên quan chặt (Product, Variant, Image) — tránh 1 package phẳng quá nhiều file.
- `security/`, `config/`, `common/`, `exception/` là cross-cutting, dùng chung cho mọi domain.
- Không tạo package riêng cho "Admin" chứa entity — Admin chỉ là **role**, hầu hết API admin nằm ngay trong domain tương ứng (`/api/admin/products`, `/api/admin/coupons`...) và dùng `@PreAuthorize("hasRole('ADMIN')")`; package `admin/` chỉ chứa các endpoint tổng hợp thật sự riêng biệt (dashboard, thống kê).

---

# PHẦN III — Thiết kế chuyên sâu theo Domain

## 3.1. Authentication & Authorization

**Vai trò**: `ADMIN`, `VENDOR`, `CUSTOMER` (từ `users.role`).

**Register flow:**
`RegisterRequest {name, email, password, role∈{CUSTOMER,VENDOR}}` → validate email chưa tồn tại → hash password bằng `BCryptPasswordEncoder` → lưu `User`. Nếu `role=VENDOR`, yêu cầu thêm `shopName` (tạo `Vendor` profile cùng transaction). **`ADMIN` không thể tự đăng ký** — chỉ được tạo qua seed script hoặc bởi admin khác (nguyên tắc bảo mật quan trọng, không expose qua API public).

**Login flow:**
`LoginRequest{email, password}` → `AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)` → nếu thành công, `JwtService.generateToken(userDetails)` sinh JWT chứa `sub=email`, claim `role`, `userId`, thời hạn hết hạn → trả `JwtResponse{accessToken, tokenType="Bearer", expiresIn, user}`.

**Request có JWT:**
Client gửi header `Authorization: Bearer <token>` → `JwtAuthenticationFilter` (OncePerRequestFilter) trích token → validate chữ ký & hạn dùng → load `UserDetails` qua `CustomUserDetailsService` → set vào `SecurityContextHolder`.

**Thành phần cần xây:**
| Thành phần | Vai trò |
|---|---|
| `UserDetails` (UserPrincipal) | Bọc entity `User`, expose authority `ROLE_<ROLE>` |
| `UserDetailsService` | `loadUserByUsername(email)` |
| `PasswordEncoder` | Bean `BCryptPasswordEncoder` |
| `AuthenticationManager` | Lấy từ `AuthenticationConfiguration` |
| `JwtAuthenticationFilter` | Parse & validate JWT mỗi request |
| `SecurityFilterChain` | Stateless, CSRF disabled, cấu hình permitAll/hasRole theo endpoint |
| `JwtService`/`JwtUtil` | generate/parse/validate token |
| `AuthenticationEntryPoint` | Trả 401 JSON khi chưa xác thực |
| `AccessDeniedHandler` | Trả 403 JSON khi không đủ quyền |

**Bảng phân quyền (tổng quan):**

| Module | ADMIN | VENDOR | CUSTOMER | PUBLIC |
|---|:---:|:---:|:---:|:---:|
| Auth (register/login) | – | – | – | ✅ |
| Category (đọc) | ✅ | ✅ | ✅ | ✅ |
| Category (ghi) | ✅ | ❌ | ❌ | ❌ |
| Vendor (đọc) | ✅ | ✅ | ✅ | ✅ |
| Vendor (sửa hồ sơ) | ✅ (mọi vendor) | ✅ (của mình) | ❌ | ❌ |
| Product (đọc) | ✅ | ✅ | ✅ | ✅ |
| Product/Variant/Image (ghi) | ✅ (mọi sp) | ✅ (sp của mình) | ❌ | ❌ |
| Cart / Wishlist / Address | – | – | ✅ (của mình) | ❌ |
| Coupon (quản lý) | ✅ | ❌ | ❌ | ❌ |
| Coupon (validate khi checkout) | ✅ | ❌ | ✅ | ❌ |
| Order (xem) | ✅ (tất cả) | ✅ (đơn chứa sp của mình) | ✅ (của mình) | ❌ |
| Order (đổi status) | ✅ | ✅ (giới hạn: processing→shipped cho đơn của mình) | ❌ | ❌ |
| Order (huỷ) | ✅ | ❌ | ✅ (đơn của mình, khi còn pending/processing) | ❌ |
| Payment | ✅ (quản lý) | ❌ | ✅ (tạo/xem của mình) | ❌ |
| Review (đọc) | ✅ | ✅ | ✅ | ✅ |
| Review (ghi) | ✅ (xoá/kiểm duyệt) | ❌ | ✅ (của mình, đã mua hàng) | ❌ |
| Admin dashboard | ✅ | ❌ | ❌ | ❌ |

## 3.2. Product Domain — Business Rules

- `product.price`/`product.stock`: giá trị **mặc định/hiển thị ban đầu**, dùng để khởi tạo variant mặc định khi tạo sản phẩm không có option.
- `product_variant.price`/`product_variant.stock`: **nguồn sự thật duy nhất** cho giá bán thực tế và tồn kho thực tế.
- **Sản phẩm không có variant do người dùng khai báo** → hệ thống tự sinh 1 `ProductVariant` mặc định (`name="Default"`) ngay khi tạo Product, copy `price`/`stock` từ Product. Toàn bộ luồng Cart/Checkout **luôn thao tác trên `product_variant_id`**, không bao giờ trên Product trực tiếp.
- **Trừ kho**: luôn trừ ở `product_variant.stock`. `product.stock` (nếu vẫn hiển thị) chỉ là giá trị cache/tổng hợp, đồng bộ lại ở tầng Service mỗi khi variant thay đổi — không bắt buộc phải đồng bộ real-time nếu không phục vụ mục đích hiển thị/lọc.
- **Khi checkout, giá luôn lấy từ `product_variant.price` tại thời điểm xử lý** (server-side), tuyệt đối không dùng giá do client/cart gửi lên. Giá này sau đó được snapshot vào `order_items.price`.
- **Xoá sản phẩm/variant**: không hard-delete nếu đã từng có `order_items` tham chiếu (xem 1.5 mục 2) — chỉ cho phép set `is_active=false`.

## 3.3. Cart & Checkout Flow

1. **Thêm vào giỏ** (`CartService.addItem`): tìm hoặc tạo `Cart` của user → validate `variant` thuộc đúng `product` và đang active → nếu đã tồn tại `CartItem` cùng `(cart, product, variant)` thì cộng dồn `quantity`, ngược lại tạo mới. Kiểm tra sơ bộ `quantity ≤ variant.stock` (chỉ cảnh báo, kiểm tra chốt chặn thật sự diễn ra ở bước checkout).
2. **Cập nhật số lượng / Xoá item**: kiểm tra quyền sở hữu (cart item phải thuộc cart của chính user đang gọi API).
3. **Checkout** (`CheckoutService.checkout`, chạy trong `@Transactional`):
   - Lấy `Cart` + `CartItem` của user, báo lỗi nếu rỗng.
   - Với **từng dòng**: khoá dòng `ProductVariant` bằng **Pessimistic Write Lock** (`SELECT ... FOR UPDATE` qua `@Lock(LockModeType.PESSIMISTIC_WRITE)`) để tránh race condition khi nhiều request checkout cùng lúc; kiểm tra `variant.isActive` và `variant.stock ≥ quantity` → không đủ thì ném `OutOfStockException` (nêu rõ tên sản phẩm).
   - Tính `lineTotal = variant.price × quantity` (giá lấy từ DB, không tin giá từ client) → cộng dồn `subtotal`.
   - Nếu có `couponCode`: gọi `CouponService.validate(...)` → tính `discountAmount`.
   - `total = subtotal − discountAmount` (không âm).
   - Tạo `Order`: snapshot địa chỉ từ `CustomerAddress` được chọn, `status=PENDING`, `total`, `couponCode`, `discountAmount`.
   - Tạo từng `OrderItem`: snapshot `productName`, `variantName`, `price`, `quantity`.
   - Trừ `variant.stock -= quantity`.
   - Nếu dùng coupon: tăng `coupon.used`, upsert `CouponUser.timesUsed`.
   - Tạo `Payment` với `status=PENDING`, `paymentMethod` do client chọn, sinh `paymentReference` (UUID tạm thời cho tới khi tích hợp gateway thật).
   - Xoá toàn bộ `CartItem` của cart (giỏ hàng được làm sạch — không ảnh hưởng `Order` vì dữ liệu đã snapshot).
   - Commit → trả `OrderResponse`.
4. **Đảm bảo an toàn**: không mua vượt tồn kho (pessimistic lock), không tin giá client, transaction bao trọn toàn bộ bước 3, cart cũ không thể làm thay đổi order đã tạo (nhờ snapshot).

## 3.4. Order & Payment — State Machine

**Order**: `PENDING → PROCESSING → SHIPPED → DELIVERED` (đường thẳng), hoặc `PENDING/PROCESSING → CANCELLED`. `DELIVERED` và `CANCELLED` là trạng thái kết thúc (terminal), không cho chuyển tiếp.

**Payment**: `PENDING → PAID` hoặc `PENDING → FAILED`. Nếu `FAILED`, cho phép tạo **bản ghi Payment mới** để thử lại (không sửa lại bản ghi cũ, giữ lịch sử).

**Liên kết Order ↔ Payment**: với phương thức thanh toán online, `Order` chỉ được chuyển sang `PROCESSING` khi có `Payment.status=PAID` tương ứng. Với `COD` (thanh toán khi nhận hàng), `Order` vẫn được xử lý bình thường dù `Payment` còn `PENDING` đến khi giao hàng. Quy tắc này nên cấu hình được (theo `payment_method`), xử lý trong `OrderService`.

**Chuẩn bị cho tích hợp gateway (Stripe...) sau này**: thiết kế `PaymentService` với 1 interface trừu tượng (`PaymentGateway`) mà bản triển khai hiện tại là "manual/simulate" — sau này chỉ cần thêm implementation mới (`StripePaymentGateway`) mà không phải sửa `OrderService`/`CheckoutService`.

## 3.5. Coupon Logic

`CouponService.validate(code, userId, orderSubtotal)`:
1. Tìm coupon theo `code` → không có → `InvalidCouponException`.
2. Kiểm tra thời điểm hiện tại nằm trong `[valid_from, valid_until]` (nếu có set).
3. Kiểm tra `coupon.used < coupon.usage_limit` (nếu `usage_limit` khác null).
4. Kiểm tra `orderSubtotal ≥ coupon.min_order_amount` (nếu có).
5. Tìm/khởi tạo `CouponUser(coupon, user)` → kiểm tra `timesUsed < 1` (chính sách mặc định: mỗi user dùng 1 coupon tối đa 1 lần — xem lý do ở mục 1.5, nhóm 3, điểm 5).
6. Tính `discount = type==FIXED ? value : subtotal × value/100`, giới hạn không vượt quá `subtotal`.

Coupon **chỉ được áp dụng và validate tại thời điểm checkout**, không lưu trên Cart (khớp với schema hiện tại — `carts` không có cột coupon).

## 3.6. Review Logic

- **Product Review**: chỉ `CUSTOMER`. Trước khi cho tạo, kiểm tra: user có `OrderItem` (qua `Order.status=DELIVERED`) chứa `product_id` này không → không có thì từ chối ("verified purchase"). Enforce 1 review/user/product ở tầng Service (do DB thiếu UNIQUE). `rating` validate `@Min(1) @Max(5)`.
- **Vendor Review**: tương tự, kiểm tra user đã có đơn `DELIVERED` chứa ít nhất 1 sản phẩm của vendor đó.
- Nếu schema chưa đủ để enforce "verified purchase" ở DB — điều này **đúng, đã ghi nhận ở mục 1.5** — enforce hoàn toàn bằng query nghiệp vụ, không phải ràng buộc CSDL.

## 3.7. DTO Design (tổng quan)

Nguyên tắc: **không bao giờ expose Entity trực tiếp**. Mỗi domain có tối thiểu Request DTO (Create/Update) và Response DTO.

| Domain | DTO chính |
|---|---|
| Auth | `RegisterRequest`, `RegisterVendorRequest`, `LoginRequest`, `JwtResponse` |
| User | `UserResponse`, `UpdateProfileRequest`, `ChangePasswordRequest` |
| Vendor | `VendorCreateRequest`, `VendorUpdateRequest`, `VendorResponse` |
| Category | `CategoryCreateRequest`, `CategoryUpdateRequest`, `CategoryResponse` (có `children` lồng) |
| Product | `ProductCreateRequest`, `ProductUpdateRequest`, `ProductResponse`, `ProductListItemResponse`, `ProductVariantRequest/Response`, `ProductImageRequest/Response` |
| Cart | `AddCartItemRequest`, `UpdateCartItemRequest`, `CartResponse`, `CartItemResponse` |
| Wishlist | `WishlistResponse` |
| Address | `AddressCreateRequest`, `AddressUpdateRequest`, `AddressResponse` |
| Coupon | `CouponCreateRequest`, `CouponUpdateRequest`, `CouponResponse`, `ApplyCouponRequest`, `CouponValidationResponse` |
| Order | `CheckoutRequest`, `OrderResponse`, `OrderItemResponse`, `OrderStatusUpdateRequest` |
| Payment | `PaymentResponse`, `CreatePaymentRequest`, `PaymentStatusUpdateRequest` |
| Review | `ReviewCreateRequest`, `ReviewResponse`, `VendorReviewCreateRequest`, `VendorReviewResponse` |

## 3.8. Exception Handling

`@RestControllerAdvice GlobalExceptionHandler` xử lý tập trung:

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `BadRequestException` | 400 |
| `UnauthorizedException` | 401 |
| `ForbiddenException` | 403 |
| `MethodArgumentNotValidException` (Bean Validation) | 400 (kèm danh sách field lỗi) |
| `DuplicateResourceException` | 409 |
| `OutOfStockException` | 409 |
| `InvalidCouponException` | 400 |
| `PaymentException` | 402 / 400 |

**Format lỗi thống nhất:**
```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id 12",
  "path": "/api/products/12",
  "errors": []
}
```

---

# PHẦN IV — Development Roadmap

> Thứ tự phase tuân theo dependency thực tế: Setup → User → Auth (vì hầu hết module sau đều cần bảo mật) → Category/Vendor (độc lập, không phụ thuộc nhau) → Product (phụ thuộc cả hai) → Cart/Wishlist/Address (phụ thuộc Product & User) → Coupon (độc lập nhưng cần trước Order) → Order/Checkout → Payment → Review (cần Order để verify purchase) → Admin → Hardening/Testing/Docs/Docker.

## Phase 0 — Project Setup & Core Infrastructure
**Mục tiêu**: Khởi tạo project chạy được, kết nối DB, cấu hình nền tảng dùng chung cho toàn bộ các phase sau.
**Dependencies**: Không có.
**Modules**: `config`, `common`, `exception` (khung sườn).
**Entities**: `BaseEntity` (abstract: `id`, `createdAt`, `updatedAt` với `@CreatedDate`/`@LastModifiedDate`).
**DTOs**: `ApiResponse<T>`, `ErrorResponse`, `PageResponse<T>`.
**Repositories**: —
**Services**: —
**Controllers**: —
**APIs**: —
**Business Logic**: Cấu hình `application.yml` (datasource MySQL, JPA `ddl-auto=validate` sau khi có migration, hoặc `update` tạm thời ở dev), bật `@EnableJpaAuditing`.
**Validation**: Cấu hình Bean Validation starter.
**Security**: Chưa có (sẽ ở Phase 2).
**Testing**: Test context load (`@SpringBootTest` smoke test).
**Definition of Done**: `mvn spring-boot:run` chạy thành công, kết nối MySQL OK, health-check endpoint trả 200, cấu trúc package đã tạo đủ.

## Phase 1 — User & Role Foundation
**Mục tiêu**: Có Entity `User` và các thao tác cơ bản (không gồm login) để Phase 2 (Auth) dùng.
**Dependencies**: Phase 0.
**Modules**: `user`.
**Entities**: `User` (map bảng `users`, enum `Role{ADMIN,VENDOR,CUSTOMER}` — **không** map `remember_token`, `email_verified_at` có thể giữ để dành).
**DTOs**: `UserResponse`, `UpdateProfileRequest`, `ChangePasswordRequest`.
**Repositories**: `UserRepository` (`findByEmail`, `existsByEmail`).
**Services**: `UserService` — CRUD hồ sơ, đổi mật khẩu (verify mật khẩu cũ trước khi đổi).
**Controllers**: `UserController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/users/me | Authenticated | Lấy hồ sơ bản thân |
| PUT | /api/users/me | Authenticated | Cập nhật hồ sơ |
| PUT | /api/users/me/password | Authenticated | Đổi mật khẩu |
| GET | /api/admin/users | ADMIN | Danh sách user |
| GET | /api/admin/users/{id} | ADMIN | Chi tiết user |

**Business Logic**: Email không đổi được qua `UpdateProfileRequest` (đổi email cần flow riêng, ngoài scope). Đổi mật khẩu yêu cầu `oldPassword` đúng.
**Validation**: `name` không rỗng, password mới ≥ 8 ký tự.
**Security**: `/api/users/me/**` cho user đã đăng nhập bất kỳ role nào; `/api/admin/**` chỉ ADMIN (áp dụng thật sự từ Phase 2 khi có SecurityFilterChain — Phase này chỉ định nghĩa endpoint, gắn `@PreAuthorize` sẵn).
**Testing**: Unit test `UserService` (đổi mật khẩu sai/đúng), Repository test `findByEmail`.
**Definition of Done**: CRUD hồ sơ hoạt động qua Postman (tạm thời chưa cần JWT thật, có thể test trực tiếp Service/Repository).

## Phase 2 — Authentication & Security (JWT)
**Mục tiêu**: Đăng ký, đăng nhập, bảo vệ toàn bộ API bằng JWT + phân quyền theo role.
**Dependencies**: Phase 1.
**Modules**: `auth`, `security`.
**Entities**: (dùng lại `User`).
**DTOs**: `RegisterRequest`, `RegisterVendorRequest`, `LoginRequest`, `JwtResponse`.
**Repositories**: (dùng lại `UserRepository`).
**Services**: `AuthService` (register, login), `JwtService` (generate/validate token), `CustomUserDetailsService`.
**Controllers**: `AuthController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/register | PUBLIC | Đăng ký customer |
| POST | /api/auth/register-vendor | PUBLIC | Đăng ký vendor (tạo User + Vendor) |
| POST | /api/auth/login | PUBLIC | Đăng nhập, trả JWT |

**Business Logic**: Hash password bằng BCrypt trước khi lưu; email trùng → `DuplicateResourceException`; JWT chứa `userId`, `email`, `role`; hạn token cấu hình qua `application.yml` (vd 24h).
**Validation**: Email đúng định dạng, password ≥ 8 ký tự, `role` chỉ nhận CUSTOMER/VENDOR ở endpoint public (ADMIN bị chặn cứng trong code, không đọc từ input).
**Security**: Thiết lập `SecurityFilterChain` đầy đủ — `permitAll` cho `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, GET công khai cho `products`/`categories`/`vendors`/`reviews`; còn lại yêu cầu xác thực + role tương ứng.
**Testing**: Test đăng ký trùng email, login sai mật khẩu, login đúng trả JWT hợp lệ, test filter chặn request không có token, test `@PreAuthorize` chặn sai role (Security Test).
**Definition of Done**: Đăng ký/login qua Postman thành công; dùng JWT gọi được endpoint bảo vệ; gọi sai role bị 403; không có token bị 401.

## Phase 3 — Category Management
**Mục tiêu**: CRUD danh mục dạng cây phân cấp (self-reference).
**Dependencies**: Phase 2.
**Modules**: `category`.
**Entities**: `Category` (self-reference `parent`/`children`).
**DTOs**: `CategoryCreateRequest`, `CategoryUpdateRequest`, `CategoryResponse` (đệ quy `children`).
**Repositories**: `CategoryRepository` (`findByParentIsNull` để lấy cây gốc).
**Services**: `CategoryService` — CRUD + build cây; cảnh báo khi xoá category có children hoặc có sản phẩm (do FK CASCADE sẽ xoá luôn children — cần confirm rõ ràng ở tầng API, ví dụ trả lỗi 409 nếu còn children/products thay vì xoá âm thầm theo cascade).
**Controllers**: `CategoryController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/categories | PUBLIC | Danh sách (dạng cây hoặc phẳng) |
| GET | /api/categories/{id} | PUBLIC | Chi tiết |
| POST | /api/categories | ADMIN | Tạo mới |
| PUT | /api/categories/{id} | ADMIN | Cập nhật |
| DELETE | /api/categories/{id} | ADMIN | Xoá (chặn nếu còn children/products) |

**Business Logic**: Ngăn set `parent_id` trỏ về chính nó hoặc tạo vòng lặp cha-con.
**Validation**: `name` bắt buộc; `parent_id` (nếu có) phải tồn tại.
**Security**: Đọc public, ghi chỉ ADMIN.
**Testing**: Test tạo cây 3 cấp, test chặn vòng lặp, test chặn xoá khi còn con.
**Definition of Done**: CRUD category hoạt động đầy đủ kèm validate self-reference.

## Phase 4 — Vendor Management
**Mục tiêu**: Quản lý hồ sơ Vendor (gắn liền User role=VENDOR).
**Dependencies**: Phase 2.
**Modules**: `vendor`.
**Entities**: `Vendor`.
**DTOs**: `VendorUpdateRequest`, `VendorResponse`.
**Repositories**: `VendorRepository` (`findByUserId`).
**Services**: `VendorService` — enforce "1 user chỉ có 1 vendor" ở tầng Service (do DB thiếu UNIQUE — xem mục 1.5).
**Controllers**: `VendorController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/vendors | PUBLIC | Danh sách vendor (storefront) |
| GET | /api/vendors/{id} | PUBLIC | Trang vendor |
| GET | /api/vendors/me | VENDOR | Hồ sơ của mình |
| PUT | /api/vendors/me | VENDOR | Cập nhật hồ sơ |
| GET | /api/admin/vendors | ADMIN | Quản lý toàn bộ vendor |
| DELETE | /api/admin/vendors/{id} | ADMIN | Gỡ vendor (cảnh báo cascade xoá luôn products) |

**Business Logic**: Vendor profile được tạo cùng lúc với đăng ký (Phase 2) — Phase này chỉ xử lý xem/sửa/xoá.
**Validation**: `shop_name` bắt buộc.
**Security**: Đọc public; sửa hồ sơ chỉ chính vendor đó; xoá chỉ ADMIN.
**Testing**: Test vendor A không sửa được hồ sơ vendor B (ownership check).
**Definition of Done**: Vendor tự quản lý hồ sơ, Admin quản lý toàn bộ vendor.

## Phase 5 — Product Domain (Product, Variant, Image)
**Mục tiêu**: CRUD sản phẩm đầy đủ, áp dụng business rule variant mặc định (mục 3.2).
**Dependencies**: Phase 3, Phase 4.
**Modules**: `product`.
**Entities**: `Product`, `ProductVariant`, `ProductImage`.
**DTOs**: `ProductCreateRequest`, `ProductUpdateRequest`, `ProductResponse`, `ProductListItemResponse`, `ProductVariantRequest/Response`, `ProductImageRequest/Response`.
**Repositories**: `ProductRepository` (search/filter theo category, vendor, keyword, khoảng giá, phân trang — dùng `Specification` hoặc query method), `ProductVariantRepository` (có method `@Lock(PESSIMISTIC_WRITE)` cho checkout), `ProductImageRepository`.
**Services**: `ProductService` (CRUD, tự sinh default variant khi tạo sản phẩm không kèm variant), `ProductVariantService`, `ProductImageService`.
**Controllers**: `ProductController` (kèm sub-resource variants/images).
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/products | PUBLIC | List + filter + search + pagination |
| GET | /api/products/{id} | PUBLIC | Chi tiết (kèm variants, images) |
| POST | /api/products | VENDOR | Tạo sản phẩm của mình |
| PUT | /api/products/{id} | VENDOR(own)/ADMIN | Cập nhật |
| DELETE | /api/products/{id} | VENDOR(own)/ADMIN | Set `is_active=false` (không hard-delete) |
| POST | /api/products/{id}/variants | VENDOR(own) | Thêm variant |
| PUT | /api/products/{id}/variants/{vid} | VENDOR(own) | Sửa variant |
| DELETE | /api/products/{id}/variants/{vid} | VENDOR(own) | Xoá variant (chặn nếu là variant cuối hoặc đã có order) |
| POST | /api/products/{id}/images | VENDOR(own) | Thêm ảnh |
| DELETE | /api/products/{id}/images/{iid} | VENDOR(own) | Xoá ảnh |
| PUT | /api/products/{id}/images/{iid}/main | VENDOR(own) | Đặt ảnh chính |

**Business Logic**: Ownership check (vendor chỉ sửa được sản phẩm của chính mình, trừ ADMIN); auto-tạo variant mặc định (mục 3.2); chặn xoá cứng product/variant đã từng có `order_items` tham chiếu.
**Validation**: `price ≥ 0`, `stock ≥ 0`, `name` bắt buộc.
**Security**: Đọc public; ghi VENDOR (chỉ own) hoặc ADMIN (toàn quyền).
**Testing**: Test auto-default-variant, test vendor A không sửa được sản phẩm vendor B, test filter/search/pagination, test chặn xoá sản phẩm đã bán.
**Definition of Done**: Toàn bộ CRUD Product/Variant/Image hoạt động, business rule "1 sản phẩm ≥ 1 variant" luôn đúng.

## Phase 6 — Cart & Cart Item
**Mục tiêu**: Giỏ hàng hoạt động đầy đủ, dùng làm nền cho Checkout ở Phase 10.
**Dependencies**: Phase 5.
**Modules**: `cart`.
**Entities**: `Cart`, `CartItem`.
**DTOs**: `AddCartItemRequest`, `UpdateCartItemRequest`, `CartResponse`, `CartItemResponse`.
**Repositories**: `CartRepository` (`findByUserId`), `CartItemRepository` (`findByCartIdAndProductIdAndProductVariantId`).
**Services**: `CartService` — find-or-create cart, cộng dồn quantity nếu trùng dòng, validate stock sơ bộ.
**Controllers**: `CartController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/cart | CUSTOMER | Xem giỏ hàng |
| POST | /api/cart/items | CUSTOMER | Thêm sản phẩm/variant |
| PUT | /api/cart/items/{id} | CUSTOMER | Cập nhật số lượng |
| DELETE | /api/cart/items/{id} | CUSTOMER | Xoá 1 item |
| DELETE | /api/cart | CUSTOMER | Xoá toàn bộ giỏ |

**Business Logic**: Ownership check cart item thuộc đúng user; validate `quantity ≤ variant.stock` (cảnh báo mềm, chốt chặn thật ở checkout).
**Validation**: `quantity ≥ 1`.
**Security**: Chỉ CUSTOMER (Vendor/Admin không có giỏ hàng theo phạm vi hiện tại — có thể mở rộng nếu cần).
**Testing**: Test cộng dồn quantity khi thêm trùng, test vượt tồn kho, test ownership.
**Definition of Done**: Toàn bộ luồng thêm/sửa/xoá giỏ hàng hoạt động đúng.

## Phase 7 — Wishlist
**Mục tiêu**: Danh sách yêu thích.
**Dependencies**: Phase 5.
**Modules**: `wishlist`.
**Entities**: `Wishlist`.
**DTOs**: `WishlistResponse`.
**Repositories**: `WishlistRepository` (`existsByUserIdAndProductId`).
**Services**: `WishlistService` — chặn trùng ở tầng Service (do DB thiếu UNIQUE).
**Controllers**: `WishlistController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/wishlist | CUSTOMER | Xem danh sách |
| POST | /api/wishlist/{productId} | CUSTOMER | Thêm |
| DELETE | /api/wishlist/{productId} | CUSTOMER | Xoá |

**Business Logic**: Idempotent — thêm lại sản phẩm đã có trong wishlist không tạo dòng trùng.
**Security**: Chỉ CUSTOMER, chỉ thao tác trên wishlist của chính mình.
**Testing**: Test thêm trùng không tạo bản ghi mới.
**Definition of Done**: Thêm/xem/xoá wishlist hoạt động, không trùng lặp.

## Phase 8 — Customer Address
**Mục tiêu**: Quản lý sổ địa chỉ giao hàng.
**Dependencies**: Phase 2.
**Modules**: `address`.
**Entities**: `CustomerAddress`.
**DTOs**: `AddressCreateRequest`, `AddressUpdateRequest`, `AddressResponse`.
**Repositories**: `CustomerAddressRepository` (`findByUserId`).
**Services**: `AddressService` — khi set `isDefault=true`, transaction unset toàn bộ default cũ của user trước.
**Controllers**: `AddressController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/addresses | CUSTOMER | Danh sách địa chỉ |
| POST | /api/addresses | CUSTOMER | Thêm |
| PUT | /api/addresses/{id} | CUSTOMER | Sửa |
| DELETE | /api/addresses/{id} | CUSTOMER | Xoá |
| PUT | /api/addresses/{id}/default | CUSTOMER | Đặt làm mặc định |

**Business Logic**: Đảm bảo tại một thời điểm chỉ có tối đa 1 địa chỉ `is_default=true`/user.
**Validation**: Các field địa chỉ bắt buộc theo schema (`address_line1`, `city`, `country`, `phone_number`...).
**Security**: Chỉ CUSTOMER, chỉ thao tác trên địa chỉ của chính mình.
**Testing**: Test set default → các địa chỉ khác tự động unset.
**Definition of Done**: CRUD địa chỉ hoạt động, luôn đúng 1 default.

## Phase 9 — Coupon
**Mục tiêu**: Quản lý mã giảm giá + endpoint validate trước checkout.
**Dependencies**: Phase 2 (Admin quản lý), độc lập với Cart nhưng cần xong trước Phase 10.
**Modules**: `coupon`.
**Entities**: `Coupon`, `CouponUser`.
**DTOs**: `CouponCreateRequest`, `CouponUpdateRequest`, `CouponResponse`, `ApplyCouponRequest`, `CouponValidationResponse`.
**Repositories**: `CouponRepository` (`findByCode`), `CouponUserRepository` (`findByCouponIdAndUserId`).
**Services**: `CouponService` — implement đầy đủ logic validate ở mục 3.5 (tái sử dụng ở Phase 10 khi checkout).
**Controllers**: `CouponController` (Admin CRUD), `CouponValidationController` hoặc gộp chung.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/admin/coupons | ADMIN | Danh sách |
| POST | /api/admin/coupons | ADMIN | Tạo |
| PUT | /api/admin/coupons/{id} | ADMIN | Sửa |
| DELETE | /api/admin/coupons/{id} | ADMIN | Xoá/vô hiệu hoá |
| POST | /api/coupons/validate | CUSTOMER | Xem trước mức giảm giá dựa trên giỏ hàng hiện tại |

**Business Logic**: Xem mục 3.5 đầy đủ.
**Validation**: `code` UNIQUE, `value > 0`, nếu `type=percent` thì `value ≤ 100`.
**Security**: Quản lý chỉ ADMIN; validate cho CUSTOMER.
**Testing**: Test hết hạn, hết lượt, chưa đạt min order, đã dùng rồi (per-user).
**Definition of Done**: Toàn bộ rule coupon test pass, sẵn sàng dùng ở Checkout.

## Phase 10 — Order & Checkout
**Mục tiêu**: Luồng checkout hoàn chỉnh — phase quan trọng nhất, hiện thực hoá mục 3.3.
**Dependencies**: Phase 6, 8, 9.
**Modules**: `order`.
**Entities**: `Order`, `OrderItem`.
**DTOs**: `CheckoutRequest`, `OrderResponse`, `OrderItemResponse`, `OrderStatusUpdateRequest`.
**Repositories**: `OrderRepository` (filter theo user/vendor/status), `OrderItemRepository` (dùng cho check "verified purchase" ở Phase 12).
**Services**: `CheckoutService` (transaction chính, mục 3.3), `OrderService` (query, đổi status theo state machine mục 3.4).
**Controllers**: `OrderController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/orders/checkout | CUSTOMER | Tạo đơn từ giỏ hàng |
| GET | /api/orders | CUSTOMER(own)/VENDOR(đơn chứa sp mình)/ADMIN(tất cả) | Danh sách đơn |
| GET | /api/orders/{id} | CUSTOMER(own)/VENDOR(own)/ADMIN | Chi tiết |
| PUT | /api/orders/{id}/status | ADMIN/VENDOR(giới hạn transition) | Cập nhật trạng thái |
| PUT | /api/orders/{id}/cancel | CUSTOMER(own, khi còn pending/processing) | Huỷ đơn |

**Business Logic**: Toàn bộ mục 3.3 + state machine mục 3.4. Không cho cancel khi đã `shipped`/`delivered`.
**Validation**: Giỏ hàng không rỗng, địa chỉ hợp lệ, coupon hợp lệ (nếu có).
**Security**: Ownership check nghiêm ngặt (đặc biệt Vendor chỉ thấy đơn có chứa sản phẩm của mình — cần join qua `order_items.product.vendor`).
**Testing** (integration, ưu tiên cao): checkout thành công trừ đúng kho; checkout khi hết hàng bị chặn; 2 request checkout đồng thời cùng 1 variant sắp hết hàng (race condition) chỉ 1 request thành công; áp coupon đúng; huỷ đơn đúng state machine.
**Definition of Done**: Toàn bộ integration test checkout pass, không xảy ra oversell khi test tải đồng thời.

## Phase 11 — Payment
**Mục tiêu**: Ghi nhận thanh toán, chuẩn bị sẵn sàng tích hợp gateway thật.
**Dependencies**: Phase 10.
**Modules**: `payment`.
**Entities**: `Payment`.
**DTOs**: `PaymentResponse`, `CreatePaymentRequest`, `PaymentStatusUpdateRequest`.
**Repositories**: `PaymentRepository` (`findByOrderId`, `findByPaymentReference`).
**Services**: `PaymentService` (interface `PaymentGateway` trừu tượng cho tương lai — mục 3.4).
**Controllers**: `PaymentController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/orders/{orderId}/payments | CUSTOMER(own)/ADMIN | Lịch sử các lần thanh toán |
| POST | /api/orders/{orderId}/payments | CUSTOMER(own) | Thử thanh toán lại (nếu lần trước FAILED) |
| PUT | /api/admin/payments/{id}/status | ADMIN | Giả lập webhook gateway (đến khi tích hợp thật) |

**Business Logic**: Mỗi lần thử thanh toán tạo bản ghi mới (mục 3.4); khi `status=PAID`, đồng bộ `Order.status` sang `PROCESSING` (trừ COD).
**Validation**: `payment_reference` UNIQUE (đã có ở DB).
**Security**: Customer chỉ thao tác payment của đơn mình; cập nhật status thật do ADMIN (tạm thời, sau này webhook gateway sẽ thay).
**Testing**: Test retry sau FAILED tạo bản ghi mới; test đồng bộ Order status khi PAID.
**Definition of Done**: Luồng thanh toán giả lập hoạt động đầy đủ, sẵn sàng thay bằng gateway thật mà không đổi cấu trúc.

## Phase 12 — Reviews (Product & Vendor)
**Mục tiêu**: Đánh giá sản phẩm/vendor kèm kiểm tra "verified purchase".
**Dependencies**: Phase 10 (cần dữ liệu Order để verify).
**Modules**: `review`.
**Entities**: `Review`, `VendorReview`.
**DTOs**: `ReviewCreateRequest`, `ReviewResponse`, `VendorReviewCreateRequest`, `VendorReviewResponse`.
**Repositories**: `ReviewRepository` (`existsByUserIdAndProductId`), `VendorReviewRepository` (tương tự).
**Services**: `ReviewService`, `VendorReviewService` — implement mục 3.6.
**Controllers**: `ReviewController`, `VendorReviewController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/products/{id}/reviews | PUBLIC | Danh sách review sản phẩm |
| POST | /api/products/{id}/reviews | CUSTOMER (verified) | Tạo review |
| PUT | /api/reviews/{id} | CUSTOMER(own) | Sửa review |
| DELETE | /api/reviews/{id} | CUSTOMER(own)/ADMIN | Xoá |
| GET | /api/vendors/{id}/reviews | PUBLIC | Danh sách review vendor |
| POST | /api/vendors/{id}/reviews | CUSTOMER (verified) | Tạo review vendor |
| PUT/DELETE | /api/vendor-reviews/{id} | CUSTOMER(own)/ADMIN | Sửa/xoá |

**Business Logic**: Mục 3.6 — kiểm tra `Order.status=DELIVERED` chứa sản phẩm/vendor trước khi cho review; chặn review trùng ở Service.
**Validation**: `rating` 1–5.
**Security**: Đọc public; ghi CUSTOMER đã verified; xoá/kiểm duyệt ADMIN.
**Testing**: Test chặn review khi chưa mua hàng; chặn review trùng; test rating ngoài khoảng 1–5.
**Definition of Done**: Review chỉ tạo được bởi khách đã mua & nhận hàng thành công.

## Phase 13 — Admin Aggregation & Reporting
**Mục tiêu**: Các endpoint tổng hợp riêng cho Admin (không thuộc entity cụ thể).
**Dependencies**: Tất cả các phase trước.
**Modules**: `admin`.
**Entities**: — (query tổng hợp từ các repository đã có).
**DTOs**: `DashboardStatsResponse` (tổng user, tổng order, doanh thu theo khoảng thời gian...).
**Repositories**: dùng lại các repository hiện có + custom aggregate query (`@Query` với `COUNT`/`SUM`).
**Services**: `AdminDashboardService`.
**Controllers**: `AdminDashboardController`.
**APIs**:
| Method | URL | Role | Purpose |
|---|---|---|---|
| GET | /api/admin/dashboard/stats | ADMIN | Thống kê tổng quan |
| GET | /api/admin/orders | ADMIN | Toàn bộ đơn hàng, filter nâng cao |

**Ghi chú quan trọng**: Tính năng "khoá/mở tài khoản user" **không đưa vào phase này** vì bảng `users` hiện chưa có cột trạng thái (`is_active`/`status`) — xem mục 1.5, nhóm 3, điểm 6. Nếu cần, phải bổ sung cột trước.
**Business Logic**: Query tổng hợp, không có nghiệp vụ ghi dữ liệu mới.
**Security**: Toàn bộ ADMIN only.
**Testing**: Test tính đúng số liệu tổng hợp trên tập dữ liệu mẫu.
**Definition of Done**: Dashboard trả số liệu chính xác, khớp dữ liệu thực tế trong DB.

## Phase 14 — Global Exception Handling & Validation Hardening
**Mục tiêu**: Rà soát và hoàn thiện toàn bộ xử lý lỗi xuyên suốt hệ thống (khung đã dựng từ Phase 0, phase này là đợt rà soát cuối).
**Dependencies**: Tất cả các phase trước.
**Modules**: `exception`.
**Business Logic**: Đảm bảo mọi custom exception ở tất cả các phase đều được `GlobalExceptionHandler` bắt đúng, format lỗi nhất quán (mục 3.8); rà soát toàn bộ Bean Validation annotation trên các Request DTO.
**Testing**: Test từng loại exception trả đúng HTTP status & format.
**Definition of Done**: Không còn lỗi nào trả về dạng stacktrace mặc định của Spring; toàn bộ lỗi đi qua format chuẩn.

## Phase 15 — Testing Consolidation
**Mục tiêu**: Đảm bảo coverage đầy đủ theo Phần VI (Testing Strategy) trước khi release.
**Dependencies**: Tất cả các phase trước.
**Business Logic**: Rà soát lại toàn bộ Unit Test, Integration Test, Repository Test, Controller Test, Security Test đã liệt kê; bổ sung test còn thiếu, đặc biệt các flow trọng yếu (Checkout, Coupon, Payment, Review verified-purchase).
**Definition of Done**: Coverage đạt mức chấp nhận được cho các module lõi (Auth, Cart, Order, Payment); toàn bộ flow trọng yếu có Integration Test.

## Phase 16 — Swagger / OpenAPI Finalization
**Mục tiêu**: Tài liệu hoá toàn bộ API.
**Dependencies**: Tất cả các phase trước.
**Business Logic**: Cấu hình `springdoc-openapi`, gắn `@Operation`/`@ApiResponse` mô tả rõ cho từng endpoint, cấu hình `SecurityScheme` Bearer JWT để test trực tiếp trên Swagger UI.
**Definition of Done**: Truy cập `/swagger-ui.html` thấy đầy đủ API, test được endpoint có JWT ngay trên UI.

## Phase 17 — Dockerization & Deployment Prep
**Mục tiêu**: Đóng gói ứng dụng để triển khai.
**Dependencies**: Tất cả các phase trước.
**Business Logic**: Viết `Dockerfile` (multi-stage build: Maven build → JRE runtime), `docker-compose.yml` (app + MySQL), biến môi trường cho JWT secret/DB credentials (không hardcode).
**Definition of Done**: `docker-compose up` chạy được toàn bộ hệ thống từ máy sạch.

---

# PHẦN V — Master API Catalogue

> Bảng tổng hợp toàn bộ endpoint theo module, dùng làm tài liệu tra cứu nhanh (chi tiết nghiệp vụ xem lại Phần IV).

### AUTH
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| POST | /api/auth/register | PUBLIC | RegisterRequest | JwtResponse | Đăng ký customer |
| POST | /api/auth/register-vendor | PUBLIC | RegisterVendorRequest | JwtResponse | Đăng ký vendor + tạo hồ sơ |
| POST | /api/auth/login | PUBLIC | LoginRequest | JwtResponse | Đăng nhập |

### USERS
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/users/me | Authenticated | – | UserResponse | Hồ sơ bản thân |
| PUT | /api/users/me | Authenticated | UpdateProfileRequest | UserResponse | Cập nhật hồ sơ |
| PUT | /api/users/me/password | Authenticated | ChangePasswordRequest | – | Đổi mật khẩu |
| GET | /api/admin/users | ADMIN | – (query params) | Page\<UserResponse\> | Danh sách user |
| GET | /api/admin/users/{id} | ADMIN | – | UserResponse | Chi tiết user |

### VENDORS
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/vendors | PUBLIC | query params | Page\<VendorResponse\> | Danh sách vendor |
| GET | /api/vendors/{id} | PUBLIC | – | VendorResponse | Chi tiết |
| GET | /api/vendors/me | VENDOR | – | VendorResponse | Hồ sơ của mình |
| PUT | /api/vendors/me | VENDOR | VendorUpdateRequest | VendorResponse | Cập nhật |
| GET | /api/admin/vendors | ADMIN | query params | Page\<VendorResponse\> | Quản lý vendor |
| DELETE | /api/admin/vendors/{id} | ADMIN | – | – | Gỡ vendor |

### CATEGORIES
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/categories | PUBLIC | – | List\<CategoryResponse\> | Danh sách (cây) |
| GET | /api/categories/{id} | PUBLIC | – | CategoryResponse | Chi tiết |
| POST | /api/categories | ADMIN | CategoryCreateRequest | CategoryResponse | Tạo |
| PUT | /api/categories/{id} | ADMIN | CategoryUpdateRequest | CategoryResponse | Sửa |
| DELETE | /api/categories/{id} | ADMIN | – | – | Xoá (chặn nếu còn con) |

### PRODUCTS
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/products | PUBLIC | query params (search/filter/paging) | Page\<ProductListItemResponse\> | Danh sách |
| GET | /api/products/{id} | PUBLIC | – | ProductResponse | Chi tiết |
| POST | /api/products | VENDOR | ProductCreateRequest | ProductResponse | Tạo (tự sinh default variant) |
| PUT | /api/products/{id} | VENDOR(own)/ADMIN | ProductUpdateRequest | ProductResponse | Sửa |
| DELETE | /api/products/{id} | VENDOR(own)/ADMIN | – | – | Ẩn (is_active=false) |
| POST | /api/products/{id}/variants | VENDOR(own) | ProductVariantRequest | ProductVariantResponse | Thêm variant |
| PUT | /api/products/{id}/variants/{vid} | VENDOR(own) | ProductVariantRequest | ProductVariantResponse | Sửa variant |
| DELETE | /api/products/{id}/variants/{vid} | VENDOR(own) | – | – | Xoá variant (nếu chưa bán) |
| POST | /api/products/{id}/images | VENDOR(own) | ProductImageRequest | ProductImageResponse | Thêm ảnh |
| DELETE | /api/products/{id}/images/{iid} | VENDOR(own) | – | – | Xoá ảnh |
| PUT | /api/products/{id}/images/{iid}/main | VENDOR(own) | – | – | Đặt ảnh chính |

### CART
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/cart | CUSTOMER | – | CartResponse | Xem giỏ |
| POST | /api/cart/items | CUSTOMER | AddCartItemRequest | CartItemResponse | Thêm |
| PUT | /api/cart/items/{id} | CUSTOMER | UpdateCartItemRequest | CartItemResponse | Sửa số lượng |
| DELETE | /api/cart/items/{id} | CUSTOMER | – | – | Xoá 1 dòng |
| DELETE | /api/cart | CUSTOMER | – | – | Xoá toàn bộ |

### WISHLIST
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/wishlist | CUSTOMER | – | List\<WishlistResponse\> | Danh sách |
| POST | /api/wishlist/{productId} | CUSTOMER | – | WishlistResponse | Thêm |
| DELETE | /api/wishlist/{productId} | CUSTOMER | – | – | Xoá |

### ADDRESSES
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/addresses | CUSTOMER | – | List\<AddressResponse\> | Danh sách |
| POST | /api/addresses | CUSTOMER | AddressCreateRequest | AddressResponse | Thêm |
| PUT | /api/addresses/{id} | CUSTOMER | AddressUpdateRequest | AddressResponse | Sửa |
| DELETE | /api/addresses/{id} | CUSTOMER | – | – | Xoá |
| PUT | /api/addresses/{id}/default | CUSTOMER | – | AddressResponse | Đặt mặc định |

### COUPONS
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/admin/coupons | ADMIN | query params | Page\<CouponResponse\> | Danh sách |
| POST | /api/admin/coupons | ADMIN | CouponCreateRequest | CouponResponse | Tạo |
| PUT | /api/admin/coupons/{id} | ADMIN | CouponUpdateRequest | CouponResponse | Sửa |
| DELETE | /api/admin/coupons/{id} | ADMIN | – | – | Xoá/vô hiệu hoá |
| POST | /api/coupons/validate | CUSTOMER | ApplyCouponRequest | CouponValidationResponse | Xem trước mức giảm |

### ORDERS
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| POST | /api/orders/checkout | CUSTOMER | CheckoutRequest | OrderResponse | Tạo đơn |
| GET | /api/orders | CUSTOMER/VENDOR/ADMIN (phạm vi khác nhau) | query params | Page\<OrderResponse\> | Danh sách |
| GET | /api/orders/{id} | CUSTOMER(own)/VENDOR(own)/ADMIN | – | OrderResponse | Chi tiết |
| PUT | /api/orders/{id}/status | ADMIN/VENDOR(giới hạn) | OrderStatusUpdateRequest | OrderResponse | Đổi trạng thái |
| PUT | /api/orders/{id}/cancel | CUSTOMER(own) | – | OrderResponse | Huỷ đơn |

### PAYMENTS
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/orders/{orderId}/payments | CUSTOMER(own)/ADMIN | – | List\<PaymentResponse\> | Lịch sử thanh toán |
| POST | /api/orders/{orderId}/payments | CUSTOMER(own) | CreatePaymentRequest | PaymentResponse | Thử thanh toán (lại) |
| PUT | /api/admin/payments/{id}/status | ADMIN | PaymentStatusUpdateRequest | PaymentResponse | Giả lập webhook |

### REVIEWS
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/products/{id}/reviews | PUBLIC | query params | Page\<ReviewResponse\> | Danh sách |
| POST | /api/products/{id}/reviews | CUSTOMER (verified) | ReviewCreateRequest | ReviewResponse | Tạo review sản phẩm |
| PUT | /api/reviews/{id} | CUSTOMER(own) | ReviewCreateRequest | ReviewResponse | Sửa |
| DELETE | /api/reviews/{id} | CUSTOMER(own)/ADMIN | – | – | Xoá |
| GET | /api/vendors/{id}/reviews | PUBLIC | query params | Page\<VendorReviewResponse\> | Danh sách review vendor |
| POST | /api/vendors/{id}/reviews | CUSTOMER (verified) | VendorReviewCreateRequest | VendorReviewResponse | Tạo review vendor |
| PUT/DELETE | /api/vendor-reviews/{id} | CUSTOMER(own)/ADMIN | VendorReviewCreateRequest | VendorReviewResponse | Sửa/xoá |

### ADMIN
| Method | Endpoint | Role | Request | Response | Description |
|---|---|---|---|---|---|
| GET | /api/admin/dashboard/stats | ADMIN | query params (date range) | DashboardStatsResponse | Thống kê tổng quan |
| GET | /api/admin/orders | ADMIN | query params | Page\<OrderResponse\> | Toàn bộ đơn hàng |

---

# PHẦN VI — Testing Strategy

| Loại test | Phạm vi | Ưu tiên |
|---|---|---|
| **Unit Test** | Service layer: business logic thuần (tính discount, tính total, state machine transition, validate coupon) — mock Repository bằng Mockito | Cao |
| **Repository Test** (`@DataJpaTest`) | Custom query method (`findByEmail`, `findByParentIsNull`, lock query...) chạy trên H2/Testcontainers MySQL | Trung bình |
| **Controller Test** (`@WebMvcTest` + MockMvc) | Validate request/response, mã HTTP status, format lỗi | Trung bình |
| **Security Test** | JWT hợp lệ/hết hạn/sai chữ ký; role-based access (`@PreAuthorize`) đúng/sai role | Cao |
| **Integration Test** (`@SpringBootTest` + Testcontainers MySQL) | Toàn bộ flow xuyên nhiều layer | **Cao nhất** |

**Các flow bắt buộc phải có Integration Test:**
1. Register → Login → gọi API bảo vệ bằng JWT.
2. Vendor tạo sản phẩm không kèm variant → hệ thống tự sinh default variant.
3. Customer thêm sản phẩm vào giỏ → cập nhật số lượng → checkout thành công → tồn kho bị trừ đúng.
4. Checkout khi tồn kho không đủ → bị chặn, không tạo Order.
5. **Race condition**: 2 request checkout đồng thời cùng 1 variant chỉ còn đủ hàng cho 1 đơn → chỉ 1 request thành công (test bằng `CompletableFuture`/thread pool giả lập đồng thời).
6. Áp coupon hợp lệ/hết hạn/hết lượt/dưới min order.
7. Payment: tạo → PAID → Order chuyển `PROCESSING`; tạo → FAILED → retry tạo bản ghi mới.
8. Review: chặn review khi chưa mua hàng; cho phép review khi đơn đã `DELIVERED`.
9. Vendor A không truy cập/sửa được tài nguyên (product, order) không thuộc về mình.

---

# PHẦN VII — Development Checklist chi tiết

```
PHASE 0 — Project Setup
[ ] Khởi tạo Spring Boot project (Spring Initializr): Web, JPA, MySQL Driver, Security, Validation, Lombok
[ ] Cấu hình application.yml (datasource, JPA, JWT secret placeholder)
[ ] Tạo package structure đầy đủ theo Phần II
[ ] Tạo BaseEntity (id, createdAt, updatedAt) + @EnableJpaAuditing
[ ] Tạo ApiResponse<T>, PageResponse<T> wrapper
[ ] Tạo khung GlobalExceptionHandler rỗng + ErrorResponse
[ ] Cấu hình Swagger/OpenAPI cơ bản (springdoc-openapi)
[ ] Test chạy ứng dụng thành công, kết nối MySQL OK

PHASE 1 — User
[ ] Tạo Role enum (ADMIN, VENDOR, CUSTOMER)
[ ] Tạo entity User (map bảng users, bỏ qua remember_token)
[ ] Tạo UserRepository (findByEmail, existsByEmail)
[ ] Tạo UserResponse, UpdateProfileRequest, ChangePasswordRequest DTO
[ ] Tạo UserService (getProfile, updateProfile, changePassword)
[ ] Tạo UserController (/api/users/me, /api/admin/users)
[ ] Unit test UserService

PHASE 2 — Authentication & Security
[ ] Cấu hình PasswordEncoder bean (BCrypt)
[ ] Tạo UserPrincipal implements UserDetails
[ ] Tạo CustomUserDetailsService
[ ] Tạo JwtService (generateToken, extractClaims, isTokenValid)
[ ] Tạo JwtAuthenticationFilter
[ ] Tạo AuthenticationEntryPoint, AccessDeniedHandler
[ ] Cấu hình SecurityFilterChain đầy đủ (permitAll/hasRole theo endpoint)
[ ] Tạo RegisterRequest, RegisterVendorRequest, LoginRequest, JwtResponse DTO
[ ] Tạo AuthService (register, login) — chặn tự đăng ký ADMIN
[ ] Tạo AuthController (/api/auth/register, /register-vendor, /login)
[ ] Test: đăng ký trùng email, login sai mật khẩu, JWT hợp lệ/hết hạn, chặn sai role

PHASE 3 — Category
[ ] Tạo entity Category (self-reference parent/children)
[ ] Tạo CategoryRepository
[ ] Tạo CategoryCreateRequest, CategoryUpdateRequest, CategoryResponse (đệ quy)
[ ] Tạo CategoryService (CRUD, chặn vòng lặp cha-con, chặn xoá khi còn con/sản phẩm)
[ ] Tạo CategoryController
[ ] Test cây phân cấp, chặn vòng lặp

PHASE 4 — Vendor
[ ] Tạo entity Vendor
[ ] Tạo VendorRepository (findByUserId)
[ ] Tạo VendorUpdateRequest, VendorResponse
[ ] Tạo VendorService (enforce 1 user - 1 vendor)
[ ] Tạo VendorController
[ ] Test ownership (vendor A không sửa được vendor B)

PHASE 5 — Product Domain
[ ] Tạo entity Product, ProductVariant, ProductImage
[ ] Tạo ProductRepository (search/filter/pagination), ProductVariantRepository (+ pessimistic lock method), ProductImageRepository
[ ] Tạo đầy đủ DTO Product/Variant/Image
[ ] Tạo ProductService: logic tự sinh default variant khi tạo sản phẩm không kèm variant
[ ] Tạo ProductVariantService, ProductImageService
[ ] Tạo ProductController + sub-resource variants/images
[ ] Chặn hard-delete Product/Variant đã có order_items
[ ] Test auto-default-variant, ownership, filter/search, chặn xoá đã bán

PHASE 6 — Cart
[ ] Tạo entity Cart, CartItem
[ ] Tạo CartRepository, CartItemRepository
[ ] Tạo AddCartItemRequest, UpdateCartItemRequest, CartResponse, CartItemResponse
[ ] Tạo CartService: find-or-create cart, cộng dồn quantity khi trùng dòng
[ ] Tạo CartController
[ ] Test cộng dồn, vượt tồn kho (cảnh báo mềm), ownership

PHASE 7 — Wishlist
[ ] Tạo entity Wishlist
[ ] Tạo WishlistRepository (existsByUserIdAndProductId)
[ ] Tạo WishlistService (chặn trùng)
[ ] Tạo WishlistController
[ ] Test thêm trùng không tạo bản ghi mới

PHASE 8 — Customer Address
[ ] Tạo entity CustomerAddress
[ ] Tạo CustomerAddressRepository
[ ] Tạo AddressCreateRequest, AddressUpdateRequest, AddressResponse
[ ] Tạo AddressService: transaction đảm bảo chỉ 1 default/user
[ ] Tạo AddressController
[ ] Test set default → các địa chỉ khác tự unset

PHASE 9 — Coupon
[ ] Tạo entity Coupon, CouponUser
[ ] Tạo CouponRepository (findByCode), CouponUserRepository (findByCouponIdAndUserId)
[ ] Tạo đầy đủ DTO Coupon
[ ] Tạo CouponService: validate đầy đủ (hạn dùng, usage_limit, min_order, per-user 1 lần)
[ ] Tạo CouponController (admin CRUD + validate endpoint)
[ ] Test từng nhánh lỗi coupon

PHASE 10 — Order & Checkout
[ ] Tạo entity Order, OrderItem
[ ] Tạo OrderRepository, OrderItemRepository
[ ] Tạo CheckoutRequest, OrderResponse, OrderItemResponse, OrderStatusUpdateRequest
[ ] Tạo CheckoutService: transaction đầy đủ (lock variant, tính giá server-side, snapshot, trừ kho, áp coupon, tạo payment, xoá cart)
[ ] Tạo OrderService: state machine transition, ownership theo role
[ ] Tạo OrderController
[ ] Integration test: checkout thành công, hết hàng bị chặn, race condition đồng thời, coupon áp dụng đúng

PHASE 11 — Payment
[ ] Tạo entity Payment
[ ] Tạo PaymentRepository (findByOrderId, findByPaymentReference)
[ ] Tạo PaymentResponse, CreatePaymentRequest, PaymentStatusUpdateRequest
[ ] Tạo interface PaymentGateway (trừu tượng, chuẩn bị Stripe sau này)
[ ] Tạo PaymentService: retry tạo bản ghi mới, đồng bộ Order.status khi PAID
[ ] Tạo PaymentController
[ ] Test retry, đồng bộ status

PHASE 12 — Reviews
[ ] Tạo entity Review, VendorReview
[ ] Tạo ReviewRepository, VendorReviewRepository (existsByUserIdAndProductId/VendorId)
[ ] Tạo đầy đủ DTO Review/VendorReview
[ ] Tạo ReviewService, VendorReviewService: kiểm tra verified purchase qua OrderItemRepository
[ ] Tạo ReviewController, VendorReviewController
[ ] Test chặn review khi chưa mua, chặn trùng, validate rating 1-5

PHASE 13 — Admin Dashboard
[ ] Tạo DashboardStatsResponse DTO
[ ] Tạo AdminDashboardService (aggregate query)
[ ] Tạo AdminDashboardController
[ ] Test số liệu thống kê đúng với dữ liệu mẫu

PHASE 14 — Exception & Validation Hardening
[ ] Rà soát toàn bộ custom exception ở mọi module, đăng ký đầy đủ trong GlobalExceptionHandler
[ ] Rà soát Bean Validation trên toàn bộ Request DTO
[ ] Chuẩn hoá format lỗi trả về nhất quán toàn hệ thống

PHASE 15 — Testing Consolidation
[ ] Rà soát coverage Unit/Integration/Repository/Controller/Security test theo Phần VI
[ ] Bổ sung test còn thiếu cho các flow trọng yếu

PHASE 16 — Swagger/OpenAPI
[ ] Cấu hình SecurityScheme Bearer cho Swagger UI
[ ] Gắn @Operation/@ApiResponse mô tả cho toàn bộ endpoint
[ ] Kiểm tra test thử API trực tiếp trên Swagger UI với JWT

PHASE 17 — Docker & Deployment
[ ] Viết Dockerfile multi-stage (build + runtime)
[ ] Viết docker-compose.yml (app + MySQL)
[ ] Đưa JWT secret, DB credentials ra biến môi trường
[ ] Test docker-compose up từ máy sạch
```
