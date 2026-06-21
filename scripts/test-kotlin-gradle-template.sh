#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${PICATINNY_KOTLIN_GRADLE_TEMPLATE_WORKDIR:-$(mktemp -d)}"
TEMPLATES_DIR="$WORK_DIR/templates-gatling"
REGISTRY_DIR="$WORK_DIR/registry"
PROJECT_DIR="$WORK_DIR/kotlin-gradle-example"

cleanup() {
  if [[ -z "${PICATINNY_KOTLIN_GRADLE_TEMPLATE_WORKDIR:-}" ]]; then
    rm -rf "$WORK_DIR"
  fi
}
trap cleanup EXIT

if ! command -v galaxio >/dev/null 2>&1; then
  echo "galaxio CLI is required on PATH" >&2
  exit 1
fi

git clone --depth 1 https://github.com/galax-io/templates-gatling.git "$TEMPLATES_DIR"

mkdir -p "$REGISTRY_DIR"
cat > "$REGISTRY_DIR/galaxio-registry.yaml" <<YAML
apiVersion: galaxio.io/v1
kind: TemplateRegistry
packs:
  - name: gatling
    source: local:$TEMPLATES_DIR
    description: Gatling performance testing templates from main
YAML

galaxio template init gatling/kotlin-gradle \
  --registry "local:$REGISTRY_DIR" \
  --destination "$PROJECT_DIR" \
  --set Name=kotlin-gradle-example \
  --set NameWord=picatinny \
  --set Package=org.galaxio.performance \
  --set PackagePath=org/galaxio/performance \
  --set GatlingPicatinnyVersion=0.0.0-ci-local \
  --set BaseUrl=http://localhost \
  --set BaseAuthUrl=http://localhost/auth \
  --set WsBaseUrl=ws://localhost/ws \
  --set Intensity="60 rpm" \
  --set StagesNumber=2 \
  --set RampDuration="1 second" \
  --set StageDuration="1 second" \
  --set TestDuration="5 seconds" \
  --set StartupBannerEnabled=true \
  --set DiagnosticsEnabled=true

rm -rf "$PROJECT_DIR/src/gatling/kotlin/org/galaxio/performance/picatinny"
cp -R "$ROOT_DIR/examples/kotlin-gradle-example/src/gatling/kotlin/org/galaxio/performance/picatinny" \
  "$PROJECT_DIR/src/gatling/kotlin/org/galaxio/performance/picatinny"

# Keep the template's rendered resources (logback.xml quiets debug,
# gatling.conf, simulation.conf via --set). Inject sources only.

# WireMock for the e2e HTTP integration simulation (Debug)
cat >> "$PROJECT_DIR/build.gradle.kts" <<'GRADLE'

dependencies {
    gatling("org.wiremock:wiremock:3.13.2")
}
GRADLE

(
  cd "$PROJECT_DIR"
  gradle clean gatlingRun --all
)
