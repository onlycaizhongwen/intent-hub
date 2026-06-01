package com.intenthub.application;

import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;

public interface IdempotencyPort {
    String reserve(Envelope envelope, DownstreamAction action);
}
