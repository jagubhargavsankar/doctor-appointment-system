package com.bhargava.doctorappointment.tools;

import com.bhargava.doctorappointment.dto.CreateSlotRequest;
import com.bhargava.doctorappointment.dto.SlotResponse;
import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.entity.Appointment;
import com.bhargava.doctorappointment.enums.AppointmentStatus;
import com.bhargava.doctorappointment.enums.UserRole;
import com.bhargava.doctorappointment.repository.AppUserRepository;
import com.bhargava.doctorappointment.service.AppointmentService;
import com.bhargava.doctorappointment.service.AppointmentSlotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class DoctorAppointmentTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoctorAppointmentTools.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final AppointmentService appointmentService;
    private final AppointmentSlotService appointmentSlotService;
    private final AppUserRepository appUserRepository;

    public DoctorAppointmentTools(
            AppointmentService appointmentService,
            AppointmentSlotService appointmentSlotService,
            AppUserRepository appUserRepository) {
        this.appointmentService = appointmentService;
        this.appointmentSlotService = appointmentSlotService;
        this.appUserRepository = appUserRepository;
    }

    private String requireDoctor(ToolContext context) {
        if (context == null || context.getContext() == null) {
            throw new AccessDeniedException("Authenticated doctor identity is missing.");
        }
        Object value = context.getContext().get("doctorEmail");
        if (!(value instanceof String email) || email.isBlank()) {
            throw new AccessDeniedException("Authenticated doctor identity is missing.");
        }
        AppUser user = appUserRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new AccessDeniedException("Doctor account was not found."));
        if (user.getRole() != UserRole.DOCTOR) {
            throw new AccessDeniedException("Only doctors can use these tools.");
        }
        return user.getEmail();
    }

    @Tool(name = "getMyDoctorAppointments", description = """
            Retrieve appointments assigned to the currently authenticated doctor.
            Use when the doctor asks for my appointments, today's appointments,
            upcoming appointments, or their appointment statuses. Never access
            appointments belonging to another doctor. Do not return raw entities.
            """, returnDirect = true)
    public String getMyDoctorAppointments(ToolContext context) {
        return listMyAppointments(requireDoctor(context));
    }

    public String listMyAppointments(String doctorEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(doctorEmail)
                .orElseThrow(() -> new AccessDeniedException("Doctor account was not found."));
        if (user.getRole() != UserRole.DOCTOR) throw new AccessDeniedException("Only doctors can use this operation.");
        List<Appointment> appointments = appointmentService.getDoctorAppointments(user.getEmail());
        if (appointments.isEmpty()) return "No appointments were found for your doctor account.";

        StringBuilder result = new StringBuilder("Your appointments (" + appointments.size() + "):\n\n");
        for (Appointment appointment : appointments) {
            result.append("Appointment ID: ").append(appointment.getId())
                    .append("\nPatient: ").append(appointment.getPatient() == null ? "unknown" : appointment.getPatient().getName())
                    .append("\nDate and time: ").append(formatDateTime(appointment.getAppointmentDateTime()))
                    .append("\nReason: ").append(appointment.getReason())
                    .append("\nStatus: ").append(appointment.getStatus()).append("\n\n");
        }
        return result.toString().trim();
    }

    public String listTodaysAppointmentsForDoctor(String doctorEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(doctorEmail)
                .orElseThrow(() -> new AccessDeniedException("Doctor account was not found."));
        if (user.getRole() != UserRole.DOCTOR) throw new AccessDeniedException("Only doctors can use this operation.");
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<Appointment> appointments = appointmentService.getDoctorAppointments(user.getEmail()).stream()
                .filter(a -> a.getAppointmentDateTime() != null && a.getAppointmentDateTime().toLocalDate().equals(today))
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDateTime))
                .toList();
        if (appointments.isEmpty()) return "You have no appointments scheduled for today (" + today + ").";
        StringBuilder result = new StringBuilder("Your appointments for today (" + today + "):\n\n");
        for (Appointment appointment : appointments) {
            result.append("Appointment ID: ").append(appointment.getId())
                    .append("\nPatient: ").append(appointment.getPatient() == null ? "unknown" : appointment.getPatient().getName())
                    .append("\nDate and time: ").append(formatDateTime(appointment.getAppointmentDateTime()))
                    .append("\nReason: ").append(appointment.getReason())
                    .append("\nStatus: ").append(appointment.getStatus()).append("\n\n");
        }
        return result.toString().trim();
    }

    public String listAppointmentsNeedingAttentionForDoctor(String doctorEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(doctorEmail)
                .orElseThrow(() -> new AccessDeniedException("Doctor account was not found."));
        if (user.getRole() != UserRole.DOCTOR) throw new AccessDeniedException("Only doctors can use this operation.");
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<Appointment> appointments = appointmentService.getDoctorAppointments(user.getEmail()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                        || (a.getStatus() == AppointmentStatus.CONFIRMED
                            && a.getAppointmentDateTime() != null
                            && a.getAppointmentDateTime().isBefore(now)))
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDateTime))
                .toList();
        if (appointments.isEmpty()) return "No appointments currently need attention.";
        StringBuilder result = new StringBuilder("Appointments needing attention:\n\n");
        for (Appointment appointment : appointments) {
            result.append("Appointment ID: ").append(appointment.getId())
                    .append("\nPatient: ").append(appointment.getPatient() == null ? "unknown" : appointment.getPatient().getName())
                    .append("\nDate and time: ").append(formatDateTime(appointment.getAppointmentDateTime()))
                    .append("\nStatus: ").append(appointment.getStatus())
                    .append("\nReason: ").append(appointment.getStatus() == AppointmentStatus.BOOKED
                            ? "Awaiting doctor confirmation" : "Scheduled time has passed")
                    .append("\n\n");
        }
        return result.toString().trim();
    }

    @Tool(name = "listMyAvailability", description = """
            List future availability slots belonging to the authenticated doctor,
            including each actual slot ID, start time, and status. Use for questions
            about availability, free slots, or existing slot management. Never guess IDs.
            """, returnDirect = true)
    public String listMyAvailability(ToolContext context) {
        String email = requireDoctor(context);
        List<SlotResponse> slots = appointmentSlotService.getMySlots(email);
        if (slots.isEmpty()) return "You have no future availability slots. You can ask me to add a slot using a future date and time.";
        StringBuilder result = new StringBuilder("Your future availability slots:\n\n");
        for (SlotResponse slot : slots) {
            result.append("Slot ID: ").append(slot.id())
                    .append("\nStart time: ").append(slot.startTime().format(DATE_TIME_FORMAT))
                    .append("\nStatus: ").append(slot.status()).append("\n\n");
        }
        return result.toString().trim();
    }

    public String listMyAvailabilityForDoctor(String doctorEmail) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(doctorEmail)
                .orElseThrow(() -> new AccessDeniedException("Doctor account was not found."));
        if (user.getRole() != UserRole.DOCTOR) throw new AccessDeniedException("Only doctors can use this operation.");
        List<SlotResponse> slots = appointmentSlotService.getMySlots(user.getEmail());
        if (slots.isEmpty()) return "You have no future availability slots yet.";
        StringBuilder result = new StringBuilder("Your future availability slots:\n\n");
        for (SlotResponse slot : slots) {
            result.append("Slot ID: ").append(slot.id())
                    .append("\nStart time: ").append(slot.startTime().format(DATE_TIME_FORMAT))
                    .append("\nStatus: ").append(slot.status()).append("\n\n");
        }
        return result.toString().trim();
    }

    @Tool(name = "addAvailabilityForDoctor", description = """
            Add a future availability slot for the authenticated doctor using a
            date-time in ISO format YYYY-MM-DDTHH:mm. Never add a slot unless the
            doctor explicitly confirms the exact date and time. First call with
            confirmed=false to show the confirmation request; only a later explicit
            yes/confirm for that date/time permits confirmed=true. Do not accept a
            doctor ID from the model; the backend uses the logged-in doctor's identity.
            """, returnDirect = true)
    public String addAvailabilityForDoctor(
            @ToolParam(description = "Future local date/time in ISO format YYYY-MM-DDTHH:mm") String startDateTime,
            @ToolParam(description = "True only after the doctor explicitly confirms this exact date/time") boolean confirmed,
            ToolContext context) {
        String email = requireDoctor(context);
        if (startDateTime == null || startDateTime.isBlank()) return "Please provide a date and time in YYYY-MM-DDTHH:mm format.";
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(startDateTime.trim());
        } catch (DateTimeParseException ex) {
            return "Invalid date/time. Please use YYYY-MM-DDTHH:mm, for example 2026-10-29T10:30.";
        }
        if (!confirmed) return "No slot has been created. Please confirm: add an availability slot for " + start.format(DATE_TIME_FORMAT) + "? Reply yes to confirm.";
        try {
            SlotResponse slot = appointmentSlotService.createSlotForDoctor(email, new CreateSlotRequest(start));
            return "Availability slot created successfully.\nSlot ID: " + slot.id()
                    + "\nStart time: " + slot.startTime().format(DATE_TIME_FORMAT)
                    + "\nStatus: " + slot.status();
        } catch (RuntimeException ex) {
            LOGGER.warn("Could not create doctor availability slot for {} at {}", email, start, ex);
            return "Could not create the slot: " + safe(ex.getMessage());
        }
    }

    @Tool(name = "removeAvailabilityForDoctor", description = """
            Remove an available future slot owned by the authenticated doctor.
            Never delete a slot unless the doctor explicitly confirms the exact
            slot ID. Pass confirmed=false to ask for confirmation and make no change.
            The backend rejects booked, past, or another doctor's slots.
            """, returnDirect = true)
    public String removeAvailabilityForDoctor(
            @ToolParam(description = "Actual slot ID from listMyAvailability") Long slotId,
            @ToolParam(description = "True only after the doctor explicitly confirms removal of this exact slot") boolean confirmed,
            ToolContext context) {
        String email = requireDoctor(context);
        if (slotId == null || slotId <= 0) return "Please select an actual slot ID from your availability list.";
        if (!confirmed) return "No changes have been made. Please confirm removal of available slot ID " + slotId + ".";
        try {
            appointmentSlotService.deleteAvailableSlot(slotId, email);
            return "Available slot " + slotId + " was removed successfully.";
        } catch (RuntimeException ex) {
            LOGGER.warn("Could not remove slot {} for doctor {}", slotId, email, ex);
            return "Could not remove the slot: " + safe(ex.getMessage());
        }
    }

    @Tool(name = "confirmMyDoctorAppointment", description = """
            Confirm a BOOKED appointment assigned to the authenticated doctor.
            Use the actual appointment ID from getMyDoctorAppointments. Ask for
            explicit confirmation before changing the status; confirmed=false
            performs no change. Never confirm another doctor's appointment.
            """, returnDirect = true)
    public String confirmMyDoctorAppointment(
            @ToolParam(description = "Existing appointment ID from the doctor's own appointment list") Long appointmentId,
            @ToolParam(description = "True only after the doctor explicitly confirms this status change") boolean confirmed,
            ToolContext context) {
        String email = requireDoctor(context);
        if (appointmentId == null || appointmentId <= 0) return "Please provide an appointment ID from your own list.";
        if (!confirmed) return "No changes have been made. Please confirm that appointment " + appointmentId + " should be marked CONFIRMED.";
        try {
            Appointment appointment = appointmentService.confirmAppointment(appointmentId);
            return "Appointment status updated.\nAppointment ID: " + appointment.getId()
                    + "\nPatient: " + appointment.getPatient().getName()
                    + "\nDate and time: " + formatDateTime(appointment.getAppointmentDateTime())
                    + "\nStatus: " + appointment.getStatus();
        } catch (RuntimeException ex) {
            LOGGER.warn("Doctor {} could not confirm appointment {}", email, appointmentId, ex);
            return "Could not confirm the appointment: " + safe(ex.getMessage());
        }
    }

    @Tool(name = "completeMyDoctorAppointment", description = """
            Mark an appointment COMPLETED only when assigned to the authenticated
            doctor and currently CONFIRMED. Use the real appointment ID. Ask for
            explicit confirmation first; confirmed=false performs no change.
            """, returnDirect = true)
    public String completeMyDoctorAppointment(
            @ToolParam(description = "Existing appointment ID from the doctor's own appointment list") Long appointmentId,
            @ToolParam(description = "True only after the doctor explicitly confirms completion") boolean confirmed,
            ToolContext context) {
        String email = requireDoctor(context);
        if (appointmentId == null || appointmentId <= 0) return "Please provide an appointment ID from your own list.";
        if (!confirmed) return "No changes have been made. Please confirm that appointment " + appointmentId + " is completed.";
        try {
            Appointment appointment = appointmentService.completeAppointment(appointmentId);
            return "Appointment marked COMPLETED.\nAppointment ID: " + appointment.getId()
                    + "\nPatient: " + appointment.getPatient().getName()
                    + "\nDate and time: " + formatDateTime(appointment.getAppointmentDateTime())
                    + "\nStatus: " + appointment.getStatus();
        } catch (RuntimeException ex) {
            LOGGER.warn("Doctor {} could not complete appointment {}", email, appointmentId, ex);
            return "Could not complete the appointment: " + safe(ex.getMessage());
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "unavailable" : value.format(DATE_TIME_FORMAT);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "an unexpected error occurred" : value;
    }
}
