#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${REGISTRY:-your-dockerhub-username}"
TAG="${TAG:-latest}"
PUSH="${PUSH:-false}"
DOCKERFILE="${DOCKERFILE:-Dockerfile}"
PLATFORM="${PLATFORM:-}"
DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-}"
DOCKERHUB_TOKEN="${DOCKERHUB_TOKEN:-${DOCKERHUB_PASSWORD:-}}"
DOCKER_PROGRESS="${DOCKER_PROGRESS:-plain}"
COLOR="${COLOR:-auto}"

SERVICES=(
  config-server
  discovery-server
  api-gateway
  auth-service
  account-service
  transaction-service
  notification-service
) 

usage() {
  cat <<'EOF'
Build SmartBank Docker images with the root Dockerfile.

Usage:
  scripts/build-images.sh [options] [service...]

Options:
  -r, --registry REGISTRY   Image registry/namespace. Default: $REGISTRY or your-dockerhub-username
  -t, --tag TAG             Image tag. Default: $TAG or latest
  -p, --push                Push images after successful build
      --docker-username USER Docker Hub username for login. Default: $DOCKERHUB_USERNAME or registry value
      --platform PLATFORM   Docker build platform, for example linux/amd64
      --no-color            Disable colored output
  -h, --help                Show this help

Examples:
  scripts/build-images.sh -r mydockerhub -t dev
  DOCKERHUB_TOKEN=xxxxx scripts/build-images.sh -r mydockerhub -t main-abc123 --push
  scripts/build-images.sh -r mydockerhub auth-service api-gateway

Environment alternatives:
  REGISTRY=mydockerhub TAG=dev PUSH=true DOCKERHUB_TOKEN=xxxxx scripts/build-images.sh
EOF
}

setup_colors() {
  if [[ "${NO_COLOR:-}" != "" || "$COLOR" == "never" ]]; then
    use_color=false
  elif [[ "$COLOR" == "always" ]]; then
    use_color=true
  elif [[ -t 1 ]]; then
    use_color=true
  else
    use_color=false
  fi

  if [[ "$use_color" == "true" ]]; then
    RESET=$'\033[0m'
    BOLD=$'\033[1m'
    DIM=$'\033[2m'
    RED=$'\033[31m'
    GREEN=$'\033[32m'
    YELLOW=$'\033[33m'
    BLUE=$'\033[34m'
    CYAN=$'\033[36m'
  else
    RESET=""
    BOLD=""
    DIM=""
    RED=""
    GREEN=""
    YELLOW=""
    BLUE=""
    CYAN=""
  fi
}

timestamp() {
  date +"%H:%M:%S"
}

log() {
  printf '%s[%s]%s %s\n' "$DIM" "$(timestamp)" "$RESET" "$*"
}

success() {
  log "${GREEN}$*${RESET}"
}

warn() {
  log "${YELLOW}$*${RESET}"
}

error() {
  printf '%s[%s]%s %s%s%s\n' "$DIM" "$(timestamp)" "$RESET" "$RED" "$*" "$RESET" >&2
}

section() {
  printf -- '\n'
  printf -- '%s================================================================%s\n' "$CYAN" "$RESET"
  printf -- '%s%s%s\n' "$BOLD$CYAN" "$*" "$RESET"
  printf -- '%s================================================================%s\n' "$CYAN" "$RESET"
}

step() {
  printf -- '\n'
  printf -- '%s---- %s ----%s\n' "$BLUE" "$*" "$RESET"
}

format_duration() {
  local seconds="$1"
  local minutes=$((seconds / 60))
  local remainder=$((seconds % 60))

  if (( minutes > 0 )); then
    printf '%dm %02ds' "$minutes" "$remainder"
  else
    printf '%ds' "$remainder"
  fi
}

require_docker_access() {
  if docker info >/dev/null 2>&1; then
    return 0
  fi

  cat >&2 <<EOF
${RED}Docker is installed, but this user cannot access the Docker daemon.${RESET}

Typical Linux fixes:
  sudo usermod -aG docker "$USER"
  newgrp docker

Or log out and log back in after adding the group.

Quick one-time workaround:
  sudo scripts/build-images.sh --registry your-dockerhub-username --tag latest --push

If you are inside a container, also verify that /var/run/docker.sock is mounted
and that the container user has permission to read/write it.
EOF
  exit 1
}

