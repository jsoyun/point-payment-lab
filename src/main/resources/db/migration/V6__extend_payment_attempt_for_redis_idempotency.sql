alter table payment_attempt
    add column idempotency_key varchar(100) null comment 'HTTP Idempotency-Key 헤더 값' after order_id,
    add column client_id varchar(100) null comment '멱등키 범위를 구분하는 호출자 식별자' after idempotency_key,
    add column http_method varchar(10) null comment '멱등 요청 HTTP method' after client_id,
    add column api_path varchar(255) null comment '멱등 요청 API path' after http_method,
    add column request_hash char(64) null comment '정규화한 요청 payload의 SHA-256 hash' after api_path,
    add column http_status int null comment '최초 성공 HTTP status' after balance_after_payment,
    add column response_body longtext null comment '최초 성공 응답 JSON 원문' after http_status,
    add column expires_at datetime(6) null comment 'DB 멱등 기록의 논리 만료 시각' after failure_message,
    add unique key uk_payment_attempt_idempotency_scope
        (client_id, http_method, api_path, idempotency_key);
