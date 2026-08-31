# 한 일 / 할 일 정리

## 문서 운영 원칙

이 문서는 `point-payment-lab` 프로젝트의 구현 TODO와 진행 상황을 관리하는 기준 문서다.

- 앞으로 해야 할 일, 현재 진행 중인 일, 완료한 일은 이 문서에 계속 갱신한다.
- 프로젝트 작업을 시작할 때 이 문서를 먼저 확인해 다음 우선순위를 정한다.
- 구현이나 테스트가 끝나면 완료 내용과 검증 결과를 기록한다.
- 작업 중 새 문제가 발견되면 개선 TODO와 완료 조건을 추가한다.
- 상세 설계가 다른 문서에 있더라도 이 문서에 요약과 링크를 남긴다.
- "이제 무엇을 해야 하는지" 묻는 경우 이 문서의 미완료 항목을 기준으로 답한다.

## 2026-08-11 포트폴리오용 개선 흐름 도식화

diagrams.net의 `결제프로젝트` 파일에 다음 두 페이지를 작성했다.

- `01-Legacy 문제 발생 흐름`
  - 동일 주문 따닥 결제와 Deadlock
  - 잔액 부족인데 외부 바우처를 먼저 발행하는 흐름
  - 외부 API와 MySQL 트랜잭션 경계 및 보상 취소 실패 위험
- `02-단계별 개선과 검증 결과`
  - PaymentAttempt 기반 1차 개선
  - 외부 발행사 unique/replay 2차 개선
  - 외부 호출 전 포인트 검증 3차 개선
  - Redis·Redisson과 조건부 UPDATE로 이어지는 다음 개선
- `03-개선 전 Legacy API 흐름`
  - 동일 주문 동시 요청부터 외부 중복 발행, Deadlock, HTTP 500, 보상 취소까지의 요청·응답 순서
- `04-1차 PaymentAttempt API 흐름`
  - `PROCESSING` 선점, 동시 중복 HTTP 409, 성공 결과 HTTP 200 replay
- `05-2차 외부 발행 멱등 API 흐름`
  - 외부 발행 최초 HTTP 201, 같은 요청 HTTP 200 replay, 다른 상품 HTTP 409
- `06-3차 외부 호출 전 검증 API 흐름`
  - 가격·잔액·미만료 lot 검증과 실패 시 외부 호출 없는 HTTP 422
- `07-4차 Redis+DB 멱등성 API 흐름 (구현 완료)`
  - Redis/Redisson, DB 이중 확인, 조건부 잔액 차감, 원본 응답 캐시의 실제 요청 흐름
  - 2개 인스턴스, Redis 장애 fallback, 성능 비교, 동일 잔액 경쟁의 실제 검증 결과

다이어그램 링크, 페이지 설명, 색상 범례와 근거 자료:

- `docs/legacy-improvement-diagram-guide.md`

각 페이지의 요청 순서, 문제 발생 지점, 개선 효과와 남은 한계를 줄글로
설명한 포트폴리오용 해설 문서:

- `docs/payment-api-flow-diagram-explanation.md`

## 2026-08-13 테이블 명세서 및 ERD 시각화

diagrams.net의 `결제프로젝트` 파일에 실제 Flyway V1~V5 스키마를 기준으로
다음 페이지를 추가했다.

- `08-포인트 영역 테이블 명세서`
  - `point_wallet`, `point_balance`, `point_source_balance`, `point_lot`,
    `point_ledger`, `point_credit`
  - 전체 컬럼의 자료형, PK/FK/UK, 설명과 업무 역할
- `09-바우처·멱등성 테이블 명세서`
  - `voucher_product`, `limited_deal`, `voucher_purchase`, `payment_attempt`,
    `provider_voucher`
  - 쇼핑몰 구매 기록과 외부 발행사 기록의 차이
  - `voucher_number`는 쿠폰 식별자, `pin_number`는 향후 사용 시 검증할
    인증값이라는 차이
  - `limited_deal`은 현재 결제 로직에서 사용하지 않아 `로직 추가 예정` 표시
- `10-전체 결제·환불 ERD`
  - 실제 DB에 선언된 FK 관계와 카디널리티
  - 모든 테이블의 전체 컬럼과 키 표시

주의: `payment_attempt`과 `provider_voucher`는 `order_id`, `voucher_number`로
다른 테이블과 업무상 연결되지만 실제 DB FK는 선언되어 있지 않다. 명세서의
점선은 논리 연결이고, ERD의 실선 관계는 Flyway에 선언된 실제 FK다.

## 2026-08-21 백엔드 포트폴리오 PDF 구성안

현재 문서, diagrams.net 01~10 페이지와 evidence를 바탕으로 16~20페이지
A4 가로형 포트폴리오 목차와 페이지별 개요를 작성했다.

- 기준 문서: `docs/backend-portfolio-outline.md`
- 핵심 형식: 기능 나열이 아닌 `문제 재현 → 원인 분석 → 단계별 개선 → 실제 검증 → 남은 한계` 사례 연구
- 구현 완료: PaymentAttempt, 외부 발행사 멱등성, 외부 호출 전 사전 검증
- 설계/TODO: Redis·Redisson, 조건부 잔액 차감, 보상 재시도, Redemption
- 최종 권장 산출물: 16~20페이지 상세 PDF와 지원서용 1페이지 요약 PDF

