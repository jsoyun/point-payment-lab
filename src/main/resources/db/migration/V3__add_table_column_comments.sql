alter table point_wallet comment = '사용자 포인트 지갑. 결제 요청의 pointWalletUid로 조회되는 기준 테이블';
alter table point_wallet
    modify column id bigint not null auto_increment comment 'point_wallet PK',
    modify column point_wallet_uid varchar(100) not null comment '앱/클라이언트에서 전달하는 사용자 지갑 식별자',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '지갑 row 생성 시각';

alter table voucher_product comment = '외부 바우처 제공사가 발행할 수 있는 상품 정보';
alter table voucher_product
    modify column id bigint not null auto_increment comment 'voucher_product PK',
    modify column voucher_product_code varchar(100) not null comment '외부 바우처 제공사에 전달하는 상품 코드',
    modify column voucher_name varchar(255) not null comment '바우처 상품명',
    modify column sell_price bigint not null comment '상품 판매가. 포인트 결제 금액 검증 기준으로 사용할 수 있음',
    modify column use_term int not null default 366 comment '발행일 기준 바우처 유효기간 일수',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '상품 row 생성 시각';

alter table limited_deal comment = '한정 판매 상품의 전체/개인별 구매 제한 정보';
alter table limited_deal
    modify column id bigint not null auto_increment comment 'limited_deal PK',
    modify column voucher_product_id bigint not null comment '제한 정책이 적용되는 voucher_product.id',
    modify column total_purchase_limit int null comment '전체 구매 가능 수량. 0 이하이면 품절로 볼 수 있음',
    modify column individual_purchase_limit int null comment '사용자 1명당 구매 가능 수량',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '제한 정보 row 생성 시각';

alter table point_balance comment = '지갑 기준 총 포인트 잔액. 결제 시 차감되고 환불 시 복구됨';
alter table point_balance
    modify column id bigint not null auto_increment comment 'point_balance PK',
    modify column point_wallet_id bigint not null comment '잔액을 소유한 point_wallet.id',
    modify column balance varchar(50) not null default '0' comment '현재 총 포인트 잔액',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '잔액 row 생성 시각';

alter table point_source_balance comment = '포인트 출처별 잔액. 총 잔액의 하위 단위';
alter table point_source_balance
    modify column id bigint not null auto_increment comment 'point_source_balance PK',
    modify column point_wallet_id bigint not null comment '출처별 잔액을 소유한 point_wallet.id',
    modify column point_balance_id bigint not null comment '상위 총 잔액 point_balance.id',
    modify column balance varchar(50) not null default '0' comment '해당 출처에서 사용 가능한 포인트 잔액',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '출처별 잔액 row 생성 시각';

alter table point_lot comment = '만료일과 출처를 가진 포인트 묶음. 결제 시 만료일 빠른 순으로 사용됨';
alter table point_lot
    modify column id bigint not null auto_increment comment 'point_lot PK',
    modify column point_wallet_id bigint not null comment '포인트 묶음을 소유한 point_wallet.id',
    modify column point_source_balance_id bigint not null comment '포인트 묶음의 출처별 잔액 point_source_balance.id',
    modify column point_balance_id bigint not null comment '포인트 묶음이 속한 총 잔액 point_balance.id',
    modify column amount varchar(50) not null comment '이 포인트 묶음의 포인트 수량',
    modify column expires_at datetime(6) not null comment '포인트 묶음 만료 시각',
    modify column voucher_number varchar(255) null comment '결제에 사용된 경우 연결되는 바우처 번호',
    modify column status varchar(50) null comment '포인트 묶음 사용 상태. 사용 가능 상태는 NULL, 사용 완료는 USED',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '포인트 묶음 row 생성 시각';

