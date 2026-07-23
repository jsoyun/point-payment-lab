# 한 일 / 할 일 정리

## 문서 운영 원칙

이 문서는 `point-payment-lab` 프로젝트의 구현 TODO와 진행 상황을 관리하는 기준 문서다.

- 앞으로 해야 할 일, 현재 진행 중인 일, 완료한 일은 이 문서에 계속 갱신한다.
- 프로젝트 작업을 시작할 때 이 문서를 먼저 확인해 다음 우선순위를 정한다.
- 구현이나 테스트가 끝나면 완료 내용과 검증 결과를 기록한다.
- 작업 중 새 문제가 발견되면 개선 TODO와 완료 조건을 추가한다.
- 상세 설계가 다른 문서에 있더라도 이 문서에 요약과 링크를 남긴다.
- "이제 무엇을 해야 하는지" 묻는 경우 이 문서의 미완료 항목을 기준으로 답한다.

## 현재 최우선 작업: legacy 문제 재현 후 개선 전후 비교

포트폴리오에 개선 효과를 증명할 수 있도록 바로 `PaymentAttempt`를 구현하지 않고, 먼저 동일 조건에서 재현 가능한 baseline을 남긴다.

진행 순서:

1. 깨끗한 seed 상태에서 정상 결제 1건으로 환경을 검증한다.
2. 같은 `orderId`의 동시 요청 2건으로 따닥 결제를 재현한다.
3. 잔액 부족 요청이 외부 발행사까지 전달되는 문제를 재현한다.
4. API 응답, API 로그 JSON, 주요 테이블의 전후 상태, 외부 발행 호출 횟수를 증거로 저장한다.
5. `PaymentAttempt` 기반 `orderId` 선점과 외부 호출 전 잔액 검증을 구현한다.
6. 같은 실험을 다시 실행해 개선 전후 결과를 비교한다.

중요: 기존 4·5·6단계 중 4와 6은 재현할 문제이고, 5의 `PaymentAttempt`는 4를 재현한 뒤 적용할 개선안이다.

포트폴리오 핵심 비교 지표:

| 실험 | Legacy 기대 관찰 | 개선 후 목표 |
| --- | --- | --- |
| 동일 `orderId` 동시 요청 2건 | 외부 발행 2건, 내부 구매 최대 1건 | 외부 발행 1건, 내부 구매 1건, 재요청은 기존 결과 반환 |
| 잔액 부족 구매 요청 | 외부 발행 후 보상 취소 기록 생성 | 외부 발행 0건, 내부에서 4xx로 즉시 차단 |

재현 결과를 남길 때 사용할 자료:

- UI에서 다운로드한 API 로그 JSON
- 동시 요청 A/B의 HTTP 응답
- `provider_voucher`, `voucher_purchase`, `point_balance`, `point_lot` 조회 결과
- 외부 Mock의 issue/cancel 호출 횟수
- 개선 전후 시퀀스 다이어그램과 결과 비교표

### 2026-07-21 따닥 결제 Legacy 재현 결과

동일 `orderId = AL-DUPLICATE-001`로 5,000 포인트 결제 요청 2건을 동시에 실행했다.

HTTP 결과:

- 요청 A: `HTTP 500`, `Deadlock found when trying to get lock`
- 요청 B: `HTTP 201`, 바우처 발행 및 내부 결제 성공

DB 결과:

| 확인 대상 | 관찰 결과 |
| --- | --- |
| `provider_voucher` | 동일 `order_id`로 2건 생성: `ISSUED` 1건, `CANCELED` 1건 |
| `voucher_purchase` | 성공한 바우처 1건만 저장 |
| `point_balance` | 10,000에서 5,000으로 1회 차감 |
| `point_ledger` | `WITHDRAWAL` 1건 저장 |
| `point_lot` | 5,000 포인트만 성공 바우처에 연결되어 `USED` 처리 |

재현된 핵심 문제:

```text
동일 주문 요청 2건
-> 외부 바우처 발행 2회
-> 내부 구매 성공 1건
-> 실패 요청은 DB deadlock과 HTTP 500
-> 실패 요청의 외부 바우처는 보상 취소
```

