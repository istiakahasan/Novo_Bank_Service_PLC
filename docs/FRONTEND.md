# Frontend: customer portal and agent console

Next.js 14 (App Router) + TypeScript + Tailwind, TanStack Query, react-hook-form + zod, axios. Plain Tailwind components, no paid UI library. Two apps and one shared package in an npm-workspaces monorepo.

## File tree

```
package.json                      npm workspaces: apps/*, packages/*
apps/
  customer-portal/                port 3100  (role CUSTOMER)
    app/
      layout.tsx  page.tsx  globals.css
      login/page.tsx
      dashboard/page.tsx
      transfer/page.tsx
      transactions/[id]/page.tsx
      support/page.tsx  support/new/page.tsx  support/[id]/page.tsx
      profile/page.tsx
      api/session/route.ts            POST (token handover) / GET / DELETE
      api/session/login/route.ts      POST {username,password} server-side login
      api/session/refresh/route.ts    POST rotate cookies
      api/session/account/route.ts    POST {accountId} remember the account (signed cookie)
      api/proxy/[service]/[...path]/route.ts   authenticated pass-through to the backend
    components/AppShell.tsx  components/LinkAccountCard.tsx
    middleware.ts  next.config.js  tailwind.config.ts  postcss.config.js  tsconfig.json  package.json  .env.example
  agent-console/                  port 3200  (role STAFF)
    app/
      layout.tsx  page.tsx  globals.css
      login/page.tsx
      dashboard/page.tsx
      tickets/page.tsx  tickets/[id]/page.tsx
      kyc-queue/page.tsx
      api/session/{route.ts,login/route.ts,refresh/route.ts}
      api/proxy/[service]/[...path]/route.ts
    components/AppShell.tsx
    middleware.ts  next.config.js  tailwind.config.ts  postcss.config.js  tsconfig.json  package.json  .env.example
packages/shared/
  package.json  tsconfig.json  vitest.config.ts
  src/index.ts
  src/api/{client.ts,customer.ts,ledger.ts,support.ts}
  src/types/index.ts
  src/auth/AuthContext.tsx
  src/providers/Providers.tsx
  src/utils/{masking.ts,currency.ts,errors.ts}
  src/ui/{index.tsx,NavBar.tsx,LoginForm.tsx}
  src/server/{session.ts,handlers.ts,index.ts}
  test/shared.test.ts
```

## How it works

- **Tokens never reach browser JavaScript.** `POST /api/session/login` runs on the Next.js server, calls the auth server, and sets three httpOnly cookies: `novo_at` (access token), `novo_rt` (refresh token) and `novo_session` (an HMAC-signed `{role, customerId, agentId, accountId, exp}` using `SESSION_SECRET`). Nothing is stored in localStorage.
- **Server-side proxy.** The browser calls its own origin: `/api/proxy/{customer|ledger|support}/...`. The route handler checks the signed session, adds `Authorization: Bearer <novo_at>` and forwards to the Render service. Because of this, no backend CORS change is needed (the snippet below is only for direct browser calls). Non-GET calls need the header `X-Requested-With: XMLHttpRequest` (CSRF defence).
- **401 handling** (shared axios client): `POST /api/session/refresh` -> retry once -> a second 401 redirects to `/login`.
- **Route guard:** `middleware.ts` verifies the signed cookie and the role. The portal only accepts CUSTOMER, the console only STAFF; the login handler refuses the wrong role with 403 `Access denied` and sets no cookies.
- **Masking:** `maskMobile`, `maskEmail`, `maskUuid` are used wherever PII or ids are displayed.
- **Idempotency:** the transfer form generates `crypto.randomUUID()` on mount; only **Reset** creates a new one.

## Environment variables (each app: `.env.local` in dev, Vercel project settings in production)

```
NEXT_PUBLIC_CUSTOMER_SERVICE_URL=https://customer-service.onrender.com
NEXT_PUBLIC_LEDGER_SERVICE_URL=https://ledger-service.onrender.com
NEXT_PUBLIC_SUPPORT_SERVICE_URL=https://support-service.onrender.com
NEXT_PUBLIC_AUTH_URL=https://auth-service.onrender.com
SESSION_SECRET=<at least 32 random characters, e.g. openssl rand -base64 32>
```
Optional: `AUTH_LOGIN_PATH` (default `/auth/login`), `AUTH_REFRESH_PATH` (`/auth/refresh`), `AUTH_LOGOUT_PATH` (`/auth/logout`).
`NEXT_PUBLIC_*` values are baked in at build time: redeploy after changing them. In this repo Customer and Ledger are one Render service, so the customer and ledger URLs are the same value.

Local run:

```bash
npm install
cp apps/customer-portal/.env.example apps/customer-portal/.env.local   # fill in
cp apps/agent-console/.env.example   apps/agent-console/.env.local
npm run dev:customer     # http://localhost:3100
npm run dev:agent        # http://localhost:3200
npm test                 # shared package unit tests
```

## Vercel deployment (Hobby, free)

