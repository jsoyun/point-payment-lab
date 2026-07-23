# 테이블 구조와 로직 시각화

이 문서는 `point-payment-lab`의 테이블 관계와 포인트 결제/환불 흐름을 그림으로 정리한다.

Mermaid를 지원하는 Markdown 뷰어에서 열면 다이어그램으로 볼 수 있다.

## 1. 전체 구성

```mermaid
flowchart LR
  Client["Client\ncurl / Postman / App"] --> PaymentApi["PointPaymentController\n/api/payments/point/legacy"]
  Client --> RefundApi["PointRefundController\n/api/refunds/point/legacy"]

  PaymentApi --> PaymentService["LegacyPointPaymentService"]
  RefundApi --> RefundService["LegacyPointRefundService"]

  PaymentService --> ProviderClient["VoucherProviderClient"]
  RefundService --> ProviderClient

  ProviderClient --> MockProvider["MockVoucherProviderController\n/mock/voucher-provider/vouchers"]

  PaymentService --> DB["MySQL\npoint_payment_lab"]
  RefundService --> DB
  MockProvider --> DB
```

역할을 나누면 다음과 같다.

| 영역 | 역할 |
| --- | --- |
| `payment` | 포인트 결제 요청 처리, 포인트 차감, 결제 이력 저장 |
| `refund` | 포인트 환불 요청 처리, 포인트 복구, 환불 이력 저장 |
| `provider` | 외부 바우처 제공사 API 호출 및 mock API 제공 |
| `common` | 금액 계산, 공통 예외 처리 |
| `db/migration` | 테이블 생성, seed 데이터 입력, comment 추가 |

## 2. 테이블 관계 ERD

```mermaid
erDiagram
  POINT_WALLET ||--o{ POINT_BALANCE : "has"
  POINT_WALLET ||--o{ POINT_SOURCE_BALANCE : "has"
  POINT_WALLET ||--o{ POINT_LOT : "owns"
  POINT_WALLET ||--o{ POINT_LEDGER : "records"

  POINT_BALANCE ||--o{ POINT_SOURCE_BALANCE : "groups"
  POINT_BALANCE ||--o{ POINT_LOT : "contains"
  POINT_BALANCE ||--o{ POINT_LEDGER : "records"

  POINT_SOURCE_BALANCE ||--o{ POINT_LOT : "splits into"

  VOUCHER_PRODUCT ||--o{ LIMITED_DEAL : "has limit"
  VOUCHER_PRODUCT ||--o{ VOUCHER_PURCHASE : "purchased as"

  POINT_LEDGER ||--o| VOUCHER_PURCHASE : "withdrawal ledger"
  POINT_LEDGER ||--o| POINT_CREDIT : "credit ledger"

  VOUCHER_PURCHASE ||--o{ POINT_LOT : "uses voucher_number"

  PROVIDER_VOUCHER }o--|| VOUCHER_PRODUCT : "uses product code"

  POINT_WALLET {
    bigint id PK
    varchar point_wallet_uid UK
    datetime created_at
  }

  VOUCHER_PRODUCT {
    bigint id PK
    varchar voucher_product_code UK
    varchar voucher_name
    bigint sell_price
    int use_term
  }

  LIMITED_DEAL {
    bigint id PK
    bigint voucher_product_id FK
    int total_purchase_limit
    int individual_purchase_limit
  }

  POINT_BALANCE {
    bigint id PK
    bigint point_wallet_id FK
    varchar balance
  }

  POINT_SOURCE_BALANCE {
    bigint id PK
    bigint point_wallet_id FK
    bigint point_balance_id FK
    varchar balance
  }

  POINT_LOT {
    bigint id PK
    bigint point_wallet_id FK
    bigint point_source_balance_id FK
    bigint point_balance_id FK
    varchar amount
    datetime expires_at
    varchar voucher_number
    varchar status
  }

  POINT_LEDGER {
    bigint id PK
    bigint point_wallet_id FK
    bigint point_balance_id FK
    varchar state
    varchar amount
    varchar balance
    varchar title
    datetime occurred_at
  }

  VOUCHER_PURCHASE {
    bigint id PK
    varchar voucher_number UK
    varchar pin_number
    varchar order_id UK
    bigint voucher_product_id FK
    bigint point_ledger_id FK
    varchar payment_type
    bigint point_amount
    bigint card_amount
    varchar issue_status
    varchar use_status
  }

  POINT_CREDIT {
    bigint id PK
    varchar type
    varchar request_uid UK
    varchar value
    bigint point_ledger_id FK
  }

  PROVIDER_VOUCHER {
    bigint id PK
    varchar voucher_product_code
    varchar voucher_number UK
    varchar pin_number
    varchar order_id
    varchar status
  }
```

