# shared-security-starter

Maven library `com.banking:shared-security-starter:1.0.0` (folder `shared-security-starter/`, Java 17, Spring Boot 3.2). One dependency gives a service: RS256 JWT validation against the Auth Service's tokens, role extraction, 401/403 JSON responses, QStash webhook signature checks, CORS from an environment variable. Zero Java code in the consuming service.

It coexists with the older in-repo module `security-starter` (`com.Bank_website:security-starter`, used by the current services). A service that moves to this library removes the old dependency, its own filters and its own `SecurityConfig`; nothing in this PR changes the existing services.

## File tree

```
shared-security-starter/
├── pom.xml
└── src/main/java/com/banking/security/
    ├── autoconfigure/SecurityAutoConfiguration.java
    ├── filter/JwtAuthenticationFilter.java
    ├── filter/QStashSignatureFilter.java
    ├── util/JwtValidator.java
    ├── util/RoleExtractor.java
    ├── util/QStashSignatureVerifier.java
    ├── handler/UnauthorizedHandler.java
    ├── handler/ForbiddenHandler.java
    ├── handler/JsonErrorWriter.java          (shared by the filters and handlers)
    ├── model/AuthenticatedUser.java
    └── config/DefaultSecurityConfig.java
└── src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## What a consuming service writes

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.banking</groupId>
  <artifactId>shared-security-starter</artifactId>
  <version>1.0.0</version>
</dependency>

<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/istiakahasan/Novo_Bank_Service_PLC</url>
  </repository>
</repositories>
```

```yaml
# application.yml  (each value also works as the upper-case environment variable shown)
jwt:
  public-key: ${JWT_PUBLIC_KEY}              # base64 DER, PEM, or base64 of the PEM; OR leave empty and set auth.url
  issuer: banking-auth                       # default
auth:
  url: ${AUTH_URL:}                          # e.g. https://auth-service.onrender.com : JWKS from /api/auth/.well-known/jwks.json, cached 1 h
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:}  # comma separated; empty = no cross-origin access
qstash:
  current-signing-key: ${QSTASH_CURRENT_SIGNING_KEY:}
  next-signing-key: ${QSTASH_NEXT_SIGNING_KEY:}
# Redis host/port/password are the service's own (needed for the jwt_blocklist check)
```

No Java is needed: `DefaultSecurityConfig` applies itself. Use `@PreAuthorize("hasRole('STAFF')")` on controllers, and `RoleExtractor.roleOf(auth)`, `customerIdOf(auth)`, `agentIdOf(auth)` to read the caller. To override the chain, define your own `SecurityFilterChain` bean (inject `JwtAuthenticationFilter`, `QStashSignatureFilter`, `UnauthorizedHandler`, `ForbiddenHandler` and add them to it); method security stays enabled.

Rules applied by the default chain: stateless, CSRF off; public: `/actuator/health`, `/actuator/info`, `/api/auth/**`, `/webhooks/**` (authenticated by `QStashSignatureFilter`); everything else needs a valid token.

## Behaviour

- **JwtValidator:** RS256 only; checks signature, `exp` (30 s skew), `iss == banking-auth`, a `jti`, and that `jwt_blocklist:{jti}` is not set in Redis. No `RedisTemplate<String,String>` bean = blocklist skipped with one warning. Redis error = token rejected (set `jwt.blocklist-fail-open=true` to accept instead).
- **JwtAuthenticationFilter:** no `Authorization: Bearer` = continue (endpoint rules decide); invalid = 401 `{status, message:"Invalid or expired token", timestamp}`; valid = `AuthenticatedUser(userId, role, customerId, agentId)` principal with `ROLE_<role>`.
- **QStashSignatureFilter:** only `/webhooks/**`; reads the raw body (max 1 MB), verifies, then replays the body to the controller; 401 before the controller on failure.
- **QStashSignatureVerifier:** current key, then next key, constant-time compare. Accepts the real `Upstash-Signature` (an HS256 JWT with `iss=Upstash`, `exp`/`nbf` and a `body` claim equal to base64url(SHA-256(body))) and the simple `v1=<base64 HMAC-SHA256 of the body>` format. The `QStash-Signature` header name is accepted as an alias.
- **Boot wiring:** the two filters are beans, so their servlet-container registration is disabled (they run once, inside the security chain); a no-op `UserDetailsService` stops Boot from creating a default user with a generated password; `@EnableMethodSecurity` is applied by the auto-configuration.

