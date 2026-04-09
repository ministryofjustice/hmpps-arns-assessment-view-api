SHELL = '/bin/bash'
LOCAL_COMPOSE_FILES = -f docker/docker-compose.yml -f docker/docker-compose.local.yml
DEV_COMPOSE_FILES = -f docker/docker-compose.yml -f docker/docker-compose.local.yml -f docker/docker-compose.dev.yml
SERVICE_NAME = assessment-view-api

default: help

help: ## The help text you're reading.
	@grep --no-filename -E '^[0-9a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

up: ## Starts/restarts the API in a production container.
	docker compose ${LOCAL_COMPOSE_FILES} down ${SERVICE_NAME}
	docker compose ${LOCAL_COMPOSE_FILES} up ${SERVICE_NAME} --wait --no-recreate

down: ## Stops and removes all containers in the project.
	docker compose ${DEV_COMPOSE_FILES} down
	docker compose ${LOCAL_COMPOSE_FILES} down

dev-up: ## Starts/restarts the API in a development container.
	docker compose ${DEV_COMPOSE_FILES} down ${SERVICE_NAME}
	docker compose ${DEV_COMPOSE_FILES} up --wait --no-recreate ${SERVICE_NAME}

dev-down: ## Stops and removes the API container.
	docker compose ${DEV_COMPOSE_FILES} down ${SERVICE_NAME}

test: ## Runs all the test suites.
	docker compose ${DEV_COMPOSE_FILES} exec \
	   --env HMPPS_AUTH_URL=http://localhost:8090/auth \
	   ${SERVICE_NAME} \
	   gradle test integrationTest --parallel

unit-test: ## Runs unit tests.
	docker compose ${DEV_COMPOSE_FILES} exec ${SERVICE_NAME} gradle test --parallel

integration-test: ## Runs integration tests.
	docker compose ${DEV_COMPOSE_FILES} exec \
	   --env HMPPS_AUTH_URL=http://localhost:8090/auth \
	   ${SERVICE_NAME} \
	   gradle integrationTest --parallel

lint: ## Runs the Kotlin linter.
	docker compose ${DEV_COMPOSE_FILES} exec ${SERVICE_NAME} gradle ktlintCheck --parallel

lint-fix: ## Runs the Kotlin linter and auto-fixes.
	docker compose ${DEV_COMPOSE_FILES} exec ${SERVICE_NAME} gradle ktlintFormat --parallel

update: ## Pulls the latest images for all services.
	docker compose ${LOCAL_COMPOSE_FILES} pull

clean: ## Stops all services and deletes build directories.
	docker compose ${DEV_COMPOSE_FILES} down
	docker compose ${LOCAL_COMPOSE_FILES} down
	rm -rf .gradle build
