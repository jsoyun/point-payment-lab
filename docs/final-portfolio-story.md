# Point Payment Lab 최종 포트폴리오 이야기

## 이 문서의 역할

이 문서는 새로운 TODO 목록이 아니다. 포트폴리오에서 프로젝트를 어떤
순서와 메시지로 설명할지 관리하는 서사 기준 문서다.

- 구현 현황과 다음 작업의 기준: `docs/worklog-and-todo.md`
- Redis 상세 설계의 기준: `docs/redis-payment-idempotency-design.md`
- PDF 페이지 구성의 기준: `docs/backend-portfolio-outline.md`
- 최종 발표·PDF 이야기의 기준: 이 문서

기능이 구현되거나 검증 결과가 달라지면 먼저 `worklog-and-todo.md`를
갱신하고, 포트폴리오에 영향을 주는 변화만 이 문서에 반영한다.

## 한 문장 소개

> Legacy 포인트 결제에서 동일 주문의 동시 요청이 외부 쿠폰을 중복 발행하고
> MySQL Deadlock을 일으키는 문제를 재현한 뒤, DB 기반 멱등성을 거쳐
> Redis·Redisson 분산 조율과 결과 캐시까지 단계적으로 개선하고 장애 상황의
> DB fallback을 검증한 프로젝트다.

Redis 구현이 완료되기 전에는 마지막 문장을 다음처럼 사용한다.

> Legacy 포인트 결제의 중복 발행과 MySQL Deadlock을 재현하고 DB 기반
> 멱등성으로 개선했으며, Redis·Redisson을 이용한 다중 서버 멱등성 확장을
> 설계하고 구현 중인 프로젝트다.

## 프로젝트에서 보여줄 핵심 역량

1. Legacy 코드를 바로 교체하지 않고 문제를 재현 가능한 baseline으로 보존했다.
2. API 응답뿐 아니라 외부 호출 수, DB 행, 잔액, lot, Deadlock 로그를 함께 수집했다.
3. 중복 요청, 외부 시스템 멱등성, 잔액 검증을 서로 다른 문제로 분리했다.
4. DB unique를 정확성의 최종 방어선으로 사용했다.
5. Redis를 DB 대체재가 아닌 분산 조율과 빠른 결과 재사용 계층으로 사용한다.
6. Redis 장애와 TTL 만료에도 DB 기록으로 복구되는 구조를 검증한다.

## 이야기 1. Legacy 문제를 직접 재현했다

같은 `orderId`로 결제 요청 두 건을 동시에 보내자 두 요청 모두 외부
바우처 발행 API를 호출했다. 외부에는 쿠폰 두 장이 생성됐고 내부에서는
같은 포인트와 주문 unique key를 변경하는 트랜잭션이 충돌했다.

실제 결과:

```text
요청 A: HTTP 500, MySQL Deadlock
요청 B: HTTP 201, 결제 성공
provider_voucher: ISSUED 1건 + CANCELED 1건
voucher_purchase: 1건
포인트 차감: 1회
```

최종 구매 행이 한 건이라는 사실만 보면 문제가 작아 보이지만, 외부 발행
두 번과 보상 취소 한 번이 이미 발생했다. 이 프로젝트는 최종 DB 상태뿐
아니라 시스템 경계에서 발생한 부작용까지 문제로 정의했다.

## 이야기 2. 1차로 쇼핑몰 주문 실행을 선점했다

외부 API를 호출하기 전에 `payment_attempt.order_id` unique 제약으로
`PROCESSING` 상태를 선점했다. 최초 요청만 결제를 실행하고 동시 중복 요청은
외부 호출 전에 `409 PAYMENT_PROCESSING`으로 차단했다. 완료 후 재요청에는
저장된 바우처 결과를 반환했다.

개선 결과:

```text
외부 발행: 2 → 1
내부 구매: 1 → 1
Deadlock: 1 → 0
보상 취소: 1 → 0
```