## 3. 테이블 역할 요약

| 테이블 | 역할 | 데이터 생성/변경 시점 |
| --- | --- | --- |
| `point_wallet` | 사용자 포인트 지갑 | seed 데이터로 생성 |
| `voucher_product` | 구매 가능한 바우처 상품 | seed 데이터로 생성 |
| `limited_deal` | 한정 판매 제한 정보 | 현재 흐름에서는 직접 사용하지 않음 |
| `point_balance` | 지갑 기준 총 포인트 잔액 | seed 생성, 결제 시 차감, 환불 시 복구 |
| `point_source_balance` | 포인트 출처별 잔액 | seed 생성, 결제 시 차감, 환불 시 복구 |
| `point_lot` | 만료일이 있는 포인트 묶음 | seed 생성, 결제 시 `USED`, 환불 시 복구 |
| `provider_voucher` | 외부 바우처 API mock 결과 | 외부 발행 API 호출 시 생성, 취소 API 호출 시 `CANCELED` |
| `voucher_purchase` | 내부 구매/결제 결과 | 결제 transaction 안에서 생성, 환불 시 취소 상태 변경 |
| `point_ledger` | 포인트 사용/환불 원장 | 결제 시 `WITHDRAWAL`, 환불 시 `RETURN` 생성 |
| `point_credit` | 환불성 포인트 입금 기록 | 환불 transaction 안에서 생성 |

## 4. 정상 포인트 결제 시퀀스

```mermaid
sequenceDiagram
  autonumber
  actor User as User / Client
  participant PaymentApi as PointPaymentController
  participant PaymentService as LegacyPointPaymentService
  participant ProviderClient as VoucherProviderClient
  participant MockProvider as MockVoucherProviderController
  participant DB as MySQL

  User->>PaymentApi: POST /api/payments/point/legacy
  PaymentApi->>PaymentService: pay(request)

  PaymentService->>DB: point_wallet 조회
  PaymentService->>DB: voucher_product 조회
  PaymentService->>DB: point_balance 조회
  PaymentService->>PaymentService: point_balance가 point_wallet 소유인지 검증

  PaymentService->>ProviderClient: issue(voucherProductCode, orderId)
  ProviderClient->>MockProvider: POST /vouchers/issue
  MockProvider->>DB: provider_voucher insert
  MockProvider-->>ProviderClient: voucherNumber, pinNumber
  ProviderClient-->>PaymentService: issuedVoucher

  PaymentService->>PaymentService: transactionTemplate.execute()
  PaymentService->>DB: 사용 가능한 point_lot 조회
  PaymentService->>DB: point_lot status=USED, voucher_number 연결
  PaymentService->>DB: point_source_balance 차감
  PaymentService->>DB: point_balance 차감
  PaymentService->>DB: point_ledger insert, state=WITHDRAWAL
  PaymentService->>DB: voucher_purchase insert
  PaymentService-->>PaymentApi: PointPaymentResponse
  PaymentApi-->>User: 201 Created
```

핵심 포인트:

```text
외부 바우처 발행 API 호출
-> provider_voucher insert
-> 그 다음 내부 DB transaction 실행
```

즉 외부 발행은 DB transaction 밖에서 먼저 일어난다.

## 5. 정상 포인트 결제 플로우차트