1. Push the repository to GitHub (already done) and sign in at `https://vercel.com` with GitHub.
2. **Add New... -> Project** -> import `istiakahasan/Novo_Bank_Service_PLC`.
3. Project 1: name `novo-customer-portal`. **Root Directory** = `apps/customer-portal`. **Framework Preset** = Next.js. Leave **Include source files outside of the Root Directory in the Build Step** ON (the app imports `packages/shared`). Install command: default (Vercel runs `npm install` at the workspace root).
4. **Environment Variables:** add the five variables above for Production, Preview and Development. Click **Deploy**.
5. Repeat for Project 2: name `novo-agent-console`, **Root Directory** = `apps/agent-console`, same variables (a different `SESSION_SECRET` is recommended).
6. Backend: no CORS change is required (server-side proxy). If you later call the backend directly from the browser, add the Vercel URLs to the CORS allowlist (snippet below).
7. Vercel Hobby is for **non-commercial** use only.

## Five manual test steps

1. **Customer login:** open the portal, sign in as `alice` (or your customer user). You land on `/dashboard`; paste the account ID once when asked ("Link your account"). Balance, last 5 entries and the KYC badge appear.
2. **Transfer:** `/transfer`, enter a destination account ID and `100`, click **Send money**: a receipt with the transaction ID appears. Click **Send money** again without Reset: the banner says "Duplicate request — already processed" and the balance is debited once. Click **Reset** to get a fresh key.
3. **Raise a ticket:** `/support/new`, category `TRANSACTION_DISPUTE`, subject "Wrong amount", paste the transaction ID, submit. The ticket opens with the first system message; post a reply.
4. **Agent view:** open the console, sign in as `staff`. `/tickets` lists the ticket; open it, check the customer panel (KYC, risk tier), add a reply with **Internal note** ticked: it shows in yellow with an INTERNAL badge. In the customer portal that note is not visible.
5. **Transition:** in the ticket, the status dropdown enables only `IN_PROGRESS`; apply it. `RESOLVED` stays disabled until the ticket reaches `PENDING_CUSTOMER`. Signing into the console with a customer account shows "Access denied".

## Backend additions the UI needs (NOT applied; copy into the Customer Service when you want them)

The KYC queue and the dashboard's pending-KYC count call `GET /api/customers?kycStatus=pending`, which does not exist yet. Until it does, both screens show a message pointing here; everything else works.

```java
// repository/CustomerRepository.java
Page<Customer> findByKycStatusIgnoreCase(String kycStatus, Pageable pageable);

// dto/CustomerPageResponse.java
public record CustomerPageResponse(List<CustomerResponse> content, int page, int size, long totalElements, int totalPages) {}

// service/CustomerService.java
@Transactional(readOnly = true)
public CustomerPageResponse listByKycStatus(String kycStatus, int page, int size) {
    Page<Customer> result = repository.findByKycStatusIgnoreCase(kycStatus,
            PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)), Sort.by("createdAt").descending()));
    return new CustomerPageResponse(result.getContent().stream().map(mapper::toResponse).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
}

// controller/CustomerController.java
@PreAuthorize("hasRole('STAFF')")
@GetMapping
public ResponseEntity<CustomerPageResponse> list(@RequestParam String kycStatus,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(customerService.listByKycStatus(kycStatus, page, size));
}

// security/ServiceSecurityConfig.java: add BEFORE the existing "/api/customers/*" rules
.requestMatchers(HttpMethod.GET, "/api/customers").hasRole("STAFF")
```

## CORS allowlist (only for direct browser-to-backend calls; not used by these apps)

```java
@Bean
public CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins}") List<String> origins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(origins); // e.g. https://novo-customer-portal.vercel.app, https://novo-agent-console.vercel.app
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```
Also call `http.cors(Customizer.withDefaults())` in the service's `SecurityFilterChain`. Vercel preview URLs change per deployment, so allow them by pattern with `setAllowedOriginPatterns(List.of("https://*-your-team.vercel.app"))` instead of fixed origins.

## Known limits and mismatches with the backend

- **Auth contract:** the auth service in this repo is `POST /auth/login` with `{username, password}` and returns `{accessToken, refreshToken, tokenType, expiresIn}`; role and customer id are read from the JWT claims (`roles`, `cid`, `sub`). The login form labels the field "Email or username" and sends it as `username`. The handler also accepts a body that already contains `role` and `customerId`.
- **No "my accounts" endpoint:** the backend cannot list a customer's accounts, so the customer links the account ID once (stored in the signed session cookie).
- **Transaction details:** `GET /api/transfers/{id}` returns only id, status, amount and time. From/to accounts are shown only if the backend starts returning them.
- **Idempotent replay:** the backend answers 200 for a replayed key (shown as "already processed"); 409 means the account is busy with another transfer.
- **Fraud-flagged count:** the dashboard counts today's auto-created "Fraud flag on transaction ..." tickets among the newest 100, because no endpoint exposes fraud flags.
- **KYC values:** `kycStatus` values are lowercase in the backend (`pending`, `verified`, `rejected`).
