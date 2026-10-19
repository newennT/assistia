package com.assistia.dto;

public record ApiErrorResponse(int status, String error, String message, String path) {
}
