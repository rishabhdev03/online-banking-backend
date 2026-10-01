package com.onlinebanking.backend.controller;

import com.onlinebanking.backend.dto.TransactionResponse;
import com.onlinebanking.backend.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory() {
        String email = accountService.getCurrentUserEmail();
        List<TransactionResponse> transactions = accountService.getTransactionHistory(email);
        return ResponseEntity.ok(transactions);
    }
}
