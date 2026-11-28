# service-registry

Central **service registry** for the Smart Health Nation microservices platform, built on
**Netflix Eureka Server** (Spring Cloud Netflix).

Each microservice registers its network location with the registry, sends periodic heartbeats,
and uses the registry to find other services. The registry has **no business logic, no
database and no knowledge of the platform's authentication** (that belongs to the identity
service, Keycloak and the API gateway). It holds an in-memory list of who is running where.

> **Status:** suitable for development and integration environments. Before calling it
> production-ready, complete the hardening in [Security considerations](#security-considerations),
> deploy at least two peers, and run integration tests against the real deployment topology.

---

## Contents

1. [Architecture](#architecture)
2. [Technology stack](#technology-stack)
3. [Running locally](#running-locally)
4. [Eureka dashboard](#eureka-dashboard)
5. [Instance lifecycle](#instance-lifecycle)
6. [Registering Spring Boot services](#registering-spring-boot-services)
7. [Registering non-Spring services (HTTP API)](#registering-non-spring-services-http-api)
8. [Eureka REST API reference](#eureka-rest-api-reference)
9. [Configuration](#configuration)
10. [Monitoring and health checks](#monitoring-and-health-checks)
11. [Security considerations](#security-considerations)
12. [Running the tests](#running-the-tests)

---

## Architecture

```
                        ┌──────────────────────────┐
   register / renew /   │     service-registry     │   replication (prod)
   cancel / query       │      Eureka Server       │◄────────────────────► peer registry nodes
  ┌────────────────────►│  /eureka/**  (REST API)  │
  │                     │  /           (dashboard) │
  │                     │  /actuator/* (health…)   │
  │                     └──────────────────────────┘
  │                                  ▲
  │                                  │ query (service name -> instances)
  │                                  │
┌─┴──────────────┬─────────────────┬─┴──────────────┬────────────────┐
│ Spring Boot    │ FastAPI         │ NestJS         │ Go / other     │
│ (eureka-client)│ (py-eureka-...) │ (eureka-js-...)│ (HTTP client)  │
└────────────────┴─────────────────┴────────────────┴────────────────┘
          ▲
          │ lb://SERVICE-NAME
      api-gateway (Eureka client + Spring Cloud LoadBalancer)
```

* **Registration protocol = HTTP + JSON/XML.** Any language that can send HTTP requests can
  take part. The registry contains no framework-specific code.
* **State is in memory.** After a restart the registry is empty. Clients rebuild it with their
  next heartbeat: a heartbeat for an unknown instance returns `404`, and the client then
  registers again.
* **High availability** comes from running several Eureka peers that replicate to each other
  (the `prod` profile). A database is not needed.

Code layout (deliberately minimal):

```
service-registry/
├── src/main/java/com/g1/serviceregistry/ServiceRegistryApplication.java   # @EnableEurekaServer
├── src/main/resources/application.yml                                     # all configuration
├── src/test/java/com/g1/serviceregistry/
│   ├── ServiceRegistryApplicationTests.java     # startup, dashboard, actuator
│   └── EurekaHttpApiIntegrationTest.java        # full lifecycle over plain HTTP
├── pom.xml
└── README.md
```

## Technology stack

| Component            | Version                                    |
|----------------------|--------------------------------------------|
| Java                 | 21                                         |
| Spring Boot          | 3.5.16                                     |
| Spring Cloud (BOM)   | 2025.0.3                                   |
| Netflix Eureka Server| managed by the Spring Cloud BOM            |
| Spring Boot Actuator | managed by Spring Boot                     |
| Build                | Maven (wrapper included: `mvnw`, `mvnw.cmd`) |

Runtime dependencies: `spring-cloud-starter-netflix-eureka-server` and
`spring-boot-starter-actuator`. Test dependency: `spring-boot-starter-test`.

> The `api-gateway` module is currently on Spring Boot 4.x / Spring Cloud 2026.0.x. This
> doesn't affect compatibility, because clients and the server only talk over the Eureka HTTP
> protocol.

## Running locally

Prerequisite: JDK 21.

```bash
# Linux / macOS / Git Bash
./mvnw spring-boot:run

# Windows PowerShell / cmd
mvnw.cmd spring-boot:run
```

Or build and run the jar:

```bash
./mvnw clean package
java -jar target/service-registry-0.0.1-SNAPSHOT.jar
```

The server listens on **http://localhost:8761**. To use another port, set `SERVER_PORT`.

Quick check:

```bash
curl http://localhost:8761/actuator/health
curl -H "Accept: application/json" http://localhost:8761/eureka/apps
```

## Eureka dashboard

Open **http://localhost:8761/** in a browser. It shows:

* registered applications, with their instances and statuses;
* lease and renewal statistics, and whether self-preservation is active;
* the configured peer replicas (in clustered mode).

You can turn the dashboard off with `EUREKA_DASHBOARD_ENABLED=false`. Consider doing this in
production, or restrict who can reach it (see [Security](#security-considerations)).

## Instance lifecycle

```
  POST /eureka/apps/{APP}                 -> 204   registered (status UP / STARTING)
        │
        ▼
  PUT  /eureka/apps/{APP}/{id}  every 30s -> 200   lease renewed
        │                                 -> 404   unknown instance -> client must re-register
        │
        ├── no heartbeat for 90s (durationInSecs)  -> lease expires, instance evicted
        │      (eviction pauses while self-preservation is active)
        ▼
  DELETE /eureka/apps/{APP}/{id}           -> 200   graceful deregistration on shutdown
```

| Parameter                         | Default | Where it is set                     |
|-----------------------------------|---------|-------------------------------------|
| Heartbeat interval                | 30 s    | client: `leaseInfo.renewalIntervalInSecs` |
| Lease expiration                  | 90 s    | client: `leaseInfo.durationInSecs`  |
| Eviction task interval            | 60 s    | server: `EUREKA_EVICTION_INTERVAL_MS` |
| Read-only response cache refresh  | 30 s    | server: `EUREKA_RESPONSE_CACHE_UPDATE_INTERVAL_MS` |
| Client registry refresh           | 30 s    | client setting                      |

**Propagation delay.** A newly registered instance can take up to about 30 s (server response
cache) plus 30 s (client local cache) to show up for consumers that list instances. Clients
should retry and load-balance across instances rather than expect discovery to be instantly
consistent.

**Self-preservation.** If the registry receives fewer renewals than expected
(`renewal-percent-threshold`, 85 %), it assumes there is a network problem rather than mass
failure and stops evicting instances. This is intended behaviour and stays **enabled**. The
dashboard warning about it is normal in a local setup with only a few instances.

## Registering Spring Boot services

Add the client starter (version managed by the Spring Cloud BOM):

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

```yaml
spring:
  application:
    name: ms-identite-ins            # becomes the Eureka app name MS-IDENTITE-INS
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true          # recommended in containers
    metadata-map:
      version: 1.0.0
```

The client registers on startup, sends heartbeats, and deregisters on graceful shutdown. Other
services and the gateway can then call it as `lb://ms-identite-ins`.

## Registering non-Spring services (HTTP API)

Services in any other framework can register in either of two ways:

1. **Use a community Eureka client library** (use the versions your team has vetted), e.g.
   * Python / FastAPI: `py-eureka-client`
   * Node.js / NestJS: `eureka-js-client`
   * Go: `github.com/hudl/fargo` or a small hand-written client (see below)
2. **Call the REST API directly** with whatever HTTP client the framework already uses.

### Requirements for a compatible client

A client is compatible if it:

1. **Registers** on startup with `POST /eureka/apps/{APP_NAME}` and a JSON body like the one
   below. The required fields are `instanceId`, `hostName`, `app`, `ipAddr`, `status`, `port`,
   `vipAddress` and `dataCenterInfo` (`name: "MyOwn"` outside AWS).
2. **Sends a heartbeat** every `renewalIntervalInSecs` (30 s) with
   `PUT /eureka/apps/{APP_NAME}/{instanceId}`.
3. **Registers again** whenever a heartbeat returns **404**.
4. **Deregisters** on graceful shutdown with `DELETE /eureka/apps/{APP_NAME}/{instanceId}`.
5. **Advertises a reachable address**: `hostName`/`ipAddr` and `port` must be what other
   services (not just localhost) use to reach it. In containers, advertise the container or
   service IP.
6. Uses a **stable, unique `instanceId`** per running instance, e.g. `{app}:{host}:{port}`.
7. Sends `Content-Type: application/json` and `Accept: application/json`. Without the `Accept`
   header, responses are XML.
8. Exposes a health endpoint and puts its URL in `healthCheckUrl` (useful for operators and
   for health-aware clients).

Conventions: Eureka shows `app` names **in upper case**. Use the same logical name as the
`spring.application.name` convention (`ms-xxx`), so the gateway can route `lb://ms-xxx` in the
same way whatever the framework.

### HTTP examples

The examples use `curl`. The same requests work from `httpx` (Python), `fetch`/`axios`
(Node.js) or `net/http` (Go).

**Register an instance**

```bash
curl -i -X POST http://localhost:8761/eureka/apps/MS-PHARMACY-SENTINEL \
  -H "Content-Type: application/json" \
  -d '{
    "instance": {
      "instanceId": "ms-pharmacy-sentinel:10.0.0.12:8004",
      "hostName": "10.0.0.12",
      "app": "MS-PHARMACY-SENTINEL",
      "ipAddr": "10.0.0.12",
      "vipAddress": "ms-pharmacy-sentinel",
      "secureVipAddress": "ms-pharmacy-sentinel",
      "status": "UP",
      "port": { "$": 8004, "@enabled": "true" },
      "securePort": { "$": 443, "@enabled": "false" },
      "homePageUrl": "http://10.0.0.12:8004/",
      "statusPageUrl": "http://10.0.0.12:8004/info",
      "healthCheckUrl": "http://10.0.0.12:8004/health",
      "dataCenterInfo": {
        "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
        "name": "MyOwn"
      },
      "leaseInfo": { "renewalIntervalInSecs": 30, "durationInSecs": 90 },
      "metadata": { "framework": "fastapi", "version": "1.0.0" }
    }
  }'
# HTTP/1.1 204 No Content
```

**Send a heartbeat (renew the lease)**

```bash
curl -i -X PUT http://localhost:8761/eureka/apps/MS-PHARMACY-SENTINEL/ms-pharmacy-sentinel:10.0.0.12:8004
# 200 OK  -> renewed
# 404     -> unknown instance: register again
```

**Discover services**

```bash
# All applications
curl -H "Accept: application/json" http://localhost:8761/eureka/apps

# All instances of one application
curl -H "Accept: application/json" http://localhost:8761/eureka/apps/MS-PHARMACY-SENTINEL

# One instance
curl -H "Accept: application/json" \
  http://localhost:8761/eureka/apps/MS-PHARMACY-SENTINEL/ms-pharmacy-sentinel:10.0.0.12:8004

# Instances by VIP address (logical name)
curl -H "Accept: application/json" http://localhost:8761/eureka/vips/ms-pharmacy-sentinel
```

Abridged response:

```json
{
  "application": {
    "name": "MS-PHARMACY-SENTINEL",
    "instance": [
      {
        "instanceId": "ms-pharmacy-sentinel:10.0.0.12:8004",
        "ipAddr": "10.0.0.12",
        "status": "UP",
        "port": { "$": 8004, "@enabled": "true" },
        "metadata": { "framework": "fastapi", "version": "1.0.0" }
      }
    ]
  }
}
```

Choose from the instances whose `status` is `UP`, connect to `ipAddr`/`hostName` and `port`,
and do client-side load balancing (round-robin is enough).

**Deregister**

```bash
curl -i -X DELETE http://localhost:8761/eureka/apps/MS-PHARMACY-SENTINEL/ms-pharmacy-sentinel:10.0.0.12:8004
# 200 OK
```

### Minimal client sketch (Python / FastAPI)

```python
import asyncio, os, httpx
from contextlib import asynccontextmanager
from fastapi import FastAPI

EUREKA = os.getenv("EUREKA_URL", "http://localhost:8761/eureka")
APP, HOST, PORT = "MS-PHARMACY-SENTINEL", os.getenv("HOST_IP", "127.0.0.1"), 8004
INSTANCE_ID = f"{APP.lower()}:{HOST}:{PORT}"
BODY = {"instance": {
    "instanceId": INSTANCE_ID, "hostName": HOST, "app": APP, "ipAddr": HOST,
    "vipAddress": APP.lower(), "secureVipAddress": APP.lower(), "status": "UP",
    "port": {"$": PORT, "@enabled": "true"}, "securePort": {"$": 443, "@enabled": "false"},
    "healthCheckUrl": f"http://{HOST}:{PORT}/health",
    "dataCenterInfo": {"@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo", "name": "MyOwn"},
    "leaseInfo": {"renewalIntervalInSecs": 30, "durationInSecs": 90},
    "metadata": {"framework": "fastapi"}}}

async def heartbeat(client: httpx.AsyncClient):
    while True:
        await asyncio.sleep(30)
        r = await client.put(f"{EUREKA}/apps/{APP}/{INSTANCE_ID}")
        if r.status_code == 404:
            await client.post(f"{EUREKA}/apps/{APP}", json=BODY)

@asynccontextmanager
async def lifespan(app: FastAPI):
    async with httpx.AsyncClient(timeout=5) as client:
        await client.post(f"{EUREKA}/apps/{APP}", json=BODY)
        task = asyncio.create_task(heartbeat(client))
        yield
        task.cancel()
        await client.delete(f"{EUREKA}/apps/{APP}/{INSTANCE_ID}")

app = FastAPI(lifespan=lifespan)
```

NestJS (`onModuleInit` / `onApplicationShutdown`) and Go (`time.Ticker` plus `signal.Notify`)
follow the same pattern: register, send a heartbeat on a timer, register again on 404, and
deregister on shutdown. A production client should also retry failed calls with backoff and
read the registry URL(s) from configuration.

## Eureka REST API reference

All paths are relative to `http://<registry-host>:8761/eureka`. Send
`Accept: application/json` to get JSON (the default is XML).

| Operation                    | Method & path                                         | Success | Notes |
|------------------------------|-------------------------------------------------------|---------|-------|
| Register instance            | `POST /apps/{appId}`                                  | 204     | JSON/XML body `{"instance": {...}}` |
| Heartbeat / renew lease      | `PUT /apps/{appId}/{instanceId}`                      | 200     | 404 means register again |
| Deregister instance          | `DELETE /apps/{appId}/{instanceId}`                   | 200     | |
| List all applications        | `GET /apps`                                           | 200     | Cached (see propagation delay) |
| Registry delta               | `GET /apps/delta`                                     | 200     | Changes since the last fetch |
| Get application              | `GET /apps/{appId}`                                   | 200     | All instances |
| Get instance                 | `GET /apps/{appId}/{instanceId}`                      | 200     | 404 if unknown |
| Get instance (by id only)    | `GET /instances/{instanceId}`                         | 200     | |
| Instances by VIP             | `GET /vips/{vipAddress}`                              | 200     | |
| Instances by secure VIP      | `GET /svips/{svipAddress}`                            | 200     | |
| Take out of service          | `PUT /apps/{appId}/{instanceId}/status?value=OUT_OF_SERVICE` | 200 | Instance stays registered but is not routed to |
| Restore status               | `DELETE /apps/{appId}/{instanceId}/status?value=UP`   | 200     | Removes the status override |
| Update metadata              | `PUT /apps/{appId}/{instanceId}/metadata?key=value`   | 200     | |

Instance statuses: `UP`, `DOWN`, `STARTING`, `OUT_OF_SERVICE`, `UNKNOWN`.

## Configuration

All configuration is in [`src/main/resources/application.yml`](src/main/resources/application.yml)
and can be overridden with environment variables.

### Local (default profile)

Runs a single standalone node:

* `register-with-eureka: false` and `fetch-registry: false`, so the server does not register
  with itself or try to replicate.
* Self-preservation is **enabled** (it can be disabled for local experiments only, with
  `EUREKA_SELF_PRESERVATION=false`).
* Actuator exposes `health`, `info` and `metrics`, with full health details.

### Production (`prod` profile)

Activate it with `SPRING_PROFILES_ACTIVE=prod`. It:

* **requires** `EUREKA_INSTANCE_HOSTNAME` (the DNS name peers use to reach this node) and
  `EUREKA_PEER_URLS` (a comma-separated list of peer URLs);
* registers with and fetches from its peers, which is how the cluster replicates;
* forces self-preservation **on**;
* exposes only `health` and `info`, with no health details and no env/java info.

Example with three nodes (each node lists the other nodes; listing itself is harmless):

```bash
SPRING_PROFILES_ACTIVE=prod
EUREKA_INSTANCE_HOSTNAME=registry-1.internal
EUREKA_PEER_URLS=https://registry-2.internal:8761/eureka/,https://registry-3.internal:8761/eureka/
```

Clients should list **all** peers in their registry URL setting so they fail over if one is
down.

### Environment variables

| Variable                                  | Default (local)                         | Purpose |
|-------------------------------------------|-----------------------------------------|---------|
| `SERVER_PORT`                             | `8761`                                  | HTTP port |
| `SPRING_PROFILES_ACTIVE`                  | *(none)*                                | `prod` for clustered / hardened mode |
| `EUREKA_INSTANCE_HOSTNAME`                | `localhost` (required in `prod`)        | Advertised hostname |
| `EUREKA_INSTANCE_PREFER_IP_ADDRESS`       | `false`                                 | Advertise the IP instead of the hostname |
| `EUREKA_INSTANCE_IP_ADDRESS`              | auto-detected                           | Force the advertised IP |
| `EUREKA_REGISTER_WITH_EUREKA`             | `false` (`true` in `prod`)              | Register with peers |
| `EUREKA_FETCH_REGISTRY`                   | `false` (`true` in `prod`)              | Replicate from peers |
| `EUREKA_PEER_URLS`                        | `http://localhost:8761/eureka/` (required in `prod`) | Peer list |
| `EUREKA_SELF_PRESERVATION`                | `true` (always `true` in `prod`)        | Self-preservation mode |
| `EUREKA_RENEWAL_PERCENT_THRESHOLD`        | `0.85`                                  | Self-preservation threshold |
| `EUREKA_EVICTION_INTERVAL_MS`             | `60000`                                 | How often the eviction task runs |
| `EUREKA_RESPONSE_CACHE_UPDATE_INTERVAL_MS`| `30000`                                 | Refresh interval of the response cache |
| `EUREKA_DASHBOARD_ENABLED`                | `true`                                  | Dashboard at `/` |
| `MANAGEMENT_ENDPOINTS_INCLUDE`            | `health,info,metrics` (`health,info` in `prod`) | Exposed actuator endpoints |
| `MANAGEMENT_HEALTH_SHOW_DETAILS`          | `always` (`never` in `prod`)            | Health detail level |

## Monitoring and health checks

| Endpoint                       | Local | prod | Use |
|--------------------------------|:-----:|:----:|-----|
| `/actuator/health`             | ✔ (details) | ✔ (status only) | Load balancer / orchestrator checks |
| `/actuator/health/liveness`    | ✔ | ✔ | Kubernetes liveness probe |
| `/actuator/health/readiness`   | ✔ | ✔ | Kubernetes readiness probe |
| `/actuator/info`               | ✔ | ✔ | Build / app information |
| `/actuator/metrics`            | ✔ | ✘ | Micrometer metrics (local diagnostics) |

In production, collect metrics on a **separate management port that is not reachable from the
public network** (for example `MANAGEMENT_SERVER_PORT=9761` and
`MANAGEMENT_ENDPOINTS_INCLUDE=health,info,metrics`), or add `micrometer-registry-prometheus`
and protect the scrape endpoint. Don't expose `env`, `configprops`, `heapdump` or similar
endpoints.

## Security considerations

The registry is **internal infrastructure** and must never be reachable from the internet.
Anyone who can call `/eureka/**` can register fake instances (and so hijack traffic) or
deregister real ones. Until the measures below are in place, the deployment is not
production-ready.

1. **Network isolation (first line of defence).** Do not publish port 8761 outside the
   platform network (Docker network `smart-health-net`, Kubernetes `NetworkPolicy`, security
   groups). Only microservices, the gateway and peer registries should be able to reach it.
   **Do not route the registry through the public API gateway.**
2. **TLS.** Run behind a TLS-terminating proxy or service mesh (mTLS), or enable TLS in Spring
   Boot directly:
   ```bash
   SERVER_SSL_ENABLED=true
   SERVER_SSL_BUNDLE=registry          # with spring.ssl.bundle.* configured from mounted certs
   ```
   Then use `https://` in `EUREKA_PEER_URLS` and in every client's registry URL, and set
   `securePort` / `secureVipAddress` for clients that are reached over TLS.
3. **Client authentication.** Eureka clients support HTTP Basic credentials in the registry
   URL (`https://user:pass@registry:8761/eureka/`), and most non-Java libraries support it as
   well. To enforce it, add `spring-boot-starter-security` and a small configuration that
   requires authentication on `/eureka/**`, disables CSRF for `/eureka/**` (machine-to-machine
   API), and leaves `/actuator/health/**` open for probes:
   ```java
   @Bean
   SecurityFilterChain registrySecurity(HttpSecurity http) throws Exception {
       return http
           .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
           .authorizeHttpRequests(auth -> auth
               .requestMatchers("/actuator/health/**").permitAll()
               .anyRequest().authenticated())
           .httpBasic(Customizer.withDefaults())
           .build();
   }
   ```
   Credentials (`SPRING_SECURITY_USER_NAME` / `SPRING_SECURITY_USER_PASSWORD`, or a proper
   user store) must come from a secret manager, never from the repository. mTLS at the mesh or
   proxy level is a stronger alternative. This was not enabled by default so that local
   development stays simple for teams whose clients don't support it yet.
4. **No custom JWT here.** End-user authentication and authorization are handled by Keycloak,
   the identity service and the API gateway. The registry only needs to authenticate
   *machines*.
5. **Dashboard.** Turn it off (`EUREKA_DASHBOARD_ENABLED=false`) or protect it with the same
   authentication. It reveals the internal topology.
6. **Actuator.** The `prod` profile limits exposure to `health` and `info`, with no details.
   Keep anything more on a separate, private management port.
7. **Availability.** Run at least two peers on separate hosts or zones, keep self-preservation
   enabled, and point clients at all peers.

## Running the tests

```bash
./mvnw test          # Linux / macOS / Git Bash
mvnw.cmd test        # Windows
```

Each test class starts the full Eureka Server on a random port.

| Test class | Covers |
|------------|--------|
| `ServiceRegistryApplicationTests` | Server startup and instance registry bean, dashboard, `/eureka/apps`, `/actuator/health` (+ liveness/readiness), `/actuator/info` |
| `EurekaHttpApiIntegrationTest` | Registration with metadata, heartbeat / lease renewal, 404 on heartbeat for an unknown instance, deregistration, discovery (`/apps`, `/apps/{app}`, `/vips/{vip}`), multiple instances under one service name |

`EurekaHttpApiIntegrationTest` uses only `java.net.http.HttpClient` and hand-written JSON (no
Spring Cloud Eureka client), which shows that a non-Spring service can register. It disables
the server's read-only response cache so that results don't depend on timing. A real
deployment keeps the cache on.
