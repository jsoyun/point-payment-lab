# 코드 구조와 결제/환불 흐름

이 문서는 `point-payment-lab` 프로젝트의 패키지 구조, 도메인 역할, 테이블 변경 순서, Java 구현 의도를 정리한다.

테스트 전에 이 문서를 먼저 읽으면 API 호출 후 어떤 코드가 실행되고 어떤 테이블이 바뀌는지 따라가기 쉽다.

## 1. 패키지 구조

`com.paymentlab.voucher` 아래는 기능별로 나뉘어 있다.

```text
common
-> 공통 유틸/예외 처리

payment
-> 포인트 결제 API, 결제 서비스, 포인트/상품/구매/원장 엔티티

provider
-> 외부 바우처 제공사 API 연동부
-> 현재는 실제 외부 API 대신 mock API도 함께 구현

refund
-> 포인트 환불 API, 환불 서비스
```

엄격한 DDD 구조라기보다는 학습용으로 결제 흐름을 이해하기 쉽게 나눈 구조다.

`payment` 패키지 안에는 결제뿐 아니라 포인트 지갑, 포인트 잔액, 포인트 묶음, 원장 엔티티도 들어 있다. 이유는 이 프로젝트의 핵심 흐름이 "포인트 결제"라서, 결제 흐름에서 직접 변경되는 포인트 관련 모델을 한곳에 모아두었기 때문이다.

## 2. common 패키지

### `Money`

포인트 금액 계산을 담당하는 공통 유틸이다.

현재 일부 포인트 값은 DB에 문자열로 저장된다.

예를 들어 `point_balance.balance`, `point_lot.amount`, `point_source_balance.balance`가 `varchar` 타입이다. 그래서 계산할 때 문자열을 숫자로 바꾸고 다시 문자열로 저장하는 처리가 필요하다.

`Money`는 이런 계산을 한곳에 모아둔다.

### `ApiExceptionHandler`

Controller에서 발생한 예외를 HTTP 응답으로 바꿔주는 공통 예외 처리 클래스다.

예를 들어 잘못된 요청이나 비즈니스 검증 실패가 발생했을 때, 서버가 그냥 죽지 않고 클라이언트에 에러 응답을 내려주기 위한 역할이다.

## 3. payment 패키지

포인트 결제의 중심 패키지다.

### `PointPaymentController`

포인트 결제 API 진입점이다.

```http
POST /api/payments/point/legacy
```

요청을 받으면 `LegacyPointPaymentService.pay()`를 호출한다.

Controller는 직접 결제 로직을 처리하지 않는다. Controller는 HTTP 요청을 Java 객체로 받고, 서비스에 넘기고, 서비스 결과를 HTTP 응답으로 반환하는 얇은 계층이다.

### `LegacyPointPaymentService`

기존 방식의 포인트 결제 흐름을 재현한 핵심 서비스다.

주요 메서드는 두 개다.

```java
public PointPaymentResponse pay(PointPaymentRequest request)
```

전체 결제 흐름을 조율한다.

```java
private PointPaymentResponse savePaymentTransaction(...)
```

내부 DB transaction 안에서 실행될 DB 변경만 담당한다.

이렇게 나눈 이유는 외부 API 호출과 내부 DB transaction의 경계를 명확하게 보기 위해서다.

현재 실습에서 확인하려는 문제는 바로 이 경계에서 발생한다.

```text
외부 API 호출 성공
-> 내부 DB 저장 실패
-> 외부와 내부 상태가 어긋날 수 있음
```

## 4. provider 패키지

외부 바우처 제공사와의 연동을 담당한다.

### `VoucherProviderClient`

외부 API를 호출하는 클라이언트 역할이다.

```java
public IssueVoucherResponse issue(IssueVoucherRequest request)
```

바우처 발행 API를 호출한다.

```java
public void cancel(CancelVoucherRequest request)
```

바우처 취소 API를 호출한다.

현재는 실제 외부 API가 아니라 프로젝트 내부 mock API를 호출하도록 되어 있다.

### `MockVoucherProviderController`

실제 외부 바우처 제공사 대신 만든 mock 서버다.