따라서 내부 최종 데이터는 1건으로 수습되었지만, 외부 API 중복 호출과 불필요한 발행·취소가 이미 발생한다는 baseline을 확보했다.

#### MySQL Deadlock 원인 분석

`SHOW ENGINE INNODB STATUS`의 `LATEST DETECTED DEADLOCK`을 확인한 결과, 두 트랜잭션이 다음 락을 서로 기다렸다.

```text
Transaction 2008
- point_source_balance PK(id=1)의 S lock 보유
- voucher_purchase.order_id unique 인덱스 lock 대기

Transaction 2007
- voucher_purchase.order_id(AL-DUPLICATE-001)의 X lock 보유
- point_source_balance PK(id=1)의 X lock 대기
```

순환 대기:

```text
T2008: point_source_balance 보유 -> voucher_purchase unique lock 대기
T2007: voucher_purchase unique lock 보유 -> point_source_balance lock 대기
```

MySQL은 순환 대기를 해소하기 위해 Transaction 2008을 victim으로 선택해 rollback했다. API 응답에는 victim이 실행 중이던 `voucher_purchase insert` 문장이 표시됐지만, 실제 원인은 `voucher_purchase`와 `point_source_balance` 두 자원 사이의 락 획득 순서 충돌이다.

#### 보상 취소 실패 위험과 개선 설계

현재는 내부 DB transaction 실패를 catch하면 외부 바우처 `cancel` API를 한 번 즉시 호출한다. 이 호출까지 실패하면 내부 결제는 이미 rollback됐지만 외부 바우처는 `ISSUED`로 남을 수 있다. 또한 현재 코드에서는 cancel 예외가 원래 DB 예외를 덮어쓸 수 있고, 실패 이력·재시도·운영 알림이 없다.

개선 우선순위:

1. 쇼핑몰의 `payment_attempt.order_id` unique 제약으로 외부 호출 전에 같은 주문을 선점한다.
2. 외부 발행 API에도 `orderId`를 멱등성 키로 전달하고, 외부 Mock의 `provider_voucher.order_id`에도 unique 제약을 적용한다.
3. 동일 주문 재요청은 새 쿠폰을 발행하지 않고 기존 `PROCESSING` 또는 `SUCCEEDED` 결과를 반환한다.
4. 외부 호출 전 잔액·상품 가격·사용 가능한 `point_lot`을 검증한다.
5. 내부 결제 실패 후 즉시 취소가 실패하면 `compensation_task` 또는 outbox에 `PENDING` 작업을 저장한다.
6. 백그라운드 작업이 취소를 재시도하고 `SUCCEEDED`, `FAILED` 상태와 시도 횟수·마지막 오류를 기록한다.
7. 외부 취소 API도 같은 요청을 여러 번 보내도 안전한 멱등 API로 만든다.
8. 장시간 `PROCESSING` 또는 `COMPENSATION_PENDING`인 요청은 상태 조회/reconciliation과 운영 알림 대상으로 관리한다.

`PaymentAttempt` 권장 상태:

```text
PROCESSING
EXTERNAL_ISSUED
SUCCEEDED
COMPENSATION_PENDING
COMPENSATED
FAILED
```

주의: `PaymentAttempt`는 같은 `orderId` 중복 요청을 막지만 서로 다른 `orderId`가 같은 지갑 잔액을 동시에 쓰는 문제는 막지 못한다. 이 문제는 지갑/잔액 row lock 또는 `balance >= amount` 조건부 차감으로 별도 해결한다.

테스트 DB 초기화 확인 주의사항:

