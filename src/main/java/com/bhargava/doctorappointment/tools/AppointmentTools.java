package com.bhargava.doctorappointment.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.bhargava.doctorappointment.dto.AppointmentRequest;
import com.bhargava.doctorappointment.dto.SlotResponse;
import com.bhargava.doctorappointment.entity.Appointment;
import com.bhargava.doctorappointment.entity.Doctor;
import com.bhargava.doctorappointment.service.AppointmentService;
import com.bhargava.doctorappointment.service.AppointmentSlotService;
import com.bhargava.doctorappointment.service.DoctorService;

@Component
public class AppointmentTools {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentTools.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final AppointmentService appointmentService;
    private final AppointmentSlotService appointmentSlotService;
    private final DoctorService doctorService;

    public AppointmentTools(
            AppointmentService appointmentService,
            AppointmentSlotService appointmentSlotService,
            DoctorService doctorService) {
        this.appointmentService = appointmentService;
        this.appointmentSlotService = appointmentSlotService;
        this.doctorService = doctorService;
    }

    @Tool(name = "getMyAppointments", description = """
            Retrieve the authenticated patient's real appointments.
            Use when the patient asks to view appointments, bookings, history,
            dates, reasons, doctors, or appointment statuses. Only use the
            patient identity supplied by the server. Never access another
            patient's records.
            """, returnDirect = true)
    public String getMyAppointments(ToolContext toolContext) {
        String patientEmail = getAuthenticatedEmail(toolContext);
        if (patientEmail == null) return "Patient identity is unavailable.";

        List<Appointment> appointments = appointmentService.getMyAppointments(patientEmail);
        if (appointments.isEmpty()) return "No appointments were found for your account.";

        StringBuilder result = new StringBuilder("Your appointments:\n\n");
        for (Appointment appointment : appointments) appendAppointment(result, appointment);
        LOGGER.info("Retrieved {} appointments for authenticated patient", appointments.size());
        return result.toString().trim();
    }

    @Tool(name = "listDoctorsForBooking", description = """
            List real doctors registered in the system with IDs, names,
            specializations, and consultation fees. Use when the patient needs
            help choosing a doctor. Never invent a doctor or doctor ID.
            """)
    public String listDoctorsForBooking() {
        List<Doctor> doctors = doctorService.getAllDoctors();
        if (doctors.isEmpty()) return "No doctors are currently available in the system.";

        StringBuilder result = new StringBuilder("Doctors registered in the system:\n\n");
        for (Doctor doctor : doctors) {
            result.append("Doctor ID: ").append(doctor.getId())
                    .append("\nName: ").append(doctor.getName())
                    .append("\nSpecialization: ").append(doctor.getSpecialization())
                    .append("\nConsultation fee: ₹").append(doctor.getConsultationFee())
                    .append("\n\n");
        }
        return result.toString().trim();
    }

    @Tool(name = "listAvailableSlotsForBooking", description = """
            List actual available future appointment slots for a real doctor.
            Use the doctor ID returned by listDoctorsForBooking. Return actual
            slot IDs and exact start times. Call this before booking. Never
            invent a slot ID or date/time.
            """)
    public String listAvailableSlotsForBooking(
            @ToolParam(description = "ID of a real doctor from the doctor list") Long doctorId) {
        if (doctorId == null || doctorId <= 0) return "Please select a valid doctor.";
        try {
            return formatSlots(appointmentSlotService.getAvailableSlots(doctorId));
        } catch (RuntimeException ex) {
            LOGGER.error("Could not retrieve slots for doctor {}", doctorId, ex);
            return "Could not retrieve available slots. Please try again.";
        }
    }

