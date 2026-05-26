# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**iBPMS** (Intelligent Business Process Management System) — a multi-repo project for managing and executing business workflow policies modeled as UML Activity Diagrams.

| Repo | Tech | Purpose |
|---|---|---|
| `PoliticaNegocio` (this repo) | Spring Boot 4.x · Java 17 · MongoDB | Backend API + workflow engine |
| `frontend_web` | Angular 17+ · Angular Material · bpmn-js | ADMIN_DESIGNER web app |
| `ibpms_mobile` | Flutter 3 · Riverpod · Dio | EMPLOYEE mobile app |
| `ibpms_ia` | FastAPI · Gemini | AI diagram generation microservice |

Backend runs on port `3000` (the deployed instance is at `34.237.109.152:3000`).

---

## Commands

### Backend (PoliticaNegocio)
```bash
# Run
./mvnw spring-boot:run

# Build JAR
./mvnw package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=PoliticaNegocioApplicationTests
```

### Frontend (frontend_web)
```bash
npm start          # dev server on :4200
npm test           # karma unit tests
npm run build      # production build → dist/
```

### Mobile (ibpms_mobile)
```bash
flutter pub get
flutter run             # debug on connected device/emulator
flutter test            # unit + widget tests
flutter build apk       # Android release APK
```

### AI Service (ibpms_ia)
```bash
pip install -r requirements.txt   # (if present)
uvicorn main:app --reload --port 8000
```

---

## Backend Architecture

### Package structure
```
com.ibpms
├── config/          # SecurityConfig, WebSocketConfig, WebSocketAuthChannelInterceptor, DataSeeder
├── domain/          # @Document entities + enums/
├── repository/      # MongoRepository<T, String> only — no custom query methods beyond find*By*
├── service/
│   ├── api/         # Interfaces (public contract)
│   └── impl/        # @Service implementations
├── engine/
│   ├── api/         # WorkflowEngine interface
│   ├── impl/        # WorkflowEngineImpl — drives all state transitions
│   ├── evaluator/   # One NodeEvaluator bean per NodeType (Strategy pattern)
│   └── router/      # FlowRouter — looks up nodes and outgoing flows
├── controller/      # @RestController — delegates only, zero business logic
├── dto/
│   ├── request/     # Java records with @NotNull/@NotBlank
│   └── response/    # Java records
├── security/        # JwtService, JwtAuthFilter, UserDetailsServiceImpl
└── exception/       # GlobalExceptionHandler + custom exceptions
```

### Mandatory coding conventions

**Constructor injection only — never `@Autowired` on fields.**

```java
// Correct
public UserServiceImpl(UserRepository repo) { this.repo = repo; }

// Wrong
@Autowired private UserRepository repo;
```

- Define the interface in `service/api/` before writing the implementation.
- DTOs **must** be Java 17 `record` types. Never use POJOs with getters/setters for DTOs.
- Domain entities use `@Document`. Never `@Entity` or `@Table`.
- Business exceptions are custom classes caught only in `GlobalExceptionHandler` via `@ExceptionHandler`. Never `try/catch` inside controllers or services for flow control.
- HTTP errors must follow RFC 7807 (`application/problem+json`).
- `guardCondition` on `ControlFlow` is evaluated with **SpEL** (`SpelExpressionParser`). Never parse guard expressions manually.
- Zero hard-coded department names, policy names, or business role strings anywhere in the code.

### Workflow engine

`WorkflowEngineImpl` orchestrates everything. `advanceTo()` recurses depth-first through nodes until it hits a terminal (ACTION blocks, FLOW_FINAL/ACTIVITY_FINAL terminate). Each `NodeType` has exactly one `NodeEvaluator` bean — add new node types by implementing `NodeEvaluator`, not by modifying `WorkflowEngineImpl`.

