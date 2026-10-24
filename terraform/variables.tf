variable "aws_region" {
  description = "AWS region, e.g. eu-west-2 (London)"
  type        = string
  default     = "eu-west-2"
}

variable "image_tag" {
  description = "Container image tag to deploy (CI uses the git SHA)"
  type        = string
  default     = "latest"
}

variable "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers, e.g. an Amazon MSK cluster"
  type        = string
}

variable "orders_db_url" {
  description = "JDBC URL of the orders database (e.g. Amazon RDS for PostgreSQL)"
  type        = string
}

variable "payments_db_url" {
  description = "JDBC URL of the payments database"
  type        = string
}

variable "orders_db_password" {
  type      = string
  sensitive = true
}

variable "payments_db_password" {
  type      = string
  sensitive = true
}

variable "jwt_secret" {
  description = "HS256 signing secret for order-service (32+ bytes)"
  type        = string
  sensitive   = true
}

variable "desired_count" {
  description = "Tasks per service"
  type        = number
  default     = 2
}