- `docker compose down -v` 후 DBeaver의 기존 결과 탭에는 삭제 전 조회 결과가 그대로 보일 수 있다.
- 결과 탭 표시만 보고 판단하지 말고 SQL을 다시 실행하거나 연결을 새로고침한다.
- 초기화 성공 기준은 `select count(*) from voucher_purchase;` 결과가 `0`이고, `flyway_schema_history`에 V1~V3가 새로 적용된 상태다.
- 중복 결제 스크립트 결과가 `HTTP 000`이면 애플리케이션 응답이 아니라 `localhost:8080` 연결 실패이므로 Spring Boot 실행 상태부터 확인한다.
- DB 추출 명령의 `> evidence/.../db-after.txt`는 조회 결과를 터미널이 아닌 파일에 저장한다. 터미널에는 비밀번호 경고만 보일 수 있으므로 `cat evidence/.../db-after.txt`로 결과를 확인한다.

## 2026-07-21 문서 보완

- 실습 UI에서 사용하는 지갑 요약, 쇼핑몰 구매 내역, 외부 발행사 목록 API의 역할과 자동 호출 시점을 `docs/code-structure-and-flow.md`의 `실습 UI API 빠른 참조`에 정리했다.
- `voucher_purchase`는 내부 결제가 완료된 구매 결과이고, `provider_voucher`는 외부 발행 요청 결과라는 차이를 명시했다.
- 현재는 바우처 구매·발급·환불까지만 구현되어 있고, 발급받은 바우처로 실제 상품을 구매하는 사용(Redemption) 기능은 미구현임을 확인했다.

## 2026-07-23 개선 구현 로드맵

Legacy 따닥 결제 문제의 재현과 Deadlock 분석을 완료했으며, 개선 효과를 단계별로 검증하기 위해 다음 순서로 구현한다. 네 가지 개선을 한 번에 적용하지 않고 각 단계마다 같은 실험을 다시 실행해 개선 전후 증거를 남긴다.

### 1차: PaymentAttempt 기반 동일 주문 멱등성

이번에 가장 먼저 구현할 범위다.

- `payment_attempt` 테이블과 `order_id` unique 제약 추가
- 외부 바우처 발행 API 호출 전에 `orderId` 선점
- 최소 상태 `PROCESSING`, `SUCCEEDED`, `FAILED` 관리
- 동일 `orderId` 재요청 시 외부 API를 다시 호출하지 않음
- 성공한 요청은 저장된 기존 결과를 반환
- 기존 따닥 결제 스크립트를 다시 실행해 개선 효과 검증

완료 목표:

| 항목 | Legacy baseline | 1차 개선 후 목표 |
| --- | ---: | ---: |
| 동시 요청 | 2 | 2 |
| 외부 발행 | 2 | 1 |
| 내부 구매 | 1 | 1 |
| Deadlock | 1 | 0 |
| 보상 취소 | 1 | 0 |

### 2차: 외부 발행사 이중 멱등성

- `provider_voucher.order_id` unique 제약 추가
- 같은 `orderId` 발행 재요청 시 새 바우처를 만들지 않음
- 이미 발행된 `voucherNumber`, `pinNumber`를 기존 결과로 반환
- 쇼핑몰과 외부 발행사 양쪽에서 동일 주문 중복 발행 방어

### 3차: 잔액 부족 요청의 외부 호출 차단

- 외부 발행 전에 상품 판매가와 요청 포인트 일치 검증
- `point_balance` 잔액 검증
- 사용 가능하고 만료되지 않은 `point_lot` 합계 검증
- 잔액 부족 시 외부 issue 호출과 `provider_voucher` 생성을 하지 않음
- 명확한 4xx 오류 응답 반환

완료 목표:

```text
잔액 부족 외부 issue 호출 1회 -> 0회
잔액 부족 provider_voucher 생성 1건 -> 0건
```

### 4차: 보상 취소 실패 복구

- 외부 Mock 취소 실패 시나리오 추가
- `compensation_task` 또는 outbox에 실패 작업 저장
- 상태, 재시도 횟수, 다음 재시도 시각, 마지막 오류 기록
- 백그라운드 취소 재시도
- 외부 취소 API 멱등성 보장
- 장시간 미완료 작업에 대한 reconciliation 및 운영 확인 방법 마련

이 단계에서 필요에 따라 상태를 다음처럼 확장한다.

```text
PROCESSING
EXTERNAL_ISSUED
SUCCEEDED
COMPENSATION_PENDING
COMPENSATED
FAILED
```

