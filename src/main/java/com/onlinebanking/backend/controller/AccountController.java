package com.onlinebanking.backend.controller;

import com.onlinebanking.backend.dto.*;
import com.onlinebanking.backend.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getAccountDetails() {
        String email = accountService.getCurrentUserEmail();
        AccountResponse response = accountService.getAccountDetails(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit")
    public ResponseEntity<AccountResponse> deposit(@Valid @RequestBody DepositRequest request) {
        String email = accountService.getCurrentUserEmail();
        AccountResponse response = accountService.deposit(email, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<AccountResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        String email = accountService.getCurrentUserEmail();
        AccountResponse response = accountService.withdraw(email, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<AccountResponse> transfer(@Valid @RequestBody TransferRequest request) {
        String email = accountService.getCurrentUserEmail();
        AccountResponse response = accountService.transfer(email, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statement")
    public ResponseEntity<AccountStatementResponse> getAccountStatement() {
        String email = accountService.getCurrentUserEmail();
        AccountStatementResponse response = accountService.getAccountStatement(email);
        return ResponseEntity.ok(response);
    }
}