## 2026-08-30 Redis 중심 최종 포트폴리오 진행 원칙

새 TODO 문서를 만들지 않고 이 문서를 구현 현황과 다음 작업의 유일한 기준으로
계속 사용한다. 포트폴리오 서사는 별도 문서로 분리했다.

- 최종 이야기 기준: `docs/final-portfolio-story.md`
- Redis 상세 설계: `docs/redis-payment-idempotency-design.md`
- PDF 페이지 구성: `docs/backend-portfolio-outline.md`

진행 순서:

1. 현재 미커밋 문서·evidence·테스트 스크립트를 검토하고 커밋한다.
2. 현재 개선 브랜치에서 `improve/redis-idempotency`를 분기한다.
3. Redis·Redisson, `Idempotency-Key`, request hash, 결과 캐시와 정확한 replay를 구현한다.
4. 두 애플리케이션 인스턴스 동시 요청을 검증한다.
5. Redis 장애와 cache TTL 만료 시 DB fallback을 검증한다.
6. Redis 개선 결과와 수치를 문서·다이어그램·evidence에 반영한다.
7. 완료 시점에 제출용 `portfolio/v1` 브랜치와 PDF를 만든다.

이번 제출 범위에서는 보상 취소 outbox, Redemption, `limited_deal`을 제외한다.
서로 다른 주문의 동일 잔액 경쟁은 Redis 멱등키와 다른 문제임을 문서에
명시하고 조건부 UPDATE 또는 row lock을 후속 개선으로 둔다.

## 2026-08-30 Redis + DB 결제 멱등성 구현 및 검증 완료

`improve/redis-idempotency` 브랜치에서 Redis를 DB의 대체재가 아닌 분산 조율·결과
캐시 계층으로 추가했다. 상세 구현과 실제 검증 결과는 다음 문서를 기준으로 본다.

- 구현 결과: `docs/redis-payment-idempotency-result.md`
- 설계 배경: `docs/redis-payment-idempotency-design.md`
- HTTP 증거: `evidence/redis-idempotency/`

완료한 핵심 내용:

- `POST /api/payments/point/redis-idempotent`와 필수 `Idempotency-Key` 헤더
- client/method/path/key 범위와 SHA-256 request hash
- Redisson `RLock` 분산락, lock 획득 후 Redis·DB double check
- 최초 HTTP status/body의 MySQL 저장과 Redis TTL 결과 캐시
- 같은 키·다른 payload의 HTTP 422 거절
- Redis 장애와 cache miss 시 `payment_attempt` 기반 fallback 및 cache 재생성
- 8080·8081 두 Spring Boot 인스턴스 동시 호출 검증

실제 결과:

```text
최초 실행: HTTP 201, Idempotency-Replayed=false, DATABASE_CREATED
동시 요청: HTTP 201, Idempotency-Replayed=true, REDIS_CACHE
외부 provider_voucher: 1건
내부 voucher_purchase: 1건
cache 삭제 후: HTTP 201, DATABASE_CACHE_REBUILD, 최초 body 그대로
Redis 중지 후: HTTP 201, DATABASE_REPLAY, 최초 body 그대로
같은 키·다른 payload: HTTP 422 IDEMPOTENCY_KEY_REUSED
```

다음 우선순위:

1. 포트폴리오 PDF의 공개 정보와 표현을 최종 검토한다.
2. Redis·조건부 UPDATE 구현, evidence, 문서와 PDF를 하나의 커밋으로 정리한다.
3. 필요하면 지원서 첨부용 1페이지 요약 PDF를 추가한다.

### 문제–개선 중심 15페이지 백엔드 포트폴리오 PDF 재구성 완료

기능과 기술을 나열하던 기존 18페이지 구성을 걷어내고, 현재 구현과 실제 evidence를
기준으로 API 요청·응답 흐름의 변화가 먼저 보이는 A4 가로형 포트폴리오로 재구성했다.

- 결과: `output/pdf/point-payment-lab-portfolio.pdf`
- 생성 스크립트: `scripts/generate-problem-improvement-portfolio-pdf.py`
- 페이지 구성 문서: `docs/problem-improvement-portfolio-flow.md`
- 분량: 15페이지
- 표지 다음에 프로젝트 목적과 `Client`, `Shopping API`, `Application`, `MySQL`,
  `외부 쿠폰 발행사 (Provider Mock)`의 의미·책임·시스템 경계를 설명하는 입문 페이지 추가
- 공통 서사: `기존 API 흐름 → 문제 발생 지점 → 수정한 코드·데이터 → 개선 후 응답 → 달라진 이유`
- 각 개선 페이지의 고정 항목: `기존 문제점`, `수정한 부분`, `개선된 결과`, `달라진 이유`
- 개선 단계: `payment_attempt` 선점, provider 멱등성, 외부 호출 전 검증,
  Redis+DB 멱등성, 장애 fallback, 조건부 UPDATE

기존 구성안의 “Redis 구현 예정” 내용을 현재 완료 상태로 바꾸고 다음 실제
수치를 반영했다.

