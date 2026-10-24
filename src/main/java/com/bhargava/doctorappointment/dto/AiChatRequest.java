
package com.bhargava.doctorappointment.dto;

public class AiChatRequest {

    private String question;

    public AiChatRequest() {
    }

    public AiChatRequest(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
