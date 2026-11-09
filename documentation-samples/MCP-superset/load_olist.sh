#!/usr/bin/env bash
# ============================================================================
# Olist Brazilian E-Commerce — StarRocks Stream Load
# Prereq: olist_schema.sql already run (database `olist` + tables exist),
#         and the Kaggle CSVs are in CSV_DIR (one file per table).
#         Dataset: https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce
#
# Stream Load posts to the FE HTTP port (8030 in the shared-data quick start).
# The FE redirects each load to the CN by hostname (starrocks-cn), which needs
# a matching /etc/hosts entry (127.0.0.1 starrocks-cn). No sudo? Post straight
# to the CN's published HTTP port instead — no redirect involved:
#   FE_HTTP_PORT=8040 bash load_olist.sh
# Each table loads in seconds on the 1 FE + 1 CN setup; geolocation (~1M rows)
# is the largest and still lands fast.
#
# Notes baked in:
#   -u root:                empty password, non-interactive (note the colon)
#   skip_header:1           CSVs have a header row
#   enclose:"\""            fields are double-quote enclosed (free text, commas)
#   trim_space:true         trim padding around delimiters
#   nullif(col,'')          empty -> NULL for nullable DATETIME / INT columns
# enclose + trim_space require StarRocks v3.0+ (the quick start is newer — fine).
# ============================================================================
set -euo pipefail

FE_HOST="${FE_HOST:-localhost}"
FE_HTTP_PORT="${FE_HTTP_PORT:-8030}"
DB="${DB:-olist}"
USER_PW="${USER_PW:-root:}"          # user root, empty password
CSV_DIR="${CSV_DIR:-.}"              # directory holding the Olist CSVs
SUF="$(date +%s)"                    # unique label suffix so loads are re-runnable

load () {
  local table="$1" file="$2"; shift 2   # remaining args = extra -H headers
  echo ">>> Loading ${table} from ${file}"
  curl --location-trusted -u "${USER_PW}" \
    -H "label:olist_${table}_${SUF}" \
    -H "format:CSV" \
    -H "column_separator:," \
    -H "skip_header:1" \
    -H "enclose:\"" \
    -H "trim_space:true" \
    -H "max_filter_ratio:0.05" \
    "$@" \
    -T "${CSV_DIR}/${file}" \
    -XPUT "http://${FE_HOST}:${FE_HTTP_PORT}/api/${DB}/${table}/_stream_load"
  echo   # newline after the JSON status
}

# --- simple direct loads (CSV column order matches the table) ----------------
load customers olist_customers_dataset.csv \
  -H "columns:customer_id,customer_unique_id,customer_zip_code_prefix,customer_city,customer_state"

load geolocation olist_geolocation_dataset.csv \
  -H "columns:geolocation_zip_code_prefix,geolocation_lat,geolocation_lng,geolocation_city,geolocation_state"

load sellers olist_sellers_dataset.csv \
  -H "columns:seller_id,seller_zip_code_prefix,seller_city,seller_state"

load order_items olist_order_items_dataset.csv \
  -H "columns:order_id,order_item_id,product_id,seller_id,shipping_limit_date,price,freight_value"

load order_payments olist_order_payments_dataset.csv \
  -H "columns:order_id,payment_sequential,payment_type,payment_installments,payment_value"

load category_translation product_category_name_translation.csv \
  -H "columns:product_category_name,product_category_name_english"

# --- products: empty numeric fields -> NULL via temp columns -----------------
load products olist_products_dataset.csv \
  -H "columns:product_id,product_category_name,t_namelen,t_desclen,t_photos,t_weight,t_len,t_height,t_width,product_name_lenght=nullif(t_namelen,''),product_description_lenght=nullif(t_desclen,''),product_photos_qty=nullif(t_photos,''),product_weight_g=nullif(t_weight,''),product_length_cm=nullif(t_len,''),product_height_cm=nullif(t_height,''),product_width_cm=nullif(t_width,'')"

# --- orders: nullable delivery timestamps -> NULL via temp columns -----------
# purchase + estimated are always present; approved/carrier/delivered may be empty.
load orders olist_orders_dataset.csv \
  -H "columns:order_id,customer_id,order_status,order_purchase_timestamp,t_approved,t_carrier,t_delivered,order_estimated_delivery_date,order_approved_at=nullif(t_approved,''),order_delivered_carrier_date=nullif(t_carrier,''),order_delivered_customer_date=nullif(t_delivered,'')"

# --- order_reviews: free-text comments contain commas/quotes/NEWLINES ---------
# Embedded newlines inside quoted fields can break the CSV row parser. If this
# load rejects rows (check NumberFilteredRows in the JSON), use the LEAN variant
# below instead — it loads everything except the two free-text comment columns.
load order_reviews olist_order_reviews_dataset.csv \
  -H "columns:review_id,order_id,review_score,review_comment_title,review_comment_message,review_creation_date,review_answer_timestamp"

# LEAN variant for order_reviews (uncomment if the full load rejects rows):
# load order_reviews olist_order_reviews_dataset.csv \
#   -H "columns:review_id,order_id,review_score,t_title,t_msg,review_creation_date,review_answer_timestamp"
#   (t_title / t_msg are read and discarded; comment columns stay NULL.)

echo
echo "=== Done. Verify row counts (expected values in comments): ==="
cat <<'SQL'
USE olist;
SELECT 'orders' t, count(*) c FROM orders               -- ~99,441
UNION ALL SELECT 'order_items',          count(*) FROM order_items          -- ~112,650
UNION ALL SELECT 'order_payments',       count(*) FROM order_payments       -- ~103,886
UNION ALL SELECT 'order_reviews',        count(*) FROM order_reviews        -- ~99,224
UNION ALL SELECT 'products',             count(*) FROM products             -- ~32,951
UNION ALL SELECT 'sellers',              count(*) FROM sellers              -- ~3,095
UNION ALL SELECT 'customers',            count(*) FROM customers            -- ~99,441
UNION ALL SELECT 'geolocation',          count(*) FROM geolocation          -- ~1,000,163
UNION ALL SELECT 'category_translation', count(*) FROM category_translation -- ~71
ORDER BY t;
SQL
