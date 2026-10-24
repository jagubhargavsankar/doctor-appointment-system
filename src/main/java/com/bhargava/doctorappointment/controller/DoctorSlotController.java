package com.bhargava.doctorappointment.controller;

import com.bhargava.doctorappointment.dto.CreateSlotRequest;
import com.bhargava.doctorappointment.dto.SlotResponse;
import com.bhargava.doctorappointment.service.AppointmentSlotService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/doctor-slots")
public class DoctorSlotController {

    private final AppointmentSlotService slotService;

    public DoctorSlotController(
            AppointmentSlotService slotService) {
        this.slotService = slotService;
    }
    // Admin: create a slot for any doctor
    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlotResponse> createSlotForAnyDoctor(
            @RequestParam Long doctorId,
            @Valid @RequestBody CreateSlotRequest request) {

        SlotResponse response =
                slotService.createSlot(doctorId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Patients can view available slots.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')")
    public List<SlotResponse> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        if (date == null) {
            return slotService.getAvailableSlots(doctorId);
        }

        return slotService.getAvailableSlots(doctorId, date);
    }

    // A doctor can view their own slots.
    @GetMapping("/mine")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<SlotResponse> getMySlots(
            Authentication authentication) {

        return slotService.getMySlots(
                authentication.getName()
        );
    }

    // Create a slot for the logged-in doctor.
    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<SlotResponse> createSlot(
            @Valid @RequestBody CreateSlotRequest request,
            Authentication authentication) {

        SlotResponse response =
                slotService.createSlotForDoctor(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    // Admin: delete an available slot belonging to any doctor
    @DeleteMapping("/{slotId}/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> deleteSlotAsAdmin(
            @PathVariable Long slotId) {

        slotService.deleteAvailableSlotByAdmin(slotId);

        return Map.of(
                "message",
                "Available slot deleted successfully."
        );
    }

    // Delete an available slot owned by the logged-in doctor.
    @DeleteMapping("/{slotId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public Map<String, String> deleteAvailableSlot(
            @PathVariable Long slotId,
            Authentication authentication) {

        slotService.deleteAvailableSlot(
                slotId,
                authentication.getName()
        );

        return Map.of(
                "message",
                "Available slot deleted successfully."
        );
    }
}