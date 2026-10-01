package com.onlinebanking.backend.service;

import com.onlinebanking.backend.dto.*;
import com.onlinebanking.backend.entity.Account;
import com.onlinebanking.backend.entity.Transaction;
import com.onlinebanking.backend.entity.TransactionType;
import com.onlinebanking.backend.entity.User;
import com.onlinebanking.backend.exception.AccountNotFoundException;
import com.onlinebanking.backend.exception.InsufficientBalanceException;
import com.onlinebanking.backend.exception.InvalidTransferException;
import com.onlinebanking.backend.repository.AccountRepository;
import com.onlinebanking.backend.repository.TransactionRepository;
import com.onlinebanking.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final CacheManager cacheManager;

    private static final String ACCOUNTS_CACHE = "accounts";

    @Cacheable(value = ACCOUNTS_CACHE, key = "#email")
    public AccountResponse getAccountDetails(String email) {
        Account account = getAccountByEmail(email);
        return mapToAccountResponse(account);
    }

    @Transactional
    @CacheEvict(value = ACCOUNTS_CACHE, key = "#email")
    public AccountResponse deposit(String email, DepositRequest request) {
        User user = getUserByEmail(email);

        Account account = accountRepository.findByUserIdWithLock(user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + email));

        account.setBalance(account.getBalance().add(request.getAmount()));
        Account savedAccount = accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .description("Deposit of INR " + request.getAmount())
                .account(savedAccount)
                .build();

        transactionRepository.save(transaction);

        return mapToAccountResponse(savedAccount);
    }

    @Transactional
    @CacheEvict(value = ACCOUNTS_CACHE, key = "#email")
    public AccountResponse withdraw(String email, WithdrawRequest request) {
        User user = getUserByEmail(email);

        Account account = accountRepository.findByUserIdWithLock(user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + email));

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available balance: INR " + account.getBalance()
            );
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        Account savedAccount = accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .description("Withdrawal of INR " + request.getAmount())
                .account(savedAccount)
                .build();

        transactionRepository.save(transaction);

        return mapToAccountResponse(savedAccount);
    }

    @Transactional
    @CacheEvict(value = ACCOUNTS_CACHE, key = "#email")
    public AccountResponse transfer(String email, TransferRequest request) {
        User senderUser = getUserByEmail(email);

        Account senderAccount = accountRepository.findByUserIdWithLock(senderUser.getId())
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found"));

        Account receiverAccount = accountRepository.findByAccountNumberWithLock(request.getReceiverAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Receiver account not found with account number: " + request.getReceiverAccountNumber()
                ));

        if (senderAccount.getAccountNumber().equals(receiverAccount.getAccountNumber())) {
            throw new InvalidTransferException("Cannot transfer money to your own account");
        }

        if (senderAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available balance: INR " + senderAccount.getBalance()
            );
        }

        senderAccount.setBalance(senderAccount.getBalance().subtract(request.getAmount()));
        receiverAccount.setBalance(receiverAccount.getBalance().add(request.getAmount()));

        Account savedSender = accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        Transaction senderTransaction = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .description("Transfer to account " + receiverAccount.getAccountNumber())
                .account(savedSender)
                .relatedAccountNumber(receiverAccount.getAccountNumber())
                .build();

        Transaction receiverTransaction = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .description("Transfer from account " + senderAccount.getAccountNumber())
                .account(receiverAccount)
                .relatedAccountNumber(senderAccount.getAccountNumber())
                .build();

        transactionRepository.save(senderTransaction);
        transactionRepository.save(receiverTransaction);

        String receiverEmail = receiverAccount.getUser().getEmail();
        if (cacheManager.getCache(ACCOUNTS_CACHE) != null) {
            cacheManager.getCache(ACCOUNTS_CACHE).evict(receiverEmail);
        }

        return mapToAccountResponse(savedSender);
    }

    public List<TransactionResponse> getTransactionHistory(String email) {
        Account account = getAccountByEmail(email);

        List<Transaction> transactions = transactionRepository
                .findByAccountOrderByTimestampDesc(account);

        return transactions.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    public AccountStatementResponse getAccountStatement(String email) {
        Account account = getAccountByEmail(email);
        AccountResponse accountResponse = mapToAccountResponse(account);

        List<Transaction> transactions = transactionRepository
                .findByAccountOrderByTimestampDesc(account);

        List<TransactionResponse> transactionResponses = transactions.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());

        return AccountStatementResponse.builder()
                .account(accountResponse)
                .transactions(transactionResponses)
                .build();
    }

    public String getCurrentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccountNotFoundException("User not found: " + email));
    }

    private Account getAccountByEmail(String email) {
        User user = getUserByEmail(email);
        return accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found for user: " + email));
    }

    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .createdAt(account.getCreatedAt())
                .ownerName(account.getUser().getFullName())
                .ownerEmail(account.getUser().getEmail())
                .build();
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .timestamp(transaction.getTimestamp())
                .description(transaction.getDescription())
                .relatedAccountNumber(transaction.getRelatedAccountNumber())
                .build();
    }
}