### 단계별 증거 관리

각 단계에서 다음 자료를 `evidence` 아래에 개선 전·후로 구분해 보관한다.

- 동시 요청 A/B HTTP 응답
- API 로그 JSON
- `provider_voucher`, `voucher_purchase`, `point_balance`, `point_lot`, `point_ledger` 조회 결과
- 외부 issue/cancel 호출 횟수
- 개선 전후 결과 비교표와 시퀀스 다이어그램

## 2026-07-23 공개 저장소 커밋 전 보안 설정 정리

- 이전 회사 시스템 분석과 로컬 원본 소스 경로가 포함된 `docs/legacy-point-payment-interview-notes.md`를 `.gitignore`에 추가했다.
- 비공개 분석 문서 상단에 Git 저장소나 외부 공개 공간에 올리면 안 된다는 경고를 추가했다.
- `application.yml`의 DB URL, 사용자명, 비밀번호, 서버 포트, 외부 바우처 URL을 환경변수로 재정의할 수 있게 변경했다.
- 로컬 개발 편의를 위해 환경변수가 없을 때는 기존 실습 기본값을 사용한다.
- Docker Compose의 MySQL 포트는 기본적으로 `127.0.0.1:3307`에만 바인딩한다.
- 운영에서는 MySQL 포트를 공인망에 노출하지 않고 애플리케이션과 DB를 private network로 연결하는 것을 원칙으로 한다.
- Compose의 DB 포트와 개발용 계정 정보도 환경변수로 교체할 수 있게 변경했다.
- 비공개 문서 파일명에서 회사명을 제거하고 면접 정리 성격을 포함한 `legacy-point-payment-interview-notes.md`로 변경했다.

### TODO: 발급 바우처 사용(Redemption) 기능

사용자가 발급받은 `voucherNumber`와 `pinNumber`로 바우처를 실제 사용하는 흐름을 추가한다.

예상 API:

```http
POST /api/voucher-redemptions
Content-Type: application/json

{
  "voucherNumber": "CP-...",
  "pinNumber": "PIN-..."
}
```

구현할 검증:

- 바우처 번호가 존재하는지 확인
- 요청 PIN이 발급된 PIN과 일치하는지 확인
- `issue_status = ISSUED`인지 확인
- `use_status = UNUSED`인지 확인
- 현재 시각이 `valid_from` 이상, `valid_until` 이하인지 확인
- 환불되었거나 이미 사용된 바우처는 거절
- 동시에 여러 사용 요청이 들어와도 한 번만 성공하도록 row lock 또는 조건부 update 적용

사용 성공 시 변경:

```text
voucher_purchase.use_status = USED
voucher_purchase.used_or_canceled_at = 사용 시각
```

함께 보완할 사항:

- 사용 완료된 바우처는 환불 API에서 거절
- 쇼핑몰 UI에서 바우처 번호, PIN 입력 또는 보유 바우처의 `사용` 버튼 제공
- 사용 전·사용 후 상태를 API 로그와 바우처 목록에서 확인
- 필요하면 외부 발행사 Mock에도 바우처 사용 API와 외부 상태를 추가

완료 조건:

- 정상 바우처는 한 번만 사용할 수 있다.
- 잘못된 PIN, 만료, 취소, 이미 사용된 바우처는 명확한 4xx 응답으로 거절된다.
- 사용 성공 후 `use_status = USED`, `used_or_canceled_at`이 저장된다.
- 사용된 바우처의 환불 요청은 포인트를 복구하지 않고 거절된다.
- 중복·동시 사용 요청에서도 한 건만 성공한다.

## 2026-06-23 한 일

오늘은 `point-payment-lab` 프로젝트를 로컬에서 실행할 수 있도록 Docker DB와 Spring Boot 서버 구동을 확인했다.

### 1. Docker Desktop 실행

처음 `docker compose up -d`를 실행했을 때 다음 오류가 발생했다.

```text
Cannot connect to the Docker daemon
```

이 오류는 프로젝트 문제가 아니라 Docker Desktop이 켜져 있지 않아서 발생한 것이다. macOS에서는 Docker Desktop을 실행해야 백그라운드의 Docker daemon이 같이 뜬다.

