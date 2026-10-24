# CV entry (fill in the numbers after you have run it yourself)

**Event-Driven Order Processing Platform** | github.com/Rajkumar2119/event-driven-order-platform
*Java 21, Spring Boot 3, Apache Kafka, PostgreSQL, Docker, Kubernetes, Terraform (AWS ECS), GitHub Actions*

- Built two Spring Boot microservices that process orders and payments through Kafka, using a transactional outbox, idempotent consumers and dead-letter topics so no event is lost or processed twice.
- Secured the REST API with Spring Security OAuth2/JWT and scope-based access; covered it with [N] JUnit 5, Mockito and Testcontainers tests ([X]% coverage), run in a GitHub Actions CI pipeline.
- Containerised with Docker; deployable to Kubernetes or AWS ECS Fargate via Terraform; monitored with Micrometer, Prometheus and Grafana; load-tested at [Y] orders/s with p95 < [Z] ms (k6).

Where the numbers come from:
- [N] and [X]%: run `mvn verify`, then open `order-service/target/site/jacoco/index.html` and the payment-service equivalent.
- [Y] and [Z]: run `docker compose up --build`, then `k6 run loadtest/create-orders.js`.

Never quote a number you haven't measured.
