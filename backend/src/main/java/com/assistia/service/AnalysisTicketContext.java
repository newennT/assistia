package com.assistia.service;

import com.assistia.entity.TicketCategory;

public record AnalysisTicketContext(String title, String description, TicketCategory category) {
}