Docker 명령어의 구조는 다음과 같다.

```text
터미널의 docker 명령어
-> Docker daemon에 요청
-> Docker daemon이 이미지 다운로드, 컨테이너 생성, 컨테이너 실행
```

따라서 Docker Desktop을 켠 뒤 `docker compose up -d`를 다시 실행하자 정상적으로 MySQL 컨테이너가 생성되고 실행되었다.

### 2. Docker Compose로 MySQL DB 실행

실행한 명령어:

```bash
cd /Users/soyunlee/Documents/ProgrammingProjects/point-payment-lab
docker compose up -d
```

이 명령어는 프로젝트의 `docker-compose.yml`을 읽어서 MySQL 8.4 컨테이너를 실행한다.

현재 설정은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| 컨테이너 이름 | `point-payment-lab-mysql` |
| MySQL 이미지 | `mysql:8.4` |
| 로컬 접속 포트 | `3307` |
| 컨테이너 내부 포트 | `3306` |
| DB 이름 | `point_payment_lab` |
| DB 사용자 | `lab` |
| DB 비밀번호 | `lab` |

Docker Compose 실행 결과 MySQL 이미지가 다운로드되고, 네트워크, volume, 컨테이너가 생성되었다.

```text
mysql Pulled
Network point-payment-lab_default Created
Volume point-payment-lab_point-payment-lab-mysql Created
Container point-payment-lab-mysql Started
```

### 3. Spring Boot 서버 실행

DB 컨테이너를 먼저 띄운 뒤 Spring Boot 서버를 실행했다.

```bash
./gradlew bootRun
```

`./gradlew`는 Gradle Wrapper이고, 로컬에 Gradle이 직접 설치되어 있지 않아도 프로젝트가 지정한 Gradle 버전으로 빌드/실행할 수 있게 해준다.

`bootRun`은 Spring Boot 애플리케이션을 실행하는 Gradle task다.

실행 결과 서버가 정상 기동되었다.

```text
Tomcat started on port 8080
Started PointVoucherPaymentLabApplication
```

### 4. 서버 실행 중 발생한 DB 컬럼 오류 수정

처음 서버 실행 시 다음 오류가 발생했다.

```text
Schema-validation: missing column [wallet_uid] in table [point_wallet]
```

원인은 Java 엔티티와 실제 DB 테이블 컬럼명이 맞지 않았기 때문이다.

Flyway SQL에서는 `point_wallet` 테이블의 컬럼을 `point_wallet_uid`로 만들고 있었지만, JPA 엔티티 `PointWallet`은 `wallet_uid` 컬럼을 찾고 있었다.

수정한 내용:

```java
@Table(name = "point_wallet")
@Column(name = "point_wallet_uid")
```

수정 후 다시 서버를 실행했고, Hibernate schema validation을 통과하여 서버가 정상 기동되었다.

## 2026-06-30 한 일

오늘은 DB를 완전히 초기화한 뒤 다시 구동해서 Flyway migration이 처음부터 정상 적용되는지 확인했다.

### 1. 기존 DB volume까지 삭제

실행한 명령어:

```bash
docker compose down -v
```

이 명령어는 MySQL 컨테이너를 내리고, MySQL 데이터가 저장되어 있던 Docker volume까지 삭제한다.

즉 기존에 생성된 DB, 테이블, 테스트 데이터, 결제 테스트 결과가 모두 삭제된다.

이 명령어를 사용한 이유는 다음과 같다.

- 테이블/컬럼 comment를 추가한 `V3__add_table_column_comments.sql`이 처음부터 잘 적용되는지 확인
- 기존 테스트 데이터 없이 깨끗한 상태에서 결제/환불 실습을 시작
- Flyway가 V1, V2, V3 migration을 순서대로 다시 실행하는지 확인

### 2. MySQL 컨테이너 재생성

실행한 명령어:

```bash
docker compose up -d
```

`down -v`로 volume을 삭제했기 때문에, 이 단계에서 MySQL 컨테이너와 DB 저장 공간이 새로 만들어진다.

