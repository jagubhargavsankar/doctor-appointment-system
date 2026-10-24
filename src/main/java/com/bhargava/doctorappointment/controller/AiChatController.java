
package com.bhargava.doctorappointment.controller;

import java.util.List;
import java.util.Locale;

import com.bhargava.doctorappointment.service.AiAdminAppointmentService;
import com.bhargava.doctorappointment.service.AiDoctorAppointmentService;
import com.bhargava.doctorappointment.enums.UserRole;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.bhargava.doctorappointment.dto.AiChatRequest;
import com.bhargava.doctorappointment.dto.AiChatResponse;
import com.bhargava.doctorappointment.entity.AppUser;
import com.bhargava.doctorappointment.entity.Doctor;
import com.bhargava.doctorappointment.service.AiAppointmentService;
import com.bhargava.doctorappointment.service.AiChatService;
import com.bhargava.doctorappointment.service.DoctorRecommendationService;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;
    private final DoctorRecommendationService doctorRecommendationService;
    private final ChatMemory chatMemory;
    private final AiAppointmentService aiAppointmentService;
    private final AiAdminAppointmentService aiAdminAppointmentService;
    private final AiDoctorAppointmentService aiDoctorAppointmentService;

    public AiChatController(
            AiChatService aiChatService,
            DoctorRecommendationService doctorRecommendationService,
            ChatMemory chatMemory,
            AiAppointmentService aiAppointmentService,
            AiAdminAppointmentService aiAdminAppointmentService,
            AiDoctorAppointmentService aiDoctorAppointmentService) {

        this.aiChatService = aiChatService;
        this.doctorRecommendationService = doctorRecommendationService;
        this.chatMemory = chatMemory;
        this.aiAppointmentService = aiAppointmentService;
        this.aiAdminAppointmentService = aiAdminAppointmentService;
        this.aiDoctorAppointmentService = aiDoctorAppointmentService;
    }
    // Existing healthcare chatbot
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @RequestBody AiChatRequest request,
            Authentication authentication) {

        String question = request.getQuestion();

        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Route by the authenticated role even when an existing frontend calls
        // the shared /chat URL. Each role gets a distinct assistant/tool set.
        if (user.getRole() == UserRole.ADMIN) {
            String answer = aiAdminAppointmentService.answerAdminAppointmentQuestion(
                    user.getEmail(), question);
            return ResponseEntity.ok(new AiChatResponse(question, answer));
        }
        if (user.getRole() == UserRole.DOCTOR) {
            String answer = aiDoctorAppointmentService.answerDoctorQuestion(
                    user.getEmail(), question);
            return ResponseEntity.ok(new AiChatResponse(question, answer));
        }

        String conversationId = "patient-" + user.getId();
        String normalized = question.toLowerCase(Locale.ROOT);

        // Handle doctor recommendation questions.
        if (isDoctorRequest(normalized)) {

            String specialization = findSpecialization(question);

            if (specialization == null) {
                specialization =
                        findSpecializationInHistory(conversationId);
            }

            String answer;

            if (specialization == null) {

                answer = "I can recommend a doctor from the specialties "
                        + "available in the system: Dentist, Dermatologist, "
                        + "Cardiologist, Orthopedic, and General Physician. "
                        + "Please tell me what health issue you need help with.";

            } else {

                List<Doctor> doctors =
                        doctorRecommendationService
                                .recommendDoctors(specialization);

                if (doctors.isEmpty()) {

                    answer = "I couldn't find a matching doctor for "
                            + specialization + " in the system.";

                } else {

                    answer = doctors.stream()
                            .map(doctor ->
                                    "Name: " + doctor.getName()
                                            + ", Specialization: "
                                            + doctor.getSpecialization()
                                            + ", Qualification: "
                                            + doctor.getQualification()
                                            + ", Experience: "
                                            + doctor.getExperienceYears()
                                            + " years"
                                            + ", Consultation fee: "
                                            + doctor.getConsultationFee()
                                            + ", Rating: "
                                            + doctor.getRating())
                            .reduce(
                                    (first, second) ->
                                            first + "\n" + second)
                            .orElse("No matching doctors found.");
                }
            }

            chatMemory.add(
                    conversationId,
                    List.of(
                            new UserMessage(question),
                            new AssistantMessage(answer)
                    )
            );

            return ResponseEntity.ok(
                    new AiChatResponse(question, answer));
        }

        // Existing RAG and chat-memory functionality.
        String answer =
                aiChatService.askQuestion(user.getId(), question);

        return ResponseEntity.ok(
                new AiChatResponse(question, answer));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin-appointment-assistant")
    public ResponseEntity<AiChatResponse> adminAppointmentAssistant(
            @RequestBody AiChatRequest request,
            Authentication authentication) {

        if (request.getQuestion() == null
                || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String question = request.getQuestion();

        String answer = aiAdminAppointmentService.answerAdminAppointmentQuestion(
                user.getEmail(),
                question
        );

        return ResponseEntity.ok(new AiChatResponse(question, answer));
    }
    // Explicit doctor assistant endpoint. The shared /chat endpoint is also
    // routed by authenticated role for backward compatibility with the React app.
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/doctor-appointment-assistant")
    public ResponseEntity<AiChatResponse> doctorAppointmentAssistant(
            @RequestBody AiChatRequest request,
            Authentication authentication) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppUser user)
                || user.getRole() != UserRole.DOCTOR) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String question = request.getQuestion();
        String answer = aiDoctorAppointmentService.answerDoctorQuestion(user.getEmail(), question);
        return ResponseEntity.ok(new AiChatResponse(question, answer));
    }

    // New AI appointment assistant
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/appointment-assistant")
    public ResponseEntity<AiChatResponse> appointmentAssistant(
            @RequestBody AiChatRequest request,
            Authentication authentication) {

        if (request.getQuestion() == null
                || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String question = request.getQuestion();

        String answer =
                aiAppointmentService.answerAppointmentQuestion(
                        user.getEmail(),
                        question
                );

        return ResponseEntity.ok(
                new AiChatResponse(question, answer));
    }

    private boolean isDoctorRequest(String question) {

        return question.contains("doctor")
                || question.contains("dermatologist")
                || question.contains("cardiologist")
                || question.contains("dentist")
                || question.contains("orthopedic")
                || question.contains("general physician")
                || question.contains("recommend")
                || question.contains("specialist")
                || question.contains("doctors available");
    }

    private String findSpecialization(String text) {

        String normalized = text.toLowerCase(Locale.ROOT);

        if (normalized.contains("dermatolog")
                || normalized.contains("skin")
                || normalized.contains("eczema")) {
            return "Dermatologist";
        }

        if (normalized.contains("dent")
                || normalized.contains("tooth")
                || normalized.contains("teeth")
                || normalized.contains("gum")) {
            return "Dentist";
        }

        if (normalized.contains("cardiolog")
                || normalized.contains("heart")
                || normalized.contains("blood pressure")) {
            return "Cardiologist";
        }

        if (normalized.contains("orthoped")
                || normalized.contains("knee")
                || normalized.contains("joint")
                || normalized.contains("bone")) {
            return "Orthopedic";
        }

        if (normalized.contains("general physician")
                || normalized.contains("fever")
                || normalized.contains("cough")
                || normalized.contains("common cold")) {
            return "General Physician";
        }

        return null;
    }

    private String findSpecializationInHistory(String conversationId) {

        List<Message> history = chatMemory.get(conversationId);

        for (int i = history.size() - 1; i >= 0; i--) {

            Message message = history.get(i);

            if (message instanceof UserMessage) {

                String specialization =
                        findSpecialization(message.getText());

                if (specialization != null) {
                    return specialization;
                }
            }
        }

        return null;
    }
}
