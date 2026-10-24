package com.bhargava.doctorappointment.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bhargava.doctorappointment.tools.AdminAppointmentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class AiAdminAppointmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiAdminAppointmentService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DOCTOR_ID = Pattern.compile("\\bdoctor\\s*(?:id\\s*)?[#:]?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AdminAppointmentTools adminAppointmentTools;

    public AiAdminAppointmentService(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            AdminAppointmentTools adminAppointmentTools) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        this.adminAppointmentTools = adminAppointmentTools;
    }

    public String answerAdminAppointmentQuestion(String adminEmail, String question) {
        if (adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated admin email is required.");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty.");
        }

        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        String conversationId = "appointment-admin-" + normalizedEmail;
        LOGGER.info("Admin AI question received: {}", question.trim());

        // Deterministic handling for common admin operations prevents a greeting
        // or a summary question from accidentally calling the slot lookup tool.
        if (isGreeting(normalized)) {
            String answer = "Hello! I'm the CarePoint Admin Assistant. I can summarize today's appointments, find appointments needing attention, show status trends, list doctors/patients, and check available slots. What would you like to review?";
            remember(conversationId, question, answer);
            return answer;
        }

        if (asksAttention(normalized)) {
            return rememberAndReturn(conversationId, question,
                    adminAppointmentTools.getAppointmentsNeedingAttention(adminEmail));
        }
        if (asksTrends(normalized)) {
            return rememberAndReturn(conversationId, question,
                    adminAppointmentTools.getAppointmentStatusTrends(adminEmail));
        }
        if (asksTodaySummary(normalized)) {
            return rememberAndReturn(conversationId, question,
                    adminAppointmentTools.summarizeTodaysAppointments(adminEmail));
        }

        if (asksAvailableSlots(normalized)) {
            Matcher dateMatcher = ISO_DATE.matcher(question);
            LocalDate requestedDate = null;
            if (dateMatcher.find()) {
                try {
                    requestedDate = LocalDate.parse(dateMatcher.group(1));
                } catch (DateTimeParseException ignored) {
                    return rememberAndReturn(conversationId, question,
                            "That date is invalid. Please use YYYY-MM-DD, for example " + LocalDate.now(APP_ZONE) + ".");
                }
            }

            Long doctorId = null;
            Matcher idMatcher = DOCTOR_ID.matcher(question);
            if (idMatcher.find()) {
                try {
                    doctorId = Long.valueOf(idMatcher.group(1));
                } catch (NumberFormatException ignored) {
                    // Ask for a valid ID below.
                }
            }
            if (doctorId == null) doctorId = adminAppointmentTools.findDoctorIdByName(adminEmail, question);

            if (doctorId == null || requestedDate == null) {
                String missing = (doctorId == null && requestedDate == null)
                        ? "Please provide the doctor's registered name or ID and the date (YYYY-MM-DD)."
                        : doctorId == null
                            ? "I couldn't identify the doctor. Please provide the registered doctor ID or full name."
                            : "Please provide the date to check in YYYY-MM-DD format.";
                return rememberAndReturn(conversationId, question, missing);
            }
            return rememberAndReturn(conversationId, question,
                    adminAppointmentTools.listAvailableSlotsForAdmin(adminEmail, doctorId, requestedDate.toString()));
        }

        if (asksAllAppointments(normalized)) {
            return rememberAndReturn(conversationId, question,
                    adminAppointmentTools.listAllAppointmentsForAdmin(adminEmail));
        }
        if (asksPatients(normalized)) {
            String answer = adminAppointmentTools.listPatientsForAdminForAi(adminEmail);
            return rememberAndReturn(conversationId, question, answer);
        }
        if (asksDoctors(normalized)) {
            String answer = adminAppointmentTools.listDoctorsForAdminForAi(adminEmail);
            return rememberAndReturn(conversationId, question, answer);
        }

        Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        String answer = chatClient.prompt()
                .system("""
                        You are CarePoint's administrator appointment-management assistant.
                        This assistant is for authorized clinic administrators, not a patient
                        healthcare FAQ chatbot. Answer briefly and use the registered tools for
                        any question about real appointments, doctors, patients, bookings, or slots.

                        RULES:
                        - A greeting must receive a greeting; never call a tool for a greeting.
                        - For today's appointments, use the today's-summary tool.
                        - For status trends, use the appointment-status-trends tool.
                        - For appointments needing attention, use the attention tool.
                        - For slot availability, require a real doctor ID/name AND date. If either
                          value is missing, ask the administrator; never guess defaults.
                        - For appointment lists, use the appointment-list tool.
                        - Never use healthcare-guide retrieval as a source for admin operations.
                        - Never invent IDs, people, appointment records, dates, slots, or statuses.
                        - Format tool output into clear plain language. Do not expose Java object names.
                        - For actions that modify records, identify the exact record and require
                          explicit administrator confirmation first.
                        """)
                .user(question.trim())
                .advisors(memoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(adminAppointmentTools)
                .toolContext(Map.of("adminEmail", adminEmail.trim()))
                .call()
                .content();

        if (answer == null || answer.isBlank()) answer = "I couldn't generate a response. Please try again.";
        // MessageChatMemoryAdvisor already persisted this model/tool exchange.
        return answer.trim();
    }

    private boolean isGreeting(String q) {
        return q.matches("^(hi|hello|hey|good morning|good afternoon|good evening|howdy)[!. ]*$");
    }
    private boolean asksAttention(String q) {
        return q.contains("need attention") || q.contains("needs attention")
                || q.contains("requiring attention") || q.contains("follow up")
                || q.contains("follow-up") || q.contains("pending appointments");
    }
    private boolean asksTrends(String q) {
        return q.contains("trend") || q.contains("status distribution")
                || q.contains("status breakdown") || q.contains("booking trends");
    }
    private boolean asksTodaySummary(String q) {
        return q.contains("today") && (q.contains("summar") || q.contains("schedule")
                || q.contains("appointments") || q.contains("appointment"));
    }
    private boolean asksAvailableSlots(String q) {
        return q.contains("available slot") || q.contains("availability")
                || q.contains("slot availability") || q.contains("free slot")
                || q.contains("available appointment times");
    }
    private boolean asksAllAppointments(String q) {
        return (q.contains("appointment") || q.contains("booking"))
                && (q.contains("list") || q.contains("show all") || q.contains("view all")
                    || q.contains("all appointments") || q.contains("appointment list"));
    }
    private boolean asksPatients(String q) {
        return q.contains("list patients") || q.contains("show patients") || q.equals("patients");
    }
    private boolean asksDoctors(String q) {
        return q.contains("list doctors") || q.contains("show doctors") || q.equals("doctors");
    }

    private String rememberAndReturn(String conversationId, String question, String answer) {
        remember(conversationId, question, answer);
        return answer;
    }

    private void remember(String conversationId, String question, String answer) {
        chatMemory.add(conversationId, List.of(
                new org.springframework.ai.chat.messages.UserMessage(question),
                new org.springframework.ai.chat.messages.AssistantMessage(answer)));
    }
}
