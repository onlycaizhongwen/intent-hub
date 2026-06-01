package com.intenthub.infrastructure.config;

import com.intenthub.application.SceneConfigPort;
import com.intenthub.domain.config.SceneConfig;
import com.intenthub.domain.recognition.Envelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "intent-hub.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemorySceneConfigRepository implements SceneConfigPort {
    @Override
    public SceneConfig loadPublishedConfig(Envelope envelope) {
        return BuiltinSceneConfigFactory.orderScene(envelope);
    }
}
