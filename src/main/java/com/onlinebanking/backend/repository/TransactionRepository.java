package com.onlinebanking.backend.repository;

import com.onlinebanking.backend.entity.Account;
import com.onlinebanking.backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountOrderByTimestampDesc(Account account);
}