    @Tool(name = "listAvailableSlotsForDoctorOnDate", description = """
            List actual available future slots for a doctor on a specific date.
            The date must use YYYY-MM-DD. Call this before booking. Never
            invent a slot ID or time.
            """)
    public String listAvailableSlotsForDoctorOnDate(
            @ToolParam(description = "ID of a real doctor from the doctor list") Long doctorId,
            @ToolParam(description = "Requested date in YYYY-MM-DD format") String date) {
        if (doctorId == null || doctorId <= 0) return "Please select a valid doctor.";
        if (date == null || date.isBlank()) return "Please provide a date in YYYY-MM-DD format.";
        try {
            LocalDate requestedDate = LocalDate.parse(date.trim());
            return formatSlots(appointmentSlotService.getAvailableSlots(doctorId, requestedDate));
        } catch (DateTimeParseException ex) {
            return "Invalid date. Please use YYYY-MM-DD, for example 2026-10-29.";
        } catch (RuntimeException ex) {
            LOGGER.error("Could not retrieve slots for doctor {} on {}", doctorId, date, ex);
            return "Could not retrieve available slots. Please try again.";
        }
    }

    @Tool(name = "bookAppointment", description = """
            Book an appointment for the authenticated patient with an actual
            slot ID returned by an available-slot tool. Use only after the
            patient explicitly confirms the doctor, slot, date/time, and reason.
            The backend validates the patient and reserves the slot. Never
            claim success unless the backend confirms success.
            """)
    public String bookAppointment(
            @ToolParam(description = "ID of an AVAILABLE slot from the slot list") Long slotId,
            @ToolParam(description = "Reason for the appointment, at most 500 characters") String reason,
            ToolContext toolContext) {

        String patientEmail = getAuthenticatedEmail(toolContext);
        if (patientEmail == null) return "Booking failed: authenticated patient identity is unavailable.";
        if (slotId == null || slotId <= 0) return "Booking failed: please select an actual available slot.";
        if (reason == null || reason.isBlank()) return "Booking failed: please provide the appointment reason.";
        if (reason.trim().length() > 500) return "Booking failed: the reason must not exceed 500 characters.";

        AppointmentRequest request = new AppointmentRequest();
        request.setSlotId(slotId);
        request.setReason(reason.trim());
        try {
            Appointment appointment = appointmentService.createAppointment(request, patientEmail);
            LOGGER.info("Appointment {} booked for authenticated patient", appointment.getId());
            return "Appointment booked successfully!\n" + formatAppointment(appointment);
        } catch (AccessDeniedException ex) {
            return "Booking failed: only an authorized patient can book an appointment.";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return "Booking failed: " + ex.getMessage();
        } catch (RuntimeException ex) {
            LOGGER.error("Appointment booking failed", ex);
            return "Booking could not be completed. Please check the selected slot and try again.";
        }
    }

    @Tool(name = "cancelMyAppointment", description = """
            Cancel one of the authenticated patient's existing appointments.
            First use getMyAppointments to identify the correct appointment ID.
            If the patient has not explicitly confirmed cancellation of that
            exact ID, pass patientConfirmed=false; this performs no change and
            returns a confirmation prompt. Cancel only after explicit yes/confirm.
            Never invent appointment IDs. Completed appointments cannot be cancelled.
            """)
    public String cancelMyAppointment(
            @ToolParam(description = "Existing appointment ID from getMyAppointments") Long appointmentId,
            @ToolParam(description = "True only after the patient explicitly confirms cancellation of this exact appointment") boolean patientConfirmed,
            ToolContext toolContext) {

        String patientEmail = getAuthenticatedEmail(toolContext);
        if (patientEmail == null) return "Cancellation failed: authenticated patient identity is unavailable.";
        if (appointmentId == null || appointmentId <= 0) return "Please choose an appointment ID from your appointment list.";

        if (!patientConfirmed) {
            try {
                Appointment existing = appointmentService.getAppointmentByIdForCurrentUser(appointmentId, patientEmail);
                return "No changes have been made. You selected appointment " + appointmentId
                        + " with " + existing.getDoctor().getName() + " on "
                        + formatDateTime(existing) + ". Would you like me to cancel this appointment? Reply yes to confirm.";
            } catch (RuntimeException ex) {
                return "I couldn't verify that appointment. Please ask to view your appointments and choose an appointment ID you own.";
            }
        }

        try {
            Appointment cancelled = appointmentService.cancelMyAppointment(appointmentId, patientEmail);
            return "Appointment cancelled successfully.\n" + formatAppointment(cancelled);
        } catch (AccessDeniedException ex) {
            return "Cancellation failed: you can cancel only your own appointments.";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return "Cancellation failed: " + ex.getMessage();
        } catch (RuntimeException ex) {
            LOGGER.error("Cancellation failed for appointment {}", appointmentId, ex);
            return "Cancellation could not be completed. Please try again.";
        }
    }

