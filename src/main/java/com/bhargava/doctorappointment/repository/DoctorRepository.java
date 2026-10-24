
package com.bhargava.doctorappointment.repository;

import com.bhargava.doctorappointment.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUser_Id(Long userId);

    Optional<Doctor> findByUser_EmailIgnoreCase(String email);

    Page<Doctor> findBySpecializationContainingIgnoreCase(
            String specialization,
            Pageable pageable
    );

    List<Doctor> findBySpecializationContainingIgnoreCase(
            String specialization
    );
}
