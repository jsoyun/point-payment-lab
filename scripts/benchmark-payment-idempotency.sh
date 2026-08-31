#!/usr/bin/env bash
set -euo pipefail

REQUEST_COUNT="${1:-100}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-evidence/redis-idempotency/benchmark}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-point-payment-lab-mysql}"

DB_ONLY_ORDER_ID="${DB_ONLY_ORDER_ID:-PAYMENT-REGRESSION-001}"
REDIS_ORDER_ID="${REDIS_ORDER_ID:-ORDER-REDIS-VERIFY-001}"
REDIS_IDEMPOTENCY_KEY="${REDIS_IDEMPOTENCY_KEY:-KEY-REDIS-VERIFY-001}"

DB_ONLY_URL="$BASE_URL/api/payments/point/idempotent"
REDIS_URL="$BASE_URL/api/payments/point/redis-idempotent"

DB_ONLY_BODY=$(cat <<JSON
{"orderId":"$DB_ONLY_ORDER_ID","pointWalletUid":"point-wallet-001","voucherProductId":1,"pointBalanceId":1,"point":5000}
JSON
)

REDIS_BODY=$(cat <<JSON
{"orderId":"$REDIS_ORDER_ID","pointWalletUid":"point-wallet-001","voucherProductId":1,"pointBalanceId":1,"point":5000}
JSON
)

mkdir -p "$OUTPUT_DIR"

mysql_status() {
  docker exec "$MYSQL_CONTAINER" mysql -ulab -plab --batch --skip-column-names \
    -e "show global status where Variable_name in ('Com_select','Com_insert','Com_update');" \
    | sort
}

mysql_value() {
  local file="$1"
  local name="$2"
  awk -v target="$name" '$1 == target { print $2 }' "$file"
}

measure() {
  local label="$1"
  local url="$2"
  local body="$3"
  local idempotency_key="${4:-}"
  local times_file="$OUTPUT_DIR/$label-times.txt"
  local before_file="$OUTPUT_DIR/$label-mysql-before.txt"
  local after_file="$OUTPUT_DIR/$label-mysql-after.txt"
  local status_file="$OUTPUT_DIR/$label-http-statuses.txt"

  : > "$times_file"
  : > "$status_file"
  mysql_status > "$before_file"

  for ((i = 1; i <= REQUEST_COUNT; i++)); do
    if [[ -n "$idempotency_key" ]]; then
      curl -sS -o /dev/null \
        -w '%{http_code} %{time_total}\n' \
        -H "Idempotency-Key: $idempotency_key" \
        -H 'Content-Type: application/json' \
        -d "$body" "$url"
    else
      curl -sS -o /dev/null \
        -w '%{http_code} %{time_total}\n' \
        -H 'Content-Type: application/json' \
        -d "$body" "$url"
    fi
  done | tee "$OUTPUT_DIR/$label-raw.txt" | awk '{ print $1 > status; print $2 > times }' \
    status="$status_file" times="$times_file"

  mysql_status > "$after_file"
}

summarize() {
  local label="$1"
  local times_file="$OUTPUT_DIR/$label-times.txt"
  local before_file="$OUTPUT_DIR/$label-mysql-before.txt"
  local after_file="$OUTPUT_DIR/$label-mysql-after.txt"

  local avg_ms
  local min_ms
  local max_ms
  avg_ms=$(awk '{ sum += $1 } END { printf "%.3f", (sum / NR) * 1000 }' "$times_file")
  min_ms=$(sort -n "$times_file" | head -n 1 | awk '{ printf "%.3f", $1 * 1000 }')
  max_ms=$(sort -n "$times_file" | tail -n 1 | awk '{ printf "%.3f", $1 * 1000 }')

  local selects
  local inserts
  local updates
  selects=$(( $(mysql_value "$after_file" Com_select) - $(mysql_value "$before_file" Com_select) ))
  inserts=$(( $(mysql_value "$after_file" Com_insert) - $(mysql_value "$before_file" Com_insert) ))
  updates=$(( $(mysql_value "$after_file" Com_update) - $(mysql_value "$before_file" Com_update) ))

  printf '%s,%s,%s,%s,%s,%s,%s\n' \
    "$label" "$avg_ms" "$min_ms" "$max_ms" "$selects" "$inserts" "$updates"
}

echo "Warming up completed DB-only and Redis requests..."
curl -sS -o /dev/null -H 'Content-Type: application/json' -d "$DB_ONLY_BODY" "$DB_ONLY_URL"
curl -sS -o /dev/null -H "Idempotency-Key: $REDIS_IDEMPOTENCY_KEY" \
  -H 'Content-Type: application/json' -d "$REDIS_BODY" "$REDIS_URL"

echo "Measuring $REQUEST_COUNT completed retries for DB-only..."
measure db-only "$DB_ONLY_URL" "$DB_ONLY_BODY"

echo "Measuring $REQUEST_COUNT completed retries for Redis + DB..."
measure redis-db "$REDIS_URL" "$REDIS_BODY" "$REDIS_IDEMPOTENCY_KEY"

SUMMARY_FILE="$OUTPUT_DIR/summary.csv"
{
  echo 'mode,avg_ms,min_ms,max_ms,mysql_selects,mysql_inserts,mysql_updates'
  summarize db-only
  summarize redis-db
} > "$SUMMARY_FILE"

docker exec "$MYSQL_CONTAINER" mysql -ulab -plab --batch --raw point_payment_lab \
  -e "select order_id, count(*) as provider_rows from provider_voucher where order_id in ('$DB_ONLY_ORDER_ID', '$REDIS_ORDER_ID') group by order_id order by order_id; select order_id, count(*) as purchase_rows from voucher_purchase where order_id in ('$DB_ONLY_ORDER_ID', '$REDIS_ORDER_ID') group by order_id order by order_id;" \
  > "$OUTPUT_DIR/db-row-counts.txt"

echo
cat "$SUMMARY_FILE"
echo
cat "$OUTPUT_DIR/db-row-counts.txt"
echo
echo "Saved benchmark evidence under $OUTPUT_DIR"
