# Legacy 결제 문제와 단계별 개선 다이어그램 안내

## diagrams.net 원본

- [결제프로젝트 diagrams.net](https://app.diagrams.net/?splash=0#G15VCYdDP6PvsHo-a5TCzJpYTm5Hfbrb9A#%7B%22pageId%22%3A%224spT96WhYQGijNEeaSWF%22%7D)

이 다이어그램은 이전 결제 흐름의 한계가 어떤 실행 순서에서 발생했는지와,
각 개선 단계가 어느 계층에 방어선을 추가했는지를 포트폴리오에서 설명하기
위한 자료다.

## 페이지 구성

### 01-Legacy 문제 발생 흐름

세 가지 핵심 문제를 같은 출발점에서 분기해 표현한다.

1. 동일 `orderId`의 따닥 결제
   - 두 요청 모두 외부 issue API 호출
   - `provider_voucher` 두 건 발행
   - 동일 포인트와 `voucher_purchase` unique key를 두 트랜잭션이 변경
   - Lock 획득 순서 충돌과 Deadlock
   - 한 건 rollback 후 외부 바우처 보상 취소
2. 잔액 부족 결제
   - 잔액과 사용 가능한 lot을 검증하기 전에 외부 바우처 발행
   - 내부 트랜잭션에서 포인트 부족 발견
   - DB rollback과 HTTP 500
   - 이미 발행한 바우처를 보상 취소
3. 외부 API와 DB 트랜잭션의 경계
   - MySQL rollback은 외부 발행 결과를 되돌리지 못함
   - 보상 cancel API도 네트워크 오류로 실패할 수 있음
   - 최악에는 외부 `ISSUED`, 내부 구매 기록 없음 상태가 발생

### 02-단계별 개선과 검증 결과

문제, 개선 장치, 실제 검증 결과를 한 줄로 연결한다.

| 단계 | 문제 | 개선 장치 | 실제 결과 |
| --- | --- | --- | --- |
| 1차 | 쇼핑몰의 동일 주문 중복 실행 | `payment_attempt.order_id` unique와 `PROCESSING` 선점 | 외부 발행 2→1, Deadlock 1→0 |
| 2차 | 외부 Mock API 자체의 중복 발행 | `provider_voucher.order_id` unique와 기존 번호·PIN replay | 동시 issue 2회, 실제 바우처 1장 |
| 3차 | 잔액 부족도 외부 발행 후 취소 | 상품 가격·총 잔액·미만료 lot 사전 검증 | HTTP 500→422, issue/cancel 1→0 |
| 다음 | 다중 서버 멱등성, 서로 다른 주문의 잔액 경쟁 | Redis cache, Redisson 분산락, DB unique, 조건부 UPDATE/row lock | 정확한 replay와 잔액 정합성 검증 예정 |

### 03~07 API 요청·응답 시퀀스

예시와 같은 세로 생명선 형태로 클라이언트, 쇼핑몰 API, 외부 발행사,
Redis와 MySQL 사이의 실제 요청 순서를 단계별로 분리했다.

| 페이지 | 상태 | 표현한 핵심 흐름 |
| --- | --- | --- |
| `03-개선 전 Legacy API 흐름` | 재현 완료 | 같은 `orderId` 두 요청이 외부 발행을 각각 수행한 뒤 DB Deadlock, HTTP 500, 보상 취소로 이어지는 흐름 |
| `04-1차 PaymentAttempt API 흐름` | 구현·검증 완료 | `PROCESSING` 선점, 동시 중복의 HTTP 409 차단, 완료 결과의 HTTP 200 replay |
| `05-2차 외부 발행 멱등 API 흐름` | 구현·검증 완료 | 최초 발행 HTTP 201, 동일 요청 HTTP 200 replay, 다른 상품의 HTTP 409 거절 |
| `06-3차 외부 호출 전 검증 API 흐름` | 구현·검증 완료 | 상품 가격·잔액·미만료 lot 검증 실패 시 외부 호출 없이 HTTP 422 반환 |
| `07-4차 Redis 분산락 API 흐름 (설계안)` | 미구현 | 두 API 서버, Redis 분산락·응답 캐시, DB 이중 확인·조건부 차감의 목표 흐름 |

3차 페이지에는 사전 조회와 실제 차감 사이의 경쟁 조건이 아직 남아 있음을
표시했다. 4차 페이지는 구현 결과로 오해하지 않도록 페이지 이름과 본문의
노트에 모두 `설계안 · 아직 미구현`이라고 명시했다.

### 08~10 테이블 명세서와 ERD

| 페이지 | 내용 |
| --- | --- |
| `08-포인트 영역 테이블 명세서` | 포인트 지갑·총잔액·출처별 잔액·lot·원장·환불 입금의 컬럼과 역할 |
| `09-바우처·멱등성 테이블 명세서` | 상품·구매·외부 발행·결제 시도의 컬럼과 역할, 바우처 번호와 PIN의 차이 |
| `10-전체 결제·환불 ERD` | Flyway V1~V5에 선언된 실제 FK 관계와 전체 컬럼 |

명세서에서 점선으로 표시한 `payment_attempt` 및 `provider_voucher` 연결은
`order_id`와 `voucher_number`를 통한 업무상 논리 연결이다. 실제 FK가 아니므로
전체 ERD에서는 관계선으로 표시하지 않았다.

## 색상 의미

| 색상 | 의미 |
| --- | --- |
| 빨강/분홍 | 문제, 실패, 충돌, 최종 이상 상태 |
| 주황 | 외부 바우처 API와 외부 시스템 상태 |
| 초록 | 내부 DB 처리 또는 검증 완료 결과 |
| 파랑 | 적용한 개선 장치 |
| 보라 | 앞으로 구현할 Redis·분산락·잔액 동시성 개선 |
| 검정 | 전체 흐름의 핵심 주제 |

## 다이어그램의 근거 자료

- `docs/payment-idempotency-improvement-result.md`
- `docs/provider-voucher-idempotency-improvement-plan.md`
- `docs/redis-payment-idempotency-design.md`
- `evidence/duplicate-payment-legacy/`
- `evidence/provider-issue-idempotency/`
- `evidence/insufficient-balance-legacy/`
- `evidence/insufficient-balance-improved/`

## 포트폴리오 설명 순서

```text
Legacy 실행 순서를 먼저 보여준다
→ 외부 발행이 너무 이른 시점에 실행된다는 공통 원인을 설명한다
→ 동일 주문, 잔액 부족, 외부/DB 경계 문제로 분기한다
→ 각 문제에 추가한 방어선을 단계별 개선 페이지에서 연결한다
→ 실제 HTTP·DB 검증 수치로 개선 효과를 증명한다
→ Redis와 조건부 UPDATE는 서로 다른 남은 문제를 해결한다고 구분한다
```

Redis 분산락은 동일 멱등키의 여러 서버 동시 실행을 억제하고, 조건부 UPDATE
또는 row lock은 서로 다른 주문이 같은 잔액을 동시에 사용하는 문제를
보호한다. 두 개선을 같은 역할로 설명하지 않도록 주의한다.
