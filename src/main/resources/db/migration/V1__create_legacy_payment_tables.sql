create table point_wallet (
    id bigint not null auto_increment,
    point_wallet_uid varchar(100) not null,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    unique key uk_point_wallet_uid (point_wallet_uid)
);

create table voucher_product (
    id bigint not null auto_increment,
    voucher_product_code varchar(100) not null,
    voucher_name varchar(255) not null,
    sell_price bigint not null,
    use_term int not null default 366,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    unique key uk_voucher_product_code (voucher_product_code)
);

create table limited_deal (
    id bigint not null auto_increment,
    voucher_product_id bigint not null,
    total_purchase_limit int null,
    individual_purchase_limit int null,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    unique key uk_limited_deal_product_id (voucher_product_id),
    constraint fk_limited_deal_product foreign key (voucher_product_id) references voucher_product (id)
);

create table point_balance (
    id bigint not null auto_increment,
    point_wallet_id bigint not null,
    balance varchar(50) not null default '0',
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    constraint fk_point_balance_wallet foreign key (point_wallet_id) references point_wallet (id)
);

create table point_source_balance (
    id bigint not null auto_increment,
    point_wallet_id bigint not null,
    point_balance_id bigint not null,
    balance varchar(50) not null default '0',
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    constraint fk_source_balance_wallet foreign key (point_wallet_id) references point_wallet (id),
    constraint fk_source_balance_point_balance foreign key (point_balance_id) references point_balance (id)
);

create table point_lot (
    id bigint not null auto_increment,
    point_wallet_id bigint not null,
    point_source_balance_id bigint not null,
    point_balance_id bigint not null,
    amount varchar(50) not null,
    expires_at datetime(6) not null,
    voucher_number varchar(255) null,
    status varchar(50) null,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    index idx_point_lot_usable (point_wallet_id, point_balance_id, status, voucher_number, expires_at),
    constraint fk_point_lot_wallet foreign key (point_wallet_id) references point_wallet (id),
    constraint fk_point_lot_source_balance foreign key (point_source_balance_id) references point_source_balance (id),
    constraint fk_point_lot_balance foreign key (point_balance_id) references point_balance (id)
);

create table point_ledger (
    id bigint not null auto_increment,
    point_wallet_id bigint not null,
    point_balance_id bigint not null,
    state varchar(50) not null,
    amount varchar(50) not null,
    balance varchar(50) not null,
    title varchar(255) not null,
    occurred_at datetime(6) not null,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    constraint fk_point_ledger_wallet foreign key (point_wallet_id) references point_wallet (id),
    constraint fk_point_ledger_balance foreign key (point_balance_id) references point_balance (id)
);

create table voucher_purchase (
    id bigint not null auto_increment,
    voucher_number varchar(255) not null,
    pin_number varchar(255) not null,
    order_id varchar(100) not null,
    voucher_product_id bigint not null,
    point_ledger_id bigint not null,
    payment_type varchar(50) not null,
    point_amount bigint not null,
    card_amount bigint not null default 0,
    payment_method varchar(100) not null,
    issue_status varchar(50) not null,
    use_status varchar(50) not null,
    valid_from datetime(6) not null,
    valid_until datetime(6) not null,
    used_or_canceled_at datetime(6) null,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    unique key uk_voucher_purchase_voucher_number (voucher_number),
    unique key uk_voucher_purchase_order_id (order_id),
    unique key uk_voucher_purchase_ledger_id (point_ledger_id),
    constraint fk_voucher_purchase_product foreign key (voucher_product_id) references voucher_product (id),
    constraint fk_voucher_purchase_ledger foreign key (point_ledger_id) references point_ledger (id)
);

create table point_credit (
    id bigint not null auto_increment,
    type varchar(50) not null,
    request_uid varchar(255) not null,
    value varchar(50) not null,
    point_ledger_id bigint not null,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id),
    unique key uk_point_credit_request_uid (request_uid),
    unique key uk_point_credit_ledger_id (point_ledger_id),
    constraint fk_point_credit_ledger foreign key (point_ledger_id) references point_ledger (id)
);

create table provider_voucher (
    id bigint not null auto_increment,
    voucher_product_code varchar(100) not null,
    voucher_number varchar(255) not null,
    pin_number varchar(255) not null,
    order_id varchar(100) not null,
    status varchar(50) not null,
    created_at datetime(6) not null,
    primary key (id),
    unique key uk_provider_voucher_number (voucher_number),
    index idx_provider_voucher_order_id (order_id)
);