- 두 인스턴스 동시 요청: 외부 발행 1건, 내부 구매 1건, 동일 HTTP 201/body
- Redis 중지: MySQL 기반 `DATABASE_REPLAY`
- 완료 재요청 100회: DB-only 8.118ms, Redis+DB 2.588ms
- 서로 다른 주문의 동일 잔액 경쟁: HTTP 201/409, 구매 1건, 중복 차감 0

PDF 생성 후 15페이지를 PNG로 렌더링해 전체 흐름, 카드 경계, 시퀀스 다이어그램과
색상 일관성을 시각 검수했다. 최종본은 표지의 네이비·블루·퍼플·그린만 사용하며,
카드 안의 글자가 배경 영역을 벗어나지 않도록 자동 맞춤을 적용했다.

포트폴리오를 처음 읽는 사람도 한 문장만으로 의미를 파악할 수 있도록 전체 문구를
다시 점검했다. 특히 `issue`, `cancel`, `replay`, `fallback`, `unique`,
`stale balance`, `single-flight`처럼 설명 없이 사용된 용어를 다음처럼 바꿨다.

- 외부 issue → 외부 쿠폰 발행 API 호출
- cancel 보상 → 실패한 외부 쿠폰을 취소하는 API 호출
- exact replay → 최초 HTTP 상태 코드와 응답 본문을 그대로 반환
- DB fallback → Redis 장애 시 MySQL에 저장한 최초 응답으로 복구
- provider unique → 외부 발행사 DB에서 같은 orderId의 중복 저장 방지
- stale balance 경쟁 → 두 요청이 차감 전 같은 잔액을 읽는 경쟁

시스템 참여자 표기는 업무 역할이 먼저 보이도록 통일했다. 최초 소개 페이지에는
`외부 쿠폰 발행사 (Provider Mock)`로 구현 방식을 함께 밝히고, 이후 API 흐름도와
설명에는 `외부 발행사` 또는 `외부 쿠폰 발행사`를 사용한다. 실제 구현·DB 증거를
가리키는 `provider_voucher` 테이블명은 그대로 유지한다.

1차 개선 다이어그램의 `unique 승자` 표현은 unique 제약 자체를 선점하는 것처럼
보일 수 있어 수정했다. 실제 동작에 맞춰 `orderId 선점 성공 / 중복 저장 충돌`로
표시하고, 같은 orderId를 `payment_attempt`에 먼저 저장한 요청만 실제 결제를
실행한다고 설명한다. unique 제약은 선점 대상이 아니라 한 요청만 저장에 성공하게
보장하는 DB 방어 장치다.

같은 페이지의 `PROCESSING INSERT + flush`도 처음 보는 사람이 이해할 수 있도록
`PROCESSING 상태를 DB에 즉시 저장`으로 바꿨다. 수정 설명에는
`saveAndFlush`가 INSERT SQL 실행 시점을 앞당겨 외부 호출 전에 orderId 선점
성공 또는 중복 충돌을 확인한다는 내용을 추가했다. `flush`는 commit이 아니며,
SQL을 DB로 보내 제약조건을 확인하는 시점만 앞당긴다는 설명을 PDF에도 반영했다.

포트폴리오의 페이지 제목이 추상적이고 사용 기술이 한눈에 보이지 않는 문제를
수정했다. 공통 제목 크기를 22pt에서 18.5pt로 줄이고, 각 제목에 실제 구현 기술과
개선 대상을 포함했다. 3페이지는 읽기 안내에서 기술 스택·단계별 구현 지도로
재구성했다.

- Backend: Java 17, Spring Boot, Spring Data JPA, TransactionTemplate
- Data: MySQL 8, Flyway
- Concurrency: DB unique, saveAndFlush, Redisson RLock, 조건부 UPDATE
- Infra/검증: Docker Compose, Redis, curl 병렬 스크립트, 두 인스턴스 검증
- Redis 도입 이유: 여러 서버가 동시 실행 상태와 완료 결과를 공유하고, cache hit에서
  DB 중복 INSERT·SELECT를 줄이기 위해 사용
- Redis 장애 대비: 최초 HTTP status/body는 MySQL payment_attempt에 영구 저장

4~15페이지 제목도 `문제 재현`, `payment_attempt unique + saveAndFlush`,
`provider_voucher orderId unique`, `point_balance·point_lot 검증`, `Redisson
RLock + Redis cache`, `조건부 UPDATE`, 실제 검증 수치가 바로 드러나도록 변경했다.

`saveAndFlush` 사용 이유도 선점 기능 자체로 오해하지 않도록 표현을 보완했다.
orderId 처리권 선점은 `payment_attempt.orderId` unique 제약으로 한 요청만 INSERT에
성공하게 만드는 설계가 담당한다. `saveAndFlush`는 INSERT SQL과 unique 충돌 확인
시점을 외부 쿠폰 발행보다 앞으로 당기는 역할이며, flush는 commit이 아니라 SQL
실행 시점만 앞당긴다는 내용을 PDF에 반영했다.

## 2026-08-31 GitHub 공개 전 안전성 점검

이력서에 GitHub 링크를 공개하기 전에 저장소의 비공개 정보와 이식성을 점검했다.

