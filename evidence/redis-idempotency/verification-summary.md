# Redis idempotency verification

Verified on 2026-08-30.

## Single instance concurrent requests

- Request A: HTTP 201, replay=false, source=DATABASE_CREATED
- Request B: HTTP 201, replay=true, source=REDIS_CACHE
- Both response bodies are identical.
- `provider_voucher`, `voucher_purchase`, and `payment_attempt` each have one row.

## Two application instances

- Target A: `localhost:8080`
- Target B: `localhost:8081`
- Request A: HTTP 201, replay=false, source=DATABASE_CREATED
- Request B: HTTP 201, replay=true, source=REDIS_CACHE
- Both responses contain voucher `CP-4bd72639-fafe-4c7d-aa14-02e1f32cab71`
  and PIN `PIN-cf3fcdf8`.
- External issue and internal purchase were each executed once.

## Failure and misuse cases

- Same key with a different product: HTTP 422 `IDEMPOTENCY_KEY_REUSED`
- Redis result key deleted: HTTP 201, source=DATABASE_CACHE_REBUILD
- Redis container stopped: HTTP 201, source=DATABASE_REPLAY
- Redis restarted successfully after the fallback test.

Raw HTTP responses are stored in this directory and `two-instances/`.
