.PHONY: help
help: ## Show this help message
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# =============================================================================
# SunLint - Code Quality
# =============================================================================

SUNLINT_INPUT ?= src/main/java
SUNLINT_REPORT ?= sunlint-report.json

lint: ## Run sunlint on all source files
	@sunlint --all --input=$(SUNLINT_INPUT)

lint-changed: ## Run sunlint on changed files only
	@sunlint --all --changed-files

lint-security: ## Run sunlint security checks on all source files
	@sunlint --security --input=$(SUNLINT_INPUT)

lint-report: ## Run sunlint and save report to JSON file
	@sunlint --all --input=$(SUNLINT_INPUT) --output-summary=$(SUNLINT_REPORT)

lint-ci: ## Run sunlint in CI mode (fail on new violations)
	@sunlint --all --input=$(SUNLINT_INPUT) --pr-mode --fail-on-new-violations

lint-architecture: ## Run sunlint with architecture detection
	@sunlint --architecture --input=$(SUNLINT_INPUT)

lint-impact: ## Run sunlint impact analysis on changed files
	@sunlint --impact-analysis --changed-files

# =============================================================================
# Build - Java / Maven
# =============================================================================

.PHONY: build test clean compile package install verify

build: ## Compile the project
	./mvnw compile

compile: ## Compile the project (alias)
	./mvnw compile

test: ## Run unit tests
	./mvnw test

test-integration: ## Run integration tests
	./mvnw verify -DskipUnitTests

test-report: ## Run tests with coverage report
	./mvnw test jacoco:report

clean: ## Clean build artifacts
	./mvnw clean

package: ## Package the application (JAR)
	./mvnw package -DskipTests

install: ## Install dependencies
	./mvnw install -DskipTests

verify: ## Verify the project
	./mvnw verify

# =============================================================================
# Database
# =============================================================================

.PHONY: db-migrate db-rollback db-reset db-seed

db-migrate: ## Run database migrations (Flyway)
	./mvnw flyway:migrate

db-rollback: ## Rollback last database migration
	./mvnw flyway:rollback

db-reset: ## Reset database (clean + migrate)
	./mvnw flyway:clean flyway:migrate

db-seed: ## Seed database with test data
	./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=seed"

# =============================================================================
# Docker
# =============================================================================

.PHONY: env-init docker-build docker-pull docker-config docker-up docker-down docker-restart docker-logs docker-status docker-clean

DOCKER_COMPOSE := docker compose --env-file .env -f docker-compose.yml

env-init: ## Create .env from .env.example when it does not exist
	@test -f .env || cp .env.example .env

docker-build: docker-pull ## Pull service images (all services use prebuilt images)

docker-pull: env-init ## Pull Docker service images
	$(DOCKER_COMPOSE) pull

docker-config: env-init ## Validate the Docker Compose configuration
	$(DOCKER_COMPOSE) config --quiet

docker-up: env-init ## Start Docker containers
	$(DOCKER_COMPOSE) up -d

docker-down: env-init ## Stop Docker containers
	$(DOCKER_COMPOSE) down

docker-restart: env-init ## Restart Docker containers
	$(DOCKER_COMPOSE) restart

docker-logs: env-init ## View Docker container logs
	$(DOCKER_COMPOSE) logs -f

docker-status: env-init ## Show Docker container status
	$(DOCKER_COMPOSE) ps

docker-clean: env-init ## Remove Docker containers and data volumes
	$(DOCKER_COMPOSE) down -v --remove-orphans

# =============================================================================
# Development
# =============================================================================

.PHONY: run dev spring-boot stop

run: ## Run the Spring Boot application
	./mvnw spring-boot:run

dev: ## Run in development mode with live reload
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

spring-boot: ## Run Spring Boot application
	./mvnw spring-boot:run

stop: ## Stop running Spring Boot application
	@pkill -f "hotel-management" 2>/dev/null || true

# =============================================================================
# Code Quality Tools
# =============================================================================

.PHONY: fmt format checkstyle pmd

fmt: ## Format code (Spotless)
	./mvnw spotless:apply

format: ## Format code (alias for fmt)
	./mvnw spotless:apply

checkstyle: ## Run Checkstyle
	./mvnw checkstyle:check

pmd: ## Run PMD static analysis
	./mvnw pmd:check

pmd-report: ## Generate PMD HTML report
	./mvnw pmd:pmd

# =============================================================================
# Utilities
# =============================================================================

.PHONY: deps保险 docs swagger openapi

deps: ## Check/update dependencies
	./mvnw dependency:tree

deps-analyze: ## Analyze dependencies for conflicts
	./mvnw dependency:analyze

docs: ## Generate API documentation
	./mvnw springdoc-openapi:generate

swagger: ## Generate Swagger/OpenAPI docs
	./mvnw springdoc-openapi:generate -Dspringdoc.apiDocs.path=/api-docs.json

openapi: ## Generate OpenAPI spec
	./mvnw springdoc-openapi:generate-api

# =============================================================================
# Git / CI
# =============================================================================

.PHONY: ci-status branch-cleanup

ci-status: lint lint-ci test ## Run full CI pipeline locally
	@echo "CI pipeline passed!"

branch-cleanup: ## Cleanup local merged branches
	@git branch --merged master | grep -v "master" | xargs -r git branch -d

# =============================================================================
# SunLint Rules Reference
# =============================================================================
#
# Security Rules (S*):
#   S017 - Parameterized queries (no SQL concatenation)
#   S020 - No dynamic code execution (eval, Function)
#   S025 - Server-side input validation
#   S005 - Server-side authorization
#   S006 - No default credentials
#   S012 - Secrets management via env vars/vaults
#   S026 - TLS 1.2+ for all connections
#   S036 - Internal file paths only
#
# Code Quality Rules (C*):
#   C029 - Log errors with context in catch blocks
#   C030 - Custom error/exception classes
#   C018 - No generic error throwing
#   C035 - Include requestId, entityId in error logs
#   C014 - Dependency injection for testability
#   C017 - No business logic in constructors
#   C006 - Verb-Noun method naming pattern
#   C013 - No dead code, unused imports
#   C041 - No hardcoded secrets
#
# Quick Reference:
#   make lint           - Check all files
#   make lint-changed  - Check only changed files
#   make lint-security  - Security scan
#   make lint-ci        - CI mode (fails on new violations)
