# Point Payment Lab

Legacy 포인트 결제에서 발생하는 **외부 쿠폰 중복 발행, MySQL Deadlock, 잔액
동시성 문제를 재현하고 단계적으로 개선한 백엔드 프로젝트**입니다.

단순히 Redis를 추가하는 데 그치지 않고, 같은 요청의 중복 실행은 Redis와
`payment_attempt`, 외부 쿠폰 중복은 외부 발행사 DB, 서로 다른 주문의 잔액
경쟁은 MySQL 조건부 UPDATE가 각각 담당하도록 책임을 분리했습니다.

> [15페이지 포트폴리오 PDF 보기](output/pdf/point-payment-lab-portfolio.pdf)

## 핵심 결과

| 검증 항목 | Legacy | 개선 후 |
| --- | ---: | ---: |
| 같은 `orderId`의 외부 쿠폰 발행 | 2건 | 1건 |
| MySQL Deadlock | 1건 | 0건 |
| 잔액 부족 요청의 외부 쿠폰 발행 | 1건 | 0건 |
| 서로 다른 주문의 포인트 중복 차감 | 위험 존재 | 0건 |
| 완료 요청 100회 평균 응답 시간(로컬) | 8.118ms | 2.588ms |

성능 수치는 로컬에서 완료 요청을 100회 순차 재전송한 방향성 비교이며 운영 TPS를
의미하지 않습니다. HTTP 응답, 외부 호출 결과와 DB 상태는 [`evidence`](evidence)에
가공하지 않은 형태로 저장했습니다.

## 문제와 개선 과정

### Legacy 문제 재현

기존 흐름은 외부 쿠폰을 먼저 발급한 뒤 쇼핑몰 MySQL 트랜잭션에서 포인트를
차감합니다.

```text
결제 요청
→ 지갑·상품·잔액 조회
→ 외부 쿠폰 발행 API 호출
→ MySQL 포인트 차감·원장·구매 저장
→ HTTP 응답
```

같은 `orderId`로 두 요청을 동시에 보내면 두 요청이 모두 외부 API까지 진행하여
쿠폰이 두 장 발급됐습니다. 내부에서는 한 요청만 성공하고 다른 요청은 Deadlock과
HTTP 500을 반환했습니다.

### 1차: `payment_attempt`로 실행권 선점

- `payment_attempt.orderId` unique 제약
- 외부 호출 전 `PROCESSING` 상태 저장
- `saveAndFlush`로 INSERT와 unique 충돌을 즉시 확인
- 선점 성공 요청만 외부 쿠폰 발행
- 동시 중복 요청은 HTTP 409 반환

`saveAndFlush`가 직접 선점을 제공하는 것은 아닙니다. unique 제약이 한 요청만
INSERT에 성공하도록 보장하고, `saveAndFlush`는 그 결과를 외부 호출 전에 확인할
수 있도록 SQL 실행 시점을 앞당깁니다.

### 2차: 외부 발행사 API 멱등성

- `provider_voucher.orderId` unique 제약
- 같은 주문·같은 상품 재요청은 최초 쿠폰 번호와 PIN 반환
- 같은 주문·다른 상품 요청은 HTTP 409 거절
- 쇼핑몰 API를 우회해 외부 발행사 API를 호출해도 쿠폰 한 장만 생성

### 3차: 외부 API 호출 전 검증

- 상품 판매가와 요청 포인트 비교
- `point_balance` 총잔액 검증
- 만료되지 않은 미사용 `point_lot` 합계 검증
- 잔액 부족 요청은 HTTP 422 반환
- 실패가 확실한 요청의 외부 쿠폰 발행·취소 제거

### 4차: Redis + MySQL 결제 멱등성

- 필수 `Idempotency-Key`와 SHA-256 request hash
- Redisson `RLock`으로 여러 Spring Boot 인스턴스의 동시 실행 조율
- Redis 결과 cache와 lock 획득 후 Redis·DB 이중 확인
- 최초 HTTP status/body를 MySQL `payment_attempt`에 영구 저장
- Redis cache 만료 시 MySQL 결과로 cache 재생성
- Redis 장애 시 MySQL에 저장한 최초 응답 반환
- 같은 멱등키로 다른 payload를 보내면 HTTP 422 거절

Redis는 여러 서버가 실행 상태와 완료 결과를 공유하기 위해 사용했습니다. 다만
Redis 장애나 TTL 만료에도 결제를 재실행하지 않도록 최종 근거는 MySQL에 남깁니다.

### 5차: MySQL 조건부 잔액 차감

서로 다른 `orderId`와 멱등키는 서로 다른 Redis lock을 사용하므로 같은 지갑의
잔액 경쟁을 별도로 막아야 합니다.

```sql
update point_balance
set balance = balance - :amount
where id = :id
  and balance >= :amount;
```