- `docs/legacy-point-payment-interview-notes.md`는 `.gitignore`로 정상 제외
- `.env`, 비밀키, API token, Authorization/Cookie 값이 추적 파일에 없는지 확인
- `application.yml`과 Docker Compose의 비밀번호는 로컬 기본값이며 환경변수로 재정의 가능
- evidence의 쿠폰 번호와 PIN은 Provider Mock이 생성한 합성 테스트 데이터
- evidence 약 192KB, 포트폴리오 PDF 약 184KB로 저장소 공개에 무리가 없는 크기
- `build.gradle`에 남아 있던 이전 회사명 계열 group을 `com.paymentlab`으로 변경
- PDF 생성기의 사용자 절대 글꼴 경로를 `Path.home()`과
  `PORTFOLIO_BOLD_FONT` 환경변수 기반으로 변경하고 fallback 추가

다음 공개 준비 순서는 테스트·빌드 검증, 생성 파일 정리, README 개선, 커밋·push다.

공개 준비 검증 결과:

- `./gradlew clean test`: 성공
- `./gradlew build`: 성공, Spring Boot JAR 생성 확인
- Gradle 9 호환성 관련 deprecated feature 경고는 후속 정리 대상
- `.gradle`, `.idea`, `build`, 로그와 로컬 비공개 문서는 Git 추적에서 제외
- evidence와 PDF는 작은 용량이며 재현·검증 근거이므로 공개 추적 대상으로 유지

README를 Redis 구현 전 설명에서 현재 완료 상태를 보여주는 포트폴리오 진입점으로
전면 개편했다. 포트폴리오 PDF 링크, 개선 전후 수치, 1~5차 구현, Redis를 사용한
이유와 MySQL fallback, 기술 스택, 실행·테스트·재현 명령, 공개 데이터 안내와 남은
과제를 포함했다.

면접 준비용 페이지별 발표 대본은 공개 포트폴리오 산출물이 아니므로 로컬 파일로만
보존하고 Git 추적에서 제외했다. `.gitignore`에 경로를 등록하고 공개 문서에 있던
대본 링크와 상세 설명도 제거했다.

Legacy 흐름의 핵심 문장은 다음과 같이 명확하게 수정했다.

> 내부 결제 트랜잭션이 rollback되더라도, 외부 쿠폰 발행사에서 이미 발급한
> 쿠폰은 자동으로 취소되지 않는다.

재생성 명령:

```bash
PYTHONPATH=/tmp/point-payment-lab-pdf-libs \
  python3 scripts/generate-problem-improvement-portfolio-pdf.py
```

현재 스크립트는 ReportLab과 로컬 한글 글꼴을 사용한다. 다른 개발 환경에서도
재현하려면 의존성 설치 방법과 저장소 내 공개 가능한 한글 글꼴 또는 fallback
설정을 후속으로 정리해야 한다.

### 포트폴리오 색상·카드 UI 개선

표지에서 사용한 네이비·블루·퍼플·그린 계열만 18페이지 전체 강조 색상으로
사용하도록 통일했다. 기존 빨강과 주황 계열은 각각 퍼플과 블루 계열로 바꿨다.

카드 UI는 높이에 따라 제목·본문을 자동 배치하도록 생성 함수를 개선했다.

- 낮은 안내 바: 제목과 본문을 가로로 배치
- 일반 카드: 제목 줄 수에 따라 본문 시작 위치 계산
- 본문이 길면 카드 높이 안에서 글자 크기와 줄 간격 자동 축소
- 4페이지 오른쪽 도형과 하단 카드를 A4 안전 여백 안으로 재배치

최종 PDF는 15페이지 PNG 렌더링, 텍스트 누락·깨짐, 페이지 경계 밖 글자 여부를
다시 검증했다.

### Redis+DB API 흐름 다이어그램 갱신 완료

diagrams.net `결제프로젝트`의 07페이지를 설계안에서 구현 결과로 갱신했다.

- 변경 전: `07-4차 Redis 분산락 API 흐름 (설계안)`
- 변경 후: `07-4차 Redis+DB 멱등성 API 흐름 (구현 완료)`
- 반영 내용: Redisson 분산락, 결과 cache, `payment_attempt` 최초 status/body,
  Redis 장애 DB fallback, 조건부 잔액 UPDATE
- 반영 수치: 두 인스턴스 외부 발행·구매 각 1건, 완료 재요청 평균
  8.118ms→2.588ms, Redis 중지 시 HTTP 201 replay, 잔액 경쟁 HTTP 201/409

해설 문서와 다이어그램 가이드도 실제 구현 상태로 함께 갱신했다.

### 조건부 UPDATE 기반 동일 잔액 경쟁 개선 완료

서로 다른 `orderId`와 멱등키가 같은 5,000점 잔액을 동시에 사용할 때 Redis
멱등키 lock이 막지 못하는 경쟁을 MySQL 조건부 UPDATE로 개선했다.

- 상세 결과: `docs/conditional-point-balance-debit-result.md`
- 재현 스크립트: `scripts/run-competing-balance-test.sh`
- 실제 증거: `evidence/conditional-balance-debit/`

```text
update point_balance
set balance = balance - 5000
where id = 1 and balance >= 5000
```

실제 동시 요청 결과:

