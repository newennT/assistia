package com.assistia.service;

import com.assistia.entity.UserRole;

public interface GeminiAnalysisProvider {
    String generate(AnalysisTicketContext ticket, Long userId, UserRole role);
}
