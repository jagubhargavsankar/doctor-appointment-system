# CarePoint — Doctor Appointment Management System

**A secure, role-based healthcare appointment REST API with Spring Boot and AI-powered assistance.**


CarePoint is a backend project for managing doctor profiles, appointment availability, patient bookings, and role-specific workflows. It demonstrates layered Spring Boot development, REST API design, stateless JWT authentication, database persistence, authorization, and Spring AI integrations.


## Table of contents

- [Highlights](#highlights)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Roles and capabilities](#roles-and-capabilities)
- [API reference](#api-reference)
- [Getting started](#getting-started)
- [Environment configuration](#environment-configuration)
- [Run the application](#run-the-application)
- [Try it with Postman](#try-it-with-postman)
- [AI implementation](#ai-implementation)
- [Project structure](#project-structure)
- [Security notes](#security-notes)
- [Known limitations](#known-limitations)
- [Future improvements](#future-improvements)
- [Author](#author)

## Highlights

- **Authentication and authorization:** JWT-based stateless authentication, BCrypt password hashing, and role checks for `ADMIN`, `DOCTOR`, and `PATIENT`.
- **Doctor directory:** Browse doctor profiles with pagination, view a doctor, and search for recommendations based on a described problem or specialization.
- **Appointment availability:** Doctors can manage their own future slots; administrators can manage slots for doctors.
- **Appointment lifecycle:** Book, view, cancel, confirm, and complete appointments.
- **Role-aware access:** Patient and doctor appointment reads are scoped by the authenticated account, with administrator operations available through protected endpoints.
- **Healthcare Q&A with RAG:** The app loads `healthcare-guide.txt`, chunks the document, generates local embeddings, and retrieves relevant content for healthcare-related questions.
- **AI appointment assistants:** Separate patient, doctor, and administrator endpoints connect natural-language requests to backend tools.
- **Conversation memory:** Spring AI chat memory is configured with JDBC-backed persistence.
- **Manual API testing:** A Postman collection is included in `postman/`.

## Technology stack

| Technology | Purpose |
|---|---|
| Java 21 | Application language |
| Spring Boot 3.5.x | Application framework |
| Spring Web | REST endpoints |
| Spring Security | Authentication and endpoint authorization |
| JWT (JJWT) | Signed bearer access tokens |
| BCrypt | Password hashing |
| Spring Data JPA / Hibernate | Object-relational mapping and persistence |
| MySQL | Relational database |
| Maven Wrapper | Build and application execution |
| Spring AI | Chat model integration, RAG, tool calling, and chat memory |
| Groq API | Hosted chat-model provider |
| Transformers / ONNX embeddings | Local embedding generation for document retrieval |
| SimpleVectorStore | In-memory vector similarity store |
| Postman | Manual API testing |

## Architecture

The application follows a layered structure:

```text
Client (Postman or frontend)
          |
          v
REST Controllers
          |
          v
Services and business rules
          |
          v
Spring Data JPA Repositories
          |
          v
        MySQL
```

The security flow validates a bearer token before protected requests reach role-restricted operations. The AI flow can use retrieved healthcare-guide chunks and/or call approved Java backend tools.

```text
Healthcare guide -> Text chunks -> Local embeddings -> SimpleVectorStore
                                                        |
User question ------------------------------------------+
                                                        v
                                           Spring AI / chat model
                                                        |
                                                        v
                                                   AI response
```

## Roles and capabilities

| Role | Main capabilities |
|---|---|
| `PATIENT` | Register and log in, view/update own profile, browse doctors, view available slots, book appointments, view own appointments, and cancel eligible appointments. The patient AI assistant supports appointment-related questions and supported booking, cancellation, and rescheduling workflows. |
| `DOCTOR` | Log in, view own appointment schedule, view today's and upcoming appointments, identify appointments needing attention, manage own availability, and confirm or complete appointments. |
| `ADMIN` | Manage doctor and patient records, access administrative appointment views, manage slots, and use administrator appointment tools and summaries. |

Registration through `POST /api/auth/register` creates a patient account. Doctor and administrator accounts are not created through public patient registration.

## API reference

Base URL for local development:

```text
http://localhost:8080
```

Protected requests require this header:

```http
Authorization: Bearer YOUR_ACCESS_TOKEN
```

### Authentication

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a patient account |
| `POST` | `/api/auth/login` | Public | Authenticate and receive a JWT access token |

### Doctors

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/doctors?page=0&size=5` | Public | List doctors using pagination |
| `GET` | `/api/doctors/recommend?problem=skin%20rash&page=0&size=5` | Public | Recommend doctors for a described problem |
| `GET` | `/api/doctors/{id}` | Public | Get a doctor profile |
| `POST` | `/api/doctors` | `ADMIN` | Create a doctor profile and login account |
| `PUT` | `/api/doctors/{id}` | `ADMIN` | Update a doctor |
| `DELETE` | `/api/doctors/{id}` | `ADMIN` | Delete a doctor |

### Patients

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/patients/me` | `PATIENT` | Get the authenticated patient's profile |
| `PUT` | `/api/patients/me` | `PATIENT` | Update the authenticated patient's profile |
| `GET` | `/api/patients` | `ADMIN` | List patients |
| `GET` | `/api/patients/{id}` | `ADMIN` | Get a patient |
| `POST` | `/api/patients` | `ADMIN` | Create a patient record |
| `PUT` | `/api/patients/{id}` | `ADMIN` | Update a patient |
| `DELETE` | `/api/patients/{id}` | `ADMIN` | Delete a patient |

### Appointment slots

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/doctor-slots?doctorId=1` | Authenticated `ADMIN`, `DOCTOR`, or `PATIENT` | List available future slots for a doctor |
| `GET` | `/api/doctor-slots?doctorId=1&date=2026-11-20` | Authenticated `ADMIN`, `DOCTOR`, or `PATIENT` | List available slots for a date (`YYYY-MM-DD`) |
| `GET` | `/api/doctor-slots/mine` | `DOCTOR` | List the logged-in doctor's slots |
| `POST` | `/api/doctor-slots` | `DOCTOR` | Create a slot for the logged-in doctor |
| `POST` | `/api/doctor-slots/admin?doctorId=1` | `ADMIN` | Create a slot for a selected doctor |
| `DELETE` | `/api/doctor-slots/{slotId}` | `DOCTOR` | Delete an eligible available slot owned by that doctor |
| `DELETE` | `/api/doctor-slots/{slotId}/admin` | `ADMIN` | Delete an eligible available slot |

Slot creation request:

```json
{
  "startTime": "2026-11-20T10:30:00"
}
```

### Appointments

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/appointments` | `ADMIN` | List all appointments |
| `GET` | `/api/appointments/mine` | `PATIENT` or `ADMIN` | List the patient's appointments; an administrator can list all appointments here |
| `GET` | `/api/appointments/doctor` | `DOCTOR` or `ADMIN` | List appointments assigned to the doctor; an administrator can list all appointments here |
| `GET` | `/api/appointments/{id}` | Authenticated `ADMIN`, `PATIENT`, or `DOCTOR`, subject to ownership checks | Get an appointment by ID |
| `POST` | `/api/appointments` | `PATIENT` or `ADMIN` | Book an appointment using an available slot |
| `PATCH` | `/api/appointments/{id}/cancel` | `PATIENT` or `ADMIN` | Cancel an eligible appointment |
| `PATCH` | `/api/appointments/{id}/confirm` | `DOCTOR` or `ADMIN` | Confirm an appointment |
| `PATCH` | `/api/appointments/{id}/complete` | `DOCTOR` or `ADMIN` | Mark an appointment completed |
| `PUT` | `/api/appointments/{id}` | `ADMIN` | Update an appointment |
| `DELETE` | `/api/appointments/{id}` | `ADMIN` | Delete an appointment |

Patient booking request:

```json
{
  "slotId": 5,
  "reason": "Regular check-up"
}
```

Use an actual available slot ID returned by the slot endpoint. A patient books for their own account; the service rejects a patient-supplied `patientId`. Administrator booking uses an existing patient ID in addition to the slot ID and reason.

### AI assistants

All AI endpoints below require authentication. The role-specific routes also enforce their respective role.

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/ai/chat` | Authenticated | Healthcare Q&A and role-aware chat functionality |
| `POST` | `/api/ai/appointment-assistant` | `PATIENT` | Patient appointment assistant |
| `POST` | `/api/ai/doctor-appointment-assistant` | `DOCTOR` | Doctor schedule and appointment assistant |
| `POST` | `/api/ai/admin-appointment-assistant` | `ADMIN` | Administrative appointment assistant |

AI request body:

```json
{
  "question": "Show me my upcoming appointments"
}
```

AI response structure:

```json
{
  "question": "Show me my upcoming appointments",
  "answer": "..."
}
```

AI responses depend on the configured model, available data, and supported tools. Backend services remain responsible for authorization and business-rule validation.

## Getting started

### Prerequisites

- JDK 21
- MySQL 8.x
- Git
- The included Maven Wrapper (`mvnw.cmd` on Windows or `mvnw` on Linux/macOS)
- A Groq API key for the AI chat features
- Postman (recommended for API exploration)

### 1. Clone the repository

```bash
git clone https://github.com/jagubhargavsankar/doctor-appointment-system-backend.git
cd doctor-appointment-system-backend
```

If your cloned folder has a different name, change into that folder instead.

### 2. Create the database

Run in MySQL:

```sql
CREATE DATABASE doctor_appointment_db;
```

The JDBC URL can also create the database automatically when the MySQL user has permission to do so.

## Environment configuration

**Do not commit API keys, database passwords, JWT signing secrets, or administrator passwords.** Keep local values in your environment or a local configuration file excluded from Git.

Make sure the relevant lines in `src/main/resources/application.properties` use environment variables rather than hardcoded credentials:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/doctor_appointment_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD}
security.jwt.secret=${JWT_SECRET}
security.jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}
app.admin.email=${ADMIN_EMAIL:admin@demo.com}
app.admin.password=${ADMIN_PASSWORD}
spring.ai.openai.api-key=${GROQ_API_KEY}
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=openai/gpt-oss-20b
```

Generate a private Base64-encoded JWT secret from at least 32 random bytes. Do not use an example value as a real key.

### Windows PowerShell

Run these commands in the same terminal session you will use to start the application:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/doctor_appointment_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "YOUR_MYSQL_PASSWORD"

$key = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($key)
$env:JWT_SECRET = [Convert]::ToBase64String($key)
$env:JWT_EXPIRATION_MS = "3600000"

$env:ADMIN_EMAIL = "admin@example.com"
$env:ADMIN_PASSWORD = "REPLACE_WITH_A_STRONG_PRIVATE_PASSWORD"
$env:GROQ_API_KEY = "YOUR_NEW_GROQ_API_KEY"
```

Replace the example values locally. Do not put real values into the README or commit them. Environment variables set this way apply to the current PowerShell session.

### Linux/macOS

```bash
export DB_URL='jdbc:mysql://localhost:3306/doctor_appointment_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata'
export DB_USERNAME='root'
export DB_PASSWORD='YOUR_MYSQL_PASSWORD'
export JWT_SECRET="$(openssl rand -base64 32)"
export JWT_EXPIRATION_MS='3600000'
export ADMIN_EMAIL='admin@example.com'
export ADMIN_PASSWORD='REPLACE_WITH_A_STRONG_PRIVATE_PASSWORD'
export GROQ_API_KEY='YOUR_NEW_GROQ_API_KEY'
```

The application creates the initial administrator account on startup only when both administrator variables are configured and an account for that email does not already exist. Passwords for created accounts are hashed using BCrypt.

## Run the application

**Windows PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux/macOS:**

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

The API should be available at `http://localhost:8080` when startup completes successfully. The first startup may take longer while the local embedding model is initialized and the healthcare guide is loaded.

To build the project without starting the application:

```powershell
.\mvnw.cmd clean package
```

On Linux/macOS, use `./mvnw clean package`.

## Try it with Postman

Import this collection into Postman:

```text
postman/doctor-appointment-system.postman_collection.json
```

Suggested manual test flow:

1. Register a patient with `POST /api/auth/register`.
2. Log in using `POST /api/auth/login` and save `accessToken` from the response.
3. Browse doctors with `GET /api/doctors?page=0&size=5`.
4. Log in as an administrator and create a doctor if no suitable doctor record exists.
5. Log in as a doctor and create a future slot with `POST /api/doctor-slots`.
6. As a patient, fetch available slots with `GET /api/doctor-slots?doctorId=1`.
7. Book an actual available slot with `POST /api/appointments`.
8. Test appointment listing, cancellation, confirmation, and completion with the appropriate role tokens.
9. Test the AI endpoints using a JSON body containing a `question` field.

Example patient registration:

```http
POST http://localhost:8080/api/auth/register
Content-Type: application/json
```

```json
{
  "name": "Demo Patient",
  "email": "patient@example.com",
  "phone": "9876543210",
  "password": "UseAUniquePassword123!"
}
```

Example login:

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

```json
{
  "email": "patient@example.com",
  "password": "UseAUniquePassword123!"
}
```

For protected calls, set the Authorization type to **Bearer Token** in Postman and paste the token returned in `accessToken`. Use a token for the correct role for each endpoint.

## AI implementation

### Healthcare Q&A with retrieval-augmented generation (RAG)

1. `HealthcareDocumentLoader` reads `src/main/resources/healthcare-guide.txt` at startup.
2. The text is split into smaller chunks.
3. A local Transformers/ONNX embedding model generates vector representations.
4. `SimpleVectorStore` keeps the vectors in memory for similarity search.
5. Relevant context can be passed to the chat model to help answer healthcare questions.

The in-memory vector store is recreated when the application starts. The healthcare guide is a limited knowledge source, not a clinical reference system.

### Tool calling and role-specific assistants

The patient, doctor, and administrator assistants use Spring AI tool calling to connect natural-language prompts to selected Java methods. Examples include retrieving appointment information, listing available slots, managing eligible patient bookings, viewing doctor schedules, and producing administrative appointment summaries. Tools are not a replacement for backend authorization and validation.

### Chat model and conversation memory

- **Chat model:** `openai/gpt-oss-20b` served through Groq's OpenAI-compatible API endpoint.
- **Chat integration:** Spring AI.
- **Conversation memory:** JDBC-backed Spring AI chat memory.
- **Embeddings:** Local Transformers/ONNX embedding model.
- **Vector store:** In-memory `SimpleVectorStore`.

AI output can be incomplete or incorrect. It must not be treated as a diagnosis or a substitute for a qualified healthcare professional. Do not use real patient data while evaluating this portfolio project.

## Project structure

```text
doctor-appointment-system-backend/
├── .env.example
├── .gitignore
├── mvnw
├── mvnw.cmd
├── pom.xml
├── postman/
│   └── doctor-appointment-system.postman_collection.json
├── SECURITY_WALKTHROUGH.md
├── src/
│   └── main/
│       ├── java/com/bhargava/doctorappointment/
│       │   ├── auth/          # Login and registration logic
│       │   ├── config/        # Spring AI and vector-store configuration
│       │   ├── controller/    # REST API controllers
│       │   ├── dto/           # Request and response models
│       │   ├── entity/        # JPA entities
│       │   ├── enums/         # Roles and appointment statuses
│       │   ├── exception/     # API exceptions and error handling
│       │   ├── rag/           # Healthcare document loading for RAG
│       │   ├── repository/    # Spring Data repositories
│       │   ├── security/      # JWT filter, token service, access rules
│       │   ├── service/       # Business logic and AI services
│       │   ├── tools/         # Backend methods exposed to AI assistants
│       │   └── DoctorAppointmentApplication.java
│       └── resources/
│           ├── application.properties
│           └── healthcare-guide.txt
└── README.md
```

## Security notes

- Never commit real API keys, database credentials, JWT secrets, or administrator passwords.
- Rotate any credential that has been pasted into a chat, uploaded file, commit, or public repository.
- Use a fresh, random JWT secret and keep it consistent for the environment where the application is running.
- JWTs are signed, not encrypted. Do not store sensitive information inside a token.
- Patient registration creates the `PATIENT` role; it does not allow public self-registration as an administrator or doctor.
- Protected endpoints use JWT authentication and role-based access checks. Appointment services also apply ownership and business-rule checks.
- The default local JPA setting `spring.jpa.hibernate.ddl-auto=update` is intended for development. Production deployments should use controlled database migrations and managed secrets.
- Before deployment, add HTTPS, robust audit logging, rate limiting, monitoring, backup/restore procedures, privacy controls, and a security review.

## Known limitations

- This is a portfolio project and is not designed or reviewed as a production medical platform.
- API testing is currently documented around the included Postman collection; an automated unit/integration test suite is not included in the current dependency setup.
- `SimpleVectorStore` is in memory and is rebuilt on startup.
- AI accuracy depends on the model, retrieved guide content, prompts, and tool responses. AI responses can be wrong or incomplete.
- `spring.jpa.hibernate.ddl-auto=update` is used for local development rather than a formal schema migration workflow.
- Production-grade clinical privacy, audit, availability, and regulatory controls are outside the current project scope.

## Future improvements

- Add automated unit and integration tests for security, booking, slot concurrency, and AI-tool authorization.
- Add database migrations with Flyway or Liquibase.
- Add API documentation using OpenAPI/Swagger if needed.
- Add persistent vector storage and an explicit vector-store lifecycle.
- Add observability, rate limiting, structured audit logs, and deployment configuration.
- Expand end-to-end testing across patient, doctor, and administrator workflows.

## Author

**Bhargava Sankar Jagu**

- GitHub: [@jagubhargavsankar](https://github.com/jagubhargavsankar)
- Repository: [doctor-appointment-system-backend](https://github.com/jagubhargavsankar/doctor-appointment-system-backend)

---

*Built as a Java and Spring Boot portfolio project to demonstrate backend API development, authentication, database integration, and practical AI integration.*
