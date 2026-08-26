#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Job discovery smoke assertion failed at line $LINENO"' ERR

api='http://localhost:8080'

assert_empty_page() {
  python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["content"] == []; assert d["totalElements"] == 0; assert d["page"] == 0; assert d["size"] == 10' <<< "$1"
}

WITHOUT_QUERY=$(curl --fail --silent "$api/jobs?page=0&size=10")
assert_empty_page "$WITHOUT_QUERY"

BLANK_QUERY=$(curl --fail --silent "$api/jobs?query=%20%20%20&page=0&size=10")
assert_empty_page "$BLANK_QUERY"

echo "Public job discovery without a text filter: OK"
