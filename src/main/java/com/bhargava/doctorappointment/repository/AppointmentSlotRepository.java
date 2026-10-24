
package com.bhargava.doctorappointment.repository;

import com.bhargava.doctorappointment.entity.AppointmentSlot;
import com.bhargava.doctorappointment.entity.SlotStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentSlotRepository
        extends JpaRepository<AppointmentSlot, Long> {

    // Find available slots for a doctor from a given time
    List<AppointmentSlot>
    findByDoctor_IdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            Long doctorId,
            SlotStatus status,
            LocalDateTime from
    );

    // Find all slots for a doctor within a time range
    List<AppointmentSlot>
    findByDoctor_IdAndStartTimeBetweenOrderByStartTimeAsc(
            Long doctorId,
            LocalDateTime start,
            LocalDateTime end
    );
    void deleteByDoctor_Id(Long doctorId);
    // Find a slot belonging to a specific doctor
    Optional<AppointmentSlot> findByIdAndDoctor_Id(
            Long slotId,
            Long doctorId
    );

    // NEW: Find future slots belonging to a doctor, regardless of status
    List<AppointmentSlot>
    findByDoctor_IdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
            Long doctorId,
            LocalDateTime from
    );

    // Atomically reserve a slot only if it is still available
    @Modifying
    @Query("""
        update AppointmentSlot s
        set s.status = :booked
        where s.id = :slotId
          and s.status = :available
          and s.startTime > :now
    """)
    int reserveIfAvailable(
            @Param("slotId") Long slotId,
            @Param("available") SlotStatus available,
            @Param("booked") SlotStatus booked,
            @Param("now") LocalDateTime now
    );

    // Check whether this doctor already has a slot at that time
    boolean existsByDoctor_IdAndStartTime(
            Long doctorId,
            LocalDateTime startTime
    );
}
