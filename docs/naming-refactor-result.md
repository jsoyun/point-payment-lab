# 네이밍 리팩터링 결과

기존 회사/서비스명이 드러나는 이름을 실습 프로젝트용 중립 이름으로 변경했다. 목표는 원본 비즈니스 구조는 공부하되, 코드와 문서에서는 특정 회사명, 제휴사명, 포인트 브랜드명을 직접 사용하지 않는 것이다.

## 변경 원칙

| 원칙 | 적용 내용 |
| --- | --- |
| 회사/서비스명 제거 | 패키지와 앱 이름을 `paymentlab`, `voucher` 중심으로 변경 |
| 제휴 쿠폰사명 제거 | 외부 시스템은 `provider`, `voucherProvider`로 표현 |
| 특정 포인트 브랜드명 제거 | 포인트 도메인은 `Point`, `PointWallet`, `PointBalance`로 표현 |
| 쿠폰 표현 일반화 | 외부 발행 결과물은 `Voucher`로 표현 |
| UTXO 표현 단순화 | 만료일이 있는 포인트 조각은 `PointLot`으로 표현 |

## Java 패키지/클래스명

| 이전 의미 | 변경 후 이름 | 비고 |
| --- | --- | --- |
| 쇼핑몰 포인트 결제 실습 앱 | `PointVoucherPaymentLabApplication` | Spring Boot 진입점 |
| 쿠폰 상품 | `VoucherProduct` | `voucher_product` 테이블과 매칭 |
| 쿠폰 구매/결제 이력 | `VoucherPurchase` | `voucher_purchase` 테이블과 매칭 |
| 지갑 | `PointWallet` | `point_wallet` 테이블과 매칭 |
| 총 포인트 잔액 | `PointBalance` | `point_balance` 테이블과 매칭 |
| 출처별 포인트 잔액 | `PointSourceBalance` | `point_source_balance` 테이블과 매칭 |
| 포인트 조각/묶음 | `PointLot` | `point_lot` 테이블과 매칭 |
| 포인트 원장 | `PointLedger` | `point_ledger` 테이블과 매칭 |
| 환불성 포인트 입금 | `PointCredit` | `point_credit` 테이블과 매칭 |
| 외부 쿠폰 API 클라이언트 | `VoucherProviderClient` | 외부 바우처 제공사 역할 |
| 외부 쿠폰 mock 저장소 | `ProviderVoucher` | `provider_voucher` 테이블과 매칭 |

## DB 테이블명

| 변경 후 테이블 | 역할 |
| --- | --- |
| `point_wallet` | 사용자 포인트 지갑 |
| `voucher_product` | 외부 발행 가능한 바우처 상품 |
| `limited_deal` | 한정 판매 상품 제한 정보 |
| `point_balance` | 지갑 기준 총 포인트 잔액 |
| `point_source_balance` | 포인트 출처별 잔액 |
| `point_lot` | 만료일이 있는 포인트 묶음 |
| `voucher_purchase` | 바우처 구매/결제 결과 |
| `point_ledger` | 포인트 사용/환불 원장 |
| `point_credit` | 환불성 포인트 입금 기록 |
| `provider_voucher` | 외부 바우처 발행 API mock 결과 |

## API 요청 필드명

| 변경 후 필드 | 설명 |
| --- | --- |
| `orderId` | 내부 거래 식별자. 중복 요청 판단 기준 |
| `pointWalletUid` | 사용자 지갑 식별자 |
| `voucherProductId` | 구매할 바우처 상품 ID |
| `pointBalanceId` | 차감할 총 포인트 잔액 ID |
| `point` | 사용할 포인트 금액 |

## 의도적으로 남긴 이름

`LegacyPointPaymentService`, `LegacyPointRefundService`의 `Legacy`는 기존 방식을 재현한다는 의미라서 유지했다. 이후 개선 버전을 만들 때 `IdempotentPointPaymentService` 같은 별도 구현을 추가하면 두 방식의 차이를 비교하기 쉽다.