이 단계는 동일 주문 중복 실행을 막았지만 쇼핑몰 경로를 우회해 외부 Mock
API를 직접 호출하는 경우까지 보호하지는 않았다.

## 이야기 3. 2차로 외부 발행사도 자신의 멱등성을 보장했다

`provider_voucher.order_id`에 unique 제약을 적용했다. 같은 주문과 같은 상품의
재요청에는 새 쿠폰을 만들지 않고 최초 `voucherNumber`와 `pinNumber`를
반환한다. 같은 주문에 다른 상품을 넣으면 멱등 재시도가 아니므로
`IDEMPOTENCY_KEY_REUSED`로 거절한다.

실제 동시 발행 요청 두 건에서 최초 요청은 HTTP 201, 재사용 요청은 HTTP 200을
받았고 두 응답의 쿠폰 번호와 PIN은 같았다. DB에도 한 행만 저장됐다.

쇼핑몰과 외부 발행사 양쪽에서 방어한 이유는 각 시스템이 자신의 데이터
정합성을 스스로 보장해야 하기 때문이다.

## 이야기 4. 3차로 실패가 확실한 외부 호출을 차단했다

Legacy 흐름은 포인트가 부족해도 외부 쿠폰을 먼저 발행하고 내부 실패 후
취소했다. 이를 개선하기 위해 외부 issue 호출 전에 상품 가격, 총잔액,
미만료·사용 가능 point lot 합계를 검증했다.

결과:

```text
잔액 부족 응답: HTTP 500 → HTTP 422
외부 issue: 1 → 0
외부 cancel: 1 → 0
내부 구매: 0
잔액: 변경 없음
```

다만 사전 조회와 실제 차감 사이에는 다른 주문이 잔액을 먼저 사용할 수
있는 TOCTOU 경쟁이 남아 있다. 이는 Redis 분산락과 다른 문제이며, 향후
조건부 UPDATE 또는 DB row lock으로 해결한다.

## 이야기 5. DB-only 멱등성의 역할과 한계를 확인했다

현재 DB unique 기반 방식은 중복 결제를 정확하게 막는 최종 방어선으로
유효하다. 그러나 여러 애플리케이션 인스턴스의 동일 요청이 모두 DB까지
도달해 unique 충돌을 만들고, 완료 재요청마다 DB를 조회하며, 처리 중인
요청의 결과를 여러 서버가 공유하기 어렵다는 운영상 한계가 있다.

또한 현재 재요청은 최초 HTTP 201과 완전히 같은 응답이 아니라 HTTP 200과
변경된 message를 반환한다. 목표 멱등성은 실행이 한 번이라는 조건뿐 아니라
최초 status와 body를 동일하게 재사용하는 것이다.

## 이야기 6. Redis를 정확성 대체재가 아닌 분산 조율 계층으로 추가한다

Redis 단계에서 구현할 핵심은 다음과 같다.

```text
Idempotency-Key + request hash
→ Redis 결과 cache 조회
→ cache miss이면 Redisson 분산락 획득
→ lock 획득 후 Redis와 DB double check
→ payment_attempt DB unique 선점
→ 외부 발행과 내부 결제
→ 최초 HTTP status/body를 DB에 저장
→ commit 후 Redis 결과 cache 저장
→ 동일 요청에 최초 결과 replay
```

각 기술의 역할은 분리한다.

| 장치 | 담당 문제 |
| --- | --- |
| Redisson 분산락 | 여러 서버의 같은 멱등키 동시 실행 조율 |
| Redis 결과 캐시 | 완료된 최초 응답을 빠르게 재사용 |
| `payment_attempt` unique | Redis 장애·TTL 만료 시 최종 중복 결제 방어 |
| `provider_voucher` unique | 외부 쿠폰 중복 발행의 최종 방어 |
| 조건부 UPDATE/row lock | 서로 다른 주문의 동일 잔액 경쟁 방어 |

## Redis 단계 완료 조건

아래 항목을 모두 실제 결과로 남긴 후 Redis 구현을 포트폴리오의 완료
성과로 표현한다.