주의할 점은 `docker compose up -d` 자체가 매번 DB를 초기화하는 명령어는 아니라는 것이다.

```text
docker compose up -d
-> 컨테이너가 없으면 생성
-> 컨테이너가 멈춰 있으면 시작
-> volume이 남아 있으면 기존 DB 데이터 유지
-> volume이 삭제되어 있으면 새 DB로 시작
```

오늘은 바로 직전에 `docker compose down -v`를 실행했기 때문에 새 DB가 만들어진 것이다.

### 3. Spring Boot 서버 실행

실행한 명령어:

```bash
./gradlew bootRun
```

서버가 시작되면서 Flyway가 `src/main/resources/db/migration` 아래 SQL을 순서대로 실행한다.

현재 migration 순서는 다음과 같다.

| 파일 | 역할 |
| --- | --- |
| `V1__create_legacy_payment_tables.sql` | 결제/환불 실습에 필요한 테이블 생성 |
| `V2__seed_legacy_payment_data.sql` | 테스트용 지갑, 상품, 잔액, 포인트 묶음 데이터 입력 |
| `V3__add_table_column_comments.sql` | DBeaver에서 보기 좋도록 테이블/컬럼 comment 추가 |

서버가 정상 실행되면 DB에는 깨끗한 초기 데이터가 들어간 상태가 된다.

## 다음에 이어서 할 작업

현재 상태는 "DB 초기화 완료, 서버 실행 가능, 테이블/컬럼 comment 적용 완료"다.

이제는 기존 legacy 방식이 실제로 어떻게 동작하는지 테스트하면서 관찰하면 된다.

### 1. 정상 포인트 결제 1건 호출

서버가 켜진 상태에서 아래 요청을 실행한다.

```bash
curl -X POST http://localhost:8080/api/payments/point/legacy \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "AL-TEST-001",
    "pointWalletUid": "point-wallet-001",
    "voucherProductId": 1,
    "pointBalanceId": 1,
    "point": 5000
  }'
```

확인할 것:

- 응답에 `voucherNumber`, `pinNumber`가 내려오는지
- 외부 바우처 mock 테이블인 `provider_voucher`에 발행 이력이 생기는지
- 내부 구매 이력인 `voucher_purchase`에 결제 결과가 저장되는지
- `point_balance.balance`가 차감되는지
- `point_lot.status`가 `USED`로 바뀌고 `voucher_number`가 연결되는지

### 2. DBeaver에서 결제 후 DB 상태 확인

DBeaver 연결 정보:

| 항목 | 값 |
| --- | --- |
| Host | `localhost` |
| Port | `3307` |
| Database | `point_payment_lab` |
| Username | `lab` |
| Password | `lab` |

확인할 주요 테이블:

```sql
select * from provider_voucher;
select * from voucher_purchase;
select * from point_ledger;
select * from point_balance;
select * from point_source_balance;
select * from point_lot;
```

### 3. 포인트 환불 테스트

정상 결제 응답에서 받은 `voucherNumber`로 환불 API를 호출한다.

```bash
curl -X POST http://localhost:8080/api/refunds/point/legacy \
  -H "Content-Type: application/json" \
  -d '{
    "voucherNumber": "결제응답에서_받은_voucherNumber"
  }'
```

확인할 것:

- `voucher_purchase.issue_status`가 `CANCELED`로 바뀌는지
- `voucher_purchase.use_status`가 `CANCELED`로 바뀌는지
- `point_balance.balance`가 복구되는지
- `point_lot.status`와 `voucher_number`가 다시 사용 가능 상태로 복구되는지
- `point_ledger`에 `RETURN` 이력이 추가되는지
- `point_credit`에 환불성 입금 기록이 추가되는지

### 4. 따닥 결제 문제 재현

같은 `orderId`로 동시에 결제 요청을 보내 기존 방식의 문제를 확인한다.

```bash
bash scripts/run-duplicate-payment-test.sh AL-DUPLICATE-001
```

확인할 쿼리:

