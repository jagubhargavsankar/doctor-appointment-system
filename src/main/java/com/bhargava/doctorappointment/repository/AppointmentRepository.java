package com.bhargava.doctorappointment.repository;

import com.bhargava.doctorappointment.entity.Appointment;
import com.bhargava.doctorappointment.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Long> {

    boolean existsByDoctor_IdAndAppointmentDateTimeAndStatus(
            Long doctorId,
            LocalDateTime appointmentDateTime,
            AppointmentStatus status
    );

    boolean existsByDoctor_IdAndAppointmentDateTimeAndStatusIn(
            Long doctorId,
            LocalDateTime appointmentDateTime,
            Collection<AppointmentStatus> statuses
    );

    boolean existsByDoctor_IdAndAppointmentDateTimeAndStatusAndIdNot(
            Long doctorId,
            LocalDateTime appointmentDateTime,
            AppointmentStatus status,
            Long id
    );

    boolean existsByDoctor_IdAndAppointmentDateTimeAndStatusInAndIdNot(
            Long doctorId,
            LocalDateTime appointmentDateTime,
            Collection<AppointmentStatus> statuses,
            Long id
    );

    List<Appointment> findByPatient_IdOrderByAppointmentDateTimeDesc(
            Long patientId
    );

    List<Appointment> findByDoctor_IdOrderByAppointmentDateTimeDesc(
            Long doctorId
    );

    // Check all appointment records, including cancelled or completed ones.
    boolean existsByDoctor_Id(Long doctorId);

    boolean existsByPatient_Id(Long patientId);
}