package com.bhargava.doctorappointment.dto;

import com.bhargava.doctorappointment.entity.AppointmentSlot;
import com.bhargava.doctorappointment.entity.SlotStatus;
import java.time.LocalDateTime;

public record SlotResponse(
        Long id,
        Long doctorId,
        LocalDateTime startTime,
        SlotStatus status
) {
    public static SlotResponse from(AppointmentSlot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getDoctor().getId(),
                slot.getStartTime(),
                slot.getStatus()
        );
    }
}