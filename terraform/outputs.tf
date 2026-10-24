output "ecr_repository_urls" {
  description = "Push images here: docker push <url>:<tag>"
  value       = { for k, repo in aws_ecr_repository.service : k => repo.repository_url }
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}