변경 행이 1건인 요청만 결제를 계속하고, 0건이면 HTTP 409
`POINT_BALANCE_CONFLICT`를 반환합니다. MySQL이 최신 잔액으로 조건을 다시
평가하므로 포인트 중복 차감을 방지합니다.

## 기술 스택

- Java 17, Spring Boot 3.3
- Spring Data JPA, TransactionTemplate
- MySQL 8, Flyway
- Redis 7, Redisson
- Docker Compose
- JUnit 5, Mockito
- ReportLab, Poppler 기반 포트폴리오 PDF 생성·검수

## 시스템 역할

| 구성 요소 | 역할 |
| --- | --- |
| Client | 웹 UI, curl, 테스트 스크립트로 결제 요청·재시도 전송 |
| Shopping API | HTTP 요청·응답을 담당하는 Spring MVC Controller 계층 |
| Application | 멱등성, 검증, 외부 호출과 포인트 차감 순서를 조율하는 Service 계층 |
| MySQL | 잔액·원장·구매·결제 시도와 최초 응답의 영구 저장소 |
| 외부 쿠폰 발행사 | 프로젝트 내부 Provider Mock API로 재현한 외부 시스템 경계 |
| Redis | 다중 인스턴스 분산락과 완료 결과 cache |

## 실행 방법

### 1. MySQL과 Redis 실행

```bash
docker compose up -d
```

MySQL과 Redis 포트는 기본적으로 `127.0.0.1`에만 바인딩됩니다. 비밀번호와 포트는
환경변수로 변경할 수 있습니다.

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8080`으로 접속하면 관리자, 쇼핑몰 사용자, 외부
발행사 Mock과 API 로그 탭을 사용할 수 있습니다.

### 3. 테스트

```bash
./gradlew clean test
./gradlew build
```

## 주요 API

| 기능 | Method | Endpoint |
| --- | --- | --- |
| Legacy 포인트 결제 | POST | `/api/payments/point/legacy` |
| Redis+DB 멱등 결제 | POST | `/api/payments/point/redis-idempotent` |
| Legacy 포인트 환불 | POST | `/api/refunds/point/legacy` |
| 지갑·포인트 요약 | GET | `/api/point-wallets/{pointWalletUid}/summary` |
| 구매 내역 | GET | `/api/voucher-purchases` |
| 바우처 상품 등록 | POST | `/api/admin/voucher-products` |
| 바우처 상품 조회 | GET | `/api/admin/voucher-products` |
| 외부 쿠폰 발행 | POST | `/mock/voucher-provider/vouchers/issue` |
| 외부 쿠폰 취소 | POST | `/mock/voucher-provider/vouchers/cancel` |
| 외부 쿠폰 조회 | GET | `/mock/voucher-provider/vouchers` |

Redis 멱등 결제 API에는 `Idempotency-Key` 헤더가 필요합니다. 전체 요청 예시는
[`http/payment-lab.http`](http/payment-lab.http)에서 확인할 수 있습니다.

## 재현 및 검증 스크립트

```bash
# Legacy 동일 주문 중복 결제 재현
bash scripts/run-duplicate-payment-test.sh ORDER-DUPLICATE-001

# payment_attempt 기반 멱등성 검증
bash scripts/run-idempotent-payment-test.sh ORDER-IDEMPOTENT-001

# 외부 발행사 orderId 멱등성 검증
bash scripts/run-duplicate-provider-issue-test.sh PROVIDER-IDEMPOTENT-001

# Redis+DB 멱등성 검증
bash scripts/run-redis-idempotent-payment-test.sh ORDER-REDIS-001

# 서로 다른 주문의 동일 잔액 경쟁 검증
bash scripts/run-competing-balance-test.sh ORDER-BALANCE-RACE-001
```

상세 결과:

- [Redis+DB 멱등성 구현 결과](docs/redis-payment-idempotency-result.md)
- [조건부 잔액 차감 결과](docs/conditional-point-balance-debit-result.md)
- [API 흐름도 해설](docs/payment-api-flow-diagram-explanation.md)
- [테이블 설계](docs/table-design.md)
- [현재 작업 현황과 TODO](docs/worklog-and-todo.md)

## 공개 데이터 안내

`evidence`에 포함된 주문번호, 쿠폰 번호와 PIN은 실제 고객 또는 외부 업체 데이터가
아니라 이 프로젝트의 Provider Mock이 생성한 합성 테스트 데이터입니다. 실제 회사
시스템 분석과 면접 메모가 포함된 로컬 문서는 `.gitignore`로 제외되어 있습니다.

## 남은 과제

- 외부 쿠폰 발행 전 포인트 예약으로 경쟁 실패분의 불필요한 발행·취소 제거
- 외부 취소 실패를 영속화하고 재시도하는 Outbox·reconciliation
- 쿠폰 사용(Redemption) 도메인과 사용 취소 흐름
- Redis HA·TLS·관측성과 Testcontainers 기반 동시성 회귀 테스트
