# Point Payment Lab 백엔드 포트폴리오 구성안

## 1. 포트폴리오 방향

이 포트폴리오는 쇼핑몰 기능을 많이 구현했다는 소개서보다, 포인트 결제
Legacy 흐름에서 중복 요청과 외부 API 정합성 문제를 직접 재현하고 단계적으로
개선한 백엔드 문제 해결 사례로 구성한다.

핵심 메시지는 다음과 같다.

> 동일 주문의 동시 요청으로 발생한 외부 바우처 중복 발행과 MySQL Deadlock을
> 재현하고, DB 기반 멱등성·외부 발행사 이중 방어·사전 검증을 단계적으로
> 적용해 외부 발행 2건을 1건으로, Deadlock과 불필요한 보상 취소를 0건으로
> 줄였다.

PDF 권장 분량은 본문 16~20페이지다. 전체 테이블 컬럼, 긴 로그, 전체 코드와
상세 설계는 부록 또는 GitHub 문서로 보내고, 본문에는 판단과 결과를 이해하는
데 필요한 근거만 넣는다.

## 2. 권장 목차

### 01. 표지 — 1페이지

- 제목: `Point Payment Lab`
- 부제: `Legacy 포인트 결제의 중복 요청·외부 API 정합성 개선 프로젝트`
- 역할: 개인 프로젝트 / 백엔드 설계·구현·검증
- 기술 키워드: Java, Spring Boot, JPA, MySQL, Flyway, Docker Compose
- GitHub 링크 또는 QR 코드

표지에는 기술 로고를 많이 배치하기보다 프로젝트를 한 문장으로 설명하는
문구를 크게 보여준다.

### 02. Executive Summary — 1페이지

채용 담당자가 이 페이지만 읽어도 프로젝트의 가치를 이해하게 만든다.

- 문제: 같은 `orderId`의 동시 결제 요청이 외부 쿠폰을 두 번 발행하고 DB
  Deadlock 및 HTTP 500을 발생시킴
- 접근: Legacy 보존 → 재현 → DB 증거 수집 → 방어선을 단계적으로 적용
- 결과:
  - 외부 발행 `2 → 1`
  - 내부 구매 `1 → 1`
  - Deadlock `1 → 0`
  - 보상 취소 `1 → 0`
  - 잔액 부족 외부 호출 `1 → 0`
  - 잔액 부족 응답 `HTTP 500 → HTTP 422`
- 내가 보여주고 싶은 역량: 트랜잭션 분석, 동시성, 멱등성, 외부 API 정합성,
  실험 기반 검증, DB 설계

### 03. 프로젝트 배경과 목표 — 1페이지

- 포인트로 외부 바우처를 구매하고 환불하는 Legacy 업무를 Spring Boot로 재현
- 운영 시스템을 그대로 복제하는 목적이 아니라 문제를 관찰하고 개선하는 실험실
- 구현 범위: 상품 등록·조회, 포인트 결제·환불, 외부 발행사 Mock, 조회 UI,
  API 로그, 증거 수집 스크립트
- 범위 밖: 실제 쿠폰 사용 Redemption, 운영 인증·보안, 결제 승인기관 연동
- `limited_deal`은 로직 추가 예정임을 명시

### 04. 시스템 구조와 핵심 도메인 — 2페이지

첫 페이지에는 시스템 경계를 보여준다.

```text
사용자/관리자 UI
→ 쇼핑몰 Spring Boot
→ MySQL
→ 외부 바우처 발행사 Mock
```

두 번째 페이지에는 핵심 ERD를 축약해서 넣는다.

- 포인트: `point_wallet`, `point_balance`, `point_source_balance`, `point_lot`,
  `point_ledger`
- 구매: `voucher_product`, `voucher_purchase`
- 멱등성: `payment_attempt`
- 외부 시스템: `provider_voucher`
- 환불: `point_credit`

전체 컬럼 명세는 본문에 모두 넣지 않고 부록으로 보낸다. 본문에서는 다음
차이만 확실하게 설명한다.

- `point_balance`: 현재 총잔액
- `point_lot`: 출처·만료일을 가진 실제 사용 단위
- `point_ledger`: 잔액이 변한 이유를 기록하는 원장
- `voucher_purchase`: 쇼핑몰 구매 결과
- `provider_voucher`: 외부 발행사 관점의 쿠폰
- `payment_attempt`: 구매 결과가 아니라 요청 처리 상태와 재사용할 응답

### 05. Legacy 결제·환불 흐름 — 1페이지

- 결제: 지갑/상품/잔액 조회 → 외부 issue → 내부 포인트 트랜잭션
- 환불: 구매/원장/lot 조회 → 내부 포인트 복구 → 외부 cancel
- MySQL rollback이 외부 API 결과를 되돌릴 수 없다는 트랜잭션 경계
- `voucherNumber`와 `pinNumber`의 차이
  - 바우처 번호: 어떤 쿠폰인지 식별하고 환불·추적에 사용
  - PIN: 향후 Redemption에서 쿠폰 사용 권한을 검증하는 비밀값
  - 현재 프로젝트에서는 발급·저장·조회까지만 구현

