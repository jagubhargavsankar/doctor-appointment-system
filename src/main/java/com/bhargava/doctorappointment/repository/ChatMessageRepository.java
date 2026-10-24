
package com.bhargava.doctorappointment.repository;

import com.bhargava.doctorappointment.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByPatientIdOrderByCreatedAtAsc(Long patientId);
}
