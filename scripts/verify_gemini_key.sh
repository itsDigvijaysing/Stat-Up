#!/usr/bin/env bash
# Verify a Google Generative Language API key end-to-end.
#
# Usage:
#   1. Create a `.env` at the repo root with one line:
#        GOOGLE_API_KEY=AIza...
#   2. From the repo root: ./scripts/verify_gemini_key.sh
#
# What it does:
#   - Loads GOOGLE_API_KEY from .env (no shell sourcing - safer if the file has quotes/spaces)
#   - Lists all models the key can see (catches "wrong project / disabled API" issues fast)
#   - Tries each candidate model with a 1-token prompt and prints HTTP status, model
#     reply, and any rate-limit metadata Google returns
#   - Models tested:
#       gemini-2.5-flash         - what the app uses now (current stable)
#       gemini-2.5-flash-lite    - higher free-tier quota (15 RPM / 1000 RPD vs 10 / 500)
#       gemini-2.0-flash         - retired 2026-03-03; confirms the deprecation if it 4xxs

set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "ERROR: ${ENV_FILE} not found. Create it with one line:" >&2
    echo "  GOOGLE_API_KEY=AIza..." >&2
    exit 1
fi

# Extract GOOGLE_API_KEY without sourcing the file (don't execute user content).
API_KEY="$(grep -E '^[[:space:]]*GOOGLE_API_KEY[[:space:]]*=' "${ENV_FILE}" \
    | tail -1 \
    | sed -E 's/^[[:space:]]*GOOGLE_API_KEY[[:space:]]*=[[:space:]]*//' \
    | sed -E 's/^"(.*)"$/\1/' \
    | sed -E "s/^'(.*)'\$/\\1/" \
    | tr -d '\r')"

if [[ -z "${API_KEY}" ]]; then
    echo "ERROR: GOOGLE_API_KEY in ${ENV_FILE} is empty." >&2
    exit 1
fi

# Don't print the full key in any output. Just a fingerprint.
KEY_LEN=${#API_KEY}
KEY_PREFIX="${API_KEY:0:6}"
KEY_SUFFIX="${API_KEY: -4}"
echo "Loaded GOOGLE_API_KEY (length=${KEY_LEN}, prefix=${KEY_PREFIX}…${KEY_SUFFIX})"
echo

BASE="https://generativelanguage.googleapis.com/v1beta"

#
# Step 1 - list models the key can access.
#
echo "=== Step 1: ListModels (does the key auth at all?) ==="
LIST_OUT=$(mktemp); trap 'rm -f "$LIST_OUT"' EXIT
LIST_CODE=$(curl -sS -o "$LIST_OUT" -w '%{http_code}' \
    -H "x-goog-api-key: ${API_KEY}" \
    "${BASE}/models")
echo "HTTP ${LIST_CODE}"
if [[ "$LIST_CODE" != "200" ]]; then
    echo "Response body (first 1KB):"
    head -c 1024 "$LIST_OUT"; echo
    echo
    echo "If this is 403, the Generative Language API isn't enabled on the key's project,"
    echo "or the key was restricted. Open the API Studio key page and check."
    exit 1
fi

# Pull just the model IDs that have "generateContent" support.
echo "Models visible to this key that support generateContent:"
# Try jq, fall back to grep if jq is missing.
if command -v jq >/dev/null 2>&1; then
    jq -r '.models[] | select(.supportedGenerationMethods? // [] | index("generateContent")) | .name' "$LIST_OUT" \
        | sed 's|^models/||' | sort -u | sed 's/^/  - /'
else
    grep -oE '"name":[[:space:]]*"models/[^"]+"' "$LIST_OUT" \
        | sed -E 's|.*models/([^"]+)"|  - \1|' | sort -u
fi
echo

#
# Step 2 - for each candidate model, make a tiny generateContent call and report status.
#
test_model() {
    local model="$1"
    local body='{"contents":[{"parts":[{"text":"reply with the single word OK"}]}],"generationConfig":{"maxOutputTokens":5,"temperature":0}}'
    local out hdr code
    out=$(mktemp); hdr=$(mktemp)
    code=$(curl -sS -o "$out" -D "$hdr" -w '%{http_code}' \
        -X POST \
        -H "x-goog-api-key: ${API_KEY}" \
        -H "Content-Type: application/json" \
        --data "$body" \
        "${BASE}/models/${model}:generateContent")

    printf 'model=%-28s HTTP=%s' "$model" "$code"
    # Pull the rate-limit / quota-related response headers Google returns
    local quota_hdr
    quota_hdr=$(grep -i -E '^(x-goog-quota-user|x-ratelimit|retry-after|x-goog-api-client|content-type):' "$hdr" \
        | tr -d '\r' | head -5 | sed 's/^/    /')

    case "$code" in
        200)
            local text
            if command -v jq >/dev/null 2>&1; then
                text=$(jq -r '.candidates[0].content.parts[0].text // .candidates[0].finishReason // "(no text)"' "$out")
            else
                text=$(grep -oE '"text":[[:space:]]*"[^"]*"' "$out" | head -1 | sed -E 's/.*"text":[[:space:]]*"([^"]*)".*/\1/')
            fi
            echo "  reply=\"${text}\""
            ;;
        404)
            echo "  → model not found (likely retired or renamed)"
            ;;
        429)
            echo "  → 429 RESOURCE_EXHAUSTED (rate / quota hit)"
            ;;
        400|403)
            echo
            echo "  body (first 400B):"
            head -c 400 "$out" | sed 's/^/    /'
            echo
            ;;
        *)
            echo
            echo "  body (first 400B):"
            head -c 400 "$out" | sed 's/^/    /'
            echo
            ;;
    esac
    if [[ -n "$quota_hdr" ]]; then
        echo "$quota_hdr"
    fi
    rm -f "$out" "$hdr"
}

echo "=== Step 2: per-model generateContent probes ==="
for m in gemini-2.5-flash gemini-2.5-flash-lite gemini-2.0-flash; do
    test_model "$m"
    echo
done

echo "Done."
echo
echo "Interpretation cheat-sheet:"
echo "  - 200 on gemini-2.5-flash       → key is healthy; the app should work."
echo "  - 200 on gemini-2.5-flash-lite  → also healthy; this model has 1.5x the daily quota."
echo "  - 404 on gemini-2.0-flash       → confirms the model is retired (expected)."
echo "  - 429 on every model            → daily quota (RPD) exhausted; wait until midnight UTC-7"
echo "                                   for the Google AI Studio reset, or use a paid tier."
echo "  - 403 across the board          → API not enabled on the key's project, or key restricted."