## Publishing, option A: GitHub Packages (recommended)

1. The library `pom.xml` already contains `<distributionManagement>` with `https://maven.pkg.github.com/istiakahasan/Novo_Bank_Service_PLC` (server id `github`).
2. The workflow `.github/workflows/publish-shared-security-starter.yml` (below) runs `mvn deploy` on every push to `main` that touches the library. First publish: merge to `main`, or run it from the Actions tab (**Run workflow**).
3. GitHub Packages needs a token even to download (also for public repos). Create a classic personal access token with `read:packages` and store it as `GH_PACKAGES_TOKEN` (and your login as `GH_PACKAGES_USER`) in each Render service.
4. Each consuming service has the `<repositories>` block above in its `pom.xml`; the credentials come from `settings.xml` (next section).
5. Bump `<version>` in the library pom for every release (a published version cannot be overwritten) and update the dependency version in the services.

```yaml
name: publish-shared-security-starter
on:
  push:
    branches: [main]
    paths: ["shared-security-starter/**", ".github/workflows/publish-shared-security-starter.yml"]
  workflow_dispatch:
permissions:
  contents: read
  packages: write
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven
          server-id: github
          server-username: GITHUB_ACTOR
          server-password: GITHUB_TOKEN
      - name: Test and publish
        working-directory: shared-security-starter
        run: mvn -B deploy
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Render build command (option A)

Render environment variables: `GH_PACKAGES_USER`, `GH_PACKAGES_TOKEN`. Build script:

```bash
echo "<settings><servers><server><id>github</id><username>${GH_PACKAGES_USER}</username><password>${GH_PACKAGES_TOKEN}</password></server></servers></settings>" > settings.xml && mvn -s settings.xml clean package -DskipTests
```
(`-s settings.xml` is required: Maven does not read a `settings.xml` in the project folder by itself.)

Render has no native Java/Maven runtime, so these services deploy with Docker. Equivalent Dockerfile build stage (the token exists only in the throw-away build stage, not in the final image):

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN
RUN echo "<settings><servers><server><id>github</id><username>${GH_PACKAGES_USER}</username><password>${GH_PACKAGES_TOKEN}</password></server></servers></settings>" > settings.xml \
 && ./mvnw -s settings.xml -q clean package -DskipTests && rm settings.xml
```
Render passes the service's environment variables to the Docker build as build arguments that the Dockerfile declares with `ARG`.

## Publishing, option B: fallback (no registry)

Single-line Render build command that clones the library, installs it into the build machine's local repository, and builds the service:

```bash
git clone --depth 1 https://github.com/istiakahasan/Novo_Bank_Service_PLC.git /tmp/lib && (cd /tmp/lib/shared-security-starter && mvn -q -B install -DskipTests) && mvn clean package -DskipTests
```
In a Docker build, run the same two steps in the build stage (or `COPY shared-security-starter/` from a repository-root build context and run `./mvnw -f shared-security-starter/pom.xml install -DskipTests` before the service build, as the other services in this repo do).

## Differences from the written brief

- QStash's real signature is a JWT, not `v1=<base64 HMAC>`. Both are accepted so real QStash deliveries verify; the real header is `Upstash-Signature` (`QStash-Signature` is accepted as well).
- `@EnableMethodSecurity` is on `SecurityAutoConfiguration` (not on `DefaultSecurityConfig`), so `@PreAuthorize` keeps working when a service defines its own `SecurityFilterChain`.
- `JwtValidator` takes the Redis template from the context (`RedisTemplate<String,String>`); a blocklist outage rejects tokens by default.
- Additions: `JsonErrorWriter`, container registration of the two filters disabled, a no-op `UserDetailsService`, `kid`-based JWKS cache with a one-minute re-fetch limit, 1 MB body limit on webhooks.
- Tokens issued by the Auth Service (`auth-server`, branch `feat/auth-v2`) carry `role`, `customerId`, `agentId`, `iss=banking-auth`, `jti`: exactly what this library reads. `roles` / `cid` are accepted as fallbacks.
