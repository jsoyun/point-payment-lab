#!/usr/bin/env bash
set -euo pipefail

ORDER_ID="${1:-ORDER-REDIS-DUPLICATE-001}"
IDEMPOTENCY_KEY="${2:-KEY-REDIS-DUPLICATE-001}"
BASE_URL_A="${BASE_URL_A:-http://localhost:8080}"
BASE_URL_B="${BASE_URL_B:-$BASE_URL_A}"
OUTPUT_DIR="${OUTPUT_DIR:-evidence/redis-idempotency}"
URL_PATH="/api/payments/point/redis-idempotent"
BODY=$(cat <<JSON
{
  "orderId": "$ORDER_ID",
  "pointWalletUid": "point-wallet-001",
  "voucherProductId": 1,
  "pointBalanceId": 1,
  "point": 5000
}
JSON
)

mkdir -p "$OUTPUT_DIR"

echo "Sending the same Idempotency-Key to $BASE_URL_A and $BASE_URL_B"

curl -sS -D "$OUTPUT_DIR/headers-a.txt" -o "$OUTPUT_DIR/body-a.json" \
  -w "HTTP %{http_code}\n" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "Content-Type: application/json" \
  -d "$BODY" "$BASE_URL_A$URL_PATH" > "$OUTPUT_DIR/status-a.txt" &

curl -sS -D "$OUTPUT_DIR/headers-b.txt" -o "$OUTPUT_DIR/body-b.json" \
  -w "HTTP %{http_code}\n" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "Content-Type: application/json" \
  -d "$BODY" "$BASE_URL_B$URL_PATH" > "$OUTPUT_DIR/status-b.txt" &

wait

echo "=== response A ==="
cat "$OUTPUT_DIR/status-a.txt" "$OUTPUT_DIR/headers-a.txt" "$OUTPUT_DIR/body-a.json"
echo
echo "=== response B ==="
cat "$OUTPUT_DIR/status-b.txt" "$OUTPUT_DIR/headers-b.txt" "$OUTPUT_DIR/body-b.json"
echo
echo "Saved responses under $OUTPUT_DIR"