### 06. 문제 재현 — 동일 주문 따닥 결제 — 2페이지

첫 페이지에는 개선 전 시퀀스 다이어그램을 넣는다.

- 같은 `orderId` 요청 A/B
- 외부 issue 두 번
- 내부 트랜잭션 동시 실행
- 한 건 성공, 한 건 Deadlock과 HTTP 500
- 실패 요청의 외부 쿠폰 보상 취소

두 번째 페이지에는 실제 증거를 넣는다.

- 응답 A: HTTP 500 및 Deadlock 메시지
- 응답 B: HTTP 201
- `provider_voucher`: 같은 주문으로 ISSUED 1건 + CANCELED 1건
- `voucher_purchase`: 1건
- `point_balance`: 한 번 차감
- Deadlock 순환 대기:
  - T1: `point_source_balance` 보유 → 주문 unique lock 대기
  - T2: 주문 unique lock 보유 → `point_source_balance` 대기

### 07. 1차 개선 — 쇼핑몰 결제 멱등성 — 2페이지

설계 페이지:

- 외부 호출 전에 `payment_attempt.order_id` unique로 `PROCESSING` 선점
- `REQUIRES_NEW`의 짧은 트랜잭션과 `saveAndFlush`
- `PROCESSING`, `SUCCEEDED`, `FAILED` 상태
- 같은 주문·같은 payload와 같은 주문·다른 payload 구분
- 완료된 요청은 저장된 바우처 결과 반환

검증 페이지:

- 최초 요청 HTTP 201
- 동시 요청 HTTP 409 `PAYMENT_PROCESSING`
- 완료 후 재요청 HTTP 200 + 기존 바우처
- 외부 발행 1건, 구매 1건, Deadlock 0건, 보상 취소 0건
- 남은 한계: 외부 Mock API 직접 중복 호출은 아직 방어하지 못함

### 08. 2차 개선 — 외부 발행사 이중 멱등성 — 2페이지

설계 페이지:

- `provider_voucher.order_id` unique
- 최초 요청은 새 쿠폰과 PIN 생성, HTTP 201
- 같은 주문·같은 상품은 기존 번호와 PIN replay, HTTP 200
- 같은 주문·다른 상품은 HTTP 409 `IDEMPOTENCY_KEY_REUSED`
- 애플리케이션의 조회-후-insert가 아니라 DB unique를 최종 경쟁 방어선으로 사용

검증 페이지:

- 동시 issue 요청 두 건의 `voucherNumber`, `pinNumber` 동일
- DB의 `provider_voucher` 한 건
- 완료 후 재요청도 같은 결과
- 쇼핑몰 계층과 외부 발행사 계층이 각각 자신의 멱등성을 보장하는 이유

### 09. 3차 개선 — 실패가 확실한 외부 호출 차단 — 1페이지

- 상품 판매가와 요청 포인트 일치 검증
- `point_balance` 총잔액 검증
- 미만료·사용 가능 `point_lot` 합계 검증
- 외부 `issue`보다 검증을 앞에 배치
- 결과: HTTP 500 → 422, issue/cancel 1 → 0, 잔액 유지
- 남은 한계: 조회와 실제 차감 사이 TOCTOU 경쟁 조건

### 10. 개선 전후 종합 비교 — 1페이지

| 지표 | Legacy | 1~3차 개선 후 |
| --- | ---: | ---: |
| 같은 주문 동시 요청 | 2 | 2 |
| 외부 쿠폰 발행 | 2 | 1 |
| 내부 구매 | 1 | 1 |
| Deadlock | 1 | 0 |
| 보상 취소 | 1 | 0 |
| 중복 요청 응답 | HTTP 500 | HTTP 409 / 완료 후 replay |
| 잔액 부족 외부 issue | 1 | 0 |
| 잔액 부족 응답 | HTTP 500 | HTTP 422 |

결과를 과장하지 않도록 최초 응답과 replay의 HTTP status/body가 현재 완전히
동일하지 않다는 점도 함께 적는다.

### 11. 기술적 의사결정과 트레이드오프 — 1페이지

- 왜 메모리 Map이나 `synchronized`가 아닌 DB unique를 먼저 선택했는가
- 왜 `payment_attempt`과 `provider_voucher` 양쪽에 방어선이 필요한가
- 왜 Redis를 처음부터 도입하지 않았는가
- 왜 사전 잔액 검증만으로 동시 차감이 안전하지 않은가
- `REQUIRES_NEW`의 장점과 상태가 분리 commit되는 데 따른 주의점
- 외부 API와 DB 사이에는 단일 ACID 트랜잭션을 만들 수 없다는 점

### 12. 남은 한계와 다음 개선 — 1페이지

구현 완료와 설계안을 시각적으로 구분한다.

구현 예정:

