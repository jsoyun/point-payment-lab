# 문제와 개선 중심 포트폴리오 흐름

## 핵심 구성 원칙

기술을 도입한 순서보다 API 요청과 응답이 달라진 원인을 중심으로 설명한다.
각 개선 페이지는 다음 네 질문에 답한다.

1. 기존 요청은 어느 시스템을 어떤 순서로 통과했는가?
2. 정확히 어느 지점에서 중복 호출, 실패 또는 경쟁이 발생했는가?
3. 코드와 DB의 무엇을 어느 앞단에 추가했는가?
4. HTTP 응답, 외부 호출 수, DB 행이 왜 달라졌는가?

## 최종 15페이지 구성

| 페이지 | 질문 | 핵심 메시지 |
| ---: | --- | --- |
| 1 | 어떤 프로젝트인가? | Legacy API 문제를 재현하고 흐름을 바꾼 결제 개선 프로젝트 |
| 2 | 누가 요청을 처리하는가? | Client·Shopping API·Application·MySQL·외부 쿠폰 발행사의 역할과 경계 |
| 3 | 어떤 기술을 어디에 적용했는가? | Java·Spring·JPA·MySQL·Redis·Redisson과 1~5차 구현 연결 |
| 4 | Legacy 요청은 어떻게 움직이는가? | 외부 HTTP 호출과 MySQL 트랜잭션의 불일치 |
| 5 | 무엇이 실제로 잘못됐는가? | 같은 orderId로 쿠폰 2장, HTTP 201/500, Deadlock |
| 6 | 1차에서 무엇을 바꿨는가? | payment_attempt unique와 saveAndFlush로 실행권 선점 |
| 7 | 왜 외부 발행사에도 개선이 필요한가? | 쇼핑몰 우회 요청도 발행사 DB의 orderId 중복 방지로 차단 |
| 8 | 왜 잔액 부족 외부 호출이 사라졌는가? | issue 전에 가격·balance·lot 검증 |
| 9 | 두 서버 요청은 어떻게 한 번만 실행되는가? | Redisson RLock + Redis cache + MySQL 최초 응답 저장 |
| 10 | Redis 장애 때 왜 결과가 유지되는가? | MySQL 최초 status/body fallback |
| 11 | 다른 주문의 잔액 경쟁은 어떻게 막는가? | 조건부 UPDATE와 affected rows |
| 12 | API 응답이 구체적으로 어떻게 변했는가? | 시나리오별 기존/개선 HTTP와 중단 위치 비교 |
| 13 | 최종 방어선은 어떻게 겹쳐 있는가? | 검증·Redis·쇼핑몰 DB·외부 발행사 DB·조건부 UPDATE |
| 14 | 개선을 무엇으로 증명했는가? | HTTP A/B, 외부 행, 구매 행, balance/lot, 성능 수치 |
| 15 | 무엇을 배웠고 무엇이 남았는가? | 역할 분리와 포인트 예약·보상 outbox 후속 과제 |

## 2페이지 시스템 역할 설명

- `Client`: 웹 UI, curl, HTTP Client와 테스트 스크립트처럼 요청을 보내고 응답을 받는 호출자
- `Shopping API`: 같은 Spring Boot 서버의 Controller 영역으로, HTTP 입력과 출력의 경계
- `Application`: Service 영역으로, 멱등성·검증·외부 호출·포인트 차감의 실행 순서를 조율
- `MySQL`: 잔액·원장·구매·최초 응답을 보존하는 영구 저장소이자 장애 복구 근거
- `외부 쿠폰 발행사`: 프로젝트 내부의 `Provider Mock` API로 재현했으며, 쇼핑몰 DB transaction 밖에서 동작

`Shopping API`와 `Application`은 서로 다른 서버가 아니라 동일 Spring Boot
애플리케이션의 서로 다른 계층이다. 외부 쿠폰 발행사는 프로젝트 내부의
`Provider Mock` API로 구현했지만, 외부 HTTP 시스템과의 transaction 경계를
재현하기 위해 논리적으로 분리했다.

## 개선 페이지 공통 설명 순서

### 기존 문제점

“기존에는 요청이 이 지점까지 동시에 통과했습니다. 따라서 외부 호출 또는 DB
변경이 중복으로 발생할 수 있었습니다.”

### 수정한 부분

“실패가 발생한 뒤 처리하는 대신, 문제가 발생하기 전 단계에 선점·검증·원자적
변경을 추가했습니다.”

### 개선된 결과

“HTTP 코드뿐 아니라 provider_voucher, voucher_purchase, balance와 lot까지
확인해 실제 부작용이 줄었는지 검증했습니다.”

### 달라진 이유

“요청이 이전보다 앞선 위치에서 멈추거나, 완료 결과를 다시 계산하지 않고 최초
결과를 재사용하거나, DB가 한 트랜잭션만 자원을 선점하게 되었기 때문입니다.”

## 발표의 중심 문장

> Redis를 추가해서 결제가 안전해진 것이 아니라, 같은 요청의 동시 실행은
> Redis와 payment_attempt가, 외부 쿠폰 중복은 발행사 DB의 orderId 중복 방지가, 서로 다른
> 주문의 포인트 경쟁은 조건부 UPDATE가 각각 담당하도록 API 흐름을 바꿨습니다.
