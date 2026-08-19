#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${PICATINNY_SCALA_SBT_TEMPLATE_WORKDIR:-$(mktemp -d)}"
TEMPLATES_GATLING_VERSION="${TEMPLATES_GATLING_VERSION:-v0.15.0}"
TEMPLATES_DIR="$WORK_DIR/templates-gatling"
REGISTRY_DIR="$WORK_DIR/registry"
PROJECT_DIR="$WORK_DIR/scala-sbt-example"

cleanup() {
  if [[ -z "${PICATINNY_SCALA_SBT_TEMPLATE_WORKDIR:-}" ]]; then
    rm -rf "$WORK_DIR"
  fi
}
trap cleanup EXIT

if ! command -v galaxio >/dev/null 2>&1; then
  echo "galaxio CLI is required on PATH" >&2
  exit 1
fi

git clone --depth 1 --branch "${TEMPLATES_GATLING_VERSION}" https://github.com/galax-io/templates-gatling.git "$TEMPLATES_DIR"

mkdir -p "$REGISTRY_DIR"
cat > "$REGISTRY_DIR/galaxio-registry.yaml" <<YAML
apiVersion: galaxio.io/v1
kind: TemplateRegistry
packs:
  - name: gatling
    source: local:$TEMPLATES_DIR
    description: Gatling performance testing templates from main
YAML

galaxio template init gatling/scala-sbt \
  --registry "local:$REGISTRY_DIR" \
  --destination "$PROJECT_DIR" \
  --set Name=scala-sbt-example \
  --set NameWord=picatinny \
  --set Package=org.galaxio.performance \
  --set PackagePath=org/galaxio/performance \
  --set GatlingPicatinnyVersion=0.0.0-ci-local \
  --set SbtVersion="${SBT1_VERSION:-1.12.15}" \
  --set SbtGatlingVersion="${SBT_GATLING_VERSION:-4.19.1}" \
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

cat >> "$PROJECT_DIR/build.sbt" <<'SBT'

resolvers += Resolver.mavenLocal

// Full Gatling e2e (test-model layer 4): WireMock backs HttpIntegrationCoverage — picatinny features driven over real HTTP,
// responses validated with Gatling `check`. Test scope only; this is the example overlay (a real consumer), never the library.
libraryDependencies += "org.wiremock" % "wiremock" % "3.13.2" % Test
SBT

rm -rf "$PROJECT_DIR/src/test/scala/org/galaxio/performance/picatinny"
cp -R "$ROOT_DIR/examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny" \
  "$PROJECT_DIR/src/test/scala/org/galaxio/performance/picatinny"

# Overlay the example's own logback.xml: it routes the diagnostics logger through a dedicated
# prefix-free appender (additivity="false") so the multi-line startup banner/ASCII chart stays
# aligned, instead of the template's generic default. Without this the CI run uses whatever
# templates-gatling scaffolds and the banner never appears in the log at the levels tested here.
cp "$ROOT_DIR/examples/scala-sbt-example/src/test/resources/logback.xml" \
  "$PROJECT_DIR/src/test/resources/logback.xml"

(
  cd "$PROJECT_DIR"
  # `Gatling/testOnly *`, NOT `Gatling/test`. On sbt 2 `test` is `testQuick`: with the project's
  # target/ deleted it still prints "Passed: Total 0" / "No tests to run" / [success], because the
  # pass record lives in the GLOBAL disk cache (~/.cache/sbt) and survives clean and rm -rf target.
  # `testFull` is not an option either — it does not exist on sbt 1. Only `testOnly *` is
  # convergent across both majors. Do not "simplify" this line in either direction.
  sbt_cmd=(sbt)
  if [ "${SBT_MAJOR:-1}" = "2" ]; then
    sbt_cmd=(sbt --sbt-version "${SBT2_VERSION:-2.0.6}")
  fi
  "${sbt_cmd[@]}" 'Gatling/testOnly *'
)
