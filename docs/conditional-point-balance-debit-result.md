# 조건부 UPDATE 기반 포인트 중복 차감 방어

## 해결하려는 문제

Redis 멱등성은 **같은 Idempotency-Key**의 중복 실행을 막는다. 하지만 사용자가
서로 다른 주문과 서로 다른 멱등키로 같은 지갑의 마지막 5,000점을 동시에
사용하면 두 요청은 서로 다른 Redis lock을 사용한다.

기존 check-then-act 흐름에서는 두 요청이 모두 잔액 5,000을 읽고 사전 검증을
통과할 수 있다.

```text
요청 A: balance=5000 확인 ─┐
                           ├─ 둘 다 결제 가능하다고 판단
요청 B: balance=5000 확인 ─┘
```

## 개선 방법

Redis 지갑 lock을 하나 더 추가하지 않고 MySQL 조건부 UPDATE를 정확성의 기준으로
사용했다.

```sql
update point_balance
   set balance = cast(balance as signed) - :amount
 where id = :pointBalanceId
   and point_wallet_id = :pointWalletId
   and cast(balance as signed) >= :amount;
```

변경 행이 1개면 이 트랜잭션이 잔액을 선점한 것이고, 0개면 다른 트랜잭션이
먼저 사용했거나 잔액이 부족한 것이다.

MySQL InnoDB는 UPDATE 대상 행에 배타적 row lock을 잡는다. 같은 `point_balance`
행을 갱신하는 두 번째 트랜잭션은 첫 번째 트랜잭션 종료까지 기다린 다음 최신
잔액으로 WHERE 조건을 다시 평가한다. 첫 결제가 5,000점을 모두 사용했다면
두 번째 UPDATE의 affected rows는 0이다.

## 적용 범위

- 문제 재현용 `POST /api/payments/point/legacy`는 기존 read-modify-write를 유지한다.
- DB-only 1차 개선 API도 기존 비교 기준으로 유지한다.
- `POST /api/payments/point/redis-idempotent`의 실제 결제 실행과 Redis 장애 DB
  fallback 경로에 조건부 차감을 적용했다.

조건부 UPDATE가 트랜잭션의 첫 잔액 변경이므로 같은 잔액을 사용하는 결제들은
이 지점에서 직렬화된다. lock을 획득한 요청만 point lot, source balance, ledger,
purchase를 이어서 변경한다.

## 실제 동시성 검증

초기 잔액 5,000점에 서로 다른 두 주문을 동시에 전송했다.

```text
ORDER-BALANCE-RACE-001-A / KEY-A
ORDER-BALANCE-RACE-001-B / KEY-B
```

결과:

| 항목 | 요청 A | 요청 B |
| --- | --- | --- |
| HTTP | 409 | 201 |
| 결과 | `POINT_BALANCE_CONFLICT` | 결제 성공 |
| `payment_attempt` | FAILED | SUCCEEDED |
| 외부 바우처 | CANCELED | ISSUED |
| 내부 구매 | 없음 | 1건 |

최종 DB 상태:

```text
point_balance.balance = 0
성공 주문 voucher_purchase = 1건
성공 주문 provider_voucher ISSUED = 1건
실패 주문 provider_voucher CANCELED = 1건
사용된 point_lot = 5,000점 한 묶음
```

두 요청 모두 사전 검증 후 외부 쿠폰을 발행했기 때문에 경쟁에서 진 요청은 DB
트랜잭션 실패 후 외부 쿠폰을 보상 취소했다. 포인트 중복 사용은 방지했지만 외부
발행·취소 비용은 남는다. 이를 없애려면 외부 호출 전에 포인트를 예약하는 별도
상태와 실패 복구 설계가 필요하며, DB 트랜잭션을 외부 API 호출 동안 오래 잡는
방식은 피해야 한다.

## 코드 위치

- 조건부 SQL: `PointBalanceRepository.debitIfSufficient`
- affected rows 판정: `PointBalanceDebitService`
- Redis 결제 경로 적용: `LegacyPointPaymentService.payWithConditionalDebit`
- 재현 스크립트: `scripts/run-competing-balance-test.sh`
- 증거: `evidence/conditional-balance-debit/`

자동 테스트에서는 조건부 UPDATE의 affected rows가 1일 때 갱신 잔액을 반환하고,
0일 때 `POINT_BALANCE_CONFLICT`를 발생시키는 두 경로를 검증했다. 전체 Gradle
테스트 22개가 성공했다. 실제 InnoDB lock과 동시 실행 결과는 위 수동 동시 요청
검증으로 보완했다.

## 남은 데이터 모델 개선

현재 legacy 스키마의 `balance`가 `varchar`여서 SQL에서 숫자 CAST가 필요하다.
금액 컬럼은 운영 설계에서 `BIGINT` 또는 요구 정밀도에 맞는 `DECIMAL`로 migration
해야 타입 안정성, 인덱스 활용, 잘못된 문자열 저장 방지를 확보할 수 있다.
