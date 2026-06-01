#!/usr/bin/env bash
set -a
source .env
set +a

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle}"

./gradlew bootRun
