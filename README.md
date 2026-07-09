# point-payment-lab

기존 쇼핑몰 포인트 결제 방식을 Java/Spring Boot로 재현하는 실험 프로젝트입니다.

목표는 먼저 기존 방식의 흐름을 그대로 따라 구현한 뒤, 같은 `orderId`로 동시에 결제 요청이 들어올 때 외부 바우처 발행 API가 중복 호출될 수 있는 문제를 확인하고, 이후 API 서버 레벨 멱등성 개선을 적용하는 것입니다.

## 실행 준비

```bash
docker compose up -d
```

이 프로젝트는 Gradle 기반이며, Gradle Wrapper를 포함합니다.

```bash
./gradlew bootRun
```

## 구현되어있는 내용

### 1. 포인트 결제 legacy 흐름 재현

`POST /api/payments/point/legacy` API로 전액 포인트 결제를 처리합니다.

현재 구현은 카드/PG 결제가 아니라 포인트만 사용하는 결제 흐름입니다. 요청으로 전달된 `pointWalletUid`, `voucherProductId`, `pointBalanceId`, `point`를 기준으로 지갑, 상품, 잔액, 사용 가능한 포인트 묶음을 조회합니다.

결제 흐름은 다음 순서로 동작합니다.

```text
결제 요청 수신
-> 지갑 조회
-> 바우처 상품 조회
-> 포인트 잔액 조회
-> 외부 바우처 발행 API 호출
-> 내부 DB transaction 실행
-> 구매 이력 저장
-> 포인트 원장 저장
-> 총 포인트 잔액 차감
-> 출처별 포인트 잔액 차감
-> 사용된 포인트 묶음에 바우처 번호 연결
```

### 2. 외부 바우처 제공사 API mock 구현

실제 외부 바우처/쿠폰 발행사를 대신해 mock API를 구현했습니다.

- `POST /mock/voucher-provider/vouchers/issue`
- `POST /mock/voucher-provider/vouchers/cancel`

발행 API가 호출되면 `provider_voucher` 테이블에 발행 이력을 저장합니다. 이 테이블은 실제 내부 결제 DB라기보다, 외부 시스템이 처리한 결과를 관찰하기 위한 실험용 저장소 역할을 합니다.

이 구조 덕분에 같은 `orderId` 요청이 동시에 들어왔을 때 내부 DB에는 하나만 저장되더라도 외부 바우처 발행 호출이 여러 번 일어났는지 확인할 수 있습니다.

### 3. 내부 DB transaction 처리[VoucherPurchase.java](src%2Fmain%2Fjava%2Fcom%2Fpaymentlab%2Fvoucher%2Fpayment%2Fdomain%2FVoucherPurchase.java)

외부 바우처 발행이 성공한 뒤 내부 DB 변경은 Spring `TransactionTemplate`으로 묶어 처리합니다.

transaction 안에서 처리되는 주요 변경은 다음과 같습니다.

- `voucher_purchase`: 바우처 구매/결제 결과 저장
- `point_ledger`: 포인트 사용 이력 저장
- `point_balance`: 총 포인트 잔액 차감
- `point_source_balance`: 출처별 포인트 잔액 차감
- `point_lot`: 사용된 포인트 묶음 상태 변경

내부 DB 저장 중 예외가 발생하면 transaction은 롤백됩니다. 또한 현재 legacy 구현은 DB 저장 실패 시 이미 발행된 외부 바우처를 취소하기 위해 외부 취소 API를 호출합니다.

### 4. DB unique 제약을 통한 중복 저장 방어

`voucher_purchase.order_id`에 unique 제약을 걸어 같은 거래번호가 내부 구매 이력에 중복 저장되지 않도록 했습니다.

즉, 동시에 같은 `orderId` 요청이 들어와도 내부 결제 결과는 하나만 성공하도록 DB 레벨 방어가 존재합니다. 다만 이 방어는 외부 바우처 API 호출 이후에 동작합니다.

### 5. 포인트 환불 legacy 흐름 재현

`POST /api/refunds/point/legacy` API로 포인트 결제 환불을 처리합니다.

환불 흐름은 다음 순서로 동작합니다.

```text
환불 요청 수신
-> voucherNumber 기준 구매 이력 조회
-> 결제 당시 포인트 원장 조회
-> 사용 처리된 포인트 묶음 조회
-> 내부 DB transaction 실행
-> 구매 이력 취소 상태 변경
-> 총 포인트 잔액 복구
-> 출처별 포인트 잔액 복구
-> 포인트 묶음 사용 상태 해제
-> 환불 원장 저장
-> 환불성 포인트 입금 기록 저장
-> 외부 바우처 취소 API 호출
```

