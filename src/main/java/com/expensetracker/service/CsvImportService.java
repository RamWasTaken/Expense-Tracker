package com.expensetracker.service;

import com.expensetracker.dto.ImportSummaryResponse;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CsvImportService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final ExecutorService csvImportExecutor;

    public CsvImportService(ExpenseRepository expenseRepository,
                             UserRepository userRepository,
                             GeminiService geminiService,
                             ExecutorService csvImportExecutor) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.geminiService = geminiService;
        this.csvImportExecutor = csvImportExecutor;
    }

    /**
     * Parses the CSV, then submits one Callable per row to the shared fixed
     * thread pool so rows are categorized (Gemini call) and saved concurrently.
     * Blocks on each Future to build a total/success/failed summary.
     */
    public ImportSummaryResponse importCsv(String userEmail, MultipartFile file) throws Exception {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<CSVRecord> records;
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader("description", "amount", "date")
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            records = parser.getRecords();
        }

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        List<Future<String>> futures = new ArrayList<>();

        for (CSVRecord record : records) {
            Callable<String> task = () -> processRow(user, record);
            futures.add(csvImportExecutor.submit(task));
        }

        for (int i = 0; i < futures.size(); i++) {
            try {
                String error = futures.get(i).get();
                if (error == null) {
                    successCount.incrementAndGet();
                } else {
                    errors.add("Row " + (i + 2) + ": " + error);
                }
            } catch (ExecutionException | InterruptedException e) {
                errors.add("Row " + (i + 2) + ": " + e.getMessage());
            }
        }

        int total = records.size();
        return new ImportSummaryResponse(total, successCount.get(), errors.size(), errors);
    }

    /**
     * Runs on a worker thread. Returns null on success, or an error message on failure.
     * Never throws — CSV rows are independent, one bad row shouldn't abort the batch.
     */
    private String processRow(User user, CSVRecord record) {
        try {
            String description = record.get("description").trim();
            BigDecimal amount = new BigDecimal(record.get("amount").trim());
            LocalDate date = LocalDate.parse(record.get("date").trim());

            if (description.isEmpty()) {
                return "Description is empty";
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return "Amount must be positive";
            }

            String category = geminiService.categorize(description);

            Expense expense = new Expense();
            expense.setUser(user);
            expense.setDescription(description);
            expense.setAmount(amount);
            expense.setDate(date);
            expense.setCategory(category);
            expenseRepository.save(expense);

            return null;
        } catch (Exception e) {
            return "Failed to process row - " + e.getMessage();
        }
    }
}
