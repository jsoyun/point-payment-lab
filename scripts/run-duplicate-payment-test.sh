#!/usr/bin/env bash
set -euo pipefail

ORDER_ID="${1:-AL-DUPLICATE-001}"
URL="http://localhost:8080/api/payments/point/legacy"
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

echo "Sending duplicate payment requests with orderId=$ORDER_ID"

curl -s -w "\nHTTP %{http_code}\n" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  "$URL" > /tmp/point-payment-lab-a.out &

curl -s -w "\nHTTP %{http_code}\n" \
  -H "Content-Type: application/json" \
  -d "$BODY" \
  "$URL" > /tmp/point-payment-lab-b.out &

wait

echo "=== response A ==="
cat /tmp/point-payment-lab-a.out
echo
echo "=== response B ==="
cat /tmp/point-payment-lab-b.out
echo
echo "Check provider_voucher table. Same order_id may have multiple issued/canceled rows:"
echo "docker exec -it point-payment-lab-mysql mysql -ulab -plab point_payment_lab -e \"select id, order_id, voucher_number, status from provider_voucher where order_id='$ORDER_ID';\""
