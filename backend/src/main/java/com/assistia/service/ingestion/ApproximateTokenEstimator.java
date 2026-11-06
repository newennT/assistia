package com.assistia.service.ingestion;

import org.springframework.stereotype.Component;

@Component
public class ApproximateTokenEstimator implements TokenEstimator {
    @Override
    public int estimate(String text) {
        if (text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.codePointCount(0, text.length()) / 4.0));
    }
}
