package com.bhargava.doctorappointment.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bhargava.doctorappointment.tools.AppointmentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class AiAppointmentService {

    private final ChatClient chatClient;
    private final AppointmentTools appointmentTools;
    private final ChatMemory chatMemory;

    public AiAppointmentService(
            ChatClient.Builder chatClientBuilder,
            AppointmentTools appointmentTools,
            ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.build();
        this.appointmentTools = appointmentTools;
        this.chatMemory = chatMemory;
    }

    public String answerAppointmentQuestion(String patientEmail, String question) {
        if (patientEmail == null || patientEmail.isBlank()) {
            return "Your authenticated patient identity is unavailable.";
        }
        if (question == null || question.isBlank()) {
            return "Please enter an appointment-related question.";
        }

        String normalized = question.trim().toLowerCase(Locale.ROOT);
        String conversationId = "appointment-patient-" + patientEmail.trim().toLowerCase(Locale.ROOT);

        if (isGreeting(normalized)) {
            String answer = "Hi! I'm your CarePoint Patient Assistant. I can help you find doctors, check real appointment slots, book an appointment, view your appointments, cancel a booking, or reschedule an existing appointment. What would you like to do?";
            remember(conversationId, question, answer);
            return answer;
        }

        Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        String answer = chatClient.prompt()
                .system("""
                    You are the CarePoint patient appointment assistant. You can use the
                    appointment-management tools provided in this request. You are not
                    limited to healthcare FAQs: appointment booking, viewing, cancellation,
                    and rescheduling are core tasks.

                    CONVERSATION:
                    - Remember information from this patient's current conversation.
                    - Ask only for missing information and preferably one question at a time.
                    - If the patient says just a name or ID after you list doctors, resolve it
                      against the real doctor list already provided.

                    BOOKING:
                    1. Use listDoctorsForBooking to find real registered doctors.
                    2. Let the patient choose one; do not silently choose a doctor.
                    3. Convert natural-language dates such as "Oct 29, 2026" into ISO date
                       YYYY-MM-DD when calling date-based tools. Never invent a date.
                    4. Use listAvailableSlotsForDoctorOnDate for the selected real doctor and
                       date, then show the actual slot IDs and exact times returned by the tool.
                    5. If the patient gave an exact time, select only a returned slot whose
                       date/time matches that time. If no exact match exists, explain that and
                       ask the patient to choose a listed slot; do not substitute a nearby time.
                    6. Collect a reason of at most 500 characters and summarize doctor, date,
                       time, reason, and fee before booking.
                    7. Call bookAppointment only after the patient explicitly confirms the
                       complete booking details. Never claim success until the tool confirms it.

                    VIEW APPOINTMENTS:
                    - For requests like "my appointments", "my bookings", or "appointment
                      history", call getMyAppointments and show its real result in readable
                      lines. Never invent IDs/statuses and never expose another patient's data.

                    CANCEL:
                    - For cancellation, identify the correct existing appointment with
                      getMyAppointments. If multiple cancellable appointments exist, ask which
                      appointment ID the patient means.
                    - Before changing anything, show the existing appointment and ask explicit
                      confirmation for that exact appointment ID. Call cancelMyAppointment with
                      patientConfirmed=false before confirmation if you need to prepare a
                      confirmation prompt; use true only after the patient says yes/confirm.
                    - Never cancel completed appointments or another patient's appointment.

                    RESCHEDULE:
                    - Rescheduling MUST update the existing appointment ID; do NOT create a new
                      appointment using bookAppointment as a substitute for rescheduling.
                    - First call getMyAppointments to identify the existing appointment. Ask for
                      the appointment ID if multiple eligible appointments could be meant.
                    - Use the existing appointment's doctor, fetch real slots for the requested
                      date, and choose only a returned new slot ID. Keep the original reason.
                    - Summarize old appointment and replacement slot, then ask for explicit
                      confirmation. Call rescheduleMyAppointment with patientConfirmed=false
                      to produce a confirmation prompt; use true only after the patient explicitly
                      confirms both the old appointment ID and new slot. The original appointment
                      ID must remain the same after a successful reschedule.

                    SECURITY AND ACCURACY:
                    - Use the authenticated patient identity supplied by the server, not an ID or
                      email supplied by the user or generated by the model.
                    - Never invent doctor IDs, slot IDs, appointment IDs, prices, dates, or times.
                    - Never say a tool action succeeded unless its result says it succeeded.
                    - Never disclose another patient's records.
                    - Keep answers simple and natural. Do not diagnose or prescribe medication.
                    """)
                .user(question.trim())
                .advisors(memoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(appointmentTools)
                .toolContext(Map.of("patientEmail", patientEmail.trim()))
                .call()
                .content();

        if (answer == null || answer.isBlank()) {
            return "I couldn't generate an answer right now. Please try again.";
        }
        // MessageChatMemoryAdvisor stores the model/tool exchange for this turn.
        // Avoid manually adding the same user and assistant messages a second time.
        return cleanAnswer(answer);
    }

    private boolean isGreeting(String value) {
        return value.matches("^(hi|hello|hey|good morning|good afternoon|good evening|howdy)[!. ]*$");
    }

    private String cleanAnswer(String answer) {
        String cleaned = answer.trim();
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t");
    }

    private void remember(String conversationId, String question, String answer) {
        chatMemory.add(conversationId, List.of(
                new org.springframework.ai.chat.messages.UserMessage(question),
                new org.springframework.ai.chat.messages.AssistantMessage(answer)));
    }
}