- [ ] Docker Compose Redis와 Redisson 연결
- [ ] `Idempotency-Key`와 request SHA-256 hash
- [ ] 같은 키·다른 payload 거절
- [ ] Redis 결과 캐시
- [ ] Redisson 분산락과 lock 획득 후 double check
- [ ] 최초 HTTP status/body DB 저장 및 정확한 replay
- [ ] Spring Boot 2개 인스턴스의 동일 요청 동시 테스트
- [ ] 실제 외부 발행 1건, 내부 구매 1건 확인
- [ ] 캐시 TTL 만료 후 DB 결과 복구와 cache 재생성
- [ ] Redis 중지 시 DB 기반 멱등성 fallback
- [ ] Redis 정상/장애의 응답과 DB 증거 저장
- [ ] DB-only와 Redis+DB 비교표 작성

## 포트폴리오에서 보여줄 최종 검증 시나리오

### 시나리오 A: Legacy

```text
동일 주문 요청 2건
→ 외부 발행 2건
→ HTTP 201 / 500
→ Deadlock과 보상 취소
```

### 시나리오 B: DB-only 개선

```text
동일 주문 요청 2건
→ payment_attempt 선점
→ 외부 발행 1건
→ HTTP 201 / 409
→ Deadlock 없음
```

### 시나리오 C: Redis + DB, 두 서버

```text
서버 A와 B에 같은 Idempotency-Key 동시 요청
→ 한 서버만 Redisson lock 획득
→ 외부 발행과 DB 결제 1회
→ 완료 결과 cache
→ 재요청에 최초 status/body replay
```

### 시나리오 D: Redis 장애

```text
Redis 중지
→ DB payment_attempt 경로 fallback
→ 캐시 성능은 포기
→ 중복 결제 방지는 유지
```

## 포트폴리오에서 정직하게 구분할 상태

### 현재 구현 및 검증 완료

- Legacy 동시 결제 문제와 Deadlock 재현
- `payment_attempt` 기반 쇼핑몰 멱등성
- 외부 발행사의 `order_id` unique와 기존 결과 replay
- 가격·총잔액·미만료 lot 사전 검증
- API 응답과 DB evidence 수집

### Redis 브랜치에서 구현할 범위

- Redis·Redisson 분산락
- 결과 캐시
- `Idempotency-Key`와 request hash
- 최초 응답의 정확한 replay
- 두 인스턴스 검증
- Redis 장애와 TTL 만료 시 DB fallback

### 이번 포트폴리오 범위에서 제외

- 보상 취소 outbox와 재시도
- 바우처 Redemption과 PIN 검증
- `limited_deal`
- 운영 인증·인가
- 실제 외부 결제·쿠폰 회사 연동

제외한 기능은 실패가 아니라 다음 개선으로 제시하되, 구현된 것처럼
표현하지 않는다.

## 최종 결과 문장 템플릿

Redis 구현과 검증 후 실제 수치를 채운다.

> DB-only 방식에서는 동일 요청을 `payment_attempt` unique로 차단해 외부 발행을
> 2건에서 1건으로 줄이고 Deadlock을 제거했다. 이후 Redis·Redisson을 추가해
> 두 애플리케이션 인스턴스의 동일 멱등 요청을 한 실행으로 조율하고, 완료
> 재요청은 `[측정값]ms`에 최초 응답을 replay했다. Redis 장애와 캐시 TTL 만료
> 상황에서도 MySQL 기록으로 결과를 복구해 외부 발행과 내부 구매가 각각
> 한 건으로 유지됨을 검증했다.

## 작업 완료 후 함께 갱신할 자료

- `docs/worklog-and-todo.md`
- `docs/redis-payment-idempotency-design.md`
- `docs/backend-portfolio-outline.md`
- `docs/payment-api-flow-diagram-explanation.md`
- diagrams.net Redis 시퀀스와 개선 전후 비교 페이지
- `evidence/redis-idempotency/`
- README의 구현 상태 및 테스트 방법
