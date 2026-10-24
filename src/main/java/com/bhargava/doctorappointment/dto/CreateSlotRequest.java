package com.bhargava.doctorappointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateSlotRequest(
        @NotNull @Future LocalDateTime startTime
) {
}