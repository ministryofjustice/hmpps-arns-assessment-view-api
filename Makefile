DOCKER_COMPOSE = docker compose

default: help

help: ## The help text you're reading.
	@grep --no-filename -E '^[0-9a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

up: ## Starts all services. The app builds and runs inside the container with source mounted.
	$(DOCKER_COMPOSE) up -d --build

down: ## Stops and removes all services.
	$(DOCKER_COMPOSE) down

update: ## Pulls the latest images for all services.
	$(DOCKER_COMPOSE) pull

unit-test: ## Runs unit tests.
	./gradlew test

integration-test: ## Runs integration tests.
	./gradlew integrationTest

lint: ## Runs the Kotlin linter.
	./gradlew ktlintCheck

lint-fix: ## Runs the Kotlin linter and auto-fixes.
	./gradlew ktlintFormat

clean: ## Stops all services and deletes build directories.
	$(DOCKER_COMPOSE) down
	rm -rf .gradle build
