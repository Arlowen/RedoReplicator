#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <expected.jsonl> <actual.jsonl>" >&2
    exit 2
fi

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/.." && pwd)
maven_bin=${MAVEN_BIN:-mvn}
expected_path=$1
actual_path=$2
if [[ "$expected_path" != /* ]]; then
    expected_path="$PWD/$expected_path"
fi
if [[ "$actual_path" != /* ]]; then
    actual_path="$PWD/$actual_path"
fi

cd "$project_dir"
"$maven_bin" -q -DskipTests test-compile dependency:build-classpath \
    -Dmdep.includeScope=test \
    -Dmdep.outputFile=target/test-classpath.txt

classpath="target/test-classes:target/classes:$(<target/test-classpath.txt)"
java_bin=java
if [[ -n "${JAVA_HOME:-}" ]]; then
    java_bin="$JAVA_HOME/bin/java"
fi

"$java_bin" -cp "$classpath" \
    io.github.arlowen.redoreplicator.testkit.JsonlComparatorMain "$expected_path" "$actual_path"
