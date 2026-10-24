# Understanding JWT Authentication and Authorization in This Project

This guide describes the actual classes used by this Doctor Appointment System.

## First understand the difference

- **Authentication = Who are you?** The user proves their identity with email and password at login.
- **Authorization = What are you allowed to do?** The application checks the role and, where required, whether the requested record belongs to the user.

A valid token alone does not make somebody an admin. A patient can have a valid token and still receive `403 Forbidden` when trying to create a doctor.

## The flow at a glance

```text
POST /api/auth/login
        |
        v
AuthController
        |
        v
AuthService -> AuthenticationManager
        |               |
        |               v
        |         DaoAuthenticationProvider
        |               |
        |               v
        |         CustomUserDetailsService -> AppUserRepository -> MySQL
        |               |
        |               v
        |         BCrypt compares submitted password to stored hash
        v
JwtService signs a JWT and returns it to the caller

Later request: Authorization: Bearer <token>
        |
        v
JwtAuthenticationFilter
  - reads Bearer token
  - verifies signature and expiry
  - loads account by token subject
  - creates Authentication with ROLE_* authority
        |
        v
SecurityConfig checks authentication
        |
        v
@PreAuthorize checks role
        |
        v
Service checks ownership/business rules
        |
        v
Repository reads/writes MySQL
```

## The classes to study, in this order

### 1. `entity/AppUser.java`

This table stores login accounts: email, BCrypt password hash, role, and enabled state. It implements Spring Security's `UserDetails` interface, so Spring Security can ask it for the username, password hash, role authorities, and enabled status.

`getAuthorities()` turns `PATIENT` into `ROLE_PATIENT`, `DOCTOR` into `ROLE_DOCTOR`, and `ADMIN` into `ROLE_ADMIN`. This is the form used by the role checks.

The password hash is never returned through JSON. Do not return `AppUser` from an API as a response DTO.

### 2. `controller/AuthController.java` and `auth/AuthService.java`

`POST /api/auth/register` creates a patient account. The caller cannot select a role; the service always assigns `PATIENT`. It hashes the submitted password through `PasswordEncoder` before saving the account, and creates the corresponding patient profile linked to that account.

`POST /api/auth/login` gives the email/password pair to `AuthenticationManager`. It does not compare raw passwords itself. Spring's `DaoAuthenticationProvider` loads the account through `CustomUserDetailsService` and validates the password against the BCrypt hash.

If login succeeds, `AuthService` asks `JwtService` for an access token and returns `accessToken`, `tokenType`, `expiresInSeconds`, email, and role.

### 3. `security/JwtService.java`

This class creates and validates JWTs. In this project the token subject is the account email. The JWT contains issued-at and expiration timestamps and is signed with the Base64-decoded `JWT_SECRET` key.

The token is **signed, not encrypted**. Someone who gets a JWT may read its claims, so do not put passwords or private health data into the token. Keep the signing key private and long enough. The code verifies the signature and expiration before trusting the token.

### 4. `security/JwtAuthenticationFilter.java`

This is the part that processes the token on incoming requests. The client sends:

```http
Authorization: Bearer eyJ...your-token...
```

The filter extracts the token, asks `JwtService` to validate it, loads the current account from the database, and then creates a Spring `Authentication` object with that account's authorities. It places the object in `SecurityContextHolder`.

Downstream controllers and Spring Security can now find the authenticated account. Controllers use `Authentication.getName()` to get the authenticated email. They do not need to accept an email or patient ID from the client as proof of identity.

### 5. `security/SecurityConfig.java`

This class configures the application as a stateless REST API:

- CSRF, form login, and HTTP Basic are disabled for this API-only setup.
- Session creation policy is `STATELESS`.
- Registration, login, and GET doctor listing/detail routes are public.
- Other routes require a valid authentication.
- `JwtAuthenticationFilter` runs before Spring's username/password authentication filter.
- `BCryptPasswordEncoder` hashes passwords and validates login attempts.
- `@EnableMethodSecurity` enables `@PreAuthorize` on controller methods.
- Missing/invalid login state returns `401`; authenticated but forbidden requests return `403`.

### 6. `@PreAuthorize` in controllers

Examples:

- `@PreAuthorize("hasRole('ADMIN')")` permits only an account with `ROLE_ADMIN`.
- `@PreAuthorize("hasRole('PATIENT')")` permits only a patient account.
- `@PreAuthorize("hasAnyRole('ADMIN', 'PATIENT', 'DOCTOR')")` requires one of those authenticated roles; the service still verifies ownership.

These are authorization checks. They do not generate JWTs and do not validate passwords.

### 7. Ownership checks in `AppointmentService`

A role alone is not always enough. All patients have `ROLE_PATIENT`, but patient A must not read patient B's appointment. `getAppointmentByIdForCurrentUser()` checks the linked account on the appointment's patient or doctor profile. Admins can view the record; a patient can view only their own; a doctor can view only an appointment assigned to their own doctor profile.

When booking, `createAppointment()` obtains the account from the authenticated email, finds that account's patient profile, and sets the appointment's patient from the database. The request contains `doctorId`, `appointmentDateTime`, and `reason`—not `patientId`. This prevents a caller from changing the patient ID in JSON to book as another person.

## Try these calls in Postman

### 1. Register a patient — no token

`POST {{baseUrl}}/api/auth/register`

```json
{
  "name": "Test Patient",
  "email": "patient1@example.com",
  "phone": "9876543210",
  "password": "PatientPass123!"
}
```

Expected: `201 Created` with a message. Registration always assigns `PATIENT`.

### 2. Login — no token

`POST {{baseUrl}}/api/auth/login`

```json
{
  "email": "patient1@example.com",
  "password": "PatientPass123!"
}
```

Expected: `200 OK` and a JSON `accessToken`. Copy that token or allow the collection's login test script to save it into `token`.

### 3. Call your own profile — patient token required

`GET {{baseUrl}}/api/patients/me`

Authorization tab: **Bearer Token**, token `{{token}}`.

Expected: `200 OK` with the currently authenticated patient's profile.

### 4. Call an admin-only API using the patient token

`POST {{baseUrl}}/api/doctors`

Expected: `403 Forbidden`. You are authenticated, but your role is PATIENT, not ADMIN.

### 5. Call an authenticated endpoint without a token

`GET {{baseUrl}}/api/patients`

Expected: `401 Unauthorized` because the request is not authenticated. This endpoint also requires ADMIN when a token is present.

### 6. Try booking as a patient

`POST {{baseUrl}}/api/appointments`

```json
{
  "doctorId": 1,
  "appointmentDateTime": "2026-11-20T10:30:00",
  "reason": "Regular check-up"
}
```

Expected: `201 Created` if doctor ID 1 exists and that future time is available. The backend determines the patient from the token-linked account.

## 3 key points to remember

1. A JWT is proof of an authenticated account, not permission for every endpoint.
2. `SecurityConfig` handles request-level rules; `@PreAuthorize` checks roles; service code checks record ownership and business rules.
3. Never trust a client-supplied `patientId` or `role` as proof of identity. Derive the user from authenticated server-side context.

## Practice question

**A patient has a valid JWT. Why should `POST /api/doctors` still return `403 Forbidden`?**

**Quick answer:** Authentication succeeded, but authorization failed. The authenticated user's authority is `ROLE_PATIENT`; the endpoint requires `ROLE_ADMIN`.
