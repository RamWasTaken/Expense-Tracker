package com.expensetracker.service;

import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ExpenseNotFoundException;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public ExpenseService(ExpenseRepository expenseRepository, UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    public ExpenseResponse createExpense(String userEmail, ExpenseRequest request) {
        User user = getUserByEmail(userEmail);

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setAmount(request.getAmount());
        expense.setDescription(request.getDescription());
        expense.setCategory(request.getCategory());
        expense.setDate(request.getDate());

        Expense saved = expenseRepository.save(expense);
        return toResponse(saved);
    }

    public Page<ExpenseResponse> getExpenses(String userEmail, Pageable pageable) {
        User user = getUserByEmail(userEmail);
        return expenseRepository.findByUserId(user.getId(), pageable)
                .map(this::toResponse);
    }

    public ExpenseResponse getExpenseById(String userEmail, Long id) {
        User user = getUserByEmail(userEmail);
        Expense expense = expenseRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with id: " + id));
        return toResponse(expense);
    }

    public ExpenseResponse updateExpense(String userEmail, Long id, ExpenseRequest request) {
        User user = getUserByEmail(userEmail);
        Expense expense = expenseRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with id: " + id));

        expense.setAmount(request.getAmount());
        expense.setDescription(request.getDescription());
        expense.setCategory(request.getCategory());
        expense.setDate(request.getDate());

        Expense updated = expenseRepository.save(expense);
        return toResponse(updated);
    }

    public void deleteExpense(String userEmail, Long id) {
        User user = getUserByEmail(userEmail);
        Expense expense = expenseRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with id: " + id));
        expenseRepository.delete(expense);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getCategory(),
                expense.getDate()
        );
    }
}
