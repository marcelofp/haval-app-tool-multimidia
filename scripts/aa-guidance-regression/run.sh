#!/bin/sh
set -eu
cd "$(dirname "$0")"
: "${KOTLIN_DEPS:?Point to existing Kotlin 2.0.21 compiler dependency directory}"
SOURCE_ROOT="${SOURCE_ROOT:-../../app/src/main/java/br/com/redesurftank/havalshisuku}"
mkdir -p build
java -cp "$KOTLIN_DEPS/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
 -no-stdlib -no-reflect -classpath "$KOTLIN_DEPS/kotlin-stdlib-2.0.21.jar" \
 -d build/classes tests/*.kt  "$SOURCE_ROOT/services/AndroidAutoNavigationMonitor.kt"  "$SOURCE_ROOT/managers/AndroidAutoNavigationTelemetry.kt"  "$SOURCE_ROOT/managers/AndroidAutoSessionTelemetry.kt"  "$SOURCE_ROOT/navigation/CarPlayGuidance.kt"  "$SOURCE_ROOT/navigation/CarPlayHudPolicy.kt"
java -cp "build/classes:$KOTLIN_DEPS/kotlin-stdlib-2.0.21.jar" RegressionKt
