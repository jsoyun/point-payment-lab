#!/usr/bin/env bash
set -euo pipefail

ORDER_A="${1:-ORDER-BALANCE-RACE-A}"
ORDER_B="${2:-ORDER-BALANCE-RACE-B}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-evidence/conditional-balance-debit}"
URL="$BASE_URL/api/payments/point/redis-idempotent"

mkdir -p "$OUTPUT_DIR"

request_body() {
  local order_id="$1"
  cat <<JSON
{"orderId":"$order_id","pointWalletUid":"point-wallet-001","voucherProductId":1,"pointBalanceId":1,"point":5000}
JSON
}

echo "Sending different orders and Idempotency-Keys against the same 5,000 point balance"

curl -sS -D "$OUTPUT_DIR/headers-a.txt" -o "$OUTPUT_DIR/body-a.json" \
  -w 'HTTP %{http_code}\n' \
  -H "Idempotency-Key: KEY-$ORDER_A" \
  -H 'Content-Type: application/json' \
  -d "$(request_body "$ORDER_A")" "$URL" > "$OUTPUT_DIR/status-a.txt" &

curl -sS -D "$OUTPUT_DIR/headers-b.txt" -o "$OUTPUT_DIR/body-b.json" \
  -w 'HTTP %{http_code}\n' \
  -H "Idempotency-Key: KEY-$ORDER_B" \
  -H 'Content-Type: application/json' \
  -d "$(request_body "$ORDER_B")" "$URL" > "$OUTPUT_DIR/status-b.txt" &

wait

echo "=== response A ==="
cat "$OUTPUT_DIR/status-a.txt" "$OUTPUT_DIR/headers-a.txt" "$OUTPUT_DIR/body-a.json"
echo
echo "=== response B ==="
cat "$OUTPUT_DIR/status-b.txt" "$OUTPUT_DIR/headers-b.txt" "$OUTPUT_DIR/body-b.json"
echo
echo "Saved responses under $OUTPUT_DIR"