```text
서로 다른 주문 A/B, 서로 다른 Idempotency-Key, 시작 잔액 5,000
요청 A: HTTP 409 POINT_BALANCE_CONFLICT
요청 B: HTTP 201 DATABASE_CREATED
최종 잔액: 0
내부 구매: 1건
외부 바우처: ISSUED 1건 + 경쟁 실패분 CANCELED 1건
```

조건부 UPDATE의 affected rows가 1인 요청만 lot/source/ledger/purchase 처리를
계속한다. 0인 요청은 다른 결제가 잔액을 먼저 사용했거나 잔액이 부족한 것으로
판단한다. 기존 `/legacy`는 문제 재현용으로 그대로 두고 Redis+DB 결제 경로에만
적용했다.

남은 한계: legacy `balance` 컬럼이 `varchar`이므로 현재 MySQL SQL에서 숫자
CAST가 필요하다. 운영형 스키마에서는 `BIGINT` 또는 `DECIMAL` migration이
필요하다. 또한 경쟁 실패 요청도 사전 검증 후 외부 발행까지 수행하므로 보상
취소 1회가 발생한다. 외부 부작용까지 제거하려면 포인트 예약 상태와 복구 흐름을
별도로 설계해야 한다.

### DB-only와 Redis+DB 완료 재요청 비교

`scripts/benchmark-payment-idempotency.sh`로 이미 완료된 동일 결제를 각 방식에
100회 순차 재요청했다. 실제 결과는
`evidence/redis-idempotency/benchmark/summary.csv`에 보관한다.

| 방식 | 평균 | 최소 | 최대 | MySQL SELECT | MySQL INSERT 시도 |
| --- | ---: | ---: | ---: | ---: | ---: |
| DB-only | 8.118ms | 5.851ms | 22.226ms | 101 | 100 |
| Redis+DB | 2.588ms | 1.911ms | 4.248ms | 1 | 0 |

- Redis cache hit의 평균 응답 시간은 이 로컬 실험에서 약 68.1% 짧았다.
- DB-only는 재요청마다 `payment_attempt` INSERT 선점 후 unique 충돌을 처리하고
  기존 행을 SELECT하므로 INSERT 시도와 DB 조회가 반복됐다.
- Redis+DB는 warm cache에서 결과를 바로 반환해 애플리케이션 결제 경로의 DB
  접근이 발생하지 않았다. 관찰된 SELECT 1회는 MySQL global counter 기준의
  환경 잡음을 포함할 수 있다.
- 두 방식 모두 100회 재요청 후 `provider_voucher`와 `voucher_purchase`가 각각
  1건으로 유지돼 추가 외부 발행과 내부 구매는 없었다.
- 로컬 단일 서버·순차 요청·JPA SQL 로그 활성화 상태의 소규모 비교이므로 운영
  처리량 수치로 일반화하지 않고, cache hit 경로의 방향성 증거로만 사용한다.

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

#### 2026-07-23 구현 상태

구현 완료:

- Flyway V4로 `payment_attempt` 테이블과 `order_id` unique 제약 추가
- `POST /api/payments/point/idempotent` 추가
- `REQUIRES_NEW` 짧은 트랜잭션으로 외부 호출 전 `PROCESSING` 선점
- 최초 요청만 기존 결제 흐름에 진입
- 동시 처리 중인 동일 요청은 `409 PAYMENT_PROCESSING`
- 완료된 동일 요청은 저장 결과와 `Idempotency-Replayed: true`를 반환
- 같은 `orderId`에 다른 요청 내용이 들어오면 `409 IDEMPOTENCY_KEY_REUSED`
- 실패한 최초 요청은 `FAILED`와 실패 메시지 기록
- 개선 전용 `scripts/run-idempotent-payment-test.sh` 추가
- 선점, 성공 결과 재사용, 처리 중 차단, 다른 payload 거절, 실패 기록 단위 테스트 추가

검증 결과:

- `./gradlew test` 성공, 단위 테스트 5개 통과
- Flyway V4가 실제 MySQL에 적용되고 Hibernate schema validation 통과
- 별도 8081 서버 기동 성공
- 2026-07-28 실제 동시 HTTP 요청 검증 성공
- 요청 A는 `HTTP 201`로 결제 성공
- 요청 B는 외부 API 호출 전에 `HTTP 409`, `PAYMENT_PROCESSING`으로 차단
- Deadlock과 보상 취소가 발생하지 않음

2026-07-28 DB 검증 결과:

| 확인 대상 | 실제 결과 |
| --- | --- |
| `payment_attempt` | 동일 `order_id` 1건, `SUCCEEDED` |
| `provider_voucher` | 동일 `order_id` 1건, `ISSUED` |
| `voucher_purchase` | 동일 `order_id` 1건, `ISSUED / UNUSED` |
| `point_balance` | 5,000 포인트가 한 번만 차감되어 0 |

발행된 바우처:

```text
orderId: AL-IDEMPOTENT-VERIFY-001
voucherNumber: CP-3995487d-0929-45ba-a182-30f1c87c33c6
pointAmount: 5000
```

Legacy baseline과 비교:

| 항목 | Legacy | PaymentAttempt 개선 후 |
| --- | ---: | ---: |
| 동시 요청 | 2 | 2 |
| 외부 발행 | 2 | 1 |
| 내부 구매 | 1 | 1 |
| Deadlock | 1 | 0 |
| 보상 취소 | 1 | 0 |
| 중복 요청 응답 | HTTP 500 | HTTP 409 `PAYMENT_PROCESSING` |

