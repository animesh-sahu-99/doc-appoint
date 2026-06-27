# MediBook — SOLID Principles & Design-Pattern Map

> Companion to [`ARCHITECTURE.md`](./ARCHITECTURE.md). Where `ARCHITECTURE.md` explains **what the system is**,
> this document explains **how well it's built** against SOLID + classic engineering principles and the GoF
> design-pattern catalog — and **what to change, where, and why**.
>
> Scope: the **backend** (`com.clinic.doc_appointment`, Spring Boot 3.5.10 / Java 17). The React-Native client is
> out of scope (different paradigm).

## How to read this document

This is a **pragmatic** map, not a pattern-checklist. The guiding rule is **KISS**: *apply a pattern where it pays
for itself; document why we skip the rest.* Forcing all 16 GoF patterns into a small CRUD backend would itself
violate the principles we're trying to honor.

**Legend**

| Mark | Meaning |
|---|---|
| ✅ | Already satisfied — the framework or existing code provides it; do not reinvent |
| 🔨 | Apply — genuine, high-value improvement; designed below |
| 🕗 | Defer — valuable later, premature now |
| ⏭️ | Skip — would be over-engineering here (with the reason) |

Line references were verified against the tree at the time of writing; re-confirm before acting on a specific line.

---

## 1. Engineering principles → codebase