alter table point_ledger comment = '포인트 사용/환불 원장. 잔액 변동 이력을 기록';
alter table point_ledger
    modify column id bigint not null auto_increment comment 'point_ledger PK',
    modify column point_wallet_id bigint not null comment '이력이 발생한 point_wallet.id',
    modify column point_balance_id bigint not null comment '변동 대상 총 잔액 point_balance.id',
    modify column state varchar(50) not null comment '원장 상태. 결제 사용은 WITHDRAWAL, 환불 복구는 RETURN',
    modify column amount varchar(50) not null comment '변동 포인트 수량',
    modify column balance varchar(50) not null comment '변동 후 총 포인트 잔액',
    modify column title varchar(255) not null comment '포인트 이력 화면에 표시할 제목',
    modify column occurred_at datetime(6) not null comment '포인트 변동 발생 시각',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '원장 row 생성 시각';

alter table voucher_purchase comment = '바우처 구매/결제 결과. 외부 발행 결과와 내부 결제 이력을 연결';
alter table voucher_purchase
    modify column id bigint not null auto_increment comment 'voucher_purchase PK',
    modify column voucher_number varchar(255) not null comment '외부 바우처 제공사가 발급한 바우처 번호. 환불 기준값',
    modify column pin_number varchar(255) not null comment '외부 바우처 제공사가 발급한 핀 번호',
    modify column order_id varchar(100) not null comment '클라이언트/서버가 생성한 거래 식별자. 중복 결제 방어 기준',
    modify column voucher_product_id bigint not null comment '구매한 바우처 상품 voucher_product.id',
    modify column point_ledger_id bigint not null comment '결제 사용 원장 point_ledger.id',
    modify column payment_type varchar(50) not null comment '결제 방식. 현재 legacy 구현은 POINT만 사용',
    modify column point_amount bigint not null comment '결제에 사용한 포인트 금액',
    modify column card_amount bigint not null default 0 comment '카드 결제 금액. 현재 구현에서는 0',
    modify column payment_method varchar(100) not null comment '사용자에게 표시할 결제 수단명',
    modify column issue_status varchar(50) not null comment '바우처 발급 상태. ISSUED 또는 CANCELED',
    modify column use_status varchar(50) not null comment '바우처 사용 상태. UNUSED 또는 CANCELED',
    modify column valid_from datetime(6) not null comment '바우처 유효 시작 시각',
    modify column valid_until datetime(6) not null comment '바우처 유효 종료 시각',
    modify column used_or_canceled_at datetime(6) null comment '바우처 사용 또는 취소 시각',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '구매 이력 row 생성 시각';

alter table point_credit comment = '포인트 지급 또는 환불성 입금 기록';
alter table point_credit
    modify column id bigint not null auto_increment comment 'point_credit PK',
    modify column type varchar(50) not null comment '입금 유형. 환불 복구는 return',
    modify column request_uid varchar(255) not null comment '입금 요청 식별자. 현재 환불에서는 voucher_number를 사용',
    modify column value varchar(50) not null comment '입금된 포인트 수량',
    modify column point_ledger_id bigint not null comment '입금과 연결된 환불 원장 point_ledger.id',
    modify column created_at datetime(6) not null default current_timestamp(6) comment '입금 기록 row 생성 시각';

alter table provider_voucher comment = '외부 바우처 제공사 API를 흉내 내는 mock 저장소';
alter table provider_voucher
    modify column id bigint not null auto_increment comment 'provider_voucher PK',
    modify column voucher_product_code varchar(100) not null comment '발행 요청으로 전달받은 외부 바우처 상품 코드',
    modify column voucher_number varchar(255) not null comment 'mock 외부 API가 생성한 바우처 번호',
    modify column pin_number varchar(255) not null comment 'mock 외부 API가 생성한 핀 번호',
    modify column order_id varchar(100) not null comment '발행 요청 거래 식별자. 외부 API 중복 호출 관찰 기준',
    modify column status varchar(50) not null comment 'mock 바우처 상태. ISSUED 또는 CANCELED',
    modify column created_at datetime(6) not null comment 'mock 바우처 발행 row 생성 시각';
