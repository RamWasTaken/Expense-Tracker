package com.expensetracker.controller;

import com.expensetracker.dto.CategorizeRequest;
import com.expensetracker.dto.CategorizeResponse;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.ImportSummaryResponse;
import com.expensetracker.service.CsvImportService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.GeminiService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final GeminiService geminiService;
    private final CsvImportService csvImportService;

    public ExpenseController(ExpenseService expenseService,
                              GeminiService geminiService,
                              CsvImportService csvImportService) {
        this.expenseService = expenseService;
        this.geminiService = geminiService;
        this.csvImportService = csvImportService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(Authentication authentication,
                                                           @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.createExpense(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<Page<ExpenseResponse>> getExpenses(Authentication authentication, Pageable pageable) {
        return ResponseEntity.ok(expenseService.getExpenses(authentication.getName(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getExpenseById(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getExpenseById(authentication.getName(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> updateExpense(Authentication authentication,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.updateExpense(authentication.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(Authentication authentication, @PathVariable Long id) {
        expenseService.deleteExpense(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/categorize")
    public ResponseEntity<CategorizeResponse> categorize(@Valid @RequestBody CategorizeRequest request) {
        String category = geminiService.categorize(request.getDescription());
        return ResponseEntity.ok(new CategorizeResponse(category));
    }

    @PostMapping("/import")
    public ResponseEntity<ImportSummaryResponse> importCsv(Authentication authentication,
                                                             @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(csvImportService.importCsv(authentication.getName(), file));
    }
}
