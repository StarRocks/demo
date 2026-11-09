-- ============================================================================
-- Olist Brazilian E-Commerce — StarRocks shared-data DDL
-- Run AFTER the storage volume (storage_volume.sql) is created and set default.
-- Connect:  docker compose exec starrocks-fe \
--             mysql -P9030 -h127.0.0.1 -uroot --prompt="StarRocks > "
-- Shared-data note: do NOT set replication_num — durability comes from the
-- object-store (MinIO) storage volume. Tables auto-bucket; no bucket count needed.
-- Column names intentionally keep the source CSV header spellings, including the
-- "lenght" typos in products, so Stream Load column mapping stays 1:1.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS olist;
USE olist;

-- ---------------------------------------------------------------------------
-- customers — one row per order's customer (customer_id), plus a stable
-- customer_unique_id for repeat-buyer analysis. zip prefix joins to geolocation.
-- ~99,441 rows
-- ---------------------------------------------------------------------------
CREATE TABLE customers (
    customer_id              VARCHAR(64),
    customer_unique_id       VARCHAR(64),
    customer_zip_code_prefix INT,
    customer_city            VARCHAR(128),
    customer_state           VARCHAR(8)
)
DUPLICATE KEY(customer_id)
DISTRIBUTED BY HASH(customer_id);

-- ---------------------------------------------------------------------------
-- geolocation — lat/lng per zip prefix. NOTE: multiple rows per zip prefix.
-- ~1,000,163 rows (largest table)
-- ---------------------------------------------------------------------------
CREATE TABLE geolocation (
    geolocation_zip_code_prefix INT,
    geolocation_lat             DOUBLE,
    geolocation_lng             DOUBLE,
    geolocation_city            VARCHAR(128),
    geolocation_state           VARCHAR(8)
)
DUPLICATE KEY(geolocation_zip_code_prefix)
DISTRIBUTED BY HASH(geolocation_zip_code_prefix);

-- ---------------------------------------------------------------------------
-- orders — the fact spine. Delivery timestamps are NULL for orders that were
-- never approved/shipped/delivered (handled via nullif on load). ~99,441 rows
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    order_id                      VARCHAR(64),
    customer_id                   VARCHAR(64),
    order_status                  VARCHAR(32),
    order_purchase_timestamp      DATETIME,
    order_approved_at             DATETIME,
    order_delivered_carrier_date  DATETIME,
    order_delivered_customer_date DATETIME,
    order_estimated_delivery_date DATETIME
)
DUPLICATE KEY(order_id)
DISTRIBUTED BY HASH(order_id);

-- ---------------------------------------------------------------------------
-- order_items — one row per item line in an order; carries price + freight.
-- Links order_id -> product_id -> seller_id. ~112,650 rows
-- ---------------------------------------------------------------------------
CREATE TABLE order_items (
    order_id            VARCHAR(64),
    order_item_id       INT,
    product_id          VARCHAR(64),
    seller_id           VARCHAR(64),
    shipping_limit_date DATETIME,
    price               DECIMAL(10,2),
    freight_value       DECIMAL(10,2)
)
DUPLICATE KEY(order_id, order_item_id)
DISTRIBUTED BY HASH(order_id);

-- ---------------------------------------------------------------------------
-- order_payments — payment_type (credit_card / boleto / voucher / debit_card),
-- installments, value. Multiple rows per order. ~103,886 rows
-- ---------------------------------------------------------------------------
CREATE TABLE order_payments (
    order_id             VARCHAR(64),
    payment_sequential   INT,
    payment_type         VARCHAR(32),
    payment_installments INT,
    payment_value        DECIMAL(10,2)
)
DUPLICATE KEY(order_id, payment_sequential)
DISTRIBUTED BY HASH(order_id);

-- ---------------------------------------------------------------------------
-- order_reviews — review_score is 1-5. The two free-text comment columns
-- contain commas/quotes/newlines; see load_olist.sh for the "lean" variant
-- that skips them if the CSV parser chokes. ~99,224 rows
-- ---------------------------------------------------------------------------
CREATE TABLE order_reviews (
    review_id               VARCHAR(64),
    order_id                VARCHAR(64),
    review_score            INT,
    review_comment_title    VARCHAR(256),
    review_comment_message  VARCHAR(2048),
    review_creation_date    DATETIME,
    review_answer_timestamp DATETIME
)
DUPLICATE KEY(review_id)
DISTRIBUTED BY HASH(review_id);

-- ---------------------------------------------------------------------------
-- products — note the source CSV header typos: product_name_lenght /
-- product_description_lenght. Numeric fields can be empty (nullif on load).
-- ~32,951 rows
-- ---------------------------------------------------------------------------
CREATE TABLE products (
    product_id                 VARCHAR(64),
    product_category_name      VARCHAR(128),
    product_name_lenght        INT,
    product_description_lenght INT,
    product_photos_qty         INT,
    product_weight_g           INT,
    product_length_cm          INT,
    product_height_cm          INT,
    product_width_cm           INT
)
DUPLICATE KEY(product_id)
DISTRIBUTED BY HASH(product_id);

-- ---------------------------------------------------------------------------
-- sellers — zip prefix joins to geolocation (like customers). ~3,095 rows
-- ---------------------------------------------------------------------------
CREATE TABLE sellers (
    seller_id              VARCHAR(64),
    seller_zip_code_prefix INT,
    seller_city            VARCHAR(128),
    seller_state           VARCHAR(8)
)
DUPLICATE KEY(seller_id)
DISTRIBUTED BY HASH(seller_id);

-- ---------------------------------------------------------------------------
-- category_translation — optional: Portuguese -> English category names.
-- Load product_category_name_translation.csv. ~71 rows
-- ---------------------------------------------------------------------------
CREATE TABLE category_translation (
    product_category_name         VARCHAR(128),
    product_category_name_english VARCHAR(128)
)
DUPLICATE KEY(product_category_name)
DISTRIBUTED BY HASH(product_category_name);