```http
POST /mock/voucher-provider/vouchers/issue
POST /mock/voucher-provider/vouchers/cancel
```

발행 API가 호출되면 `provider_voucher` 테이블에 row를 저장한다.

이 테이블을 보면 외부 API가 몇 번 호출되었는지 확인할 수 있다.

따닥 결제 실험에서 중요한 테이블이다.

## 5. refund 패키지

포인트 환불 흐름을 담당한다.

### `PointRefundController`

포인트 환불 API 진입점이다.

```http
POST /api/refunds/point/legacy
```

요청으로 `voucherNumber`를 받아 `LegacyPointRefundService.refund()`를 호출한다.

### `LegacyPointRefundService`

기존 방식의 포인트 환불 흐름을 재현한 서비스다.

주요 메서드는 두 개다.

```java
public PointRefundResponse refund(PointRefundRequest request)
```

전체 환불 흐름을 조율한다.

```java
private PointRefundResponse restorePointTransaction(VoucherPurchase withdrawal)
```

내부 DB transaction 안에서 포인트 복구와 환불 이력 저장을 담당한다.

현재 환불 구현에서는 내부 DB 환불 처리가 먼저 되고, 외부 바우처 취소 API가 나중에 호출된다.

이 구조는 외부 API 실패 시 내부 DB와 외부 바우처 상태가 어긋날 수 있는 개선 포인트다.

## 6. 테이블 역할

### `point_wallet`

사용자 포인트 지갑이다.

결제 요청의 `pointWalletUid`로 조회한다.

초기 seed 데이터에서 1건 생성된다.

### `voucher_product`

구매 가능한 바우처 상품이다.

결제 요청의 `voucherProductId`로 조회한다.

외부 바우처 API를 호출할 때 `voucher_product_code`를 사용한다.

초기 seed 데이터에서 1건 생성된다.

### `point_balance`

지갑 기준 총 포인트 잔액이다.

결제 시 차감되고, 환불 시 복구된다.

초기 seed 데이터에서 생성된다.

### `point_source_balance`

포인트 출처별 잔액이다.

총 잔액인 `point_balance`의 하위 잔액이라고 보면 된다.

결제 시 사용된 포인트 묶음만큼 차감되고, 환불 시 복구된다.

### `point_lot`

만료일을 가진 포인트 묶음이다.

결제 시 사용 가능한 `point_lot`을 만료일 빠른 순으로 조회해서 사용 처리한다.

결제 성공 시:

```text
status = USED
voucher_number = 발급된 바우처 번호
```

환불 성공 시:

```text
status = NULL
voucher_number = NULL
```

### `provider_voucher`

외부 바우처 제공사 API mock 저장소다.

내부 결제 DB라기보다는 외부 시스템이 처리한 결과를 관찰하기 위한 테이블이다.

바우처 발행 API가 호출되면 즉시 row가 저장된다.

따닥 결제에서 외부 API가 중복 호출되었는지 확인하는 핵심 테이블이다.

### `voucher_purchase`

내부 바우처 구매/결제 결과 테이블이다.

외부 바우처 번호, 핀 번호, 내부 거래번호, 사용 포인트, 결제 상태를 저장한다.

`order_id`에 unique 제약이 있다. 그래서 같은 `orderId`가 내부 결제 이력에 중복 저장되는 것은 막을 수 있다.

하지만 이 저장은 외부 바우처 발행 API 호출 이후에 일어나므로, 외부 API 중복 호출까지 막지는 못한다.

### `point_ledger`

포인트 원장 이력이다.

결제 시:

```text
state = WITHDRAWAL
title = 포인트 바우처 구매
```

환불 시:

```text
state = RETURN
title = 포인트 바우처 환불
```

### `point_credit`

환불성 포인트 입금 기록이다.

환불 처리 시 `point_ledger`의 `RETURN` 이력과 함께 생성된다.

## 7. 정상 결제 시 데이터 변경 순서

정상 결제 요청:

```http
POST /api/payments/point/legacy
```

실행 순서:

```text
1. PointPaymentController가 요청 수신
2. LegacyPointPaymentService.pay() 호출
3. point_wallet 조회
4. voucher_product 조회
5. point_balance 조회
6. point_balance가 해당 point_wallet 소유인지 검증
7. VoucherProviderClient.issue() 호출
8. MockVoucherProviderController.issue() 실행
9. provider_voucher insert
10. 내부 DB transaction 시작
11. 사용 가능한 point_lot 조회
12. point_lot 사용 처리
13. point_source_balance 차감
14. point_balance 차감
15. point_ledger insert, state = WITHDRAWAL
16. voucher_purchase insert
17. 응답 반환
```

중요한 점:

```text
provider_voucher insert가 내부 DB transaction보다 먼저 일어난다.
```

즉 외부 바우처 발행은 이미 성공했는데, 이후 내부 DB 저장이 실패할 수 있다.

현재 구현은 DB 실패 시 외부 바우처 취소 API를 호출하도록 되어 있지만, 외부 취소 API마저 실패하면 상태 불일치가 남을 수 있다.

## 8. 결제 요청 DTO와 사전 데이터

포인트 결제 API의 요청 DTO는 다음과 같다.

```java
public record PointPaymentRequest(
        @NotBlank String orderId,
        @NotBlank String pointWalletUid,
        @NotNull Long voucherProductId,
        @NotNull Long pointBalanceId,
        @Min(1) long point
) {
}
```

각 필드의 의미는 다음과 같다.

| 필드 | 의미 | 사용 위치 |
| --- | --- | --- |
| `orderId` | 주문/거래 ID | 내부 결제 이력 저장, 중복 결제 판단 기준 |
| `pointWalletUid` | 사용자 지갑 식별자 | `point_wallet` 조회 |
| `voucherProductId` | 구매할 바우처 상품 ID | `voucher_product` 조회 |
| `pointBalanceId` | 사용할 포인트 잔고 ID | `point_balance` 조회 |
| `point` | 사용할 포인트 금액 | 포인트 차감, 원장 기록, 구매 이력 저장 |

결제 서비스는 요청을 받으면 먼저 `voucherProductId`로 상품을 조회한다.

```java
VoucherProduct voucherProduct = voucherProductRepository.findById(request.voucherProductId())
        .orElseThrow(() -> new IllegalArgumentException("voucher product not found"));
```

따라서 결제 전에 `voucher_product` 테이블에 상품이 미리 등록되어 있어야 한다.

외부 바우처 발행 API를 호출할 때도 이 상품의 코드가 필요하다.

```java
voucherProduct.getVoucherProductCode()
```

DB 저장 실패 시 외부 바우처를 취소할 때도 같은 상품 코드가 필요하다.

```java
voucherProviderClient.cancel(new CancelVoucherRequest(
        voucherProduct.getVoucherProductCode(),
        issuedVoucher.voucherNumber()
));
```

초기 상품은 Flyway seed 데이터에서 테스트용으로 미리 넣는다.

```sql
insert into voucher_product (id, voucher_product_code, voucher_name, sell_price, use_term)
values (1, 'VOUCHER-COFFEE-5000', '커피 5천원권', 5000, 366);
```

그래서 테스트 결제 요청에서는 `voucherProductId`로 `1`을 사용하면 된다.

```json
{
  "orderId": "AL-TEST-001",
  "pointWalletUid": "point-wallet-001",
  "voucherProductId": 1,
  "pointBalanceId": 1,
  "point": 5000
}
```

추가로 바우처 상품을 등록하고 싶으면 admin API를 사용할 수 있다.

```http
POST /api/admin/voucher-products
Content-Type: application/json

{
  "voucherProductCode": "VOUCHER-CHICKEN-20000",
  "voucherName": "치킨 2만원권",
  "sellPrice": 20000,
  "useTerm": 366
}
```

상품 목록은 다음 API로 확인할 수 있다.

```http
GET /api/admin/voucher-products
```

현재 아직 없는 API는 다음과 같다.

```text
지갑 생성 API 없음
포인트 충전 API 없음
포인트 잔고 생성 API 없음
```

이 프로젝트의 현재 목적은 결제/환불 legacy 흐름을 재현하는 것이므로, 지갑/포인트 관련 기본 데이터는 `V2__seed_legacy_payment_data.sql`에서 미리 세팅한다.

