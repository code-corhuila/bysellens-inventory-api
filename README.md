# bysellens-inventory-api

> inventory bounded context: service API

Part of the **LMS Library** distributed system — team `lms-library`, Grupo 2.
Governance and documentation live in [`library-docs`](https://github.com/code-corhuila/library-docs).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `library-docs`.

## Run locally

1. Copy `.env.example` to `.env` and set strong local PostgreSQL and RabbitMQ credentials.
2. Set `JWT_PUBLIC_KEY_BASE64` to the Base64-encoded DER/X.509 RSA public key from Auth. Never put the private key in this repository.
3. Run `docker compose up --build -d` from the repository root.
4. Check `http://localhost:8080/api/v1/health` and `http://localhost:8080/swagger-ui/index.html`.

Run the unit and security tests with `mvn test`. The PostgreSQL integration tests use Testcontainers and require a working Docker daemon; they are skipped when Docker is unavailable.

Product creation writes `ProductCreated` to the PostgreSQL outbox in the same transaction. The RabbitMQ publisher is disabled by default. Enable `INVENTORY_EVENTS_PUBLISHER_ENABLED` only after configuring `INVENTORY_EVENTS_EXCHANGE` to the team-approved exchange. Exchange, queue/binding, retry/DLQ policy and broker integration verification remain pending; successful API responses do not prove event delivery.