포트폴리오용 개선 전후 문서:

- `docs/payment-idempotency-improvement-result.md`
- Legacy 문제 재현, Deadlock 원인, PaymentAttempt 설계, 실제 HTTP/DB 결과, 정량 비교와 시퀀스 다이어그램을 한 문서에 정리했다.

수동 검증:

```bash
bash scripts/run-idempotent-payment-test.sh AL-IDEMPOTENT-VERIFY-001
```

기대 결과:

```text
동시 요청 중 1건: HTTP 201
다른 1건: HTTP 409, code=PAYMENT_PROCESSING
provider_voucher: 동일 orderId 1건
voucher_purchase: 동일 orderId 1건
payment_attempt: 동일 orderId 1건, status=SUCCEEDED
```

결제 완료 후 같은 명령을 다시 실행하면 두 요청 모두 저장된 기존 결과를 `HTTP 200`으로 받아야 하며 외부 발행 건수는 증가하지 않아야 한다.

### 2차: 외부 발행사 이중 멱등성

- `provider_voucher.order_id` unique 제약 추가
- 같은 `orderId` 발행 재요청 시 새 바우처를 만들지 않음
- 이미 발행된 `voucherNumber`, `pinNumber`를 기존 결과로 반환
- 쇼핑몰과 외부 발행사 양쪽에서 동일 주문 중복 발행 방어

#### 문제 정의 및 개선 계획

1차 개선의 `payment_attempt`는 쇼핑몰의
`POST /api/payments/point/idempotent` 호출 경로에서 동일 주문을 방어한다.
따라서 이 경로로 들어온 따닥 결제는 최초 요청만 외부 발행 API를 호출한다.

하지만 외부 Mock의 `POST /mock/voucher-provider/vouchers/issue` 자체에는
`orderId` 중복 검사가 없다. 쇼핑몰 결제 API를 거치지 않고 같은 `orderId`로
Mock 발행 API를 두 번 호출하면 요청마다 새로운 바우처 번호와 PIN을 생성해
`provider_voucher`가 두 건 저장될 수 있다.

2차 개선에서는 호출 요청 횟수와 실제 발행 건수를 구분한다. 네트워크 재시도
등으로 외부 API 요청이 두 번 도착하더라도 실제 바우처는 한 장만 발행하고,
같은 요청에는 기존 발행 결과를 반환하는 것이 목표다.

상세 문제 정의, 계층별 보호 범위, 도식과 검증 계획:

- `docs/provider-voucher-idempotency-improvement-plan.md`

구현 전 주의:

- Legacy 재현 데이터에는 같은 `orderId`의 `provider_voucher`가 여러 건 있을 수 있다.
- 기존 데이터 보존이 필요하면 `provider_voucher.order_id`에 unique 제약을 바로
  추가하는 대신 `order_id` unique인 별도 `provider_issue_request` 테이블을
  우선 검토한다.

#### 2026-07-28 설계 검토 결과

중복 요청 방어에는 메모리, Redis, 기존 테이블의 unique 제약, 별도 요청
테이블을 사용할 수 있다. 새 테이블만이 답은 아니다.

현재 단계에서는 `provider_issue_request`를 바로 추가하지 않고 기존
`provider_voucher.order_id`에 unique 제약을 추가하는 최소 설계를 우선한다.

- 메모리는 재시작과 다중 서버 환경에서 기록을 공유할 수 없어 제외한다.
- Redis는 현재 실습 규모에 비해 인프라와 운영 복잡도가 크므로 제외한다.
- 동일 주문 재요청에는 요청을 단순히 무시하지 않고 기존 바우처 번호와 PIN을 반환한다.
- 같은 `orderId`를 다른 상품 코드에 사용하면 `409 IDEMPOTENCY_KEY_REUSED`로 거절한다.
- 단순 조회 후 insert가 아니라 DB unique 제약을 최종 동시성 방어선으로 사용한다.
- 상태·실패·재시도 이력이 필요해지면 `provider_issue_request`를 확장안으로 도입한다.

기존 Legacy 중복 데이터가 있으면 unique migration이 실패하므로 구현 전에
로컬 DB 초기화, 데이터 정리, 별도 요청 테이블 중 하나를 선택해야 한다.
현재 실습에서는 `evidence`에 Legacy 결과를 보존하고 DB를 초기화하는 방법을
우선 검토한다. 운영 환경에서는 중복 데이터를 임의로 삭제하면 안 된다.

#### 2차 개선 TODO