```mermaid
flowchart TD
  Start["결제 요청 수신"] --> FindWallet["point_wallet 조회\npointWalletUid"]
  FindWallet --> FindProduct["voucher_product 조회\nvoucherProductId"]
  FindProduct --> FindBalance["point_balance 조회\npointBalanceId"]
  FindBalance --> CheckOwner{"point_balance가\npoint_wallet 소유인가?"}

  CheckOwner -- No --> BadRequest["예외 발생\npoint balance does not belong to point wallet"]
  CheckOwner -- Yes --> IssueVoucher["외부 바우처 발행 API 호출"]

  IssueVoucher --> ProviderInsert["provider_voucher insert"]
  ProviderInsert --> TxStart["DB transaction 시작\ntransactionTemplate.execute"]

  TxStart --> FindLots["사용 가능한 point_lot 조회\n만료일 빠른 순"]
  FindLots --> UseLots["point_lot 사용 처리\nstatus=USED\nvoucher_number 연결"]
  UseLots --> SubSource["point_source_balance 차감"]
  SubSource --> SubBalance["point_balance 차감"]
  SubBalance --> InsertLedger["point_ledger insert\nstate=WITHDRAWAL"]
  InsertLedger --> InsertPurchase["voucher_purchase insert"]
  InsertPurchase --> CheckUsed{"사용된 point_lot 합계가\n요청 point 이상인가?"}

  CheckUsed -- Yes --> Commit["transaction commit"]
  Commit --> Success["결제 성공 응답"]

  CheckUsed -- No --> Rollback["transaction rollback"]
  InsertPurchase -. "unique 위반 등 DB 실패" .-> Rollback
  Rollback --> CancelVoucher["외부 바우처 취소 API 호출"]
  CancelVoucher --> Fail["결제 실패 응답"]
```

## 6. 결제 실패와 보상 취소 흐름

```mermaid
flowchart TD
  IssueOk["외부 바우처 발행 성공"] --> Tx["내부 DB transaction 실행"]
  Tx --> DbFail{"DB 작업 실패?"}

  DbFail -- No --> Commit["DB commit"]
  Commit --> Done["결제 성공"]

  DbFail -- "DataIntegrityViolationException" --> DuplicateCatch["catch: unique/FK/not-null 등\nDB 제약 위반"]
  DbFail -- "RuntimeException" --> RuntimeCatch["catch: 일반 런타임 예외\npoint_lot 부족 등"]

  DuplicateCatch --> Cancel1["voucherProviderClient.cancel()"]
  RuntimeCatch --> Cancel2["voucherProviderClient.cancel()"]

  Cancel1 --> CancelResult{"취소 API 성공?"}
  Cancel2 --> CancelResult

  CancelResult -- Yes --> Compensated["외부 바우처 CANCELED"]
  CancelResult -- No --> NeedRetry["개선 필요\n취소 실패 이력 저장\n재시도 작업 필요"]
```

현재 구현은 `cancel()`을 즉시 호출하지만, 취소 API 자체가 실패할 경우를 저장하고 재시도하는 구조는 아직 없다.

## 7. 포인트 환불 시퀀스

```mermaid
sequenceDiagram
  autonumber
  actor User as User / Client
  participant RefundApi as PointRefundController
  participant RefundService as LegacyPointRefundService
  participant ProviderClient as VoucherProviderClient
  participant MockProvider as MockVoucherProviderController
  participant DB as MySQL

  User->>RefundApi: POST /api/refunds/point/legacy
  RefundApi->>RefundService: refund(voucherNumber)

  RefundService->>DB: voucher_purchase 조회
  RefundService->>DB: voucher_product 조회

  RefundService->>RefundService: transactionTemplate.execute()
  RefundService->>DB: 결제 당시 point_ledger 조회
  RefundService->>DB: voucherNumber로 point_lot 조회
  RefundService->>RefundService: refundPoint 합산
  RefundService->>DB: voucher_purchase 상태 CANCELED
  RefundService->>DB: point_balance 복구
  RefundService->>DB: point_source_balance 복구
  RefundService->>DB: point_lot status/voucher_number 복구
  RefundService->>DB: point_ledger insert, state=RETURN
  RefundService->>DB: point_credit insert

  RefundService->>ProviderClient: cancel(voucherProductCode, voucherNumber)
  ProviderClient->>MockProvider: POST /vouchers/cancel
  MockProvider->>DB: provider_voucher status=CANCELED

  RefundService-->>RefundApi: PointRefundResponse
  RefundApi-->>User: 200 OK
```

