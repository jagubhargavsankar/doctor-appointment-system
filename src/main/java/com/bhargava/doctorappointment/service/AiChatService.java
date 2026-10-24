package com.bhargava.doctorappointment.service;

import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    private static final String NO_DOCUMENT_ANSWER =
            "I couldn't find that information in the healthcare guide.";

    private static final List<String> HEALTHCARE_KEYWORDS = List.of(
            "health", "healthcare", "medical", "medicine",
            "doctor", "patient", "hospital", "clinic",
            "disease", "symptom", "treatment", "diagnosis",
            "dermatologist", "skin", "hair", "nail",
            "diabetes", "fever", "cough", "headache", "pain",
            "blood pressure", "heart", "cancer", "infection",
            "pregnancy", "nutrition", "diet", "exercise",
            "mental health", "emergency", "appointment",
            "prescription", "pharmacy", "vaccine", "allergy",
            "asthma", "first aid", "wellness", "surgery", "nurse"
    );

    public AiChatService(
            @Qualifier("chatMemoryChatClient") ChatClient chatClient,
            ChatMemory chatMemory) {

        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    public String askQuestion(Long patientId, String question) {

        String normalized = question.toLowerCase(Locale.ROOT).trim();
        String conversationId = "patient-" + patientId;

        if (normalized.matches("^(hi|hello|hey|good morning|good afternoon|good evening)[!. ]*$")) {
            return "Hello! I can answer healthcare FAQs from the clinic guide and help explain general healthcare information. What would you like to know?";
        }

        if ((normalized.contains("explain a healthcare term")
                || normalized.contains("define a healthcare term")
                || normalized.matches("^(explain|define) a term[?.! ]*$"))) {
            return "Which healthcare term or phrase would you like me to explain? I can define it in simple language, but the meaning of a term in your own care should be confirmed with your healthcare professional.";
        }

        // Handle questions about the previous conversation.
        String previousTopicAnswer =
                answerPreviousTopicQuestion(conversationId, normalized);

        if (previousTopicAnswer != null) {
            chatMemory.add(
                    conversationId,
                    List.of(
                            new UserMessage(question),
                            new AssistantMessage(previousTopicAnswer)
                    )
            );

            return previousTopicAnswer;
        }

        // Allow healthcare-related questions only.
        boolean allowed = HEALTHCARE_KEYWORDS.stream()
                .anyMatch(normalized::contains);

        if (!allowed) {
            return "I'm designed to answer healthcare-related questions only. "
                    + "Please ask me about health, medical conditions, "
                    + "or healthcare services.";
        }

        // RetrievalAugmentationAdvisor retrieves relevant guide chunks
        // and adds them to the prompt before Groq generates the answer.
        String answer = chatClient.prompt()
                .user(question)
                .advisors(advisorSpec -> advisorSpec.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();

        if (answer == null || answer.isBlank()) {
            return NO_DOCUMENT_ANSWER;
        }

        String cleanedAnswer = answer.trim();
        String normalizedAnswer =
                cleanedAnswer.toLowerCase(Locale.ROOT);

        if (normalizedAnswer.contains("couldn't find that information")
                || normalizedAnswer.contains("could not find that information")) {
            return NO_DOCUMENT_ANSWER;
        }

        // Remove surrounding quotation marks, if present.
        if (cleanedAnswer.length() >= 2
                && ((cleanedAnswer.startsWith("\"")
                && cleanedAnswer.endsWith("\""))
                || (cleanedAnswer.startsWith("'")
                && cleanedAnswer.endsWith("'")))) {

            cleanedAnswer = cleanedAnswer
                    .substring(1, cleanedAnswer.length() - 1)
                    .trim();
        }

        return cleanedAnswer;
    }

    private String answerPreviousTopicQuestion(
            String conversationId,
            String normalizedQuestion) {

        boolean asksPreviousTopic =
                normalizedQuestion.contains("what skin condition")
                        || normalizedQuestion.contains("what condition were we discussing")
                        || normalizedQuestion.contains("what topic were we discussing")
                        || normalizedQuestion.contains("what were we discussing")
                        || normalizedQuestion.contains("what did we discuss")
                        || normalizedQuestion.contains("what did we talk about")
                        || normalizedQuestion.contains("what topic did we discuss")
                        || normalizedQuestion.contains("what was the topic");

        if (!asksPreviousTopic) {
            return null;
        }

        List<Message> history = chatMemory.get(conversationId);

        StringBuilder historyText = new StringBuilder();

        for (Message message : history) {
            String text = message.getText();

            if (text != null) {
                historyText.append(text.toLowerCase(Locale.ROOT))
                        .append(" ");
            }
        }

        String previousConversation = historyText.toString();

        if (previousConversation.contains("skin allerg")) {
            return "We were discussing skin allergies.";
        }

        if (previousConversation.contains("eczema")) {
            return "We were discussing eczema.";
        }

        if (previousConversation.contains("skin rash")) {
            return "We were discussing a skin rash.";
        }

        if (previousConversation.contains("dermatologist")
                || previousConversation.contains("skin")) {
            return "We were discussing a skin-related healthcare question "
                    + "and a dermatologist recommendation, but the recent "
                    + "conversation history does not identify a more "
                    + "specific skin condition.";
        }

        return null;
    }
}