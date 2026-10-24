package com.bhargava.doctorappointment.service;
import org.hibernate.Hibernate;
import com.bhargava.doctorappointment.dto.AppointmentRequest;
import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.entity.Appointment;
import com.bhargava.doctorappointment.entity.AppointmentSlot;
import com.bhargava.doctorappointment.entity.Doctor;
import com.bhargava.doctorappointment.entity.Patient;
import com.bhargava.doctorappointment.entity.SlotStatus;
import com.bhargava.doctorappointment.enums.AppointmentStatus;
import com.bhargava.doctorappointment.enums.UserRole;
import com.bhargava.doctorappointment.exception.ResourceNotFoundException;
import com.bhargava.doctorappointment.repository.AppUserRepository;
import com.bhargava.doctorappointment.repository.AppointmentRepository;
import com.bhargava.doctorappointment.repository.AppointmentSlotRepository;
import com.bhargava.doctorappointment.repository.DoctorRepository;
import com.bhargava.doctorappointment.repository.PatientRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppUserRepository appUserRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            AppUserRepository appUserRepository) {

        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appUserRepository = appUserRepository;
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Appointment> getMyAppointments(String email) {

        AppUser user = findUser(email);

        if (user.getRole() != UserRole.PATIENT) {
            throw new AccessDeniedException(
                    "Only patients can view the patient appointment list");
        }

        Patient patient = patientRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient profile not found for this account"));

        return appointmentRepository
                .findByPatient_IdOrderByAppointmentDateTimeDesc(
                        patient.getId());
    }

    @Transactional(readOnly = true)
    public List<Appointment> getDoctorAppointments(String email) {

        AppUser user = findUser(email);

        if (user.getRole() != UserRole.DOCTOR) {
            throw new AccessDeniedException(
                    "Only doctors can view the doctor appointment list");
        }

        Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor profile not found for this account"));

        return appointmentRepository
                .findByDoctor_IdOrderByAppointmentDateTimeDesc(
                        doctor.getId());
    }

    @Transactional(readOnly = true)
    public Appointment getAppointmentByIdForCurrentUser(
            Long id,
            String email) {

        AppUser user = findUser(email);
        Appointment appointment = getAppointmentById(id);

        if (user.getRole() == UserRole.ADMIN) {
            return appointment;
        }

        boolean isPatientOwner =
                user.getRole() == UserRole.PATIENT
                        && appointment.getPatient().getUser() != null
                        && appointment.getPatient().getUser()
                        .getId().equals(user.getId());

        boolean isAssignedDoctor =
                user.getRole() == UserRole.DOCTOR
                        && appointment.getDoctor().getUser() != null
                        && appointment.getDoctor().getUser()
                        .getId().equals(user.getId());

        if (!isPatientOwner && !isAssignedDoctor) {
            throw new AccessDeniedException(
                    "You are not allowed to view this appointment");
        }

        return appointment;
    }

    @Transactional
    public Appointment createAppointment(
            AppointmentRequest request,
            String authenticatedEmail) {

        AppUser user = findUser(authenticatedEmail);

        Patient patient;

        // ADMIN can book an appointment for any existing patient.
        if (user.getRole() == UserRole.ADMIN) {

            if (request.getPatientId() == null) {
                throw new IllegalArgumentException(
                        "Patient ID is required for admin booking");
            }

            patient = patientRepository.findById(request.getPatientId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Patient not found with id: "
                                    + request.getPatientId()));

        } else if (user.getRole() == UserRole.PATIENT) {

            // PATIENT can book only for themselves.
            if (request.getPatientId() != null) {
                throw new AccessDeniedException(
                        "Patients cannot book appointments for other patients");
            }

            patient = patientRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Patient profile not found for this account"));

        } else {
            throw new AccessDeniedException(
                    "Only admins and patients can book appointments");
        }

        // Validate the appointment request.
        if (request.getSlotId() == null) {
            throw new IllegalArgumentException("Slot ID is required");
        }

        validateReason(request.getReason());

        // Find and validate the selected slot.
        AppointmentSlot slot = findSlot(request.getSlotId());
        Hibernate.initialize(slot.getDoctor());

        checkTimeIsAvailable(
                slot.getDoctor().getId(),
                slot.getStartTime(),
                null);

        // Reserve the slot before creating the appointment.
        reserveSlot(slot);

        Appointment appointment = new Appointment();

        appointment.setPatient(patient);
        appointment.setDoctor(slot.getDoctor());
        appointment.setAppointmentDateTime(slot.getStartTime());
        appointment.setReason(request.getReason().trim());
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setSlot(slot);

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment cancelAppointment(Long id, String email) {

        AppUser user = findUser(email);
        Appointment appointment = getAppointmentById(id);

        // Patients can cancel only their own appointments.
        // Admins can cancel appointments belonging to any patient.
        if (user.getRole() != UserRole.ADMIN) {

            String patientEmail = appointment.getPatient().getEmail();

            if (patientEmail == null
                    || !patientEmail.equalsIgnoreCase(email)) {
                throw new AccessDeniedException(
                        "You can only cancel your own appointments");
            }
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Completed appointments cannot be cancelled");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Appointment is already cancelled");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);

        // Release a future slot so it can be booked again.
        releaseFutureSlot(appointment);

        // Remove the old slot reference.
        appointment.setSlot(null);

        return appointmentRepository.save(appointment);
    }

    private Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found with id: " + id));
    }

    @Transactional
    public Appointment updateAppointment(
            Long id,
            AppointmentRequest request) {

        Appointment appointment = getAppointmentById(id);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cancelled or completed appointments cannot be rescheduled");
        }

        if (request.getSlotId() == null) {
            throw new IllegalArgumentException(
                    "Slot ID is required");
        }

        validateReason(request.getReason());

        AppointmentSlot oldSlot = appointment.getSlot();
        AppointmentSlot newSlot = findSlot(request.getSlotId());
        Hibernate.initialize(newSlot.getDoctor());
        // Changing only the reason does not require another reservation.
        if (oldSlot != null
                && oldSlot.getId().equals(newSlot.getId())) {

            appointment.setReason(request.getReason().trim());
            return appointmentRepository.save(appointment);
        }

        checkTimeIsAvailable(
                newSlot.getDoctor().getId(),
                newSlot.getStartTime(),
                appointment.getId());

        // Reserve the replacement slot before releasing the original one.
        reserveSlot(newSlot);

        appointment.setSlot(newSlot);
        appointment.setDoctor(newSlot.getDoctor());
        appointment.setAppointmentDateTime(newSlot.getStartTime());
        appointment.setReason(request.getReason().trim());

        releaseFutureSlot(oldSlot);

        return appointmentRepository.save(appointment);
    }

    /** Cancel an appointment only after verifying it belongs to the logged-in patient. */
    @Transactional
    public Appointment cancelMyAppointment(Long appointmentId, String authenticatedEmail) {
        AppUser user = findUser(authenticatedEmail);
        if (user.getRole() != UserRole.PATIENT) {
            throw new AccessDeniedException("Only patients can cancel their own appointments");
        }

        Appointment appointment = getAppointmentById(appointmentId);
        Long ownerUserId = appointment.getPatient().getUser() == null
                ? null
                : appointment.getPatient().getUser().getId();
        boolean isOwner = ownerUserId != null
                ? ownerUserId.equals(user.getId())
                : appointment.getPatient().getEmail() != null
                    && appointment.getPatient().getEmail().equalsIgnoreCase(authenticatedEmail);
        if (!isOwner) {
            throw new AccessDeniedException("You can only cancel your own appointments");
        }

        return cancelAppointment(appointmentId, authenticatedEmail);
    }

    /**
     * Reschedule an appointment for the authenticated patient only.
     * The appointment ID and replacement slot ID are both validated against
     * the database; the AI model never supplies the patient's identity.
     */
    @Transactional
    public Appointment rescheduleMyAppointment(
            Long appointmentId,
            AppointmentRequest request,
            String authenticatedEmail) {

        AppUser user = findUser(authenticatedEmail);
        if (user.getRole() != UserRole.PATIENT) {
            throw new AccessDeniedException(
                    "Only patients can reschedule their own appointments");
        }

        Appointment appointment = getAppointmentById(appointmentId);
        Long ownerUserId = appointment.getPatient().getUser() == null
                ? null
                : appointment.getPatient().getUser().getId();

        boolean isOwner = ownerUserId != null
                ? ownerUserId.equals(user.getId())
                : appointment.getPatient().getEmail() != null
                    && appointment.getPatient().getEmail()
                        .equalsIgnoreCase(authenticatedEmail);

        if (!isOwner) {
            throw new AccessDeniedException(
                    "You can only reschedule your own appointments");
        }

        return updateAppointment(appointmentId, request);
    }

    @Transactional
    public void deleteAppointment(Long id) {

        Appointment appointment = getAppointmentById(id);

        releaseFutureSlot(appointment);

        appointmentRepository.delete(appointment);
    }

    private void checkTimeIsAvailable(
            Long doctorId,
            LocalDateTime dateTime,
            Long currentAppointmentId) {

        List<AppointmentStatus> activeStatuses = List.of(
                AppointmentStatus.BOOKED,
                AppointmentStatus.CONFIRMED);

        boolean slotTaken;

        if (currentAppointmentId == null) {
            slotTaken = appointmentRepository
                    .existsByDoctor_IdAndAppointmentDateTimeAndStatusIn(
                            doctorId,
                            dateTime,
                            activeStatuses);
        } else {
            slotTaken = appointmentRepository
                    .existsByDoctor_IdAndAppointmentDateTimeAndStatusInAndIdNot(
                            doctorId,
                            dateTime,
                            activeStatuses,
                            currentAppointmentId);
        }

        if (slotTaken) {
            throw new IllegalStateException(
                    "This doctor already has an appointment at that time.");
        }
    }

    private AppointmentSlot findSlot(Long slotId) {
        return slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment slot not found with id: " + slotId));
    }

    /**
     * Reserve a slot using the repository's atomic conditional update.
     * This method must run inside the calling transaction.
     */
    private void reserveSlot(AppointmentSlot slot) {

        if (!slot.getStartTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Appointment slot must be in the future");
        }

        int updated = slotRepository.reserveIfAvailable(
                slot.getId(),
                SlotStatus.AVAILABLE,
                SlotStatus.BOOKED,
                LocalDateTime.now());

        if (updated != 1) {
            throw new IllegalStateException(
                    "Slot is unavailable or already booked");
        }

        // Synchronize the managed entity with the bulk database update.
        slot.setStatus(SlotStatus.BOOKED);
    }

    private void releaseFutureSlot(Appointment appointment) {
        if (appointment.getSlot() != null) {
            releaseFutureSlot(appointment.getSlot());
        }
    }

    private void releaseFutureSlot(AppointmentSlot slot) {
        if (slot != null
                && slot.getStatus() == SlotStatus.BOOKED
                && slot.getStartTime().isAfter(LocalDateTime.now())) {

            slot.setStatus(SlotStatus.AVAILABLE);
        }
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Appointment reason is required");
        }

        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Appointment reason must not exceed 500 characters");
        }
    }

    private AppUser findUser(String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found"));
    }
    @Transactional
    public Appointment confirmAppointment(Long id) {

        Appointment appointment = getAppointmentById(id);

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        AppUser user = findUser(email);

        // Doctors can confirm only their own appointments.
        // Admins can confirm appointments for any doctor.
        if (user.getRole() != UserRole.ADMIN) {

            if (user.getRole() != UserRole.DOCTOR
                    || appointment.getDoctor().getUser() == null
                    || !appointment.getDoctor().getUser().getEmail()
                    .equalsIgnoreCase(email)) {

                throw new AccessDeniedException(
                        "You can only confirm your own appointments");
            }
        }

        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new IllegalStateException(
                    "Only booked appointments can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment completeAppointment(Long id) {

        Appointment appointment = getAppointmentById(id);

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        AppUser user = findUser(email);

        // Doctors can complete only their own appointments.
        // Admins can complete appointments for any doctor.
        if (user.getRole() != UserRole.ADMIN) {

            if (user.getRole() != UserRole.DOCTOR
                    || appointment.getDoctor().getUser() == null
                    || !appointment.getDoctor().getUser().getEmail()
                    .equalsIgnoreCase(email)) {

                throw new AccessDeniedException(
                        "You can only complete your own appointments");
            }
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only confirmed appointments can be completed");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);

        return appointmentRepository.save(appointment);
    }
}