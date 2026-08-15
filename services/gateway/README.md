# Search gateway

The Android APK never contains a search-provider key. This Worker accepts only `POST /v1/search`, normalizes and bounds the query, calls one fixed Brave Search endpoint with strict safe search, and returns at most five bounded HTTPS evidence records. Set `BRAVE_SEARCH_API_KEY` as a Worker secret and deploy behind rate limiting/App Check before production.

Build the APK with `-PMY_DEAR_SEARCH_ENDPOINT=https://your-gateway.example/v1/search`. A blank property keeps search visibly disabled and local chat sends no network traffic.

Cloud model routing for a paid tier belongs behind a separate authenticated endpoint; it must not share this unauthenticated search contract or expose provider keys.
