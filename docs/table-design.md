# 테이블 설계 설명

이 문서는 포인트 결제/바우처 발행 실험 프로젝트에서 사용하는 테이블 구조를 설명합니다.

`point_wallet`, `voucher_product`처럼 snake_case로 적힌 이름은 실제 MySQL 테이블명이고, `voucher_number`, `balance`처럼 테이블 안에 있는 값은 컬럼명입니다.

## 1. point_wallet

사용자 지갑 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 내부 PK | 다른 테이블의 `point_wallet_id`와 연결됩니다. |
| `point_wallet_uid` | 앱 사용자 지갑 식별자 | 결제 요청의 `pointWalletUid`로 지갑을 조회합니다. |
| `created_at` | 생성 시각 | 감사/확인용입니다. |

## 2. voucher_product

외부 바우처 제공사가 발행할 수 있는 상품 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 상품 PK | 결제 요청의 `voucherProductId`로 상품을 조회합니다. |
| `voucher_product_code` | 외부 바우처 상품 코드 | 외부 바우처 발행/취소 API에 전달합니다. |
| `voucher_name` | 상품명 | 결제 결과 표시용입니다. |
| `sell_price` | 판매 가격 | 요청 포인트와 비교할 수 있는 상품 가격입니다. |
| `use_term` | 바우처 유효기간 | 발행된 바우처 만료일 계산에 사용합니다. |

## 3. limited_deal

한정 판매 상품 제한 정보입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 한정 판매 PK | 내부 식별자입니다. |
| `voucher_product_id` | 상품 ID | `voucher_product`와 연결됩니다. |
| `total_purchase_limit` | 전체 구매 가능 수량 | 0 이하이면 sold out 처리가 필요합니다. |
| `individual_purchase_limit` | 개인별 구매 제한 | 현재 legacy 구현에서는 강한 서버 검증이 부족한 영역입니다. |

## 4. point_balance

지갑 기준 총 포인트 잔액 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 총 잔액 PK | 결제 요청의 `pointBalanceId`와 매칭됩니다. |
| `point_wallet_id` | 지갑 ID | 어떤 사용자의 잔액인지 나타냅니다. |
| `balance` | 총 포인트 잔액 | 결제 시 차감하고, 환불 시 복구합니다. |

## 5. point_source_balance

포인트 출처 기준의 잔액 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 출처별 잔액 PK | `point_lot.point_source_balance_id`로 연결됩니다. |
| `point_wallet_id` | 지갑 ID | 사용자 기준 조회에 사용됩니다. |
| `point_balance_id` | 총 잔액 ID | 상위 `point_balance`와 연결됩니다. |
| `balance` | 출처별 포인트 잔액 | 결제 시 사용된 포인트 묶음만큼 차감합니다. |

## 6. point_lot

포인트를 지급 단위와 만료일 단위로 쪼개 관리하는 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 포인트 묶음 PK | 포인트 조각 식별자입니다. |
| `point_wallet_id` | 지갑 ID | 사용자의 사용 가능 포인트 조회에 사용됩니다. |
| `point_source_balance_id` | 출처별 잔액 ID | 어떤 출처별 잔액에서 온 포인트인지 나타냅니다. |
| `point_balance_id` | 총 잔액 ID | 어떤 총 잔액에 속하는지 나타냅니다. |
| `amount` | 포인트 수량 | 차감 계산에 사용합니다. |
| `expires_at` | 만료일 | 만료일 빠른 순으로 사용합니다. |
| `voucher_number` | 사용된 바우처 번호 | 결제 성공 시 바우처 번호를 기록합니다. |
| `state` | 사용 여부 | 결제 시 `USED`, 사용 가능 상태는 `NULL`입니다. |

## 7. voucher_purchase

