# Doctor Appointment System — CRUD + JWT Security

A beginner-friendly REST API project built with Java 21, Spring Boot 3.5, Spring Data JPA, MySQL, Spring Security, and JWT. Use Postman to test the API. It intentionally has no JUnit, Mockito, Swagger, or automated test dependencies.

## What changed in this version?

- JWT login and patient registration.
- Passwords stored using BCrypt hashes.
- Stateless Spring Security (no server-side login session).
- Roles: `ADMIN`, `DOCTOR`, and `PATIENT`.
- Public doctor listing/detail endpoints; doctor create/update/delete require `ADMIN`.
- Patients can view/update their own profile, book appointments, and list their own appointments.
- Doctors can list appointments assigned to their doctor profile.
- Appointment detail reads check that the authenticated user is the patient or assigned doctor, unless they are an admin.
- Appointment booking derives the patient from the authenticated account. The request no longer accepts a `patientId` from the caller.
- Admin account is initialized from environment variables instead of hardcoded credentials.

## 1. Requirements

- Java 21
- MySQL 8
- Postman
- Included Maven wrapper (`mvnw` / `mvnw.cmd`)

## 2. Create the database

Run in MySQL:

```sql
CREATE DATABASE doctor_appointment_db;
```

## 3. Configure environment variables

Spring Boot does not automatically load `.env` files. Set these values in your terminal or IDE run configuration.

### Windows PowerShell example

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/doctor_appointment_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_mysql_password"

# Generate a random 32-byte (or longer) key and Base64-encode it.
$key = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($key)
$env:JWT_SECRET = [Convert]::ToBase64String($key)

$env:ADMIN_EMAIL = "admin@example.com"
$env:ADMIN_PASSWORD = "replace_with_a_strong_password"
```

Set `JWT_SECRET`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD` in the same terminal session you use to start Spring Boot. Choose a private admin password yourself. Never commit `.env` files or real secrets.

### Linux/macOS example

```bash
export DB_URL='jdbc:mysql://localhost:3306/doctor_appointment_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USERNAME='root'
export DB_PASSWORD='your_mysql_password'
export JWT_SECRET="$(openssl rand -base64 32)"
export ADMIN_EMAIL='admin@example.com'
export ADMIN_PASSWORD='replace_with_a_strong_password'
```

`JWT_SECRET` must be Base64-encoded random bytes and at least 32 bytes before encoding. If it is missing or invalid, JWT initialization will fail; do not replace it with a public or fixed sample key. The default token lifetime is 60 minutes; optionally set `JWT_EXPIRATION_MS`.

On startup, the app creates an admin only if both admin variables are configured and an account for that email does not already exist. If both are omitted, other features can start but there is no admin account until you configure one.

## 4. Run the project

From the project folder:

**Windows**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux/macOS**

```bash
./mvnw spring-boot:run
```

Base URL: `http://localhost:8080`

Local learning configuration uses `spring.jpa.hibernate.ddl-auto=update`. Do not use that schema strategy as a production migration plan.

## 5. JWT in simple language

Authentication answers: **“Who are you?”** The login endpoint checks your email and password. If correct, the backend creates a signed JWT access token.

Authorization answers: **“What are you allowed to do?”** The token identifies the account; Spring Security loads the account's current role from MySQL and checks the endpoint's permission rules.

For a protected endpoint, send this HTTP header:

```http
Authorization: Bearer YOUR_ACCESS_TOKEN
```

The JWT is signed, not encrypted. Do not put passwords, sensitive medical information, or other private data inside it. This project keeps the email as the JWT subject and checks expiry/signature before trusting it. Changing a user's role in the database takes effect when a request loads that account; already-issued tokens are not a substitute for revocation controls in a larger production system.

## 6. Suggested Postman workflow

Import `postman/doctor-appointment-system.postman_collection.json`. The collection uses a `token` variable; its login requests save the returned access token for subsequent requests. Log in as the relevant account before calling role-protected endpoints because a single token variable can only represent one logged-in role at a time.

### A. Log in as admin

`POST /api/auth/login`

```json
{
  "email": "admin@example.com",
  "password": "your_configured_admin_password"
}
```

Copying or saving the returned `accessToken` into the collection's `token` variable allows admin requests. The collection login request can save it automatically.

### B. Create a doctor account (admin token required)

`POST /api/doctors`

```json
{
  "name": "Dr. Ravi Kumar",
  "email": "ravi.doctor@example.com",
  "password": "DoctorPass123!",
  "specialization": "Cardiology",
  "qualification": "MBBS, MD",
  "experienceYears": 8,
  "consultationFee": 600.00
}
```

A doctor account is created together with the doctor profile. The rating starts at zero and is not set from this request.

