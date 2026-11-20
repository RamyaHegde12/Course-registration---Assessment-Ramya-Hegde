#!/bin/sh
set -eu

build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT

find src/main/java src/test/java -name '*.java' -print > "$build_dir/sources.txt"
javac --release 8 -d "$build_dir/classes" @"$build_dir/sources.txt"
java -ea -cp "$build_dir/classes" courseregistration.CourseRegistrationServiceTest
