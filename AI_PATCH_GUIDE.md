# CarePoint AI backend update

This patch changes only the Spring Boot backend's AI behavior plus the appointment-service authorization needed for safe patient rescheduling. The React frontend and normal REST endpoints are not included in this patch.

## AI routes

All routes require a valid Bearer JWT.

- `POST /api/ai/chat` — backward-compatible role routing. PATIENT uses the healthcare FAQ/recommendation flow, DOCTOR uses Doctor AI, and ADMIN uses Admin AI.
- `POST /api/ai/appointment-assistant` — patient appointment booking, viewing, cancellation, and rescheduling.
- `POST /api/ai/doctor-appointment-assistant` — explicit Doctor AI endpoint.
- `POST /api/ai/admin-appointment-assistant` — explicit Admin AI endpoint.

Request body for each route:

```json
{"question":"Summarize today's appointments"}
```

## What changed

- Admin AI now builds a dedicated ChatClient without the healthcare RAG advisor, so admin operations are not mixed with the generic healthcare guide.
- Admin greetings are handled without calling appointment tools. Common requests now use deterministic backend operations for today's summary, appointments needing attention, seven-day status trends, appointment lists, and slot availability.
- Admin appointment/patient/doctor results are formatted into readable fields rather than raw Java entity references.
- Admin availability lookup requires a real doctor name/ID and a date; it no longer invents a default doctor ID/date for unrelated questions.
- Doctor AI is added and is selected by authenticated role on `/api/ai/chat`. It can list the logged-in doctor's appointments and availability, add/remove eligible slots with confirmation, and confirm/complete assigned appointments with confirmation.
- Patient AI gains cancellation and rescheduling tools. They verify the authenticated patient's ownership and require an explicit confirmation flag before changing data. Rescheduling updates an existing appointment ID rather than creating a replacement booking.
- Healthcare guide content now covers preparing for a visit and how to ask for a healthcare-term definition.
- `AppointmentTools.java` now includes its missing package declaration.

## Recommended Postman checks

Use a token belonging to the role being tested.

### Admin token

1. `POST /api/ai/admin-appointment-assistant` with `{"question":"hi"}` — should greet without any slot lookup.
2. `{"question":"Summarize today's appointments"}` — should show the current date, counts by status, and readable appointments.
3. `{"question":"Which appointments need attention?"}` — should list pending/overdue records based on the backend rules.
4. `{"question":"Show appointment status trends"}` — should show status counts and daily totals for the last seven calendar days including today.
5. `{"question":"Available slots for Dr Krishna on 2026-10-29"}` — should query the named registered doctor and real slots.

### Doctor token

1. `POST /api/ai/chat` with `{"question":"my appointments"}`.
2. `{"question":"How do I manage my availability?"}`.
3. `{"question":"show my availability"}`.

### Patient token

1. `POST /api/ai/appointment-assistant` with `{"question":"my appointments"}`.
2. Try booking as before and verify the selected slot exists.
3. For cancellation, verify it asks which booking and confirms the exact appointment before cancellation.
4. For rescheduling, confirm that the existing appointment ID is updated and no duplicate appointment is created.

## Build note

The Maven wrapper in this environment attempted to download Maven from Maven Central, but DNS/network access was unavailable. The code was reviewed statically, but a full Maven compile and live Postman test could not be completed here. Run `mvnw.cmd -DskipTests compile` on your Windows machine before starting the backend.
