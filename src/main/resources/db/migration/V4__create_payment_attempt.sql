create table payment_attempt (
    id bigint not null auto_increment,
    order_id varchar(100) not null,
    point_wallet_uid varchar(100) not null,
    voucher_product_id bigint not null,
    point_balance_id bigint not null,
    requested_point bigint not null,
    status varchar(50) not null,
    voucher_number varchar(255) null,
    pin_number varchar(255) null,
    point_amount bigint null,
    balance_after_payment varchar(50) null,
    failure_message varchar(500) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_payment_attempt_order_id (order_id),
    index idx_payment_attempt_status_updated_at (status, updated_at)
);
