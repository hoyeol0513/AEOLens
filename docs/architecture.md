# AeoLens Architecture

## Repository layout

```text
AEOLens/
├─ frontend/            React, TypeScript, Vite, Tailwind CSS, Zustand
├─ backend/             Java 17, Spring Boot, WebFlux, Jsoup
├─ docs/                Architecture and portfolio documentation
├─ docker-compose.yml   Local full-stack container entry point
└─ README.md            Project overview and setup
```

## Request flow

```mermaid
flowchart LR
    U[User] --> F[React dashboard]
    F -->|POST /api/v1/audits| B[Spring WebFlux BFF]
    F -->|HttpOnly guest cookie| B
    B --> V[Public URL validation]
    V --> C[Static HTML crawler]
    C --> J[Jsoup rule engine]
    J --> R[AEO and A11y report]
    R --> H[Anonymous audit history]
    H --> D[(PostgreSQL)]
    D -->|guest-scoped list and detail| B
    B --> F
    C -->|CSR suspected| P[Playwright dynamic analyzer]
    P -->|rendered DOM and visual metrics| J
    J -. extracted text .-> G[Gemini contextual analysis]
```

Solid arrows are implemented. The dashed path represents the planned AI phase.

## Design decisions

- The frontend and backend live in one repository so a reviewer can inspect and run the complete product from one entry point.
- Each application owns its dependencies, build configuration, and container image.
- The backend acts as a BFF so target-site crawling and API credentials never move into the browser.
- Static rule checks run first. Browser rendering and AI calls are reserved for cases where they add information, keeping latency and cost bounded.
- The optional `dynamic-analysis` Maven profile compiles the Playwright adapter. CSR-like shells are rendered in Chromium, then the rendered DOM, computed text colors, and element bounds are folded back into the same audit response.
- Browser subrequests are checked with the same public-host policy as the initial URL. A browser failure falls back to the static report instead of failing the full audit.
- Public-host validation rejects loopback and private-network targets before crawling.
- The first API request creates a guest identity and returns a random 256-bit bearer token in an HttpOnly, SameSite=Lax cookie. Only its SHA-256 hash is stored in PostgreSQL.
- Audit list, detail, and delete queries always include the resolved guest ID, so knowing another audit UUID is not sufficient to access it.
- Stored history contains the report JSON, summary scores, target URL, and timestamp—not the fetched source HTML. Guest sessions and audits use a rolling 90-day retention period, with scheduled cleanup for expired rows.
- The browser cookie deliberately avoids signup friction. Clearing it or switching browsers breaks access to the old anonymous history; JSON export remains the portable fallback until optional account linking is introduced.
