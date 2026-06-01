package com.intenthub.application;

import com.intenthub.domain.recognition.Envelope;
import com.intenthub.domain.recognition.IntentResult;

public interface RecognitionTracePort {
    void record(Envelope envelope, IntentResult result);
}