    @Tool(name = "rescheduleMyAppointment", description = """
            Reschedule an EXISTING appointment for the authenticated patient.
            First call getMyAppointments to identify the existing appointment,
            then list real available slots for that appointment's doctor and
            requested date. This operation MUST update the existing appointment
            ID; do not create a new booking to satisfy a reschedule request.
            If the patient has not explicitly confirmed the exact old appointment
            and new slot, pass patientConfirmed=false and make no changes. Preserve
            the existing reason unless the patient asks to change it.
            """)
    public String rescheduleMyAppointment(
            @ToolParam(description = "Existing appointment ID that must be updated") Long appointmentId,
            @ToolParam(description = "New slot ID from the actual available slots list") Long newSlotId,
            @ToolParam(description = "True only after the patient explicitly confirms the old appointment and replacement slot") boolean patientConfirmed,
            ToolContext toolContext) {

        String patientEmail = getAuthenticatedEmail(toolContext);
        if (patientEmail == null) return "Rescheduling failed: authenticated patient identity is unavailable.";
        if (appointmentId == null || appointmentId <= 0 || newSlotId == null || newSlotId <= 0)
            return "Please provide the existing appointment ID and an actual available replacement slot ID.";

        try {
            Appointment existing = appointmentService.getAppointmentByIdForCurrentUser(appointmentId, patientEmail);
            if (!patientConfirmed) {
                return "No changes have been made. You asked to move appointment " + appointmentId
                        + " with " + existing.getDoctor().getName() + " from " + formatDateTime(existing)
                        + ". The replacement slot ID is " + newSlotId
                        + ". Please confirm that you want to move this existing appointment to that slot.";
            }

            AppointmentRequest request = new AppointmentRequest();
            request.setSlotId(newSlotId);
            request.setReason(existing.getReason());
            Appointment updated = appointmentService.rescheduleMyAppointment(appointmentId, request, patientEmail);
            return "Appointment rescheduled successfully. The existing appointment ID was kept.\n"
                    + formatAppointment(updated);
        } catch (AccessDeniedException ex) {
            return "Rescheduling failed: you can reschedule only your own appointments.";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return "Rescheduling failed: " + ex.getMessage();
        } catch (RuntimeException ex) {
            LOGGER.error("Rescheduling failed for appointment {}", appointmentId, ex);
            return "Rescheduling could not be completed. Please check the replacement slot and try again.";
        }
    }

    private String formatSlots(List<SlotResponse> slots) {
        if (slots == null || slots.isEmpty()) return "No available slots were found for the selected doctor and date.";
        StringBuilder result = new StringBuilder("Available appointment slots:\n\n");
        for (SlotResponse slot : slots) {
            result.append("Slot ID: ").append(slot.id())
                    .append("\nDoctor ID: ").append(slot.doctorId())
                    .append("\nStart time: ").append(slot.startTime().format(DATE_TIME_FORMAT))
                    .append("\nStatus: ").append(slot.status()).append("\n\n");
        }
        result.append("Only the slot IDs above are valid for booking or rescheduling.");
        return result.toString().trim();
    }

    private void appendAppointment(StringBuilder result, Appointment appointment) {
        result.append(formatAppointment(appointment)).append("\n\n");
    }

    private String formatAppointment(Appointment appointment) {
        return "Appointment ID: " + appointment.getId()
                + "\nDoctor: " + appointment.getDoctor().getName()
                + "\nSpecialization: " + appointment.getDoctor().getSpecialization()
                + "\nDate and time: " + formatDateTime(appointment)
                + "\nReason: " + appointment.getReason()
                + "\nStatus: " + appointment.getStatus();
    }

    private String formatDateTime(Appointment appointment) {
        return appointment.getAppointmentDateTime() == null
                ? "date/time unavailable"
                : appointment.getAppointmentDateTime().format(DATE_TIME_FORMAT);
    }

    private String getAuthenticatedEmail(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object value = toolContext.getContext().get("patientEmail");
        return value instanceof String email && !email.isBlank() ? email.trim() : null;
    }
}
