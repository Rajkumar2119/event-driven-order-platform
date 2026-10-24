# Runs both services on Amazon ECS Fargate in the default VPC.
# Kafka (e.g. Amazon MSK) and PostgreSQL (e.g. Amazon RDS) are passed in as variables.

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

locals {
  services = {
    order-service = {
      port        = 8081
      db_url      = var.orders_db_url
      db_username = "orders"
      db_secret   = aws_ssm_parameter.orders_db_password.arn
    }
    payment-service = {
      port        = 8082
      db_url      = var.payments_db_url
      db_username = "payments"
      db_secret   = aws_ssm_parameter.payments_db_password.arn
    }
  }
}

# --- Container registry -------------------------------------------------------

resource "aws_ecr_repository" "service" {
  for_each             = local.services
  name                 = each.key
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# --- Secrets (SSM Parameter Store, encrypted) ----------------------------------

resource "aws_ssm_parameter" "orders_db_password" {
  name  = "/order-platform/orders-db-password"
  type  = "SecureString"
  value = var.orders_db_password
}

resource "aws_ssm_parameter" "payments_db_password" {
  name  = "/order-platform/payments-db-password"
  type  = "SecureString"
  value = var.payments_db_password
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/order-platform/jwt-secret"
  type  = "SecureString"
  value = var.jwt_secret
}

# --- IAM ------------------------------------------------------------------------

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "order-platform-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    actions = ["ssm:GetParameters"]
    resources = [
      aws_ssm_parameter.orders_db_password.arn,
      aws_ssm_parameter.payments_db_password.arn,
      aws_ssm_parameter.jwt_secret.arn,
    ]
  }
}

resource "aws_iam_role_policy" "read_secrets" {
  name   = "read-order-platform-secrets"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.read_secrets.json
}

# --- Networking -------------------------------------------------------------------

resource "aws_security_group" "services" {
  name        = "order-platform-services"
  description = "Order platform services"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "Service HTTP ports (put an ALB in front for production)"
    from_port   = 8081
    to_port     = 8082
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.default.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- ECS ----------------------------------------------------------------------------

resource "aws_ecs_cluster" "main" {
  name = "order-platform"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = local.services
  name              = "/ecs/order-platform/${each.key}"
  retention_in_days = 14
}

resource "aws_ecs_task_definition" "service" {
  for_each                 = local.services
  family                   = each.key
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.task_execution.arn

  container_definitions = jsonencode([{
    name         = each.key
    image        = "${aws_ecr_repository.service[each.key].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = each.value.port, protocol = "tcp" }]
    environment = [
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = var.kafka_bootstrap_servers },
      { name = "DB_URL", value = each.value.db_url },
      { name = "DB_USERNAME", value = each.value.db_username },
    ]
    secrets = concat(
      [{ name = "DB_PASSWORD", valueFrom = each.value.db_secret }],
      each.key == "order-service" ? [{ name = "JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn }] : []
    )
    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:${each.value.port}/actuator/health/liveness || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = each.key
      }
    }
  }])
}

resource "aws_ecs_service" "service" {
  for_each        = local.services
  name            = each.key
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.services.id]
    assign_public_ip = true
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
}