| Principle | Strength today | Gap → recommended action |
|---|---|---|
| **Coupling & Cohesion** | Constructor DI everywhere (`@RequiredArgsConstructor`); `FileStorageService` is an abstraction; `DoctorSpecification` isolates query logic | `AppointmentService` is coupled to notification delivery (7 inline calls); `NotificationService`→`FcmPushService`→Firebase SDK is a hard dependency chain → **Observer** + **Adapter** |
| **DRY** | `findAppointmentById`/`findPatientById` helpers; `mapToResponseList` in `SlotService`; `ApiResponse` factory methods | `buildFullName`, `findById().orElseThrow(...)` (~repeated across services), and `.stream().map(this::mapToResponse).collect(toList())` are duplicated → **shared utils + a mapper layer** |
| **KISS** | Optimistic-lock + `@Retryable` solve concurrency without a custom scheduler | Keep it simple: **skip** Abstract Factory / Command / custom Iterator / one-class-per-state |
| **Clean code** | `@Slf4j` logging; clear method names; rich custom-exception hierarchy | Role handled as magic strings; two `RuntimeException`s in `NotificationService` (lines 75, 78) bypass the exception hierarchy → **`Role` enum** + (later) typed exceptions |
| **Naming conventions** | Consistent `XService` / `XController` / `XResponse` / `XRepository` | Solid overall. One smell: the ID prefix `DOC-` is used by **both** `Doctor` and `AppointmentDocument` → an `IdPrefix` enum makes the clash explicit (don't change persisted values) |
| **Modularity** | Package-by-layer; `specification/`, `security/`, `util/` cleanly separated | Encryption is fused into the storage impl → **Decorator**; add focused packages: `mapper/`, `event/`, `listener/`, `validation/`, `service/push/`, `service/payment/`, `service/registration/`, `domain/state/` |
| **Reusability** | `DoctorSpecification`, `ApiResponse<T>`, `EntityMapper<E,R>` (planned) | Each service hand-rolls a private `mapToResponse` → a shared, testable mapper layer |

### SOLID, specifically

- **S**ingle Responsibility — `AppointmentService` currently does state-guarding **and** persistence **and**
  notification fan-out. `ReviewService` does reviews **and** doctor-rating rollups. `LocalFileStorageServiceImpl`
  does IO **and** crypto. Each is split below.
- **O**pen/Closed — adding a payment method, a notification channel, or a slot-validation rule today means editing
  a method body. **Strategy / Adapter / Composite** turn those into "add a class."
- **L**iskov — the planned `FileStorageService` decorator must honor the exact contract (same return semantics,
  same on-disk format) so callers can't tell which implementation they hold.
- **I**nterface Segregation — keep the new ports small: `PushNotificationProvider.send(...)`, `Validator<T>.validate(...)`.
- **D**ependency Inversion — `NotificationService` should depend on a `PushNotificationProvider` port, not the
  concrete `FcmPushService`/Firebase SDK.

---

## 2. Design-pattern catalog → verdict

### 2.1 Creational

| Pattern | Verdict | Where / why |
|---|---|---|
| **Factory Method** | 🔨 apply (light) | `generateAppointmentNumber()` ([`AppointmentService.java:356`](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)) already is one. Add `IdGenerator.withPrefix(prefix)` + an `IdPrefix` enum to centralize the per-entity prefixes scattered across `@PrePersist` (see §4). |
| **Abstract Factory** | ⏭️ skip | There is no *family* of related products across interchangeable variants — just two flat entity types created once each. A `DoctorAccountFactory`/`PatientAccountFactory` would add indirection without removing the real duplication. Use **Template Method** + a shared `RegistrationValidator` instead. |
| **Singleton** | ✅ satisfied | Every `@Service`/`@Component`/`@Configuration` bean is a container-managed singleton. A hand-rolled `static INSTANCE` would be an anti-pattern here. |
| **Builder** | ✅ + 🔨 standardize | Lombok `@Builder` is already used (`PatientResponse`, `ReviewResponse`, `DocumentResponse`, `NotificationResponse`, `AuthResponse`, `ApiResponse`). Three responses still use setter-chaining — `DoctorResponse`, `AppointmentResponse`, `SlotResponse`. Add `@Builder` to them for one consistent construction style. |
| **Prototype** | ⏭️ skip | `createBulkSlots` builds fresh `DoctorAvailability` objects in a loop ([`SlotService.java:101-131`](src/main/java/com/clinic/doc_appointment/service/SlotService.java)); cloning a template adds nothing and risks shared-reference bugs. |

### 2.2 Structural

| Pattern | Verdict | Where / why |
|---|---|---|
| **Adapter** | 🔨 apply | `FcmPushService` imports `com.google.firebase.*` directly and `NotificationService` calls it concretely ([`NotificationService.java:115`](src/main/java/com/clinic/doc_appointment/service/NotificationService.java)). Wrap Firebase behind a `PushNotificationProvider` port; the Firebase SDK then lives behind one adapter (testable, swappable). |
| **Decorator** | 🔨 apply | `LocalFileStorageServiceImpl` does both raw IO **and** AES crypto ([lines 51-59, 77-80](src/main/java/com/clinic/doc_appointment/service/document/LocalFileStorageServiceImpl.java)). Split into a plain storer + an `EncryptingFileStorageDecorator` (`@Primary`) implementing the same `FileStorageService`. |
| **Facade** | ✅ + 🕗 defer | Services already act as facades (e.g. `bookAppointment` orchestrates patient+slot+appointment+notification). A dedicated `BookingFacade` would coordinate a **no-op payment** today (`PaymentService` is an empty stub) → defer until payments are real, then it earns its place (charge → create → notify with compensation). |
| **Proxy** | ✅ satisfied | `@Transactional` and `@Retryable` are Spring AOP dynamic proxies. Optional illustrative add: `@Cacheable` on `getAllSpecializations()` ([`DoctorService.java:116`](src/main/java/com/clinic/doc_appointment/service/DoctorService.java)) — the one safely-cacheable, immutable-source read. |
| **Composite** | 🔨 apply (scoped) | Slot-creation and booking preconditions are inline `if` blocks. Introduce `Validator<T>` + `CompositeValidator<T>` so rules compose (and new rules = new classes). |

### 2.3 Behavioral

| Pattern | Verdict | Where / why |
|---|---|---|
| **Strategy** | ✅ + 🔨 apply | Already present: `FileStorageService`, `DoctorSpecification`. **Apply** for payments — behavior genuinely varies per `PaymentMethod` — to flesh out the empty `PaymentService`. **Skip** for review-sort: `getDoctorReviews(sort,…)` maps cleanly to a Spring Data `Sort`; a strategy hierarchy would be more code for less. |
| **Observer** | 🔨 apply (highest value) | Decouple notifications from `AppointmentService` via `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`. Also fixes a latent bug (see §4, Bug #1). |
| **Command** | ⏭️ skip | No undo/redo, queueing, audit-replay, or batch-macro requirement. On top of a transition validator it is pure indirection. Revisit only if a bulk-operations or audit/outbox need appears. |
| **State** | 🔨 apply (KISS variant) | Centralize the 5 scattered status guards into an **enum transition-table** `AppointmentTransitionValidator`. A full GoF State (one class per status) is over-engineering for 5 stateless transitions and fights the JPA enum column. |
| **Template Method** | ✅ + 🔨 apply | `AppointmentEntityListener` is already a callback template. **Apply** an `AbstractRegistrationService` to remove the doctor/patient registration duplication in `AuthService`. |
| **Iterator** | ✅ satisfied | Java collections + Spring Data `Page`/`Pageable` already provide iteration. Optional readability tidy: express the `createBulkSlots` time-walk as `Stream.iterate(...).takeWhile(...)` — not a deliverable. |

---

## 3. Design notes for the items we apply (🔨 / 🕗)

Each note lists **new types / packages**, **files changed**, and a **one-line risk**.

### Observer — event-driven notifications  *(top value)*
- **New:** `event/AppointmentBookedEvent`, `event/AppointmentStatusChangedEvent(from, to)`;
  `listener/AppointmentNotificationListener` annotated `@TransactionalEventListener(phase = AFTER_COMMIT)`,
  which calls the **unchanged** `NotificationService.sendNotification(...)`.
- **Changed:** `AppointmentService` — replace the 7 inline `sendNotification(...)` blocks (booking ×2, confirm,
  cancel ×2, complete, notes) with one `eventPublisher.publishEvent(...)` per mutation, right after `save()`.
- **Risk:** the event must carry **immutable primitives captured at publish time** (ids, names, slot date/time),
  never the managed entity (it may be detached by after-commit). The recipient/type/message table in §5 must be
  reproduced exactly — including the two traps: `complete` uses `GENERAL_ALERT` (not `APPOINTMENT_UPDATE`), and
  `markNoShow` sends **nothing**.

### State — `AppointmentTransitionValidator`
- **New:** `domain/state/AppointmentTransitionValidator` (`@Component`, stateless) holding
  `Map<AppointmentStatus, Set<AppointmentStatus>>` plus a separate `canEditNotes(status)` predicate.
- **Changed:** `AppointmentService` — replace the inline guards with `validator.validateTransition(from, to)`.
- **Transition table — derived from the *current* code, not idealized:**

  | From → allowed To | Source guard |
  |---|---|
  | `PENDING` → `CONFIRMED`, `CANCELLED`, `NO_SHOW` | confirm requires PENDING ([:197](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)) |
  | `CONFIRMED` → `COMPLETED`, `CANCELLED`, `NO_SHOW` | complete requires CONFIRMED ([:272](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)) |
  | `COMPLETED` → ∅ | cancel blocked when COMPLETED ([:232](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)) |
  | `CANCELLED` → ∅ | cancel blocked when already CANCELLED ([:228](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)) |
  | `NO_SHOW` → `CANCELLED` (currently legal) | markNoShow only blocks CANCELLED/COMPLETED ([:336](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)) |

  Notes editing is **not** a transition — keep it as `canEditNotes()` (blocked only for CANCELLED/NO_SHOW, [:303](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)).
- **Risk:** preserve the **specific** `InvalidStateException` messages ("Only pending appointments can be
  confirmed", etc.) — overload `validateTransition(from, to, message)` if needed. Keep status mutation on the
  freshly-loaded managed entity so the `@PostUpdate` slot-freeing still fires exactly once.

### Template Method — registration
- **New:** `service/registration/AbstractRegistrationService<Req, Entity>` with a `final register(req)` template
  and seams: `validateDuplicates → buildEntity → persist → toPrincipal → toAuthResponse`. Subclasses
  `DoctorRegistrationService`, `PatientRegistrationService`.
- **Changed:** `AuthService.registerDoctor/registerPatient` delegate to the subclasses; **login methods stay** (they
  differ genuinely). Controllers/DTOs unchanged.
- **Risk:** preserve the patient "email checked only when present" branch ([`AuthService.java:113`](src/main/java/com/clinic/doc_appointment/service/AuthService.java)) and the exact duplicate-check messages/order; emit the same `ROLE_*` principal/`AuthResponse.role`.

### Adapter — push provider port
- **New:** `service/push/PushNotificationProvider` (port), `PushMessage` record, `FcmPushAdapter` (the only class
  importing `com.google.firebase.*`).
- **Changed:** rename/relocate `FcmPushService` → `FcmPushAdapter`; `NotificationService` depends on the port.
- **Risk:** keep Firebase behavior identical — init guard (skip when no `FirebaseApp`), per-token try/catch,
  `UNREGISTERED` → deactivate token, `medibook_channel`/HIGH priority/sound, `putData("type"/"relatedEntityId")`.

### Decorator — encrypted file storage
- **New:** `LocalFileStorageService` (plain IO) + `EncryptingFileStorageDecorator` (`@Primary`, wraps the plain
  storer with `CryptoUtils` streams). Widen the SPI with `store(InputStream, originalName)` (keep the
  `MultipartFile` method as a default).
- **Changed:** `DocumentService` is untouched (still depends on `FileStorageService`).
- **Risk (medical data at rest):** the on-disk format must stay **byte-identical** — AES, prepended IV, `.enc`
  suffix, same `CryptoUtils` + `${file.encryption.secret}` — or existing uploads become unreadable.

### Composite — validation
- **New:** `validation/Validator<T>` (leaf, throws a domain exception), `validation/CompositeValidator<T>`, and
  leaves: `SlotDateNotInPastValidator`, `SlotTimeOrderValidator`, `SlotOverlapValidator` (slot-creation) and a
  `SlotAvailableValidator` (booking precondition).
- **Changed:** `SlotService.createSlot` ([:44-63](src/main/java/com/clinic/doc_appointment/service/SlotService.java)) and `AppointmentService.bookAppointment` ([:73-75](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)).
- **Risk:** preserve exception **types**, **messages**, **short-circuit order** (date → time → overlap), and the
  crucial distinction that `createSlot` **throws** on overlap while `createBulkSlots` **skips** overlapping windows
  ([:117](src/main/java/com/clinic/doc_appointment/service/SlotService.java)). Do not unify those two.

### Strategy — payment scaffold
- **New:** `service/payment/PaymentStrategy` (+ `PaymentMethod getMethod()`), one stub per method
  (CARD/UPI/NET_BANKING/CASH/WALLET), a `PaymentStrategyFactory` built from an injected `List<PaymentStrategy>`.
- **Changed:** flesh out the empty `PaymentService`, `PaymentController`, `PaymentRequest` (amount from
  `doctor.getConsultationFee()`, create a `PENDING` `Payment` row). **No real gateway** in this pass.
- **Risk:** keep payment a **separate endpoint** — do not wire it into `bookAppointment` (protects the delicate
  retry/optimistic-lock path); booking still creates no `Payment` until consciously decided.

### Factory / Builder / utils  *(foundations)*
- **New:** `util/NameUtils.fullName(first, last)`, `util/EntityFinder.findOrThrow(repo, id, msg)`,
  `util/IdGenerator` + `enums/IdPrefix`, `enums/Role { DOCTOR, PATIENT }` (with `authority()` → `"ROLE_"+name()`),
  `mapper/EntityMapper<E,R>` + 7 concrete mappers.
- **Mapper decision: manual, not MapStruct.** Lombok is already the annotation processor; the mappings flatten and
  enrich (e.g. `AppointmentMapper` does a conditional `ReviewRepository` lookup when status==COMPLETED,
  [`AppointmentService.java:395`](src/main/java/com/clinic/doc_appointment/service/AppointmentService.java)). Hand mappers are lower-risk and fully testable.
- **Risk:** `Role` is for cleaning **Java** code only — the `@PreAuthorize("hasRole('PATIENT')")` SpEL literals in
  `ReviewController` ([:26, :37](src/main/java/com/clinic/doc_appointment/controller/ReviewController.java)) stay strings, and `UserPrincipal` must keep emitting `SimpleGrantedAuthority("ROLE_…")` ([:25](src/main/java/com/clinic/doc_appointment/security/UserPrincipal.java)).

---

## 4. Refactoring roadmap — ✅ implemented

> All five phases below have been implemented and verified: `./mvnw test` → **31/31 green** (30 focused unit
> tests + the `@SpringBootTest` context-load that boots the whole app against PostgreSQL 14), 134 sources compile.
> Both latent bugs in §6 have since been fixed (after-commit notifications; unified cross-table registration check).
> Note: running the suite via surefire needs `-Duser.timezone=Asia/Kolkata` (this Windows JVM otherwise reports the
> legacy alias `Asia/Calcutta`, which the PG server rejects); `spring-boot:run` already pins it in `pom.xml`.

| Phase | Content | Focused tests |
|---|---|---|
| **1 — Foundations** | `NameUtils`, `EntityFinder`, `Role` + `IdPrefix`/`IdGenerator` | `NameUtils` null/blank cases; `Role.DOCTOR.authority() == "ROLE_DOCTOR"`; `getAuthorities()` contains it |
| **2 — Mapper + Builder** | `EntityMapper<E,R>` + per-aggregate mappers; add `@Builder` to the 3 setter DTOs | each mapper's output equals the current `mapToResponse` for representative entities |
| **3 — Structural** | Decorator (storage), Adapter (push), Composite (validation) | encryption store→load round-trip; validator order + exception types; adapter `send` invoked with expected `PushMessage` |
| **4 — Behavioral** | Observer → State (transition validator) → Template Method (registration) | after-commit listener **discards** events on rollback; transition truth-table; registration duplicate-check messages |
| **5 — Payments scaffold** | `PaymentStrategy` set + `PaymentService`/`Controller`/`Request` (no gateway) | strategy resolves by `PaymentMethod`; `Payment` row created `PENDING` |

---

## 5. Behavior-preservation invariants  *(binding on the later implementation)*

- **Auth strings unchanged:** `UserPrincipal` authorities stay `ROLE_DOCTOR`/`ROLE_PATIENT`
  ([`UserPrincipal.java:25`](src/main/java/com/clinic/doc_appointment/security/UserPrincipal.java),
  [`CustomUserDetailsService.java:28,35`](src/main/java/com/clinic/doc_appointment/security/CustomUserDetailsService.java)); JWT `role` claim and `AuthResponse.role` stay `ROLE_*`;
  `AppointmentDocument.uploaderRole` stays `DOCTOR`/`PATIENT`. These gate `@PreAuthorize hasRole(...)`.
- **ID prefixes byte-identical** (an `IdPrefix` enum only *names* them):
  `APPOINTMENT-`, `PAT-`, `SLOT-`, `REVIEW-`, `PAYMENT-`, `NOT-`, and the **shared `DOC-`** used by both
  `Doctor` ([:38](src/main/java/com/clinic/doc_appointment/entity/Doctor.java)) and `AppointmentDocument`
  ([:31](src/main/java/com/clinic/doc_appointment/entity/AppointmentDocument.java)). Do **not** "fix" the clash — persisted IDs are immutable.
- **File-at-rest format identical** (AES, `.enc`, prepended IV, same `CryptoUtils`/`${file.encryption.secret}`).
- **FCM behavior identical** (init guard, per-token try/catch, `UNREGISTERED` → deactivate, channel/priority/sound, data keys).
- **Concurrency machinery untouched:** `@Version` ([`Appointment.java:38`](src/main/java/com/clinic/doc_appointment/entity/Appointment.java)) + `@Retryable`/`@Recover` + `AppointmentEntityListener`
  (`previousStatus` via `@PostLoad` → `@PostUpdate`/`@PreRemove`). Status mutation stays on the freshly-loaded
  managed entity so the slot is freed exactly once.
- **Notifications identical:** recipients/types/messages per the table below.
- **Exceptions:** all `ResourceNotFoundException` messages unchanged; the two `RuntimeException`s in
  `NotificationService` ([:75, :78](src/main/java/com/clinic/doc_appointment/service/NotificationService.java)) are left as-is in a behavior-preserving pass (changing them would change the
  HTTP status produced by `GlobalExceptionHandler`).

**Notification preservation table**

| Trigger | Recipients | Type | Note |
|---|---|---|---|
| book | patient + doctor | `APPOINTMENT_UPDATE` | two messages |
| confirm | patient | `APPOINTMENT_UPDATE` | |
| cancel | patient + doctor | `APPOINTMENT_UPDATE` | two messages |
| complete | patient | **`GENERAL_ALERT`** | trap: not `APPOINTMENT_UPDATE` |
| update notes | patient | `APPOINTMENT_UPDATE` | |
| no-show | — | — | **trap: sends nothing today** |

---

## 6. Documented latent bugs  *(both fixed)*

1. **Notifications fire for rolled-back booking retries.** ✅ **Fixed.** `bookAppointment` is `@Retryable`; it
   previously sent notifications **inside** the transaction, so a losing optimistic-lock attempt could push
   WebSocket/FCM messages for a booking that never committed. Notifications now run from
   [`AppointmentNotificationListener`](src/main/java/com/clinic/doc_appointment/listener/AppointmentNotificationListener.java)
   via `@TransactionalEventListener(AFTER_COMMIT)`, so events from a rolled-back/retried attempt are discarded
   (a notification failure also no longer rolls back the appointment). Verified by `AppointmentNotificationListenerTxTest`.
2. **Inconsistent duplicate-checking across registration paths.** ✅ **Fixed.** Previously the `/auth/*` paths did a
   cross-table email check while `/doctors/register` and `/patients/register` checked only their own table (and the
   doctor path used a different phone message), so a patient could take an email already owned by a doctor — a login
   ambiguity, since `CustomUserDetailsService` resolves logins across **both** tables. All four paths now delegate to a
   single [`RegistrationValidator`](src/main/java/com/clinic/doc_appointment/service/registration/RegistrationValidator.java):
   email must be unique across both tables (optional for patients), phone is unique within the role's table, and the
   message is aligned to `"Phone number already registered"`. Verified by `RegistrationValidatorTest`.

---

## 7. Reuse — extend these, don't reinvent

- **`DoctorSpecification`** — the model for composable queries (Specification pattern).
- **`FileStorageService`** — the contract the Decorator extends (don't change the interface).
- **`ApiResponse<T>` + `GlobalExceptionHandler`** — keep as the response/error spine.
- **`@Retryable`/`@Version`** optimistic-lock machinery — preserve verbatim.
- **`findAppointmentById`/`findPatientById`/`mapToResponseList`** — existing precedents the new `EntityFinder` and
  `EntityMapper` generalize.

## 8. What we deliberately did NOT do (KISS ledger)

Abstract Factory, Prototype, Command, custom Iterator, hand-rolled Singleton, a one-class-per-state machine, a
`BookingFacade`, and MapStruct were all considered and **declined** for this codebase's size — see the rationale in
the verdict tables above. Honoring KISS here means *fewer, well-placed patterns*, not more.
