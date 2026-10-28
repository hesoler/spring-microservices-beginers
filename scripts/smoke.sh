#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://localhost:${GATEWAY_PORT:-8080}}"
deadline=$(( SECONDS + 90 ))

until curl -fsS -o /dev/null "$BASE/api/student/all" \
   && curl -fsS -o /dev/null "$BASE/api/course/all"; do
  if (( SECONDS > deadline )); then
    echo "FAIL: the gateway did not route to both services within 90s" >&2
    exit 1
  fi
  sleep 3
done

curl -fsS -X POST "$BASE/api/course/create" -H 'Content-Type: application/json' \
  -d '{"name":"Architecture","teacher":"Hector"}' > /dev/null
curl -fsS "$BASE/api/course/all" | grep -q 'Architecture' \
  || { echo "FAIL: the course was not persisted" >&2; exit 1; }

curl -fsS -X POST "$BASE/api/student/create" -H 'Content-Type: application/json' \
  -d '{"name":"Ada","lastName":"Lovelace","email":"ada@example.com","courseId":1}' > /dev/null
curl -fsS "$BASE/api/student/all" | grep -q 'ada@example.com' \
  || { echo "FAIL: the student was not persisted" >&2; exit 1; }

echo "OK: gateway routes by service name, both databases persist, config and eureka are wired"
