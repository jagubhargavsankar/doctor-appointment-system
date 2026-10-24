package com.bhargava.doctorappointment.repository;

import com.bhargava.doctorappointment.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUser_Id(Long userId);
    Optional<Patient> findByUser_EmailIgnoreCase(String email);
}
