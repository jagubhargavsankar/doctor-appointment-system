package com.bhargava.doctorappointment.service;

import com.bhargava.doctorappointment.dto.DoctorCreateRequest;
import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.entity.Doctor;
import com.bhargava.doctorappointment.enums.UserRole;
import com.bhargava.doctorappointment.exception.ResourceNotFoundException;
import com.bhargava.doctorappointment.repository.AppUserRepository;
import com.bhargava.doctorappointment.repository.DoctorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import com.bhargava.doctorappointment.repository.AppointmentRepository;
import com.bhargava.doctorappointment.repository.AppointmentSlotRepository;
@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    public DoctorService(
            DoctorRepository doctorRepository,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository appointmentSlotRepository) {

        this.doctorRepository = doctorRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.appointmentRepository = appointmentRepository;
        this.appointmentSlotRepository = appointmentSlotRepository;
    }

    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    public Doctor getDoctorById(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + id));
    }

    @Transactional
    public Doctor createDoctor(DoctorCreateRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        AppUser user = new AppUser(email, passwordEncoder.encode(request.getPassword()), UserRole.DOCTOR);
        user = appUserRepository.save(user);

        Doctor doctor = new Doctor();
        doctor.setName(request.getName().trim());
        doctor.setSpecialization(request.getSpecialization().trim());
        doctor.setQualification(request.getQualification());
        doctor.setExperienceYears(request.getExperienceYears());
        doctor.setConsultationFee(request.getConsultationFee());
        doctor.setRating(0.0);
        doctor.setUser(user);
        return doctorRepository.save(doctor);
    }

    public Doctor updateDoctor(Long id, Doctor details) {
        Doctor doctor = getDoctorById(id);
        doctor.setName(details.getName());
        doctor.setSpecialization(details.getSpecialization());
        doctor.setQualification(details.getQualification());
        doctor.setExperienceYears(details.getExperienceYears());
        doctor.setConsultationFee(details.getConsultationFee());
        // Rating is not accepted from an admin update payload; it should come from verified reviews.
        return doctorRepository.save(doctor);
    }

    @Transactional
    public void deleteDoctor(Long id) {

        Doctor doctor = getDoctorById(id);

        // Preserve all appointment history.
        if (appointmentRepository.existsByDoctor_Id(id)) {
            throw new IllegalStateException(
                    "Cannot delete this doctor because appointment records exist."
            );
        }

        AppUser user = doctor.getUser();

        // Remove slots first because they reference the doctor.
        appointmentSlotRepository.deleteByDoctor_Id(id);

        // Delete the doctor before deleting the linked account.
        doctorRepository.delete(doctor);

        if (user != null) {
            appUserRepository.delete(user);
        }
    }

    public Page<Doctor> getDoctorsPaginated(int page, int size) {
        validatePagination(page, size);

        return doctorRepository.findAll(
                PageRequest.of(page, size)
        );
    }

    public Page<Doctor> recommendDoctors(
            String problem, int page, int size) {

        validatePagination(page, size);

        if (problem == null || problem.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter your health problem"
            );
        }

        String specialization = findSpecialization(problem);

        if (specialization == null) {
            throw new IllegalArgumentException(
                    "Could not match this problem to a specialization. " +
                            "Please search doctors by specialization."
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        return doctorRepository
                .findBySpecializationContainingIgnoreCase(
                        specialization, pageable
                );
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 50) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 50"
            );
        }
    }

    private String findSpecialization(String problem) {
        String text = problem.toLowerCase(Locale.ROOT);

        Map<String, String> keywords = Map.ofEntries(
                Map.entry("toothache", "Dentist"),
                Map.entry("tooth pain", "Dentist"),
                Map.entry("gum", "Dentist"),
                Map.entry("skin", "Dermatologist"),
                Map.entry("rash", "Dermatologist"),
                Map.entry("itch", "Dermatologist"),
                Map.entry("heart", "Cardiologist"),
                Map.entry("chest pain", "Cardiologist"),
                Map.entry("knee", "Orthopedic"),
                Map.entry("bone", "Orthopedic"),
                Map.entry("joint", "Orthopedic"),
                Map.entry("child", "Pediatrician"),
                Map.entry("baby", "Pediatrician"),
                Map.entry("headache", "Neurologist"),
                Map.entry("migraine", "Neurologist"),
                Map.entry("nerve", "Neurologist"),
                Map.entry("fever", "General Physician"),
                Map.entry("cold", "General Physician"),
                Map.entry("cough", "General Physician")
        );

        // Check longer phrases first, so "chest pain"
        // is matched before a shorter keyword if needed.
        return keywords.entrySet().stream()
                .filter(entry -> text.contains(entry.getKey()))
                .max(java.util.Comparator.comparingInt(
                        entry -> entry.getKey().length()
                ))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

}
