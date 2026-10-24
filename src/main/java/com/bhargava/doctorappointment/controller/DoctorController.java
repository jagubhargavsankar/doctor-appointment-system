
package com.bhargava.doctorappointment.controller;

import com.bhargava.doctorappointment.dto.DoctorCreateRequest;
import com.bhargava.doctorappointment.entity.Doctor;
import com.bhargava.doctorappointment.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    // View all doctors with pagination
    @GetMapping
    public Page<Doctor> getAllDoctors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        return doctorService.getDoctorsPaginated(page, size);
    }

    // Recommend doctors based on the patient's problem
    @GetMapping("/recommend")
    public Page<Doctor> recommendDoctors(
            @RequestParam String problem,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        return doctorService.recommendDoctors(problem, page, size);
    }

    // View one doctor
    @GetMapping("/{id}")
    public Doctor getDoctorById(@PathVariable Long id) {
        return doctorService.getDoctorById(id);
    }

    // Admin: create a doctor
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Doctor> createDoctor(
            @Valid @RequestBody DoctorCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(doctorService.createDoctor(request));
    }

    // Admin: update a doctor
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Doctor updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody Doctor doctor) {

        return doctorService.updateDoctor(id, doctor);
    }

    // Admin: delete a doctor
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Map<String, String> deleteDoctor(@PathVariable Long id) {
        doctorService.deleteDoctor(id);
        return Map.of("message", "Doctor deleted successfully");
    }
}
