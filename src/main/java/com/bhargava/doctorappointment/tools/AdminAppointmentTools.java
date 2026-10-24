package com.bhargava.doctorappointment.tools;

import com.bhargava.doctorappointment.dto.AppointmentRequest;
import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.entity.Appointment;
import com.bhargava.doctorappointment.entity.Doctor;
import com.bhargava.doctorappointment.entity.Patient;
import com.bhargava.doctorappointment.enums.AppointmentStatus;
import com.bhargava.doctorappointment.enums.UserRole;
import com.bhargava.doctorappointment.repository.AppUserRepository;
import com.bhargava.doctorappointment.repository.PatientRepository;
import com.bhargava.doctorappointment.service.AppointmentService;
import com.bhargava.doctorappointment.service.AppointmentSlotService;
import com.bhargava.doctorappointment.service.DoctorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AdminAppointmentTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAppointmentTools.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final AppointmentService appointmentService;
    private final AppointmentSlotService appointmentSlotService;
    private final DoctorService doctorService;
    private final PatientRepository patientRepository;
    private final AppUserRepository appUserRepository;

    public AdminAppointmentTools(
            AppointmentService appointmentService,
            AppointmentSlotService appointmentSlotService,
            DoctorService doctorService,
            PatientRepository patientRepository,
            AppUserRepository appUserRepository) {
        this.appointmentService = appointmentService;
        this.appointmentSlotService = appointmentSlotService;
        this.doctorService = doctorService;
        this.patientRepository = patientRepository;
        this.appUserRepository = appUserRepository;
    }

    private String requireAdmin(ToolContext context) {
        if (context == null || context.getContext() == null) {
            throw new AccessDeniedException("Administrator identity is missing.");
        }
        Object emailValue = context.getContext().get("adminEmail");
        if (emailValue == null || emailValue.toString().isBlank()) {
            throw new AccessDeniedException("Administrator identity is missing.");
        }
        return requireAdminEmail(emailValue.toString());
    }

    private String requireAdminEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Administrator identity is missing.");
        }
        String normalized = email.trim();
        AppUser user = appUserRepository.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new AccessDeniedException("Administrator account was not found."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only administrators can use these tools.");
        }
        return normalized;
    }

    @Tool(name = "listAllAppointmentsForAdmin", description = """
            Retrieve and list real appointments in the system for an authenticated administrator.
            Use this for requests to see or list appointments. Do not use this tool
            to answer appointment slot availability. Return readable IDs, doctor,
            patient, date/time, reason, and status; never return raw Java objects.
            """, returnDirect = true)
    public String listAllAppointmentsForAdmin(ToolContext context) {
        return listAllAppointmentsForAdmin(requireAdmin(context));
    }

    public String listAllAppointmentsForAdmin(String adminEmail) {
        requireAdminEmail(adminEmail);
        List<Appointment> appointments = appointmentService.getAllAppointments();
        if (appointments == null || appointments.isEmpty()) return "No appointments found.";
        StringBuilder result = new StringBuilder("All appointments (" + appointments.size() + "):\n\n");
        for (Appointment appointment : appointments) {
            result.append(formatAppointment(appointment, true)).append("\n\n");
        }
        return result.toString().trim();
    }

    @Tool(name = "summarizeTodaysAppointmentsForAdmin", description = """
            Summarize appointments scheduled for the current date in Asia/Kolkata.
            Use for requests such as summarize today's appointments or today's schedule.
            Query actual appointment records. Never use a hardcoded doctor ID or date.
            """, returnDirect = true)
    public String summarizeTodaysAppointmentsForAdmin(ToolContext context) {
        return summarizeTodaysAppointments(requireAdmin(context));
    }

    public String summarizeTodaysAppointments(String adminEmail) {
        requireAdminEmail(adminEmail);
        LocalDate today = LocalDate.now(APP_ZONE);
        List<Appointment> todays = appointmentService.getAllAppointments().stream()
                .filter(a -> a.getAppointmentDateTime() != null
                        && a.getAppointmentDateTime().toLocalDate().equals(today))
                .sorted((a, b) -> a.getAppointmentDateTime().compareTo(b.getAppointmentDateTime()))
                .toList();

        if (todays.isEmpty()) {
            return "Today's appointment summary for " + today + ": no appointments are scheduled.";
        }
        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus status : AppointmentStatus.values()) counts.put(status, 0L);
        for (Appointment appointment : todays) counts.merge(appointment.getStatus(), 1L, Long::sum);

        StringBuilder result = new StringBuilder("Appointment summary for today (" + today + ")\n")
                .append("Total appointments: ").append(todays.size()).append("\n");
        appendStatusCounts(result, counts);
        result.append("\nToday's schedule:\n\n");
        for (Appointment appointment : todays) result.append(formatAppointment(appointment, true)).append("\n\n");
        return result.toString().trim();
    }

    @Tool(name = "getAppointmentStatusTrendsForAdmin", description = """
            Calculate appointment status counts for the last seven calendar days,
            including today, and daily appointment counts. Use for requests about
            appointment status trends, status distribution, or booking trends.
            Return counts calculated from database records, never guess.
            """, returnDirect = true)
    public String getAppointmentStatusTrendsForAdmin(ToolContext context) {
        return getAppointmentStatusTrends(requireAdmin(context));
    }

    public String getAppointmentStatusTrends(String adminEmail) {
        requireAdminEmail(adminEmail);
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate from = today.minusDays(6);
        List<Appointment> records = appointmentService.getAllAppointments().stream()
                .filter(a -> a.getAppointmentDateTime() != null)
                .filter(a -> {
                    LocalDate date = a.getAppointmentDateTime().toLocalDate();
                    return !date.isBefore(from) && !date.isAfter(today);
                }).toList();

        Map<AppointmentStatus, Long> totalByStatus = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus status : AppointmentStatus.values()) totalByStatus.put(status, 0L);
        for (Appointment appointment : records) totalByStatus.merge(appointment.getStatus(), 1L, Long::sum);

        StringBuilder result = new StringBuilder("Appointment status trends (" + from + " through " + today + ")\n")
                .append("Total appointments in period: ").append(records.size()).append("\nStatus distribution:\n");
        appendStatusCounts(result, totalByStatus);
        result.append("\nDaily totals:\n");
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            long count = records.stream().filter(a -> a.getAppointmentDateTime().toLocalDate().equals(currentDate)).count();
            result.append(date).append(": ").append(count).append(" appointment(s)\n");
        }
        if (records.isEmpty()) result.append("No appointments were recorded during this period.\n");
        return result.toString().trim();
    }

    @Tool(name = "getAppointmentsNeedingAttentionForAdmin", description = """
            Find appointments requiring administrative follow-up. Include BOOKED
            appointments awaiting confirmation and past BOOKED or CONFIRMED
            appointments whose scheduled time has passed. Exclude CANCELLED and
            COMPLETED appointments. Use database records only.
            """, returnDirect = true)
    public String getAppointmentsNeedingAttentionForAdmin(ToolContext context) {
        return getAppointmentsNeedingAttention(requireAdmin(context));
    }

    public String getAppointmentsNeedingAttention(String adminEmail) {
        requireAdminEmail(adminEmail);
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        List<Appointment> attention = appointmentService.getAllAppointments().stream()
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                        || ((a.getStatus() == AppointmentStatus.CONFIRMED)
                            && a.getAppointmentDateTime() != null
                            && a.getAppointmentDateTime().isBefore(now)))
                .sorted((a, b) -> {
                    if (a.getAppointmentDateTime() == null) return 1;
                    if (b.getAppointmentDateTime() == null) return -1;
                    return a.getAppointmentDateTime().compareTo(b.getAppointmentDateTime());
                }).toList();

        if (attention.isEmpty()) return "No appointments currently match the attention rules: there are no pending BOOKED appointments or overdue CONFIRMED appointments.";
        StringBuilder result = new StringBuilder("Appointments needing attention (" + attention.size() + "):\n\n");
        for (Appointment appointment : attention) {
            String reason = appointment.getStatus() == AppointmentStatus.BOOKED
                    ? "Awaiting confirmation"
                    : "Scheduled time has passed but appointment is still CONFIRMED";
            result.append(formatAppointment(appointment, true))
                    .append("\nAttention reason: ").append(reason).append("\n\n");
        }
        return result.toString().trim();
    }

    @Tool(name = "listPatientsForAdmin", description = """
            List patient records for an authenticated administrator. Include patient
            IDs and profile fields needed by the administrator; do not expose passwords.
            """, returnDirect = true)
    public String listPatientsForAdmin(ToolContext context) {
        return listPatientsForAdminForAi(requireAdmin(context));
    }

    public String listPatientsForAdminForAi(String adminEmail) {
        requireAdminEmail(adminEmail);
        List<Patient> patients = patientRepository.findAll();
        if (patients.isEmpty()) return "No patients found.";
        StringBuilder result = new StringBuilder("Patients (" + patients.size() + "):\n\n");
        for (Patient patient : patients) {
            result.append("Patient ID: ").append(patient.getId())
                    .append("\nName: ").append(safe(patient.getName()))
                    .append("\nEmail: ").append(safe(patient.getEmail()))
                    .append("\nPhone: ").append(safe(patient.getPhone()))
                    .append("\n\n");
        }
        return result.toString().trim();
    }

    @Tool(name = "listDoctorsForAdmin", description = """
            List real registered doctors with their IDs, specializations, fees,
            qualifications, years of experience, and ratings.
            """, returnDirect = true)
    public String listDoctorsForAdmin(ToolContext context) {
        return listDoctorsForAdminForAi(requireAdmin(context));
    }

    public String listDoctorsForAdminForAi(String adminEmail) {
        requireAdminEmail(adminEmail);
        List<Doctor> doctors = doctorService.getAllDoctors();
        if (doctors == null || doctors.isEmpty()) return "No doctors found.";
        StringBuilder result = new StringBuilder("Doctors (" + doctors.size() + "):\n\n");
        for (Doctor doctor : doctors) {
            result.append("Doctor ID: ").append(doctor.getId())
                    .append("\nName: ").append(safe(doctor.getName()))
                    .append("\nSpecialization: ").append(safe(doctor.getSpecialization()))
                    .append("\nQualification: ").append(safe(doctor.getQualification()))
                    .append("\nExperience: ").append(doctor.getExperienceYears()).append(" years")
                    .append("\nConsultation fee: ₹").append(doctor.getConsultationFee())
                    .append("\nRating: ").append(doctor.getRating())
                    .append("\n\n");
        }
        return result.toString().trim();
    }

    /** Resolve a doctor from a name mentioned by an authenticated admin. */
    public Long findDoctorIdByName(String adminEmail, String text) {
        requireAdminEmail(adminEmail);
        if (text == null || text.isBlank()) return null;
        String normalized = text.toLowerCase(java.util.Locale.ROOT)
                .replace("dr.", " ").replace("doctor", " ")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        List<Doctor> doctors = doctorService.getAllDoctors();
        return doctors.stream()
                .filter(d -> d.getName() != null)
                .filter(d -> {
                    String name = d.getName().toLowerCase(java.util.Locale.ROOT)
                            .replace("dr.", " ").replace("doctor", " ")
                            .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
                    return !name.isBlank() && normalized.contains(name);
                })
                .map(Doctor::getId)
                .findFirst().orElse(null);
    }

    @Tool(name = "listAvailableSlotsForAdmin", description = """
            Retrieve actual available appointment slots for a doctor on the exact
            date supplied by the administrator. Use only when the current request
            asks for availability or available slots. The date must be YYYY-MM-DD.
            Do not use this tool for greetings, summaries, trends, or appointment lists.
            Never invent a doctor ID, date, slot ID, or time.
            """, returnDirect = true)
    public String listAvailableSlotsForAdmin(
            @ToolParam(description = "Existing doctor ID from the registered doctor list") Long doctorId,
            @ToolParam(description = "Date in YYYY-MM-DD format") String date,
            ToolContext context) {
        return listAvailableSlotsForAdmin(requireAdmin(context), doctorId, date);
    }

    public String listAvailableSlotsForAdmin(String adminEmail, Long doctorId, String date) {
        requireAdminEmail(adminEmail);
        LOGGER.info("Admin slot lookup: doctorId={}, date={}", doctorId, date);
        if (doctorId == null || doctorId <= 0) return "A valid doctor ID is required.";
        boolean doctorExists = doctorService.getAllDoctors().stream().anyMatch(d -> doctorId.equals(d.getId()));
        if (!doctorExists) return "Doctor ID " + doctorId + " was not found. Please check the registered doctor list.";
        if (date == null || date.isBlank()) return "A date is required in YYYY-MM-DD format.";
        try {
            LocalDate requestedDate = LocalDate.parse(date.trim());
            var slots = appointmentSlotService.getAvailableSlots(doctorId, requestedDate);
            if (slots == null || slots.isEmpty()) return "No available appointment slots found for doctor ID " + doctorId + " on " + requestedDate + ".";
            StringBuilder result = new StringBuilder("Available slots for doctor ID ").append(doctorId).append(" on ").append(requestedDate).append(":\n\n");
            for (var slot : slots) {
                result.append("Slot ID: ").append(slot.id())
                        .append("\nDate and time: ").append(slot.startTime().format(DATE_TIME_FORMAT))
                        .append("\nStatus: ").append(slot.status()).append("\n\n");
            }
            return result.toString().trim();
        } catch (DateTimeParseException ex) {
            return "Invalid date. Use YYYY-MM-DD, for example 2026-10-29.";
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to retrieve slots for doctor {} on {}", doctorId, date, ex);
            return "Unable to retrieve available slots. Please check the doctor ID and date.";
        }
    }

    @Tool(name = "bookAppointmentForPatient", description = """
            Book an appointment for a specific existing patient using actual patient
            and available slot IDs. This operation requires explicit administrator
            confirmation. Set adminConfirmed=true only after the administrator has
            confirmed the patient, slot, and reason. Never invent IDs.
            """, returnDirect = true)
    public String bookAppointmentForPatient(
            @ToolParam(description = "Existing patient ID") Long patientId,
            @ToolParam(description = "Actual available slot ID") Long slotId,
            @ToolParam(description = "Appointment reason") String reason,
            @ToolParam(description = "True only after explicit admin confirmation") boolean adminConfirmed,
            ToolContext context) {
        String adminEmail = requireAdmin(context);
        if (!adminConfirmed) return "Booking not completed. Confirm the patient, slot, and reason first.";
        if (patientId == null || patientId <= 0) return "A valid patient ID is required.";
        if (slotId == null || slotId <= 0) return "A valid slot ID is required.";
        if (reason == null || reason.isBlank()) return "An appointment reason is required.";
        AppointmentRequest request = new AppointmentRequest();
        request.setPatientId(patientId);
        request.setSlotId(slotId);
        request.setReason(reason.trim());
        try {
            Appointment appointment = appointmentService.createAppointment(request, adminEmail);
            return "Appointment booked successfully.\n" + formatAppointment(appointment, true);
        } catch (RuntimeException ex) {
            LOGGER.error("Admin booking failed for patient {} and slot {}", patientId, slotId, ex);
            return "Booking failed: " + safe(ex.getMessage());
        }
    }

    @Tool(name = "cancelAppointmentForAdmin", description = """
            Cancel an existing appointment by its actual appointment ID. Use only
            after the administrator explicitly confirms the correct appointment.
            Never invent appointment IDs.
            """, returnDirect = true)
    public String cancelAppointmentForAdmin(
            @ToolParam(description = "Existing appointment ID") Long appointmentId,
            @ToolParam(description = "True only after explicit admin confirmation") boolean adminConfirmed,
            ToolContext context) {
        String adminEmail = requireAdmin(context);
        if (appointmentId == null || appointmentId <= 0) return "A valid appointment ID is required.";
        if (!adminConfirmed) {
            return "No changes have been made. Confirm cancellation of appointment ID " + appointmentId + " before I cancel it.";
        }
        try {
            Appointment cancelled = appointmentService.cancelAppointment(appointmentId, adminEmail);
            return "Appointment cancelled successfully.\n" + formatAppointment(cancelled, true);
        } catch (RuntimeException ex) {
            LOGGER.error("Admin cancellation failed for appointment {}", appointmentId, ex);
            return "Cancellation failed: " + safe(ex.getMessage());
        }
    }

    private void appendStatusCounts(StringBuilder result, Map<AppointmentStatus, Long> counts) {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            result.append(status).append(": ").append(counts.getOrDefault(status, 0L)).append("\n");
        }
    }

    private String formatAppointment(Appointment appointment, boolean includePatient) {
        StringBuilder result = new StringBuilder()
                .append("Appointment ID: ").append(appointment.getId())
                .append("\nDoctor: ").append(appointment.getDoctor() == null ? "unknown" : safe(appointment.getDoctor().getName()))
                .append("\nSpecialization: ").append(appointment.getDoctor() == null ? "unknown" : safe(appointment.getDoctor().getSpecialization()));
        if (includePatient) {
            result.append("\nPatient ID: ").append(appointment.getPatient() == null ? "unknown" : appointment.getPatient().getId())
                    .append("\nPatient: ").append(appointment.getPatient() == null ? "unknown" : safe(appointment.getPatient().getName()));
        }
        result.append("\nDate and time: ").append(appointment.getAppointmentDateTime() == null ? "unavailable" : appointment.getAppointmentDateTime().format(DATE_TIME_FORMAT))
                .append("\nReason: ").append(safe(appointment.getReason()))
                .append("\nStatus: ").append(appointment.getStatus());
        return result.toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "not provided" : value;
    }
}
