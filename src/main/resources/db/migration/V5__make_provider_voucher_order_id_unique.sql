alter table provider_voucher
    drop index idx_provider_voucher_order_id,
    add unique key uk_provider_voucher_order_id (order_id);
