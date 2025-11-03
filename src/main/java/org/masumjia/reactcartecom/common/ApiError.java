package org.masumjia.reactcartecom.common;

import java.util.Map;

public class ApiError {
    private String code;
    private String message;
    private Map<String, String> fields;

    public ApiError() {}

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ApiError(String code, String message, Map<String, String> fields) {
        this.code = code;
        this.message = message;
        this.fields = fields;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, String> getFields() { return fields; }
    public void setFields(Map<String, String> fields) { this.fields = fields; }
}