핵심 포인트:

```text
내부 DB 환불 transaction
-> 그 다음 외부 바우처 취소 API 호출
```

현재 환불 흐름은 내부 DB 환불이 먼저 완료된다. 따라서 외부 취소 API가 실패하면 내부는 환불 완료인데 외부 바우처는 아직 유효한 상태가 될 수 있다.

## 8. 포인트 환불 플로우차트

```mermaid
flowchart TD
  Start["환불 요청 수신\nvoucherNumber"] --> FindPurchase["voucher_purchase 조회"]
  FindPurchase --> FindProduct["voucher_product 조회"]
  FindProduct --> TxStart["DB transaction 시작"]

  TxStart --> FindHistory["결제 당시 point_ledger 조회"]
  FindHistory --> FindLots["voucherNumber로 사용된 point_lot 조회"]
  FindLots --> SumRefund["usedLots.stream()\namount 합산\nrefundPoint 계산"]

  SumRefund --> CancelPurchase["voucher_purchase\nissue_status=CANCELED\nuse_status=CANCELED"]
  CancelPurchase --> AddBalance["point_balance 복구"]
  AddBalance --> RestoreSource["point_source_balance 복구"]
  RestoreSource --> RestoreLot["point_lot 복구\nstatus=NULL\nvoucher_number=NULL"]
  RestoreLot --> ReturnLedger["point_ledger insert\nstate=RETURN"]
  ReturnLedger --> Credit["point_credit insert\ntype=return"]
  Credit --> Commit["transaction commit"]

  Commit --> ProviderCancel["외부 바우처 취소 API 호출"]
  ProviderCancel --> ProviderUpdate["provider_voucher\nstatus=CANCELED"]
  ProviderUpdate --> Success["환불 성공 응답"]
```

## 9. 따닥 결제 문제 시각화

```mermaid
sequenceDiagram
  autonumber
  participant A as Request A
  participant B as Request B
  participant Provider as External Voucher API
  participant DB as MySQL

  A->>Provider: issue(orderId=AL-DUPLICATE-001)
  B->>Provider: issue(orderId=AL-DUPLICATE-001)

  Provider-->>A: voucherNumber A
  Provider-->>B: voucherNumber B

  A->>DB: voucher_purchase insert(order_id)
  DB-->>A: success

  B->>DB: voucher_purchase insert(order_id)
  DB-->>B: unique violation

  B->>Provider: cancel(voucherNumber B)
```

현재 구조에서 DB의 `voucher_purchase.order_id` unique 제약은 내부 중복 저장을 막는다.

하지만 외부 바우처 API 호출은 그 전에 이미 일어난다.

그래서 같은 `orderId` 요청이 동시에 들어오면 외부 바우처가 두 번 발행될 수 있다.

## 10. 개선 방향 시각화

```mermaid
flowchart TD
  Start["결제 요청 수신"] --> Attempt["payment_attempt insert\norderId unique"]
  Attempt --> IsFirst{"최초 요청인가?"}

  IsFirst -- No --> ReturnExisting["기존 처리 상태 조회\nSUCCESS/PROCESSING/FAILED"]
  ReturnExisting --> NoExternalCall["외부 API 재호출하지 않음"]

  IsFirst -- Yes --> Issue["외부 바우처 발행 API 호출"]
  Issue --> Tx["내부 DB transaction"]
  Tx --> Success["payment_attempt 상태 SUCCESS"]
  Tx --> Fail["payment_attempt 상태 FAILED\n보상/재시도 대상 기록"]
```

개선 핵심:

```text
외부 API 호출 전에 orderId를 먼저 선점한다.
```

이렇게 바꾸면 같은 `orderId` 요청이 동시에 들어와도 외부 바우처 API가 중복 호출되는 문제를 줄일 수 있다.
