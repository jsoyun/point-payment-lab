#!/usr/bin/env bash
set -euo pipefail

ORDER_ID="${1:-PROVIDER-DUPLICATE-001}"
PRODUCT_CODE="${2:-VOUCHER-COFFEE-5000}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
URL="$BASE_URL/mock/voucher-provider/vouchers/issue"
OUTPUT_DIR="${OUTPUT_DIR:-evidence/provider-issue-idempotency}"
BODY=$(cat <<JSON
{
  "voucherProductCode": "$PRODUCT_CODE",
  "orderId": "$ORDER_ID"
}
JSON
)

mkdir -p "$OUTPUT_DIR"

echo "Sending duplicate provider issue requests with orderId=$ORDER_ID"

curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  "$URL" > "$OUTPUT_DIR/response-a.txt" &

curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  "$URL" > "$OUTPUT_DIR/response-b.txt" &

wait

echo "=== response A ==="
cat "$OUTPUT_DIR/response-a.txt"
echo
echo "=== response B ==="
cat "$OUTPUT_DIR/response-b.txt"
echo
echo "Saved responses under $OUTPUT_DIR"
