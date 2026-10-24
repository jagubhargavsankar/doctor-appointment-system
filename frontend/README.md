# CarePoint React Frontend

A React + Vite frontend for the existing Spring Boot Doctor Appointment System. It uses the existing backend APIs and does not replace the backend or database.

## Features

- Public doctor directory, doctor search, and specialization recommendations.
- Patient registration and JWT login.
- Patient appointment booking from existing available slots, appointment list/cancellation, and profile updates.
- Doctor schedule management, slot creation, and appointment confirmation/completion.
- Admin doctor/account creation, appointment-slot setup, patient directory, and appointment management.
- Role-specific AI assistant calls for Patient, Doctor, and Admin using the updated backend endpoints.
- Readable rendering for AI replies that contain escaped line breaks or basic Markdown emphasis.
- Responsive desktop/mobile layout.

## Requirements

- Node.js 20.19+ or 22.12+
- The Spring Boot backend running at `http://localhost:8080`
- MySQL and the backend environment variables configured as described in the backend README

## Run locally

Open one terminal for the backend and start Spring Boot as you normally do. Then open another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite (normally `http://localhost:5173`). Vite proxies requests beginning with `/api` to `http://localhost:8080`; this avoids needing local CORS changes while developing.

## Default workflow

1. Start MySQL and run the backend with its required environment variables, including the database password and JWT secret.
2. Register as a patient from the React UI, then sign in.
3. A doctor must have been created by an admin, and the doctor or admin must add future available slots before a patient can book.
4. Sign in with the admin account configured for the backend to create doctor accounts and add their slots.
5. Sign in with a doctor account to manage that doctor's schedule and appointment requests.

## API integration details

- Login: `POST /api/auth/login` returns `accessToken`, `email`, and `role`.
- Registration: `POST /api/auth/register` creates a patient account.
- Booking: `POST /api/appointments` sends `{ "slotId": 1, "reason": "..." }`.
- Slot creation: `POST /api/doctor-slots` sends `{ "startTime": "YYYY-MM-DDTHH:mm:ss" }` for a doctor, or the admin endpoint with `?doctorId=...`.
- All authenticated requests include `Authorization: Bearer <accessToken>`.

The token is kept in `sessionStorage`, so it is removed when the browser tab session ends. This is a practical student-project setup, not a complete production security design.

## Build

```powershell
npm run build
npm run preview
```

For deployment on a separate domain, configure a reverse proxy or enable narrowly scoped CORS in the backend, set `VITE_API_BASE_URL` if necessary, and use HTTPS. Do not put database credentials, JWT secrets, or AI API keys in frontend environment variables.

## Notes

- The UI is aligned to the backend controller routes and DTOs. AI requests use `POST /api/ai/appointment-assistant` for patients, `POST /api/ai/doctor-appointment-assistant` for doctors, and `POST /api/ai/admin-appointment-assistant` for admins. Each request sends `{ "question": "..." }` and reads the `answer` field from the response.
- AI pages call the backend, which calls the configured model/provider. AI features still require the backend's AI environment variables and dependencies to be working.
- No Swagger or JUnit/Mockito setup was added.
