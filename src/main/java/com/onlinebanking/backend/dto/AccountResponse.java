package com.onlinebanking.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse implements Serializable {

    private String accountNumber;
    private BigDecimal balance;
    private LocalDateTime createdAt;
    private String ownerName;
    private String ownerEmail;
}