나중에 기능을 더 확장한다면 다음 API를 추가할 수 있다.

```text
POST /api/admin/point-wallets
-> 테스트 지갑 생성

POST /api/admin/point-balances/charge
-> 포인트 충전 또는 포인트 묶음 생성
```

또 하나 주의할 점은 `pointBalanceId`를 클라이언트가 직접 넘기는 구조다.

현재 구현에서는 다음 검증으로 최소 방어를 하고 있다.

```java
if (!pointBalance.getPointWalletId().equals(pointWallet.getId())) {
    throw new IllegalArgumentException("point balance does not belong to point wallet");
}
```

즉 요청으로 넘어온 `pointBalanceId`가 요청 지갑의 잔고인지 확인한다.

하지만 더 나은 구조는 클라이언트가 `pointBalanceId`를 직접 넘기지 않고, 서버가 `pointWalletUid` 기준으로 사용 가능한 포인트 잔고를 직접 찾는 것이다.

현재는 기존 방식 재현과 실험을 위해 요청값으로 받도록 둔 상태다.

## 9. 환불 시 데이터 변경 순서

환불 요청:

```http
POST /api/refunds/point/legacy
```

실행 순서:

```text
1. PointRefundController가 voucherNumber 수신
2. LegacyPointRefundService.refund() 호출
3. voucher_purchase 조회
4. voucher_product 조회
5. 내부 DB transaction 시작
6. 결제 당시 point_ledger 조회
7. voucherNumber로 사용된 point_lot 조회
8. voucher_purchase 상태를 CANCELED로 변경
9. point_balance 복구
10. point_source_balance 복구
11. point_lot 사용 상태 해제
12. point_ledger insert, state = RETURN
13. point_credit insert
14. 내부 DB transaction 종료
15. VoucherProviderClient.cancel() 호출
16. MockVoucherProviderController.cancel() 실행
17. provider_voucher 상태를 CANCELED로 변경
```

중요한 점:

```text
내부 DB 환불 처리가 외부 바우처 취소 API보다 먼저 일어난다.
```

따라서 외부 취소 API가 실패하면 내부 DB는 환불 완료인데 외부 바우처는 아직 유효한 상태가 될 수 있다.

## 10. 왜 record를 사용했나?

Controller의 request/response DTO는 `record`로 선언했다.

예:

```java
public record PointPaymentRequest(
        String orderId,
        String pointWalletUid,
        Long voucherProductId,
        Long pointBalanceId,
        long point
) {
}
```

`record`를 사용한 이유는 request/response DTO가 단순히 데이터를 담는 객체이기 때문이다.

일반 class로 만들면 다음 코드를 직접 작성해야 한다.

```text
필드
생성자
getter
equals
hashCode
toString
```

record는 이것들을 자동으로 만들어준다.

또한 record는 값을 변경하는 setter를 만들지 않는다. 그래서 API 요청/응답처럼 중간에 값을 바꾸지 않고 전달만 하는 객체에 잘 맞는다.

이 프로젝트에서는 Entity에는 record를 사용하지 않았다. Entity는 JPA가 객체를 생성하고 변경 감지해야 하므로 일반 class로 두었다.

## 11. public과 private을 나눈 기준

`public`은 다른 객체가 호출해야 하는 기능에 붙였다.

예:

```java
public PointPaymentResponse pay(PointPaymentRequest request)
```

이 메서드는 Controller가 호출해야 하므로 `public`이다.

`private`은 클래스 내부에서만 사용하는 세부 구현에 붙였다.

예:

```java
private PointPaymentResponse savePaymentTransaction(...)
```

이 메서드는 결제 서비스 내부에서 transaction 처리용으로만 사용된다.

Controller나 다른 서비스가 직접 호출하면 외부 API 호출과 DB transaction 흐름이 깨질 수 있으므로 외부에 공개하지 않는다.

기준은 다음과 같다.

```text
public
-> 다른 객체가 호출해야 하는 기능
-> API/service의 진입점

private
-> 이 클래스 내부 구현 디테일
-> 밖에서 알 필요 없는 세부 단계
```

## 12. 클래스 내부 메서드를 나눈 이유

`LegacyPointPaymentService`는 일부러 다음처럼 나누었다.