**Known tech debt:** `ProcessInstance.currentNodeId` is a single `String`; during FORK it holds the last-written branch. Parallel state is tracked via `ActivityTask` records. If multi-branch visibility is needed, migrate to `Set<String> activeNodeIds`.

**Immutability guard:** before any edit to a `BusinessPolicy`, verify:
```java
if (processInstanceRepository.existsByBusinessPolicyIdAndStatus(id, InstanceStatus.ACTIVE))
    throw new PolicyInUseException("Policy has active instances.");
```

### Authentication

- Stateless JWT; `JwtAuthFilter extends OncePerRequestFilter` runs before `UsernamePasswordAuthenticationFilter`.
- JWT payload: `{ sub, role, departmentId, iat, exp }`.
- `departmentId` from the JWT is used by the engine to assign tasks — **no extra DB query needed**.
- Public endpoints: `POST /api/v1/auth/login`, `/auth/register`, `/auth/refresh`. WebSocket handshake (`/ws/**`) is also public at the HTTP layer; STOMP-level auth is enforced by `WebSocketAuthChannelInterceptor`.

### WebSocket (STOMP)

SockJS endpoint: `/ws`. App prefix: `/app`. Brokers: `/topic`, `/queue`.

| Topic | When |
|---|---|
| `/topic/department/{departmentId}` | New task assigned to department |
| `/queue/user/{userId}` | Task claimed by user |
| `/topic/process/{processInstanceId}` | Process state change |

**No polling** (`setInterval`) is allowed in any client.

### CORS

Allowed origins: `http://localhost:*` and the S3 frontend URL. Both `SecurityConfig` and `WebSocketConfig` must be kept in sync when adding origins.

---

## Frontend Architecture (frontend_web)

Angular 17+ with standalone components (no NgModules). All routes use lazy-loaded `loadComponent`.

**Key rules from `.github/copilot-instructions.md`:**
- Use `input()` / `output()` functions, not `@Input` / `@Output` decorators.
- Use `computed()` for derived state; signals for local state.
- `ChangeDetectionStrategy.OnPush` on every component.
- Native control flow: `@if`, `@for`, `@switch` — not `*ngIf`, `*ngFor`.
- No `ngClass` / `ngStyle` — use `class` / `style` bindings.
- `inject()` function in services — not constructor injection.
- `standalone: true` must NOT appear in decorators (it is the default in Angular v20+).
- All static images via `NgOptimizedImage`.
- Must pass AXE / WCAG AA accessibility checks.

**BPMN Designer:** `DesignerComponent` wraps `bpmn-js` (`BpmnModeler`). BPMN shape types are mapped to internal `NodeType` enum values in `mapNodeType()`. The designer serializes the canvas into `CreatePolicyRequest` / `UpdatePolicyRequest` before saving to the backend.

**formSchema:** ACTION nodes carry a `formSchema` JSON that drives dynamic form rendering. The `TaskCompleteComponent` renders fields generically — never hardcode form fields.

**Backend URL:** all services point to `http://34.237.109.152:3000/api/v1`. The `WebSocketService` connects to `http://34.237.109.152:3000/ws` via SockJS.

---

## Mobile Architecture (ibpms_mobile)

Flutter 3, state management via Riverpod (`flutter_riverpod` + `riverpod_generator`).

- `DioClient.create()` builds a singleton `Dio` instance with JWT auth interceptor. On 401/403 it clears storage automatically.
- `SecureStorageService` (flutter_secure_storage) stores access and refresh tokens.
- WebSocket via `stomp_dart_client`.
- On web (`kIsWeb`) the base URL is `localhost:3000`; on Android/iOS it is `34.237.109.152:3000`.

Feature structure: `lib/features/<feature>/screens/`, `lib/core/services/`, `lib/core/models/`.

---

## AI Service Architecture (ibpms_ia)

FastAPI microservice that calls Gemini to generate BPMN-compatible diagrams from natural language. Routes defined in `routers/diagram_router.py`. Request/response schemas in `models/`.