- [x] 개선 전 Mock issue API 동시 호출 2건을 재현하고 응답과 DB 결과를 저장한다.
- [x] 기존 DB의 중복 `provider_voucher.order_id`를 확인한다.
- [x] unique migration 적용을 위해 Legacy 증거 저장 후 로컬 DB를 초기화했다.
- [x] Flyway V5로 `provider_voucher.order_id` unique 제약을 추가한다.
- [x] 외부 Mock 발행 로직을 idempotent service로 분리한다.
- [x] 최초 요청은 새 바우처를 발행하고 `201`을 반환한다.
- [x] 동일 주문·동일 상품 재요청은 기존 결과와 `200`을 반환한다.
- [x] 재사용 응답에 `Idempotency-Replayed: true`를 추가한다.
- [x] 동일 주문·다른 상품 요청은 `409 IDEMPOTENCY_KEY_REUSED`로 거절한다.
- [x] 최초·순차 재요청·payload 충돌 단위 테스트를 추가한다.
- [x] 실제 HTTP 동시 호출에서 API 요청 2회, 실제 발행 1건을 검증한다.
- [x] HTTP·DB 증거와 개선 전후 도식을 결과 문서에 기록한다.
- [ ] CI에서 반복 가능한 DB 기반 동시 요청 통합 테스트를 추가한다.

세부 비교, 구현 및 검증 체크리스트:

- `docs/provider-voucher-idempotency-improvement-plan.md`

#### 2026-07-28 구현 및 검증 결과

- 개선 전 `PROVIDER-BASELINE-001` 동시 요청에서 HTTP 200 두 건과 서로 다른
  바우처 두 장이 생성되는 문제를 재현했다.
- V5 `V5__make_provider_voucher_order_id_unique.sql`을 추가했다.
- `ProviderVoucherIssueWriter`가 `REQUIRES_NEW` 트랜잭션에서 insert와 flush를
  수행하며 DB unique 제약으로 최초 발행 요청을 결정한다.
- `IdempotentProviderVoucherIssueService`가 unique 충돌 후 기존 바우처를 조회해
  동일 상품에는 기존 결과를, 다른 상품에는 409 conflict를 반환한다.
- 개선 후 `PROVIDER-IDEMPOTENT-001` 동시 요청은 HTTP 201/200으로 응답했고
  두 응답의 바우처 번호와 PIN이 동일했다.
- DB의 `provider_voucher`는 해당 `order_id`로 한 건만 저장됐다.
- 완료 후 재요청에서 `Idempotency-Replayed: true`, 다른 상품 요청에서
  `409 IDEMPOTENCY_KEY_REUSED`를 실제 확인했다.
- Flyway V1~V5와 Hibernate schema validation을 통과했다.
- `./gradlew test` 성공, 전체 테스트 9개 통과.
- 기존 쇼핑몰 멱등 결제 동시 요청도 HTTP 201/409
  `PAYMENT_PROCESSING`으로 정상 동작하는 것을 회귀 검증했다.

상세 결과와 증거:

- `docs/provider-voucher-idempotency-improvement-plan.md`
- `evidence/provider-issue-idempotency/`

사용자 수동 재검증:

- 2026-07-28 `PROVIDER-TEST-001` 최초 동시 호출에서 HTTP 201/200 확인
- 두 응답에서 동일한 `voucherNumber`, `pinNumber` 확인
- 같은 `orderId`로 다시 동시 호출했을 때 HTTP 200/200 확인
- 재호출에서도 최초 발행의 바우처 번호와 PIN이 그대로 반환됨
- 다른 상품 코드 충돌 요청에서 HTTP 409 `IDEMPOTENCY_KEY_REUSED` 확인
- DB 조회 결과 `PROVIDER-TEST-001`의 `provider_voucher`가 정확히 한 건임을 확인
- 저장된 바우처 번호와 PIN이 API 응답의 값과 일치함을 확인

### 3차: 잔액 부족 요청의 외부 호출 차단

- [x] 외부 발행 전에 상품 판매가와 요청 포인트 일치 검증
- [x] `point_balance` 잔액 검증
- [x] 사용 가능하고 만료되지 않은 `point_lot` 합계 검증
- [x] 잔액 부족 시 외부 issue 호출과 `provider_voucher` 생성을 하지 않음
- [x] 명확한 422 오류 응답 반환

완료 목표:

```text
잔액 부족 외부 issue 호출 1회 -> 0회
잔액 부족 provider_voucher 생성 1건 -> 0건
```

#### 2026-07-28 구현 및 검증 결과

잔액 5,000 상태에서 10,000원 상품을 구매해 Legacy 문제를 재현했다.

- 개선 전: HTTP 500, 외부 바우처 1건 발행 후 `CANCELED`, 내부 구매 0건
- 개선 후: HTTP 422 `INSUFFICIENT_POINT_BALANCE`
- 개선 후 `provider_voucher` 0건, `voucher_purchase` 0건
- 실패 전후 잔액 5,000 유지

구현:

- `PointPaymentPreValidator`에서 상품 가격, 총 잔액, 사용 가능 lot 합계를 검증
- `PointLotRepository.findUsableLots`에 만료 시각 조건 추가
- 외부 `issue` 호출 전에 검증을 완료
- 금액 불일치, 총 잔액 부족, 사용 가능 lot 부족을 서로 다른 오류 코드로 구분

검증:

- `./gradlew test` 성공, 전체 테스트 13개 통과
- 실제 8081 HTTP/DB 검증 성공
- 증거: `evidence/insufficient-balance-legacy/`,
  `evidence/insufficient-balance-improved/`

남은 고려사항:

- 사전 검증과 실제 차감 사이에 다른 주문이 잔액을 사용할 수 있는
  TOCTOU 경쟁은 별도 row lock 또는 조건부 update 개선이 필요하다.

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

