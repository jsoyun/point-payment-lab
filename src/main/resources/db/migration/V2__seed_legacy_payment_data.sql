insert into point_wallet (id, point_wallet_uid)
values (1, 'point-wallet-001');

insert into voucher_product (id, voucher_product_code, voucher_name, sell_price, use_term)
values (1, 'VOUCHER-COFFEE-5000', '커피 5천원권', 5000, 366);

insert into point_balance (id, point_wallet_id, balance)
values (1, 1, '10000');

insert into point_source_balance (id, point_wallet_id, point_balance_id, balance)
values (1, 1, 1, '10000');

insert into point_lot (point_wallet_id, point_source_balance_id, point_balance_id, amount, expires_at, voucher_number, status)
values
    (1, 1, 1, '3000', date_add(now(6), interval 30 day), null, null),
    (1, 1, 1, '7000', date_add(now(6), interval 60 day), null, null);
