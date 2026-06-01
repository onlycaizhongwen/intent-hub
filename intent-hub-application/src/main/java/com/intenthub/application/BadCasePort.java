package com.intenthub.application;

import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;

public interface BadCasePort {
    void recordIfNeeded(Envelope envelope, IntentResult result);
}