- 서로 다른 주문이 같은 잔액을 쓰는 문제: `BIGINT` 전환과 조건부 UPDATE 또는 row lock
- 외부 cancel 실패 복구: `compensation_task`/outbox와 백그라운드 재시도
- 다중 서버 실습: Redis/Redisson 분산락, 결과 캐시, DB fallback
- 정확한 멱등 replay: 최초 HTTP status/body와 request hash 저장
- Redemption: `voucherNumber + pinNumber` 검증 후 `USED` 처리
- DB 기반 동시 요청 통합 테스트와 CI 자동화

Redis는 구현 성과가 아니라 후속 설계로 표시한다. Redis lock, DB unique,
조건부 UPDATE가 서로 다른 문제를 해결한다는 점을 설명한다.

### 13. 회고와 배운 점 — 1페이지

- 최종 DB 행만 보면 놓치는 외부 호출과 중간 상태가 있다는 점
- 동시성 문제는 API 응답, 외부 호출 수, DB 상태, Deadlock 로그를 함께 봐야 함
- 멱등성은 중복을 단순 무시하는 것이 아니라 기존 결과를 안전하게 재사용하는 것
- 사전 검증, 멱등키, DB unique, 트랜잭션, 보상 처리는 서로 대체 관계가 아님
- 설계안과 실제 검증 결과를 분리해 기술하는 습관

### 14. 부록 — 2~4페이지 또는 외부 링크

- 전체 ERD
- 포인트 영역 테이블 명세서
- 바우처·멱등성 테이블 명세서
- API 목록
- 테스트 명령과 evidence 파일 경로
- 핵심 코드 및 GitHub 링크

## 3. 추천 PDF 페이지 구성

| 페이지 | 내용 |
| ---: | --- |
| 1 | 표지 |
| 2 | Executive Summary |
| 3 | 배경·목표·역할 |
| 4~5 | 아키텍처와 핵심 ERD |
| 6 | Legacy 결제·환불 흐름 |
| 7~8 | 따닥 결제 재현과 Deadlock 증거 |
| 9~10 | 1차 PaymentAttempt 개선 |
| 11~12 | 2차 외부 발행 멱등성 |
| 13 | 3차 외부 호출 전 검증 |
| 14 | 개선 전후 종합 비교 |
| 15 | 기술적 의사결정 |
| 16 | 남은 한계와 후속 설계 |
| 17 | 회고 |
| 18~20 | 부록 |

## 4. 시각 디자인 원칙

- A4 가로형을 권장한다. 시퀀스 다이어그램, ERD, 비교표가 세로형보다 잘 보인다.
- 한 페이지에는 하나의 메시지만 둔다.
- 본문 글자 크기는 인쇄 기준 10pt 이하로 내리지 않는다.
- 색상은 현재 다이어그램 범례를 유지한다.
  - 빨강: 문제와 실패
  - 주황: 외부 시스템
  - 초록: 성공과 검증 결과
  - 파랑: 적용한 개선
  - 보라: 후속 설계
- 긴 로그 전체를 이미지로 넣지 않고 핵심 행을 강조한 짧은 캡처와 해석을 함께 둔다.
- 코드는 클래스 전체가 아니라 의사결정을 보여주는 8~15줄만 사용한다.
- 각 개선 페이지는 `문제 → 선택 → 구현 → 검증 → 남은 한계` 순서를 통일한다.
- 실제 구현은 `Implemented`, 계획은 `Designed / Next` 배지로 구분한다.

## 5. PDF 제작 순서

1. diagrams.net의 01~10 페이지를 SVG 또는 고해상도 PNG로 내보낸다.
2. evidence에서 HTTP 응답과 DB 결과 중 포트폴리오에 필요한 부분만 선별한다.
3. 이 목차를 기준으로 페이지별 원고를 작성한다.
4. 동일한 마스터 레이아웃으로 문서를 편집한다.
5. PDF로 내보낸 뒤 글자 크기, 잘림, 이미지 선명도, 링크 동작을 검수한다.
6. 공개 전 PIN, 로컬 경로, 이전 회사 관련 내용, 계정 및 비밀정보 노출을 다시 확인한다.

최종 산출물은 다음 두 버전이 적합하다.

- `point-payment-lab-portfolio.pdf`: 16~20페이지 상세본
- `point-payment-lab-one-page.pdf`: 지원서 첨부용 1페이지 요약본

## 6. 현재 자료와 페이지 매핑

| 포트폴리오 내용 | 사용할 자료 |
| --- | --- |
| 문제 발생 흐름 | diagrams.net 01, 03 |
| 전체 개선 개요 | diagrams.net 02 |
| 1차 개선 | diagrams.net 04, `payment-idempotency-improvement-result.md` |
| 2차 개선 | diagrams.net 05, `provider-voucher-idempotency-improvement-plan.md` |
| 3차 개선 | diagrams.net 06, 잔액 부족 evidence |
| 후속 Redis 설계 | diagrams.net 07, `redis-payment-idempotency-design.md` |
| 테이블 설명 | diagrams.net 08, 09 |
| 전체 ERD | diagrams.net 10 |
| 서술형 흐름 | `payment-api-flow-diagram-explanation.md` |
| 정량 증거 | `evidence/duplicate-payment-legacy`, `evidence/provider-issue-idempotency`, `evidence/insufficient-balance-*` |
