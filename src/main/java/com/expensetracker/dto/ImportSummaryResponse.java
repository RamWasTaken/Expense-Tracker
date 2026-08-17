package com.expensetracker.dto;

import java.util.List;

public class ImportSummaryResponse {

    private int total;
    private int success;
    private int failed;
    private List<String> errors;

    public ImportSummaryResponse(int total, int success, int failed, List<String> errors) {
        this.total = total;
        this.success = success;
        this.failed = failed;
        this.errors = errors;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