```sql
select id, order_id, voucher_number, status
from provider_voucher
where order_id = 'AL-DUPLICATE-001';

select id, order_id, voucher_number
from voucher_purchase
where order_id = 'AL-DUPLICATE-001';
```

관찰하려는 문제:

```text
voucher_purchase는 order_id unique 제약으로 1건만 저장됨
provider_voucher는 외부 API가 먼저 호출되므로 같은 order_id로 여러 건 생길 수 있음
```

이것이 다음 개선 작업에서 해결해야 할 핵심 문제다.

### 5. 다음 개발 목표

따닥 결제 문제가 확인되면, 다음 단계는 `PaymentAttempt` 테이블을 추가하는 것이다.

개선 방향:

```text
결제 요청 수신
-> PaymentAttempt에 orderId 먼저 insert
-> 이미 존재하면 외부 API 호출하지 않음
-> 최초 요청만 외부 바우처 발행 API 호출
-> 내부 결제 transaction 처리
-> 성공/실패 상태 저장
-> 같은 orderId 재요청은 저장된 상태를 보고 응답
```

이 개선으로 막고 싶은 문제:

- 같은 `orderId`의 외부 API 중복 호출
- 따닥 결제로 인한 바우처 중복 발행
- DB unique 제약에만 의존하는 늦은 중복 방어
- 외부 API 성공 후 내부 DB 실패 시 추적 어려움

### 6. 잔액 부족 요청을 외부 바우처 발행 전에 차단

현재 legacy 결제는 외부 바우처 발행 API를 먼저 호출한 뒤 내부 DB transaction에서 사용 가능한 포인트가 부족한지 확인한다.
이 때문에 잔액이 부족한 구매 시도도 `provider_voucher`에 생성되었다가 보상 취소되어 `CANCELED` 기록으로 남는다.

현재 문제 흐름:

```text
구매 요청
-> 외부 바우처 발행
-> 내부 포인트 차감 시도
-> 잔액/point_lot 부족 발견
-> 내부 transaction rollback
-> 외부 바우처 보상 취소
```

개선 목표:

```text
구매 요청
-> 지갑과 상품 검증
-> 상품 판매가와 요청 포인트 일치 검증
-> point_balance 잔액 검증
-> 사용 가능한 point_lot 합계와 만료 여부 검증
-> 검증 통과
-> 외부 바우처 발행 API 호출
-> 내부 결제 처리
```

외부 API 호출 전에 확인할 조건:

- `point_balance.balance >= 요청 포인트`
- 사용 가능하고 만료되지 않은 `point_lot` 합계가 요청 포인트 이상
- 요청 포인트가 `voucher_product.sell_price`와 일치
- `point_balance`가 요청한 `point_wallet` 소유
- 같은 `orderId`가 이미 처리 중이거나 처리 완료된 요청이 아님

완료 조건:

- 잔액 부족 요청에서 외부 `/vouchers/issue` API 호출 횟수가 증가하지 않는다.
- 잔액 부족 요청으로 `provider_voucher` 레코드가 생성되지 않는다.
- API는 잔액 부족을 명확한 4xx 응답으로 반환한다.
- 동시 요청에서도 잔액이 중복 사용되지 않도록 row lock 또는 조건부 차감을 함께 적용한다.

사전 잔액 검증만으로는 동시 결제를 완전히 막을 수 없다. 따라서 이 항목은 `PaymentAttempt` 기반 멱등성 처리와 잔액 동시성 제어를 함께 적용하는 개선 작업으로 진행한다.


## 다음에 해볼 일

1. 정상 포인트 결제 API 호출
2. 결제 후 `voucher_purchase`, `point_ledger`, `point_balance`, `point_lot`, `provider_voucher` 테이블 변화 확인
3. 포인트 환불 API 호출
4. 환불 후 포인트 잔액과 포인트 묶음 복구 확인
5. 따닥 결제 스크립트 실행
6. 같은 `orderId`에서 외부 바우처 API가 중복 호출되는 문제 확인
7. `PaymentAttempt` 테이블을 추가해 외부 API 호출 전 요청을 선점하는 개선 작업 시작
