# Redis + DB 결제 멱등성 구현 결과

## 구현 목적

네트워크 재시도나 사용자의 연속 클릭으로 같은 결제 요청이 여러 서버에 동시에
도착해도 외부 바우처 발행과 포인트 차감은 한 번만 실행하고, 완료된 요청에는
최초 HTTP status와 body를 그대로 반환하는 것이 목표다.

Redis만으로 정확성을 보장하지 않는다.

```text
Redis·Redisson = 여러 서버의 동시 실행 조율 + 완료 결과 빠른 재사용
MySQL payment_attempt = 장애와 TTL 만료에도 남는 최종 멱등성 기록
provider_voucher unique = 외부 발행사의 최종 중복 발행 방어
```

## 추가한 API

```http
POST /api/payments/point/redis-idempotent
Idempotency-Key: KEY-REDIS-VERIFY-001
Content-Type: application/json
```

`Idempotency-Key`는 한 번의 결제 의도를 식별하고, `orderId`는 쇼핑몰 주문을
식별한다. 현재 실습에서는 둘을 별도 값으로 유지한다. 같은 멱등키를 다른 요청
본문에 재사용하면 request SHA-256 hash가 달라 HTTP 422로 거절한다.

응답에는 실습용 진단 헤더를 추가했다.

| 헤더 | 의미 |
| --- | --- |
| `Idempotency-Replayed: false` | 이 요청이 실제 결제를 최초 실행함 |
| `Idempotency-Replayed: true` | 저장된 최초 결과를 재사용함 |
| `Idempotency-Source` | Redis cache, DB replay 등 결과를 얻은 경로 |

`Idempotency-Source`는 흐름을 관찰하기 위한 실습용이며 운영 API에서 그대로
노출할 필요는 없다.

## 코드 흐름

```text
Controller
  → client/method/path/key + request hash 생성
  → Redis 결과 cache 조회
  → cache miss: Redisson RLock 획득
  → lock 안에서 Redis와 MySQL을 다시 확인
  → payment_attempt 선점
  → 외부 바우처 발행 및 내부 포인트 결제
  → 최초 HTTP 201과 JSON body를 payment_attempt에 저장
  → Redis에 TTL 1시간으로 결과 저장
  → lock 소유 thread만 unlock
```

Redis 연결이 실패하면 같은 요청을 `DatabasePaymentIdempotencyService`가 처리한다.
따라서 캐시 성능과 분산 조율은 일시적으로 잃지만 DB unique와 저장 응답을 이용한
중복 방지는 유지한다.

## DB 변경

Flyway `V6__extend_payment_attempt_for_redis_idempotency.sql`로
`payment_attempt`에 다음 정보를 추가했다.

| 컬럼 | 역할 |
| --- | --- |
| `client_id`, `http_method`, `api_path`, `idempotency_key` | 멱등키의 적용 범위 |
| `request_hash` | 같은 키가 같은 payload인지 판별 |
| `http_status` | 최초 HTTP status 보존 |
| `response_body` | 최초 JSON 응답 보존 |
| `expires_at` | DB 멱등 기록 만료 정책을 위한 시각 |

네 범위 컬럼에는 unique 제약을 두었다. Redis lock이 사라지거나 두 프로세스가
동시에 DB까지 내려와도 MySQL이 동일 멱등 요청의 중복 선점을 최종 차단한다.

## 실제 검증 결과

### 단일 인스턴스 동시 요청

같은 요청 두 건을 동시에 전송했다.

```text
요청 A: HTTP 201 / replay=false / DATABASE_CREATED
요청 B: HTTP 201 / replay=true  / REDIS_CACHE
voucherNumber와 pinNumber를 포함한 response body: 완전히 동일
```

### 두 애플리케이션 인스턴스

8080과 8081 인스턴스에 같은 요청을 동시에 보냈다.

```text
요청 A: HTTP 201 / replay=false / DATABASE_CREATED
요청 B: HTTP 201 / replay=true  / REDIS_CACHE
provider_voucher: 1건
voucher_purchase: 1건
payment_attempt: SUCCEEDED 1건
포인트 차감: 1회
```

