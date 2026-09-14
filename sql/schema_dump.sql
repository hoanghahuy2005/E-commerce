-- V1__init_schema.sql
-- E-Commerce Backend - Spring Boot + Spring Security + JWT + MySQL 8+
-- Thiết kế lại từ schema Laravel cũ để phù hợp với Spring Boot/JPA.
--
-- Quy ước chính:
-- 1. Không dùng các bảng Laravel: cache, jobs, sessions, migrations, password_reset_tokens...
-- 2. Enum lưu dạng VARCHAR + CHECK để map bằng @Enumerated(EnumType.STRING).
-- 3. Mọi Product phải có ít nhất 1 ProductVariant; giá và tồn kho nằm ở ProductVariant.
-- 4. Cart có thể chứa nhiều vendor, nhưng mỗi lần checkout chỉ checkout 1 vendor.
--    Vì vậy mỗi Order luôn thuộc đúng 1 Vendor.
-- 5. OrderItem lưu snapshot và FK product/variant nullable + ON DELETE SET NULL
--    để không làm mất lịch sử đơn hàng.
-- 6. Product/User/Vendor/Variant dùng is_active thay cho hard delete trong nghiệp vụ bình thường.

CREATE DATABASE IF NOT EXISTS ecommerce
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ecommerce;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS vendor_reviews;
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS coupon_users;
DROP TABLE IF EXISTS coupons;
DROP TABLE IF EXISTS customer_addresses;
DROP TABLE IF EXISTS wishlists;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS carts;
DROP TABLE IF EXISTS product_images;
DROP TABLE IF EXISTS product_variants;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS vendors;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- USERS
-- =========================================================
CREATE TABLE users (
                       id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                       name VARCHAR(120) NOT NULL,
                       email VARCHAR(191) NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       phone_number VARCHAR(30) NULL,
                       address VARCHAR(500) NULL,
                       role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
                       is_active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),
                       CONSTRAINT uk_users_email UNIQUE (email),
                       CONSTRAINT chk_users_role
                           CHECK (role IN ('ADMIN', 'VENDOR', 'CUSTOMER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- VENDORS
-- 1 User chỉ có tối đa 1 Vendor profile
-- =========================================================
CREATE TABLE vendors (
                         id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                         user_id BIGINT UNSIGNED NOT NULL,
                         shop_name VARCHAR(150) NOT NULL,
                         description TEXT NULL,
                         logo_url VARCHAR(500) NULL,
                         address VARCHAR(500) NULL,
                         is_active BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                         PRIMARY KEY (id),
                         CONSTRAINT uk_vendors_user UNIQUE (user_id),
                         CONSTRAINT fk_vendors_user
                             FOREIGN KEY (user_id) REFERENCES users(id)
                                 ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- CATEGORIES
-- Không cascade delete cây category để tránh xoá nhầm subtree.
-- Service phải kiểm tra trước khi xoá.
-- =========================================================
CREATE TABLE categories (
                            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            name VARCHAR(150) NOT NULL,
                            slug VARCHAR(191) NOT NULL,
                            parent_id BIGINT UNSIGNED NULL,
                            image_url VARCHAR(500) NULL,
                            is_active BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),
                            CONSTRAINT uk_categories_slug UNIQUE (slug),
                            CONSTRAINT fk_categories_parent
                                FOREIGN KEY (parent_id) REFERENCES categories(id)
                                    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- PRODUCTS
-- Product không giữ price/stock để tránh 2 nguồn sự thật.
-- Price/stock luôn nằm ở product_variants.
-- =========================================================
CREATE TABLE products (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          vendor_id BIGINT UNSIGNED NOT NULL,
                          category_id BIGINT UNSIGNED NULL,
                          name VARCHAR(255) NOT NULL,
                          slug VARCHAR(191) NOT NULL,
                          description TEXT NOT NULL,
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                          PRIMARY KEY (id),
                          CONSTRAINT uk_products_slug UNIQUE (slug),
                          CONSTRAINT fk_products_vendor
                              FOREIGN KEY (vendor_id) REFERENCES vendors(id)
                                  ON DELETE RESTRICT,
                          CONSTRAINT fk_products_category
                              FOREIGN KEY (category_id) REFERENCES categories(id)
                                  ON DELETE SET NULL,

                          INDEX idx_products_vendor_active (vendor_id, is_active),
                          INDEX idx_products_category_active (category_id, is_active),
                          INDEX idx_products_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- PRODUCT VARIANTS
-- Mỗi product phải có >= 1 variant ở tầng Service.
-- Sản phẩm đơn giản dùng variant name = 'Default'.
-- =========================================================
CREATE TABLE product_variants (
                                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                  product_id BIGINT UNSIGNED NOT NULL,
                                  name VARCHAR(150) NOT NULL,
                                  sku VARCHAR(191) NULL,
                                  price DECIMAL(12,2) NOT NULL,
                                  stock INT UNSIGNED NOT NULL DEFAULT 0,
                                  is_active BOOLEAN NOT NULL DEFAULT TRUE,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                  PRIMARY KEY (id),
                                  CONSTRAINT uk_product_variants_sku UNIQUE (sku),
                                  CONSTRAINT fk_product_variants_product
                                      FOREIGN KEY (product_id) REFERENCES products(id)
                                          ON DELETE CASCADE,
                                  CONSTRAINT chk_product_variants_price CHECK (price >= 0),

                                  INDEX idx_variants_product_active (product_id, is_active),
                                  INDEX idx_variants_price (price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- PRODUCT IMAGES
-- =========================================================
CREATE TABLE product_images (
                                id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                product_id BIGINT UNSIGNED NOT NULL,
                                image_url VARCHAR(500) NOT NULL,
                                is_main BOOLEAN NOT NULL DEFAULT FALSE,
                                sort_order INT UNSIGNED NOT NULL DEFAULT 0,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                PRIMARY KEY (id),
                                CONSTRAINT fk_product_images_product
                                    FOREIGN KEY (product_id) REFERENCES products(id)
                                        ON DELETE CASCADE,

                                INDEX idx_product_images_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- CARTS
-- 1 User = 1 Cart
-- =========================================================
CREATE TABLE carts (
                       id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                       user_id BIGINT UNSIGNED NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),
                       CONSTRAINT uk_carts_user UNIQUE (user_id),
                       CONSTRAINT fk_carts_user
                           FOREIGN KEY (user_id) REFERENCES users(id)
                               ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- CART ITEMS
-- Chỉ cần variant_id vì từ variant có thể suy ra product.
-- UNIQUE(cart_id, product_variant_id) giúp không sinh dòng trùng.
-- =========================================================
CREATE TABLE cart_items (
                            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                            cart_id BIGINT UNSIGNED NOT NULL,
                            product_variant_id BIGINT UNSIGNED NOT NULL,
                            quantity INT UNSIGNED NOT NULL,
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),
                            CONSTRAINT uk_cart_items_cart_variant
                                UNIQUE (cart_id, product_variant_id),
                            CONSTRAINT fk_cart_items_cart
                                FOREIGN KEY (cart_id) REFERENCES carts(id)
                                    ON DELETE CASCADE,
                            CONSTRAINT fk_cart_items_variant
                                FOREIGN KEY (product_variant_id) REFERENCES product_variants(id)
                                    ON DELETE CASCADE,
                            CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0),

                            INDEX idx_cart_items_variant (product_variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- WISHLISTS
-- =========================================================
CREATE TABLE wishlists (
                           id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                           user_id BIGINT UNSIGNED NOT NULL,
                           product_id BIGINT UNSIGNED NOT NULL,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                           PRIMARY KEY (id),
                           CONSTRAINT uk_wishlists_user_product UNIQUE (user_id, product_id),
                           CONSTRAINT fk_wishlists_user
                               FOREIGN KEY (user_id) REFERENCES users(id)
                                   ON DELETE CASCADE,
                           CONSTRAINT fk_wishlists_product
                               FOREIGN KEY (product_id) REFERENCES products(id)
                                   ON DELETE CASCADE,

                           INDEX idx_wishlists_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- CUSTOMER ADDRESSES
-- Chỉ 1 default/user được enforce trong AddressService + transaction.
-- =========================================================
CREATE TABLE customer_addresses (
                                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                    user_id BIGINT UNSIGNED NOT NULL,
                                    type VARCHAR(20) NOT NULL DEFAULT 'SHIPPING',
                                    name VARCHAR(120) NOT NULL,
                                    address_line1 VARCHAR(255) NOT NULL,
                                    address_line2 VARCHAR(255) NULL,
                                    city VARCHAR(120) NOT NULL,
                                    state VARCHAR(120) NULL,
                                    postal_code VARCHAR(30) NULL,
                                    country VARCHAR(120) NOT NULL,
                                    phone_number VARCHAR(30) NOT NULL,
                                    is_default BOOLEAN NOT NULL DEFAULT FALSE,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                    PRIMARY KEY (id),
                                    CONSTRAINT fk_customer_addresses_user
                                        FOREIGN KEY (user_id) REFERENCES users(id)
                                            ON DELETE CASCADE,
                                    CONSTRAINT chk_customer_addresses_type
                                        CHECK (type IN ('SHIPPING', 'BILLING')),

                                    INDEX idx_addresses_user_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- COUPONS
-- per_user_limit được thêm để times_used có ý nghĩa rõ ràng.
-- =========================================================
CREATE TABLE coupons (
                         id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                         code VARCHAR(80) NOT NULL,
                         type VARCHAR(20) NOT NULL,
                         value DECIMAL(12,2) NOT NULL,
                         min_order_amount DECIMAL(12,2) NULL,
                         usage_limit INT UNSIGNED NULL,
                         used_count INT UNSIGNED NOT NULL DEFAULT 0,
                         per_user_limit INT UNSIGNED NOT NULL DEFAULT 1,
                         valid_from DATETIME NULL,
                         valid_until DATETIME NULL,
                         is_active BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                         PRIMARY KEY (id),
                         CONSTRAINT uk_coupons_code UNIQUE (code),
                         CONSTRAINT chk_coupons_type
                             CHECK (type IN ('FIXED', 'PERCENT')),
                         CONSTRAINT chk_coupons_value
                             CHECK (value > 0),
                         CONSTRAINT chk_coupons_percent
                             CHECK (type <> 'PERCENT' OR value <= 100),
                         CONSTRAINT chk_coupons_min_order
                             CHECK (min_order_amount IS NULL OR min_order_amount >= 0),
                         CONSTRAINT chk_coupons_date_range
                             CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from),

                         INDEX idx_coupons_active_dates (is_active, valid_from, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- COUPON USERS
-- =========================================================
CREATE TABLE coupon_users (
                              id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                              coupon_id BIGINT UNSIGNED NOT NULL,
                              user_id BIGINT UNSIGNED NOT NULL,
                              times_used INT UNSIGNED NOT NULL DEFAULT 0,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                              PRIMARY KEY (id),
                              CONSTRAINT uk_coupon_users_coupon_user UNIQUE (coupon_id, user_id),
                              CONSTRAINT fk_coupon_users_coupon
                                  FOREIGN KEY (coupon_id) REFERENCES coupons(id)
                                      ON DELETE CASCADE,
                              CONSTRAINT fk_coupon_users_user
                                  FOREIGN KEY (user_id) REFERENCES users(id)
                                      ON DELETE CASCADE,

                              INDEX idx_coupon_users_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- ORDERS
-- Mỗi Order thuộc đúng 1 Vendor.
-- Cart có thể chứa nhiều vendor nhưng CheckoutRequest chọn 1 vendor.
-- =========================================================
CREATE TABLE orders (
                        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                        user_id BIGINT UNSIGNED NOT NULL,
                        vendor_id BIGINT UNSIGNED NOT NULL,

                        address_name VARCHAR(120) NOT NULL,
                        address_line1 VARCHAR(255) NOT NULL,
                        address_line2 VARCHAR(255) NULL,
                        city VARCHAR(120) NOT NULL,
                        state VARCHAR(120) NULL,
                        postal_code VARCHAR(30) NULL,
                        country VARCHAR(120) NOT NULL,
                        phone_number VARCHAR(30) NOT NULL,

                        status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                        subtotal DECIMAL(12,2) NOT NULL,
                        discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                        total DECIMAL(12,2) NOT NULL,
                        coupon_code VARCHAR(80) NULL,

                        cancelled_at DATETIME NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                        PRIMARY KEY (id),

                        CONSTRAINT fk_orders_user
                            FOREIGN KEY (user_id) REFERENCES users(id)
                                ON DELETE RESTRICT,
                        CONSTRAINT fk_orders_vendor
                            FOREIGN KEY (vendor_id) REFERENCES vendors(id)
                                ON DELETE RESTRICT,

                        CONSTRAINT chk_orders_status
                            CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
                        CONSTRAINT chk_orders_subtotal CHECK (subtotal >= 0),
                        CONSTRAINT chk_orders_discount CHECK (discount_amount >= 0),
                        CONSTRAINT chk_orders_total CHECK (total >= 0),

                        INDEX idx_orders_user_created (user_id, created_at),
                        INDEX idx_orders_vendor_status (vendor_id, status),
                        INDEX idx_orders_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- ORDER ITEMS
-- Snapshot là dữ liệu lịch sử thật của đơn hàng.
-- product_id / product_variant_id được phép NULL nếu tài nguyên gốc bị xoá.
-- =========================================================
CREATE TABLE order_items (
                             id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                             order_id BIGINT UNSIGNED NOT NULL,
                             product_id BIGINT UNSIGNED NULL,
                             product_variant_id BIGINT UNSIGNED NULL,

                             product_name VARCHAR(255) NOT NULL,
                             variant_name VARCHAR(150) NOT NULL,
                             sku VARCHAR(191) NULL,
                             price DECIMAL(12,2) NOT NULL,
                             quantity INT UNSIGNED NOT NULL,
                             line_total DECIMAL(12,2) NOT NULL,

                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                             PRIMARY KEY (id),

                             CONSTRAINT fk_order_items_order
                                 FOREIGN KEY (order_id) REFERENCES orders(id)
                                     ON DELETE CASCADE,
                             CONSTRAINT fk_order_items_product
                                 FOREIGN KEY (product_id) REFERENCES products(id)
                                     ON DELETE SET NULL,
                             CONSTRAINT fk_order_items_variant
                                 FOREIGN KEY (product_variant_id) REFERENCES product_variants(id)
                                     ON DELETE SET NULL,

                             CONSTRAINT chk_order_items_price CHECK (price >= 0),
                             CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
                             CONSTRAINT chk_order_items_line_total CHECK (line_total >= 0),

                             INDEX idx_order_items_order (order_id),
                             INDEX idx_order_items_product (product_id),
                             INDEX idx_order_items_variant (product_variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- PAYMENTS
-- 1 Order có nhiều payment attempts.
-- =========================================================
CREATE TABLE payments (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          order_id BIGINT UNSIGNED NOT NULL,
                          payment_method VARCHAR(50) NOT NULL,
                          payment_reference VARCHAR(191) NOT NULL,
                          amount DECIMAL(12,2) NOT NULL,
                          status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                          paid_at DATETIME NULL,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                          PRIMARY KEY (id),

                          CONSTRAINT uk_payments_reference UNIQUE (payment_reference),
                          CONSTRAINT fk_payments_order
                              FOREIGN KEY (order_id) REFERENCES orders(id)
                                  ON DELETE CASCADE,
                          CONSTRAINT chk_payments_status
                              CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
                          CONSTRAINT chk_payments_amount CHECK (amount >= 0),

                          INDEX idx_payments_order_created (order_id, created_at),
                          INDEX idx_payments_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- PRODUCT REVIEWS
-- order_item_id giúp chứng minh review dựa trên một lần mua thật.
-- Service vẫn phải verify:
-- order_item.order.user_id == review.user_id
-- và order.status == DELIVERED.
-- =========================================================
CREATE TABLE reviews (
                         id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                         user_id BIGINT UNSIGNED NOT NULL,
                         product_id BIGINT UNSIGNED NOT NULL,
                         order_item_id BIGINT UNSIGNED NOT NULL,
                         rating TINYINT UNSIGNED NOT NULL,
                         comment TEXT NULL,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                         PRIMARY KEY (id),

                         CONSTRAINT uk_reviews_user_product UNIQUE (user_id, product_id),
                         CONSTRAINT fk_reviews_user
                             FOREIGN KEY (user_id) REFERENCES users(id)
                                 ON DELETE CASCADE,
                         CONSTRAINT fk_reviews_product
                             FOREIGN KEY (product_id) REFERENCES products(id)
                                 ON DELETE CASCADE,
                         CONSTRAINT fk_reviews_order_item
                             FOREIGN KEY (order_item_id) REFERENCES order_items(id)
                                 ON DELETE RESTRICT,
                         CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),

                         INDEX idx_reviews_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- VENDOR REVIEWS
-- order_id giúp chứng minh user đã có đơn DELIVERED của vendor.
-- =========================================================
CREATE TABLE vendor_reviews (
                                id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                vendor_id BIGINT UNSIGNED NOT NULL,
                                user_id BIGINT UNSIGNED NOT NULL,
                                order_id BIGINT UNSIGNED NOT NULL,
                                rating TINYINT UNSIGNED NOT NULL,
                                comment TEXT NULL,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                PRIMARY KEY (id),

                                CONSTRAINT uk_vendor_reviews_user_vendor UNIQUE (user_id, vendor_id),
                                CONSTRAINT fk_vendor_reviews_vendor
                                    FOREIGN KEY (vendor_id) REFERENCES vendors(id)
                                        ON DELETE CASCADE,
                                CONSTRAINT fk_vendor_reviews_user
                                    FOREIGN KEY (user_id) REFERENCES users(id)
                                        ON DELETE CASCADE,
                                CONSTRAINT fk_vendor_reviews_order
                                    FOREIGN KEY (order_id) REFERENCES orders(id)
                                        ON DELETE RESTRICT,
                                CONSTRAINT chk_vendor_reviews_rating CHECK (rating BETWEEN 1 AND 5),

                                INDEX idx_vendor_reviews_vendor_created (vendor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
