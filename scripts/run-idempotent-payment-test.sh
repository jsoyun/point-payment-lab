#!/usr/bin/env bash
set -euo pipefail

ORDER_ID="${1:-AL-IDEMPOTENT-001}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
URL="$BASE_URL/api/payments/point/idempotent"
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

echo "Sending idempotent payment requests with orderId=$ORDER_ID"

curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  "$URL" > /tmp/point-payment-lab-idempotent-a.out &

curl -sS -w "\nHTTP %{http_code}\n" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  "$URL" > /tmp/point-payment-lab-idempotent-b.out &

wait

echo "=== response A ==="
cat /tmp/point-payment-lab-idempotent-a.out
echo
echo "=== response B ==="
cat /tmp/point-payment-lab-idempotent-b.out
echo
echo "Expected: one HTTP 201 and one HTTP 409 PROCESSING response."
echo "A later retry with the same payload should return HTTP 200 and the stored voucher."