현재 환불 구현은 포인트 복구 자체는 처리하지만, 외부 바우처 취소 API 호출이 내부 transaction 이후에 실행됩니다. 따라서 외부 취소 API 실패 시 내부 DB는 이미 환불 처리되었는데 외부 바우처는 아직 유효한 상태가 될 수 있습니다.

## 핵심 API

바우처 상품 등록 API:

```http
POST /api/admin/voucher-products
Content-Type: application/json

{
  "voucherProductCode": "VOUCHER-CHICKEN-20000",
  "voucherName": "치킨 2만원권",
  "sellPrice": 20000,
  "useTerm": 366
}
```

바우처 상품 목록 조회 API:

```http
GET /api/admin/voucher-products
```

기존 방식 재현 결제 API:

```http
POST /api/payments/point/legacy
Content-Type: application/json

{
  "orderId": "AL123456789",
  "pointWalletUid": "point-wallet-001",
  "voucherProductId": 1,
  "pointBalanceId": 1,
  "point": 5000
}
```

외부 바우처 제공사 API mock:

- `POST /mock/voucher-provider/vouchers/issue`
- `POST /mock/voucher-provider/vouchers/cancel`

기존 방식 재현 환불 API:

```http
POST /api/refunds/point/legacy
Content-Type: application/json

{
  "voucherNumber": "CP-..."
}
```

## 재현하려는 기존 방식

```text
결제 요청 수신
-> 상품/지갑/잔액/포인트 묶음 조회
-> 외부 바우처 발행 API 먼저 호출
-> 바우처 발행 성공 시 내부 DB transaction 실행
-> VoucherPurchase, PointLedger, PointBalance, PointSourceBalance, PointLot 변경
-> DB transaction 실패 시 외부 바우처 취소 API 호출
```

이 방식은 DB의 `voucher_purchase.order_id` unique 제약으로 내부 중복 저장은 막을 수 있지만, 외부 API 호출 전에 `orderId`를 선점하지 않기 때문에 외부 바우처 API가 중복 호출될 수 있습니다.

## 따닥 결제 재현

애플리케이션 실행 후 아래 스크립트를 실행합니다.

```bash
bash scripts/run-duplicate-payment-test.sh AL-DUPLICATE-001
```

그 다음 mock 외부 바우처 테이블을 확인합니다.

```bash
docker exec -it point-payment-lab-mysql mysql -ulab -plab point_payment_lab \
  -e "select id, order_id, voucher_number, status from provider_voucher where order_id='AL-DUPLICATE-001';"
```

기대하는 관찰 포인트:

- 프론트 버튼 방어가 없는 API 직접 호출에서는 같은 `orderId` 요청이 동시에 들어올 수 있습니다.
- API 서버가 외부 API 호출 전에 `orderId`를 선점하지 않으므로 mock 바우처 발행 API가 두 번 호출될 수 있습니다.
- DB의 `voucher_purchase.order_id` unique 제약 때문에 내부 구매 이력은 하나만 성공합니다.
- 그러나 `provider_voucher`에는 같은 `order_id`로 발행된 바우처 호출 흔적이 둘 이상 남을 수 있습니다.

이 상태가 이후 `PaymentAttempt` 테이블과 API 서버 멱등성 처리로 개선할 대상입니다.

## 향후 개선해야할 문제점

### 1. 외부 API 호출 전 중복 요청 선점 필요

현재 legacy 구현은 외부 바우처 발행 API를 먼저 호출하고, 그 뒤 내부 DB transaction에서 `voucher_purchase.order_id`를 저장합니다.

그래서 같은 `orderId` 요청이 거의 동시에 두 번 들어오면 다음 문제가 생길 수 있습니다.

```text
요청 A -> 외부 바우처 발행 성공 -> 내부 DB 저장 성공
요청 B -> 외부 바우처 발행 성공 -> 내부 DB 저장 실패(order_id unique 위반)
```

내부 DB 중복 저장은 막히지만, 외부 바우처 발행 API는 이미 두 번 호출될 수 있습니다. 개선하려면 외부 API 호출 전에 `PaymentAttempt` 같은 테이블에 `orderId`를 먼저 저장해 요청을 선점해야 합니다.

