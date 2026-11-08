#!/bin/sh

# Use Surefire's final summary for test failures and keep the full log for other errors.
log=/tmp/maven-package.log
if mvn -B -ntp -Dstyle.color=never package >"$log" 2>&1; then
    cat "$log" && rm "$log"
else
    status=$?
    summary=$(awk '
        /^\[INFO\] Results:[[:space:]]*$/ { in_results = 1; details = totals = ""; next }
        in_results && /^\[ERROR\] (Failures:|Errors:|  )/ { details = details $0 "\n"; next }
        in_results && /^\[ERROR\] Tests run:/ { totals = $0; in_results = 0 }
        /^\[ERROR\] Failed to execute goal org\.apache\.maven\.plugins:maven-surefire-plugin:[^:]+:test .* on project [^:]+:[[:space:]]*(There are test failures\.)?[[:space:]]*$/ { in_failure = 1; next }
        in_failure && /^\[ERROR\] -> \[Help [0-9]+\][[:space:]]*$/ { test_failure = 1; in_failure = 0; next }
        in_failure && !/^\[ERROR\]([[:space:]]*$| See .* for the individual test results\.[[:space:]]*$| See dump files \(if any exist\) )/ { in_failure = 0 }
        END { if (test_failure && details != "" && totals != "") printf "%s%s\n", details, totals }
    ' "$log")
    if [ -n "$summary" ]; then
        printf 'Failed Java tests (Maven Surefire)\n%s\n' "$summary"
    else
        cat "$log"
    fi
    exit "$status"
fi