```text
pay()
-> 전체 결제 흐름 조율
-> 조회, 검증, 외부 API 호출, transaction 실행

savePaymentTransaction()
-> transaction 안에서 실행될 내부 DB 변경만 담당
```

이렇게 나눈 이유는 외부 API 호출과 내부 DB transaction의 경계를 눈에 보이게 하기 위해서다.

현재 프로젝트의 핵심 학습 포인트는 다음 질문이다.

```text
외부 API는 성공했는데 내부 DB transaction이 실패하면 어떻게 할 것인가?
같은 orderId 요청이 동시에 들어오면 외부 API가 두 번 호출되지 않는가?
DB unique 제약만으로 충분한가?
```

따라서 코드를 볼 때는 이 경계를 집중해서 보면 좋다.

## 13. 코드 읽는 추천 순서

결제 흐름:

```text
PointPaymentController
-> LegacyPointPaymentService.pay()
-> VoucherProviderClient.issue()
-> MockVoucherProviderController.issue()
-> LegacyPointPaymentService.savePaymentTransaction()
-> PointLot.markUsed()
-> PointSourceBalance.subtract()
-> PointBalance.subtract()
-> PointLedger.withdrawal()
-> VoucherPurchase.pointPayment()
```

환불 흐름:

```text
PointRefundController
-> LegacyPointRefundService.refund()
-> LegacyPointRefundService.restorePointTransaction()
-> VoucherPurchase.cancel()
-> PointBalance.add()
-> PointSourceBalance.add()
-> PointLot.restore()
-> PointLedger.returned()
-> PointCredit.returned()
-> VoucherProviderClient.cancel()
-> MockVoucherProviderController.cancel()
```

이 순서대로 코드를 따라가고, DBeaver에서 테이블 변화를 같이 보면 결제/환불 흐름을 가장 빠르게 이해할 수 있다.
## 실습 UI API 빠른 참조

### 바우처 구매

`POST /api/payments/point/legacy`

쇼핑몰이 포인트를 차감하고 외부 발행사에서 받은 바우처를 내부 구매 결과로 저장하는 명령 API다.

응답 필드:

| 필드 | 역할 |
| --- | --- |
| `message` | 결제·발행 성공 안내 문구 |
| `orderId` | 쇼핑몰의 주문 식별자이자 향후 멱등성 판단 기준 |
| `voucherNumber` | 외부 발행사가 만든 바우처 고유 번호. 환불 요청에도 사용 |
| `pinNumber` | 사용자가 바우처를 사용할 때 필요한 PIN 번호 |
| `pointAmount` | 이번 구매에서 사용한 포인트 |
| `balanceAfterPayment` | 결제 직후 남은 총 포인트 잔액 |

### 지갑 잔액 요약

`GET /api/point-wallets/{pointWalletUid}/summary`

`point_wallet`과 연결된 `point_balance`를 조회한다. 화면 최초 진입, 지갑 조회 버튼, 구매 직전, 구매 성공 후, 환불 성공 후에 호출된다.

### 쇼핑몰 바우처 구매 내역

`GET /api/voucher-purchases?orderId={선택}`

우리 쇼핑몰 DB의 `voucher_purchase`를 조회한다. 외부 발행 성공뿐 아니라 내부 포인트 결제까지 완료된 건만 존재한다. 화면 최초 진입, 구매 성공 후, 환불 성공 후, 구매 내역 조회 버튼에서 호출된다.

### 외부 발행사 Mock 바우처 목록

`GET /mock/voucher-provider/vouchers?orderId={선택}`

외부 시스템 역할의 `provider_voucher` 목록과 발행·취소 API 호출 횟수를 조회한다. 외부 발행사 탭 진입, 조회 버튼, 구매 성공 후, 환불 성공 후에 호출된다. 내부 결제가 실패해도 외부 발행을 먼저 시도했다면 보상 취소된 기록이 남을 수 있다.

핵심 구분:

```text
voucher-purchases
-> 쇼핑몰 내부에서 결제까지 완료된 구매 결과

mock/voucher-provider/vouchers
-> 외부 발행사가 받은 발행 요청과 바우처 상태
```
