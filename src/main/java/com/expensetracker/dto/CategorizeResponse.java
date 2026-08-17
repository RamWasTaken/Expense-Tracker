package com.expensetracker.dto;

public class CategorizeResponse {

    private String category;

    public CategorizeResponse(String category) {
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
