package com.bysellens.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, List<FieldDetail> details) {
    public record FieldDetail(String field, String message) {
    }
}
