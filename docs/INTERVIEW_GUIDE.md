# Interview guide: Event-Driven Order Processing Platform

Read this before you put the project on your CV. You should be able to explain every line of it in your own words. Run it, break it, and fix it at least once.

## 1. The 30-second pitch

"It's two Spring Boot microservices, orders and payments, that talk through Kafka. When a customer places an order, I save the order and an 'order created' event in the same database transaction. That's the transactional outbox pattern, so I never lose an event or send one for an order that was rolled back. A scheduler publishes the event to Kafka. The payment service consumes it idempotently, because Kafka can deliver a message twice, and sends back a result that moves the order to PAID or REJECTED. Failures are retried and then parked on a dead-letter topic. It's secured with JWT, tested with Testcontainers against real Kafka and Postgres, monitored with Prometheus and Grafana, and deploys to Kubernetes or ECS with Terraform."

## 2. Walk through the code (know where each thing lives)

| Concept | File |
|---|---|
| Order aggregate, state rules, optimistic locking | `order-service/.../domain/Order.java` |
| Outbox write (same transaction as the order) | `OrderService.createOrder` |
| Outbox relay to Kafka | `outbox/OutboxPublisher.java` |
| Idempotency-Key handling | `OrderService.createOrder` + `OrderController.create` |
| JWT security and scopes | `config/SecurityConfig.java` |
| Retry + dead-letter topic | `config/KafkaConfig.java` (both services) |
| Idempotent consumer | `payment-service/.../service/PaymentService.handle` |
| Saga's final step | `messaging/PaymentResultListener` → `OrderService.applyPaymentResult` |
| Metrics | `Counter`/`Gauge` in the services, dashboard in `infra/grafana` |

## 3. Likely questions and good answers

**Why not just call `kafkaTemplate.send()` inside the `@Transactional` method?**
That's the dual-write problem. If the DB commit succeeds but the send fails, the event is lost. If the send succeeds but the commit fails, consumers see an order that doesn't exist. The outbox makes the event part of the DB transaction, and a separate process relays it.

**What delivery guarantee does your system give?**
At-least-once. The publisher can send an event and crash before marking it published, so it gets sent again. Consumers are idempotent, so the business effect happens exactly once. True exactly-once across a DB and Kafka would need Kafka transactions plus the outbox, or CDC, and still involves trade-offs.

**How does the idempotent consumer work?**
payment-service stores each event id in `processed_events` in the same transaction as the payment. On redelivery `existsById` is true, so it skips. A unique constraint on `payments.order_id` is a second safety net: even with a new event id, an order is never charged twice.

**Why is the payment result published inside the transaction?**
If publishing fails, the exception rolls the transaction back and the Kafka error handler retries the whole event. If the commit fails after a successful publish, order-service may see a duplicate result. That's safe, because an order that already left PENDING ignores it. (Improvement: an outbox in payment-service too.)

**What happens with a poison message?**
`ErrorHandlingDeserializer` turns bad JSON into a `DeserializationException`. `DefaultErrorHandler` doesn't retry that type and sends it straight to `orders.created.DLT`. Transient errors get 3 retries, one second apart, first. Someone can inspect the DLT and replay messages after a fix.

**How do you keep ordering?**
The message key is the order id, Kafka guarantees order within a partition, and one partition is consumed by one consumer in a group at a time. Ordering across different orders doesn't matter.

**How would you scale it?**
More partitions plus more consumer instances, up to one per partition (`concurrency = "3"` with 3 partitions). The API is stateless, so it scales horizontally behind a load balancer (the HPA in `k8s/`). The outbox poller could become a bottleneck: switch to Debezium CDC, or use `SELECT … FOR UPDATE SKIP LOCKED` so several instances can share the work.

**Choreography vs orchestration?**
Choreography means services react to each other's events with no coordinator. It's simple for 2 or 3 steps. Orchestration means a central coordinator (Camunda, Temporal, a state machine) tells each service what to do, which suits long flows with compensation logic. With more steps, such as stock reservation and shipping, I'd consider orchestration.

**Why JWT with HS256? Is that production-ready?**
It keeps the demo self-contained. In production, order-service would validate RS256 tokens from an identity provider through `issuer-uri` (Keycloak, Cognito, Entra ID). Because it's an OAuth2 resource server, only configuration changes.

**Why does another customer's order return 404 instead of 403?**
A 403 would confirm the order exists. Returning 404 for both prevents id enumeration.

**Why Testcontainers if you already have H2 and embedded Kafka?**
H2 is not PostgreSQL: SQL dialect and types differ. The fast tests give quick feedback; the `*IT` tests prove the real Flyway scripts and Kafka configuration work. Both run in CI.

**Why Flyway and `ddl-auto: none`?**
Schema changes are versioned, reviewed and repeatable. Hibernate auto-DDL in production is risky.

**What metrics would you alert on?**
`outbox_pending` growing (Kafka down or the publisher stuck), messages arriving on DLT topics, consumer lag, the error rate and p95 latency of the API, and the payment failure ratio.

## 4. Questions to prepare from your own experience

- A time you debugged a production batch or Kafka problem at Infosys: logs, SQL, root cause.
- How your Infosys Spring Batch work relates to this: chunking, retries, restartability, and idempotency (re-running a failed job must not double-process).

## 5. Before you list it on your CV

1. Run `mvn verify` and get it green, then note the test count and the JaCoCo coverage %.
2. Run `docker compose up --build` and place orders through Swagger.
3. Run the k6 test and write down orders/s and p95 latency. Only quote numbers you measured.
4. Push to GitHub with the CI badge green.
5. Change something yourself (for example add a `CANCELLED` status or a refund event) so it's genuinely yours.