두 응답은 같은 `voucherNumber`, `pinNumber`, 잔액을 반환했다. 증거는
`evidence/redis-idempotency/two-instances/`에 저장했다.

### 같은 키를 다른 payload에 사용

동일 멱등키로 다른 상품을 요청하자 외부 호출 전에 다음 결과가 반환됐다.

```text
HTTP 422
code: IDEMPOTENCY_KEY_REUSED
```

### Redis cache 삭제

TTL 만료와 같은 cache miss 상황을 만들기 위해 결과 key를 삭제하고 재요청했다.

```text
HTTP 201
Idempotency-Replayed: true
Idempotency-Source: DATABASE_CACHE_REBUILD
body: 최초 응답과 동일
```

MySQL의 완료 결과를 읽어 응답한 뒤 Redis cache도 다시 생성됐다.

### Redis 중단

애플리케이션 실행 중 Redis container를 중지한 뒤 같은 요청을 재전송했다.

```text
HTTP 201
Idempotency-Replayed: true
Idempotency-Source: DATABASE_REPLAY
body: 최초 응답과 동일
```

Redis 재시작 후에도 애플리케이션과 Redis 연결이 복구됐다.

## 자동 검증

- `RedisIdempotentPointPaymentServiceTest`: cache hit, lock 실행, lock 경쟁,
  Redis 예외 fallback
- `DatabasePaymentIdempotencyServiceTest`: 최초 실행, 완료 replay, 다른 payload 거절
- 전체 Gradle test: 20개 성공
- 수동 동시 요청: `scripts/run-redis-idempotent-payment-test.sh`

## DB-only와 Redis+DB 비교 측정

완료된 결제를 각 방식으로 100회 순차 재요청했다. 최초 실행은 외부 Mock 호출과
DB 쓰기가 포함되므로, 여기서는 완료 결과를 다시 받는 replay 경로만 비교했다.

| 방식 | HTTP | 평균 | 최소 | 최대 | MySQL SELECT | MySQL INSERT 시도 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| DB-only | 200 | 8.118ms | 5.851ms | 22.226ms | 101 | 100 |
| Redis+DB | 최초 status replay 201 | 2.588ms | 1.911ms | 4.248ms | 1 | 0 |

Redis cache hit는 이 환경에서 평균 시간이 약 68.1% 짧았다. DB-only 구현은
재요청마다 `payment_attempt` INSERT를 시도하고 unique 충돌 후 기존 결과를
SELECT한다. Redis cache hit는 저장된 status/body를 바로 반환하므로 결제 데이터
DB에 접근하지 않았다. MySQL global status에 기록된 SELECT 1회는 측정 환경의
다른 접근이 포함됐을 수 있다.

두 방식 모두 측정 후 `provider_voucher`와 `voucher_purchase`가 주문별 1건으로
유지됐다. 즉 100회 replay가 외부 발행이나 내부 구매를 추가하지 않았다.

재현 명령:

```bash
bash scripts/benchmark-payment-idempotency.sh 100
```

원본 시간과 DB counter는 `evidence/redis-idempotency/benchmark/`에 저장된다.
이 결과는 로컬 단일 인스턴스, 순차 호출, SQL 로그가 활성화된 소규모 측정이다.
운영 성능 수치가 아니라 Redis cache hit가 DB 충돌과 조회를 피하는 흐름을
증명하는 자료로 사용한다.

## 아직 해결하지 않은 문제

이 개선은 **같은 멱등키의 중복 실행**을 막는다. 서로 다른 `orderId`와 서로 다른
멱등키를 사용한 두 결제가 같은 지갑 잔액을 동시에 차감하는 경쟁은 막지 않는다.
이 문제는 잔액을 기준으로 한 조건부 UPDATE 또는 DB row lock으로 별도 해결해야
한다.

또한 운영 환경에서는 Redis 고가용성, 인증, TLS, private network, 장애 지표와
알람이 필요하다. 결과 cache에는 PIN이 포함되므로 접근 통제와 로그 마스킹,
보관 기간 정책도 필요하다.
