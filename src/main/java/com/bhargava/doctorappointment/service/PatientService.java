package com.bhargava.doctorappointment.service;

import com.bhargava.doctorappointment.dto.UpdatePatientProfileRequest;
import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.entity.Patient;
import com.bhargava.doctorappointment.exception.ResourceNotFoundException;
import com.bhargava.doctorappointment.repository.AppUserRepository;
import com.bhargava.doctorappointment.repository.AppointmentRepository;
import com.bhargava.doctorappointment.repository.PatientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final AppUserRepository appUserRepository;
    private final AppointmentRepository appointmentRepository;

    public PatientService(
            PatientRepository patientRepository,
            AppUserRepository appUserRepository,
            AppointmentRepository appointmentRepository) {

        this.patientRepository = patientRepository;
        this.appUserRepository = appUserRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Patient getPatientById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient not found with id: " + id));
    }

    public Patient getPatientByEmail(String email) {
        return patientRepository.findByUser_EmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient profile not found for this account"));
    }

    public Patient createPatient(Patient patient) {
        patient.setId(null);
        patient.setUser(null);
        return patientRepository.save(patient);
    }

    @Transactional
    public Patient updateMyProfile(
            String email,
            UpdatePatientProfileRequest details) {

        Patient patient = getPatientByEmail(email);

        patient.setName(details.getName().trim());
        patient.setPhone(details.getPhone().trim());

        return patientRepository.save(patient);
    }

    @Transactional
    public Patient updatePatient(Long id, Patient details) {

        Patient patient = getPatientById(id);

        String updatedEmail = details.getEmail()
                .trim()
                .toLowerCase();

        AppUser linkedUser = patient.getUser();

        if (linkedUser != null
                && !linkedUser.getEmail()
                .equalsIgnoreCase(updatedEmail)) {

            boolean emailBelongsToAnotherAccount =
                    appUserRepository.findByEmailIgnoreCase(updatedEmail)
                            .filter(existingUser ->
                                    !existingUser.getId()
                                            .equals(linkedUser.getId()))
                            .isPresent();

            if (emailBelongsToAnotherAccount) {
                throw new IllegalArgumentException(
                        "An account with this email already exists");
            }

            linkedUser.setEmail(updatedEmail);
            appUserRepository.save(linkedUser);
        }

        patient.setName(details.getName().trim());
        patient.setEmail(updatedEmail);
        patient.setPhone(details.getPhone().trim());

        return patientRepository.save(patient);
    }

    @Transactional
    public void deletePatient(Long id) {

        Patient patient = getPatientById(id);

        // Preserve all appointment history.
        if (appointmentRepository.existsByPatient_Id(id)) {
            throw new IllegalStateException(
                    "Cannot delete this patient because appointment records exist.");
        }

        AppUser user = patient.getUser();

        // Delete the patient profile before its linked account.
        patientRepository.delete(patient);

        if (user != null) {
            appUserRepository.delete(user);
        }
    }
}