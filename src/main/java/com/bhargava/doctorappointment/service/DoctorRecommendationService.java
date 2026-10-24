
package com.bhargava.doctorappointment.service;

import com.bhargava.doctorappointment.entity.Doctor;
import com.bhargava.doctorappointment.repository.DoctorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class DoctorRecommendationService {

    private final DoctorRepository doctorRepository;

    public DoctorRecommendationService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    public List<Doctor> recommendDoctors(String question) {

        String text = question.toLowerCase(Locale.ROOT);

        String specialization = findSpecialization(text);

        if (specialization == null) {
            return List.of();
        }

        return doctorRepository
                .findBySpecializationContainingIgnoreCase(specialization);
    }


    private String findSpecialization(String question) {
        if (question.contains("skin")
                || question.contains("dermatologist")
                || question.contains("dermatology")
                || question.contains("skin allergy")) {
            return "Dermatologist";
        }

        if (question.contains("heart")
                || question.contains("cardiologist")
                || question.contains("cardiology")) {
            return "Cardiologist";
        }

        if (question.contains("child")
                || question.contains("pediatrician")
                || question.contains("paediatrician")
                || question.contains("pediatrics")) {
            return "Pediatrician";
        }

        if (question.contains("teeth")
                || question.contains("tooth")
                || question.contains("dentist")
                || question.contains("dental")) {
            return "Dentist";
        }

        if (question.contains("bone")
                || question.contains("joint")
                || question.contains("orthopedic")
                || question.contains("orthopaedic")) {
            return "Orthopedic";
        }

        if (question.contains("general physician")
                || question.contains("general doctor")
                || question.contains("fever")
                || question.contains("cold")) {
            return "General Physician";
        }

        return null;
    }
}
