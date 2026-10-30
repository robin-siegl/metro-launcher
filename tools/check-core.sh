#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT INT TERM
java -m jdk.compiler/com.sun.tools.javac.Main -d "$out" \
  app/src/main/java/at/robin/metro/model/*.java \
  app/src/test/java/at/robin/metro/model/EngineChecks.java
java -cp "$out" at.robin.metro.model.EngineChecks
java tools/ParseJava.java app/src
