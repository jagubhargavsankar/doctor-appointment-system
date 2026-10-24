package com.bhargava.doctorappointment.controller;

import com.bhargava.doctorappointment.dto.AppointmentRequest;
import com.bhargava.doctorappointment.entity.Appointment;
import com.bhargava.doctorappointment.service.AppointmentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(
            AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentService.getAllAppointments();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT', 'DOCTOR')")
    @GetMapping("/{id}")
    public Appointment getAppointmentById(
            @PathVariable Long id,
            Authentication authentication) {

        return appointmentService.getAppointmentByIdForCurrentUser(
                id, authentication.getName());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT')")
    @GetMapping("/mine")
    public List<Appointment> getMyAppointments(
            Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return appointmentService.getAllAppointments();
        }

        return appointmentService.getMyAppointments(
                authentication.getName());
    }
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @GetMapping("/doctor")
    public List<Appointment> getDoctorAppointments(
            Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return appointmentService.getAllAppointments();
        }

        return appointmentService.getDoctorAppointments(
                authentication.getName());
    }
    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT')")
    @PostMapping
    public ResponseEntity<Appointment> createAppointment(
            @Valid @RequestBody AppointmentRequest request,
            Authentication authentication) {

        Appointment appointment =
                appointmentService.createAppointment(
                        request, authentication.getName());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(appointment);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT')")
    @PatchMapping("/{id}/cancel")
    public Appointment cancelAppointment(
            @PathVariable Long id,
            Authentication authentication) {

        return appointmentService.cancelAppointment(
                id, authentication.getName());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Appointment updateAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentRequest request) {

        return appointmentService.updateAppointment(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Map<String, String> deleteAppointment(
            @PathVariable Long id) {

        appointmentService.deleteAppointment(id);

        return Map.of(
                "message",
                "Appointment deleted successfully");
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @PatchMapping("/{id}/confirm")
    public Appointment confirmAppointment(
            @PathVariable Long id) {

        return appointmentService.confirmAppointment(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @PatchMapping("/{id}/complete")
    public Appointment completeAppointment(
            @PathVariable Long id) {

        return appointmentService.completeAppointment(id);
    }
}