### 추가 실습: Redis 분산락과 결과 캐시 기반 결제 멱등성

현재 `payment_attempt.order_id` unique 제약은 DB를 최종 멱등성 저장소로
사용한다. Redis 실습에서는 이를 제거하지 않고 다음 두 역할을 앞단에 추가한다.

- 분산락: 여러 애플리케이션 인스턴스에서 같은 멱등성 키의 결제 로직이
  동시에 실행되는 것을 줄인다.
- 결과 캐시: 완료된 동일 요청에 DB 조회 없이 저장된 응답을 빠르게 반환한다.

중요:

- Redis lock만으로는 결제 멱등성을 완성할 수 없다.
- lock TTL 만료, Redis 재시작·장애, 캐시 eviction이 발생할 수 있으므로
  `payment_attempt.order_id` unique 제약과 저장 결과를 최종 방어선으로 유지한다.
- 캐시가 없어도 DB 결과로 동일 응답을 복원할 수 있어야 한다.
- 현재 API는 최초 `201`, 완료 후 재요청 `200`, 처리 중 동시 요청 `409`를
  반환한다. HTTP 상태와 body까지 정확히 같은 응답을 목표로 한다면
  `payment_attempt`에 최초 HTTP status를 저장하고, 동시 요청은 완료를 짧게
  기다린 뒤 최초 결과를 반환하는 정책이 추가로 필요하다.

권장 처리 흐름:

```text
Idempotency-Key/orderId + request payload hash 생성
-> Redis 결과 캐시 조회
-> cache miss이면 Redis 분산락 획득
-> lock 획득 후 Redis와 DB를 다시 조회(double check)
-> payment_attempt DB unique 선점
-> 외부 발행 및 내부 결제
-> payment_attempt에 최초 status/body 저장
-> DB commit 후 Redis에 결과 캐시 저장
-> token 소유권을 확인하며 lock 해제
```

Redis 장애 시:

```text
Redis 사용 실패
-> DB payment_attempt 멱등성 경로로 fallback
-> 중복 결제는 계속 방지
-> 캐시 성능과 대기 기반 replay만 일시적으로 포기
```

구현 TODO:

- [x] Docker Compose에 Redis를 localhost 바인딩으로 추가
- [x] Redisson 의존성과 환경변수 설정 추가
- [x] `Idempotency-Key` 헤더와 기존 `orderId`의 관계 결정
- [x] 요청 payload hash를 만들어 같은 키의 다른 요청을 거절
- [x] scope hash 기반 Redis 분산락 구현
- [x] Redisson의 lock 소유권 확인과 원자적 unlock 적용
- [x] scope hash 기반 결과 캐시와 1시간 TTL 정책 구현
- [x] lock 획득 후 cache/DB double check 적용
- [x] 최초 HTTP status와 응답 body를 DB에 저장
- [x] 동시 요청의 500ms 대기와 처리 중 HTTP 409 정책 결정
- [x] Redis 장애 시 DB 기반 멱등성 fallback 구현
- [x] 애플리케이션 2개 인스턴스를 띄워 동일 요청 동시 테스트
- [x] Redis 정상·중단·cache miss·재시작 시나리오 검증
- [x] DB-only 방식과 Redis+DB 방식의 처리 흐름·지연시간·외부 호출 수 비교

상세 설계:

- `docs/redis-payment-idempotency-design.md`

첨부한 멱등성 자료를 기준으로 다음 사항을 설계에 반영했다.

- `Idempotency-Key` 요청 헤더 사용
- client, HTTP method, API path, 멱등키 조합으로 키 범위 지정
- 같은 키의 다른 payload는 `422`
- 처리 중인 같은 요청은 `409`
- 최초 HTTP status와 response body를 DB에 저장해 정확히 replay
- Redis lock/cache가 사라져도 DB 기록으로 복구
- Redis 장애 시 DB 멱등성 fallback
- Redisson `RLock`과 Java 로컬 lock의 차이
- `SET NX PX`, 소유권 token, Lua atomic unlock 원리
- Redisson watchdog의 TTL 자동 연장과 설정 주의사항
- `RLock`, `RBucket`의 프로젝트 내 역할
- Redisson이 DB unique를 대체하지 못하는 장애 시나리오
- DB-only와 Redis+DB의 충돌 수·조회 수·응답 시간 비교 지표

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

필드 역할:

- `voucherNumber`: 어떤 바우처인지 찾는 식별자
- `pinNumber`: 해당 바우처를 사용할 권한이 있는지 확인하는 비밀 인증값

현재는 두 값의 발급·저장·조회까지만 구현되어 있고, `pinNumber`를 실제로
검증하는 기능은 아직 없다. 상세 역할과 운영 보안 고려사항은
`docs/code-structure-and-flow.md`의
`voucherNumber와 pinNumber의 차이` 절을 기준으로 본다.

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
- API 로그와 관리자 목록에서는 `pinNumber`를 마스킹
- 운영 DB 저장 시 PIN 암호화와 키 관리 방식 검토
- PIN 원문 접근 권한과 조회 이력 관리

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
cd point-payment-lab
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
| DB 비밀번호 | `lab` (로컬 실습 기본값, 운영 사용 금지) |

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
| Password | `lab` (로컬 실습 기본값) |

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
