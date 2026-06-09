package com.intenthub.domain.recognition.policy;

public class ModelServiceAuthenticationException extends RuntimeException {
    private final String pathMarker;

    public ModelServiceAuthenticationException(String pathMarker) {
        super(pathMarker);
        this.pathMarker = pathMarker;
    }

    public String pathMarker() {
        return pathMarker;
    }
}
