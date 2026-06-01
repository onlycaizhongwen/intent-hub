package com.intenthub.interfaces.web;

import com.intenthub.application.RecognizeAppService;
import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intent")
public class RecognizeController {
    private final RecognizeAppService recognizeAppService;

    public RecognizeController(RecognizeAppService recognizeAppService) {
        this.recognizeAppService = recognizeAppService;
    }

    @PostMapping("/recognize")
    public IntentResult recognize(@Valid @RequestBody RecognizeRequest request) {
        Envelope envelope = new Envelope(
                request.tenantId(),
                request.source(),
                request.channel(),
                request.inputType(),
                request.text(),
                request.requestId(),
                request.traceId() == null || request.traceId().isBlank() ? UUID.randomUUID().toString() : request.traceId(),
                request.sessionId(),
                request.timestamp() == null ? Instant.now() : request.timestamp(),
                request.metadata(),
                request.attachments()
        );
        return recognizeAppService.recognize(envelope);
    }
}