### 2. API 서버 레벨 멱등성 처리 필요

DB unique 제약은 마지막 방어선입니다. API 서버에서도 같은 `orderId`나 `Idempotency-Key`에 대해 이미 처리 중인지, 성공했는지, 실패했는지 구분해야 합니다.

개선 방향은 다음과 같습니다.

- `orderId` 또는 `idempotencyKey`를 먼저 저장
- 상태를 `PROCESSING`, `SUCCESS`, `FAILED` 등으로 관리
- 같은 요청이 다시 들어오면 외부 API를 재호출하지 않고 기존 처리 결과를 반환
- 처리 중인 요청이면 409 또는 별도 응답으로 중복 진행을 막음

### 3. 외부 API 타임아웃/무응답 처리 필요

현재 mock 구현은 정상 응답 중심입니다. 실제 외부 API는 다음 상황이 발생할 수 있습니다.

- 요청은 성공했지만 응답이 오지 않음
- 네트워크 타임아웃 발생
- 외부 시스템은 발행 성공, 내부 서버는 실패로 판단
- 같은 요청 재시도로 바우처가 중복 발행됨

개선하려면 외부 API에 전달하는 거래 식별자를 멱등키로 사용하고, 타임아웃 시 바로 재발행하지 말고 상태 조회 API 또는 보상 처리 흐름을 둬야 합니다.

### 4. 환불 transaction 경계 개선 필요

현재 환불은 내부 DB transaction을 먼저 성공시킨 뒤 외부 바우처 취소 API를 호출합니다.

이 구조에서는 외부 취소 API가 실패하면 내부 DB는 환불 완료인데 외부 바우처는 취소되지 않은 불일치가 생길 수 있습니다.

개선 방향은 다음과 같습니다.

- 환불 요청 상태 테이블 추가
- 환불 상태를 `REQUESTED`, `CANCELING_PROVIDER`, `REFUNDED`, `FAILED`로 관리
- 외부 취소 성공 후 내부 포인트 복구
- 또는 내부 상태를 pending으로 둔 뒤 외부 취소 성공 이벤트/재시도 작업으로 최종 확정

### 5. 포인트 부족 검증 순서 개선 필요

현재 구현은 사용 가능한 포인트 묶음을 순회하며 차감한 뒤, 사용량이 요청 포인트보다 적으면 예외를 던집니다. transaction으로 내부 변경은 롤백되지만, 외부 바우처는 이미 발행된 뒤입니다.

개선하려면 외부 바우처 발행 전에 다음 검증을 먼저 끝내야 합니다.

- 총 잔액이 충분한지 확인
- 사용 가능한 `point_lot` 합계가 충분한지 확인
- 상품 가격과 요청 포인트가 일치하는지 확인
- 만료된 포인트가 포함되지 않았는지 확인

### 6. 동시성 제어 보강 필요

같은 사용자가 동시에 여러 결제를 시도하면 포인트 잔액과 포인트 묶음을 동시에 차감하려는 경쟁 상태가 생길 수 있습니다.

개선 방향은 다음과 같습니다.

- `point_balance` 또는 결제 시 사용할 포인트 묶음에 row lock 적용
- 낙관적 락 버전 컬럼 추가
- 잔액 차감 SQL을 조건부 update로 처리
- 차감 전후 잔액 검증 강화

### 7. 실패 보상과 재처리 작업 필요

결제/환불은 내부 DB와 외부 API가 함께 움직이기 때문에 한쪽만 성공하는 상태가 생길 수 있습니다.

현재 결제 legacy 구현은 외부 바우처 발행 성공 후 내부 DB transaction이 실패하면 `voucherProviderClient.cancel()`로 외부 취소 API를 호출합니다. 하지만 이 취소 API도 외부 API이므로 실패하거나 타임아웃될 수 있습니다. 따라서 "취소 API를 호출했다"만으로 보상 처리가 완전히 끝났다고 볼 수 없습니다.

개선하려면 다음 같은 운영성 장치가 필요합니다.

- 외부 발행 성공, 내부 DB 실패 시 취소 재시도 작업
- 외부 취소 API 실패/타임아웃 시 실패 이력 저장
- 내부 환불 성공, 외부 취소 실패 시 취소 재시도 작업
- 실패 상태를 남기는 outbox 또는 retry 테이블
- 관리자 확인용 실패 이력 조회 API
# point-payment-lab