dockerhub_login() {
  local username="$DOCKERHUB_USERNAME"

  if [[ -z "$username" ]]; then
    username="$REGISTRY"
  fi

  if [[ -z "$username" || "$username" == "your-dockerhub-username" ]]; then
    error "Docker Hub username is required before push."
    error "Set --docker-username, DOCKERHUB_USERNAME, or --registry to your Docker Hub username."
    exit 1
  fi

  step "Docker Hub login"
  log "Logging in as ${BOLD}${username}${RESET}"
  if [[ -n "$DOCKERHUB_TOKEN" ]]; then
    printf '%s\n' "$DOCKERHUB_TOKEN" | docker login --username "$username" --password-stdin
  else
    docker login --username "$username"
  fi
}

is_known_service() {
  local candidate="$1"
  local service

  for service in "${SERVICES[@]}"; do
    if [[ "$service" == "$candidate" ]]; then
      return 0
    fi
  done

  return 1
}

selected_services=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    -r|--registry)
      REGISTRY="${2:?Missing value for $1}"
      shift 2
      ;;
    -t|--tag)
      TAG="${2:?Missing value for $1}"
      shift 2
      ;;
    -p|--push)
      PUSH="true"
      shift
      ;;
    --docker-username)
      DOCKERHUB_USERNAME="${2:?Missing value for $1}"
      shift 2
      ;;
    --platform)
      PLATFORM="${2:?Missing value for $1}"
      shift 2
      ;;
    --no-color)
      COLOR="never"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      setup_colors
      error "Unknown option: $1"
      usage >&2
      exit 1
      ;;
    *)
      selected_services+=("$1")
      shift
      ;;
  esac
done

if [[ ${#selected_services[@]} -eq 0 ]]; then
  selected_services=("${SERVICES[@]}")
fi

setup_colors

for service in "${selected_services[@]}"; do
  if ! is_known_service "$service"; then
    error "Unknown service: $service"
    error "Known services: ${SERVICES[*]}"
    exit 1
  fi
done

build_args=()
if [[ -n "$PLATFORM" ]]; then
  build_args+=(--platform "$PLATFORM")
fi

section "SmartBank image build"
log "Registry: ${BOLD}${REGISTRY}${RESET}"
log "Tag: ${BOLD}${TAG}${RESET}"
log "Dockerfile: ${BOLD}${DOCKERFILE}${RESET}"
log "Push enabled: ${BOLD}${PUSH}${RESET}"
if [[ -n "$PLATFORM" ]]; then
  log "Platform: ${BOLD}${PLATFORM}${RESET}"
fi
log "Services: ${BOLD}${selected_services[*]}${RESET}"

require_docker_access

if [[ "$PUSH" == "true" ]]; then
  dockerhub_login
fi

total_start_time="$(date +%s)"
total_services="${#selected_services[@]}"
service_index=0

for service in "${selected_services[@]}"; do
  service_index=$((service_index + 1))
  image="${REGISTRY}/smartbank-${service}:${TAG}"
  service_start_time="$(date +%s)"

  section "Service ${service_index}/${total_services}: ${service}"
  log "Image: ${BOLD}${image}${RESET}"

  step "Build"
  log "${DIM}docker build --build-arg SERVICE_NAME=${service} -f ${DOCKERFILE} -t ${image} .${RESET}"
  DOCKER_BUILDKIT=1 docker build \
    --progress="$DOCKER_PROGRESS" \
    "${build_args[@]}" \
    --build-arg "SERVICE_NAME=${service}" \
    -f "$DOCKERFILE" \
    -t "$image" \
    .

  if [[ "$PUSH" == "true" ]]; then
    step "Push"
    log "${DIM}docker push ${image}${RESET}"
    docker push "$image"
  fi

  service_end_time="$(date +%s)"
  success "Finished ${service} in $(format_duration "$((service_end_time - service_start_time))")"
done

total_end_time="$(date +%s)"
section "Complete"
success "Built ${total_services} service image(s) in $(format_duration "$((total_end_time - total_start_time))")"