바우처 구매/결제 결과 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 구매 이력 PK | 내부 식별자입니다. |
| `voucher_number` | 외부 제공사가 발급한 바우처 번호 | unique입니다. 환불/상세 조회 기준값입니다. |
| `pin_number` | 바우처 핀번호 | 바우처 표시용입니다. |
| `order_id` | 내부 거래번호/orderId | unique입니다. DB 레벨 중복 저장 방어 역할을 합니다. |
| `voucher_product_id` | 상품 ID | 어떤 상품을 구매했는지 연결합니다. |
| `point_ledger_id` | 포인트 사용 이력 ID | `point_ledger`와 1:1로 연결됩니다. |
| `payment_type` | 결제 방식 | 이 실험에서는 `POINT`만 사용합니다. |
| `point_amount` | 사용 포인트 | 결제 차감/환불 복구 기준입니다. |
| `card_amount` | 카드 결제 금액 | 현재 legacy 구현에서는 0으로 저장됩니다. |
| `payment_method` | 결제 수단명 | `전액 포인트`로 저장합니다. |
| `issue_status` | 바우처 발급/취소 상태 | 기본 `ISSUED`, 환불 시 `CANCELED`가 됩니다. |
| `use_status` | 바우처 사용 상태 | 기본 `UNUSED`, 환불 시 `CANCELED`가 됩니다. |
| `valid_from` | 유효 시작 시각 | 발행 시각입니다. |
| `valid_until` | 유효 종료 시각 | 상품의 `use_term`을 기준으로 계산합니다. |
| `used_or_canceled_at` | 사용/취소 시각 | 환불 시 취소 시각을 기록합니다. |

## 8. point_ledger

포인트 원장 이력 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 이력 PK | 내부 식별자입니다. |
| `point_wallet_id` | 지갑 ID | 사용자별 포인트 이력 조회 기준입니다. |
| `point_balance_id` | 총 잔액 ID | 어떤 잔액의 변동인지 연결합니다. |
| `state` | 이력 상태 | 결제 시 `WITHDRAWAL`, 환불 시 `RETURN`입니다. |
| `amount` | 변동 포인트 | 결제 시 사용 포인트, 환불 시 복구 포인트입니다. |
| `balance` | 변동 후 잔액 | 화면/감사용 이력 값입니다. |
| `title` | 표시 문구 | 예: `포인트 바우처 구매`, `포인트 바우처 환불`. |
| `occurred_at` | 발생 시각 | 결제/환불 발생 시각입니다. |

## 9. point_credit

포인트 지급 또는 환불성 입금 기록 테이블입니다.

| 컬럼 | 설명 | 결제에서 사용하는 방식 |
| --- | --- | --- |
| `id` | 입금 기록 PK | 내부 식별자입니다. |
| `type` | 입금 유형 | 환불 시 `return`으로 저장합니다. |
| `request_uid` | 환불 요청 식별자 | 현재는 `voucher_number`를 사용합니다. |
| `value` | 입금 포인트 | 환불 복구 포인트입니다. |
| `point_ledger_id` | 원장 이력 ID | `point_ledger`의 환불 이력과 1:1로 연결됩니다. |

## 10. provider_voucher

외부 바우처 제공사 API를 흉내 내는 mock 테이블입니다. 실제 서비스 DB가 아니라 실험용 외부 시스템 저장소 역할입니다.

| 컬럼 | 설명 | 실험에서 사용하는 방식 |
| --- | --- | --- |
| `id` | mock 바우처 PK | 내부 식별자입니다. |
| `voucher_product_code` | 외부 바우처 상품 코드 | 발행 요청의 상품 코드입니다. |
| `voucher_number` | 발급된 바우처 번호 | mock 외부 API가 생성합니다. |
| `pin_number` | 발급된 핀번호 | mock 외부 API가 생성합니다. |
| `order_id` | 요청 거래번호 | 같은 orderId 요청이 몇 번 외부 API를 호출했는지 확인합니다. |
| `status` | 바우처 상태 | `ISSUED` 또는 `CANCELED`입니다. |

## 현재 방식에서 일부러 남겨둘 문제

이 실험의 legacy 구현은 외부 바우처 API 호출 전에 `orderId`를 선점하지 않습니다.

따라서 같은 `orderId` 요청이 동시에 들어오면 다음 일이 발생할 수 있습니다.

```text
요청 A -> 외부 바우처 발행 성공 -> DB 저장 성공
요청 B -> 외부 바우처 발행 성공 -> DB 저장 시 order_id unique 위반
```

이 경우 DB에는 중복 구매 이력이 저장되지 않지만, `provider_voucher`에는 같은 `order_id`로 발행된 바우처가 둘 이상 남을 수 있습니다. 이것이 이후 개선 단계에서 해결할 문제입니다.