### C. Register a patient (public)

`POST /api/auth/register`

```json
{
  "name": "Bhargava",
  "email": "bhargava@example.com",
  "phone": "9876543210",
  "password": "PatientPass123!"
}
```

Registration always creates the `PATIENT` role. It does not accept a role field, so a public caller cannot self-register as an admin or doctor. Then log in with `POST /api/auth/login` using the patient credentials.

### D. Browse doctors and book an appointment

Public endpoints:

- `GET /api/doctors`
- `GET /api/doctors/{id}`

Login as a patient and call `POST /api/appointments` with the patient token:

```json
{
  "doctorId": 1,
  "appointmentDateTime": "2026-11-20T10:30:00",
  "reason": "Regular check-up"
}
```

Notice there is no `patientId`: the backend derives the patient from the authenticated user. The requested time must be in the future and cannot be already booked for that doctor.

### E. See appointments

- `GET /api/appointments/mine` — patient token; returns only that patient's appointments.
- `GET /api/appointments/doctor` — doctor token; returns appointments for that doctor's profile.
- `GET /api/appointments/{id}` — admin, assigned doctor, or owning patient only.
- `GET /api/appointments` — admin only.

Switch the Postman `token` variable by logging in as the relevant role.

## 7. Endpoint access summary

| Endpoint | Permission |
|---|---|
| `POST /api/auth/register` | Public; creates PATIENT only |
| `POST /api/auth/login` | Public |
| `GET /api/doctors`, `GET /api/doctors/{id}` | Public |
| `POST /api/doctors`, `PUT /api/doctors/{id}`, `DELETE /api/doctors/{id}` | ADMIN |
| `GET /api/patients`, `GET /api/patients/{id}` | ADMIN |
| `GET /api/patients/me`, `PUT /api/patients/me` | PATIENT; own profile |
| `POST /api/patients`, `PUT /api/patients/{id}`, `DELETE /api/patients/{id}` | ADMIN |
| `GET /api/appointments` | ADMIN |
| `GET /api/appointments/mine` | PATIENT; own appointments |
| `GET /api/appointments/doctor` | DOCTOR; own schedule |
| `GET /api/appointments/{id}` | ADMIN, owning PATIENT, or assigned DOCTOR |
| `POST /api/appointments` | PATIENT; books for authenticated patient |
| `PUT /api/appointments/{id}`, `DELETE /api/appointments/{id}` | ADMIN |

### Common responses

- `200 OK`: read or successful update.
- `201 Created`: registration, doctor creation, patient creation, or appointment booking.
- `400 Bad Request`: invalid input or unavailable appointment time.
- `401 Unauthorized`: missing, invalid, or expired token; or invalid login credentials.
- `403 Forbidden`: token is valid but the role or resource ownership is not permitted.
- `404 Not Found`: requested resource does not exist.
- `409 Conflict`: duplicate account email or database relationship prevents a change.

## 8. How JWT authentication works in this codebase

1. `AuthController` receives login/register HTTP requests.
2. `AuthService` validates input, hashes new passwords using `PasswordEncoder`, and uses `AuthenticationManager` to verify login credentials.
3. `AppUser` represents an account and implements Spring Security's `UserDetails`. Its role becomes an authority such as `ROLE_PATIENT`.
4. `JwtService` creates signed tokens and validates the token signature, subject, and expiration.
5. `JwtAuthenticationFilter` runs for each request. It reads `Authorization: Bearer ...`, validates the token, loads the account, and puts an authenticated object into `SecurityContextHolder`.
6. `SecurityConfig` permits login/registration and public doctor reads, and requires authentication for other routes.
7. `@PreAuthorize` checks the role. Service logic additionally verifies ownership for appointment details and derives patient identity from the authenticated account when booking.

JWT is stateless: the server does not create an HTTP session for each login. A signed JWT is not encrypted; protect it as you would a credential.

For a source-level walkthrough and manual security exercises, read [`SECURITY_WALKTHROUGH.md`](SECURITY_WALKTHROUGH.md).

## 9. Important limitations for this learning project

- This is CRUD plus basic JWT access control, not a production-ready medical platform.
- The existing simple appointment duplicate check is not a full slot-calendar system and is not sufficient by itself for robust concurrent booking. A later iteration should add database-backed availability slots and locking/constraints.
- Existing legacy doctor/patient rows from before authentication may have no linked login account. Create linked doctor accounts through the admin endpoint and use public patient registration for account-linked patient profiles.
- There is no refresh-token or logout/revocation workflow. A stateless access token remains usable until expiration unless additional revocation mechanisms are added.
- `ddl-auto=update` is for local learning only. Production needs controlled database migrations, HTTPS, secrets management, auditing, rate limits, and a broader security review.
