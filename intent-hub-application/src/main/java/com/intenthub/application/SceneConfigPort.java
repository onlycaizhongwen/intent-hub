package com.intenthub.application;

import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.Envelope;

public interface SceneConfigPort {
    SceneConfig loadPublishedConfig(Envelope envelope);
}
