package com.bhargava.doctorappointment.service;

import com.bhargava.doctorappointment.tools.DoctorAppointmentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiDoctorAppointmentService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final DoctorAppointmentTools doctorTools;

    public AiDoctorAppointmentService(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            DoctorAppointmentTools doctorTools) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        this.doctorTools = doctorTools;
    }

    public String answerDoctorQuestion(String doctorEmail, String question) {
        if (doctorEmail == null || doctorEmail.isBlank()) {
            return "Your authenticated doctor identity is unavailable.";
        }
        if (question == null || question.isBlank()) {
            return "Please enter an appointment or availability question.";
        }

        String normalized = question.trim().toLowerCase(Locale.ROOT);
        String conversationId = "doctor-assistant-" + doctorEmail.trim().toLowerCase(Locale.ROOT);

        if (isGreeting(normalized)) {
            String answer = "Hello! I'm your CarePoint Doctor Assistant. I can show your appointments, check your availability, help add or remove availability slots, and update appointment statuses. What would you like to do?";
            remember(conversationId, question, answer);
            return answer;
        }
        if ((normalized.contains("how do i manage") || normalized.contains("how can i manage")
                || normalized.contains("how to manage")) && normalized.contains("availability")) {
            String answer = "You can ask me to ‘show my availability’, ‘add a slot for YYYY-MM-DDTHH:mm’, or ‘remove available slot ID 12’. I'll show the exact slot and ask you to confirm before adding or removing availability. Your appointments and slots are restricted to your authenticated doctor account.\n\n"
                    + doctorTools.listMyAvailabilityForDoctor(doctorEmail);
            remember(conversationId, question, answer);
            return answer;
        }

        if (normalized.contains("need attention") || normalized.contains("needs attention")
                || normalized.contains("pending appointments") || normalized.contains("follow up")) {
            String answer = doctorTools.listAppointmentsNeedingAttentionForDoctor(doctorEmail);
            remember(conversationId, question, answer);
            return answer;
        }
        if (normalized.contains("today's appointment") || normalized.contains("todays appointment")) {
            String answer = doctorTools.listTodaysAppointmentsForDoctor(doctorEmail);
            remember(conversationId, question, answer);
            return answer;
        }
        if (normalized.contains("my appointment") || normalized.equals("appointments")
                || normalized.contains("upcoming appointment")) {
            String answer = doctorTools.listMyAppointments(doctorEmail);
            remember(conversationId, question, answer);
            return answer;
        }
        if (normalized.contains("my availability") || normalized.contains("available slots")
                || normalized.contains("show my slots") || normalized.contains("my slots")) {
            String answer = doctorTools.listMyAvailabilityForDoctor(doctorEmail);
            remember(conversationId, question, answer);
            return answer;
        }

        Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        String answer = chatClient.prompt()
                .system("""
                        You are the authenticated doctor's CarePoint appointment assistant.
                        Your purpose is doctor operations, not generic patient medical advice.

                        APPOINTMENTS:
                        - For questions about the logged-in doctor's appointments, call
                          getMyDoctorAppointments. Never invent appointment records.
                        - Confirm or complete appointments only using the respective tool
                          and only after the doctor confirms the exact appointment ID.
                        - Never disclose appointments assigned to another doctor.

                        AVAILABILITY:
                        - For current availability, call listMyAvailability.
                        - Doctors can add future slots in YYYY-MM-DDTHH:mm format.
                        - Before creating/removing slots, show the exact date/time or slot ID
                          and ask for explicit confirmation. Never guess IDs or dates.
                        - A doctor can remove only their own future AVAILABLE slot; booked
                          slots cannot be removed.
                        - Do not ask for the doctor's ID or email; the server supplies the
                          authenticated identity to tools.

                        GENERAL:
                        - Greet naturally. If a request is ambiguous, ask one clear question.
                        - Use simple English, concise answers, and real tool results.
                        - Do not answer operational questions using the healthcare RAG guide.
                        """)
                .user(question.trim())
                .advisors(memoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(doctorTools)
                .toolContext(Map.of("doctorEmail", doctorEmail.trim()))
                .call()
                .content();

        if (answer == null || answer.isBlank()) answer = "I couldn't generate a response. Please try again.";
        // MessageChatMemoryAdvisor already persisted this model/tool exchange.
        return answer.trim();
    }

    private boolean isGreeting(String value) {
        return value.matches("^(hi|hello|hey|good morning|good afternoon|good evening|howdy)[!. ]*$");
    }

    private void remember(String conversationId, String question, String answer) {
        chatMemory.add(conversationId, List.of(
                new org.springframework.ai.chat.messages.UserMessage(question),
                new org.springframework.ai.chat.messages.AssistantMessage(answer)));
    }
}
