
package com.bhargava.doctorappointment.service;

import com.bhargava.doctorappointment.dto.CreateSlotRequest;
import com.bhargava.doctorappointment.dto.SlotResponse;
import com.bhargava.doctorappointment.entity.*;
import com.bhargava.doctorappointment.exception.ResourceNotFoundException;
import com.bhargava.doctorappointment.repository.AppointmentSlotRepository;
import com.bhargava.doctorappointment.repository.DoctorRepository;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentSlotService {

    private final AppointmentSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;

    public AppointmentSlotService(
            AppointmentSlotRepository slotRepository,
            DoctorRepository doctorRepository) {
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
    }

    // Create a slot using a doctor ID
    @Transactional
    public SlotResponse createSlot(
            Long doctorId,
            CreateSlotRequest request) {

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Doctor not found"));

        LocalDateTime startTime = request.startTime();

        if (!startTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Slot must be in the future");
        }

        if (slotRepository.existsByDoctor_IdAndStartTime(
                doctorId, startTime)) {
            throw new IllegalArgumentException(
                    "This doctor already has a slot at that time");
        }

        AppointmentSlot slot =
                new AppointmentSlot(doctor, startTime);

        return SlotResponse.from(slotRepository.save(slot));
    }
    @Transactional
    public void deleteAvailableSlotByAdmin(Long slotId) {

        AppointmentSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Slot not found"));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Only available slots can be deleted");
        }

        if (!slot.getStartTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "Past slots cannot be deleted");
        }

        slotRepository.delete(slot);
    }

    // NEW METHOD A: Create a slot for the logged-in doctor
    @Transactional
    public SlotResponse createSlotForDoctor(
            String email,
            CreateSlotRequest request) {

        Doctor doctor = doctorRepository
                .findByUser_EmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Doctor profile not found for this account"));

        return createSlot(doctor.getId(), request);
    }

    // NEW METHOD B: View slots belonging to the logged-in doctor
    @Transactional
    public List<SlotResponse> getMySlots(String email) {

        Doctor doctor = doctorRepository
                .findByUser_EmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Doctor profile not found for this account"));

        return slotRepository
                .findByDoctor_IdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                        doctor.getId(),
                        LocalDateTime.now())
                .stream()
                .map(SlotResponse::from)
                .toList();
    }

    // Release a future slot
    @Transactional
    public void releaseSlot(Long slotId) {

        AppointmentSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Slot not found"));

        if (slot.getStartTime().isAfter(LocalDateTime.now())) {
            slot.setStatus(SlotStatus.AVAILABLE);
        }
    }

    // Get all available future slots for a doctor

    // Get all available future slots for a doctor
    @Transactional
    public List<SlotResponse> getAvailableSlots(Long doctorId) {

        if (doctorId == null) {
            throw new IllegalArgumentException(
                    "Doctor ID is required.");
        }

        return slotRepository
                .findByDoctor_IdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                        doctorId,
                        SlotStatus.AVAILABLE,
                        LocalDateTime.now())
                .stream()
                .map(SlotResponse::from)
                .toList();
    }
    // Get available slots for a doctor on a specific date
    @Transactional
    public List<SlotResponse> getAvailableSlots(
            Long doctorId,
            LocalDate date) {

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        if (date.equals(LocalDate.now())) {
            start = now;
        }

        if (date.isBefore(LocalDate.now())) {
            return List.of();
        }

        return slotRepository
                .findByDoctor_IdAndStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                        doctorId,
                        SlotStatus.AVAILABLE,
                        start)
                .stream()
                .filter(slot -> slot.getStartTime().isBefore(end))
                .map(SlotResponse::from)
                .toList();
    }

    // Reserve a slot only if it is still available
    @Transactional
    public AppointmentSlot reserveSlot(Long slotId) {

        int updated = slotRepository.reserveIfAvailable(
                slotId,
                SlotStatus.AVAILABLE,
                SlotStatus.BOOKED,
                LocalDateTime.now());

        if (updated == 0) {
            throw new IllegalStateException(
                    "Slot is unavailable or already booked");
        }

        return slotRepository.findById(slotId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Slot not found"));
    }

    // NEW METHOD C: Delete an available slot owned by the logged-in doctor
    @Transactional
    public void deleteAvailableSlot(
            Long slotId,
            String email) {

        Doctor doctor = doctorRepository
                .findByUser_EmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Doctor profile not found for this account"));

        AppointmentSlot slot = slotRepository
                .findByIdAndDoctor_Id(slotId, doctor.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Slot not found for this doctor"));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Only available slots can be deleted");
        }

        if (!slot.getStartTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "Past slots cannot be deleted");
        }

        slotRepository.delete(slot);
    }
}
