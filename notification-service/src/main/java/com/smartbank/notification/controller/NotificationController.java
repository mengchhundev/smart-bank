package com.smartbank.notification.controller;

import com.smartbank.notification.dto.NotificationDto;
import com.smartbank.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/recipient/{recipient}")
    public ResponseEntity<List<NotificationDto>> getByRecipient(@PathVariable String recipient) {
        return ResponseEntity.ok(notificationService.getByRecipient(recipient));
    }
}
