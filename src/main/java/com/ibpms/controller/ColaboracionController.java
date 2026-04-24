package com.ibpms.controller;

import com.ibpms.dto.request.ColaboracionPayload;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ColaboracionController {

    private final SimpMessagingTemplate messagingTemplate;

    public ColaboracionController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/colaboracion/{policyId}")
    public void colaborar(
            @DestinationVariable String policyId,
            ColaboracionPayload payload) {
        messagingTemplate.convertAndSend(
                "/topic/colaboracion/" + policyId,
                payload
        );
    }
}
