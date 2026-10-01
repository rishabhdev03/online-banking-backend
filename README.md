# Online Banking Backend

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14%2B-blue?logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7%2B-red?logo=redis)
![Spring Security](https://img.shields.io/badge/Security-JWT-purple?logo=springsecurity)
![Build](https://img.shields.io/badge/Build-Maven-blue?logo=apachemaven)

A robust, production-structured **Online Banking REST API** built with **Spring Boot 3**, **PostgreSQL**, **JWT Authentication**, and **Redis**. Designed to demonstrate core backend engineering principles including stateless authentication, transactional data consistency (ACID), pessimistic concurrency control, distributed caching, and centralized exception handling.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [System Architecture](#system-architecture)
- [Database Design](#database-design)
- [Core Engineering Highlights](#core-engineering-highlights)
  - [Transactional Integrity (@Transactional)](#1-transactional-integrity-transactional)
  - [Pessimistic Concurrency Control](#2-pessimistic-concurrency-control)
  - [Redis Caching & Invalidation Strategy](#3-redis-caching--invalidation-strategy)
  - [Stateless Security & JWT Pipeline](#4-stateless-security--jwt-pipeline)
- [API Documentation](#api-documentation)
- [Local Setup & Getting Started](#local-setup--getting-started)
- [Postman Testing Flow](#postman-testing-flow)
- [Project Directory Structure](#project-directory-structure)

---

## Overview

The **Online Banking Backend** provides a complete banking ledger simulation. Users register to automatically receive a bank account, authenticate securely via JSON Web Tokens (JWT), and execute core financial transactions—depositing, withdrawing, and transferring funds between accounts.

PostgreSQL acts as the permanent, ACID-compliant **source of truth**, while Redis operates as a high-performance **in-memory cache** to accelerate balance queries and minimize database load.

---

## Key Features

- **User Onboarding & Account Provisioning**: Automatic unique 10-digit account generation upon verified registration.
- **Stateless JWT Authentication**: Secure password hashing with BCrypt and per-request token authorization via custom servlet filters.
- **Atomic Fund Transfers**: Mutual debit/credit operations wrapped inside ACID transactions with automatic rollback on failure.
- **Race Condition Prevention**: Database-level row locking (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`) guarding concurrent balance modifications.
- **High-Performance Redis Caching**: Sub-millisecond account data retrieval using Spring's cache abstraction, with automatic cache eviction on mutations.
- **Full Transaction Ledger**: Immutable audit trail recording deposits, withdrawals, and inter-account transfers.
- **Centralized Error Handling**: Uniform JSON error responses via `@RestControllerAdvice` and domain-specific custom exceptions.
- **Strict Input Validation**: Request payloads validated using Jakarta Bean Validation (`@Valid`, `@DecimalMin`, `@Email`, etc.).
- **Precise Financial Math**: Strict usage of `BigDecimal` for zero-loss currency arithmetic.

---

## Tech Stack

| Technology | Purpose |
|---|---|
| **Java 17** | Core programming language |
| **Spring Boot 3.2.4** | Enterprise application framework & REST web services |
| **Spring Security 6** | Filter chain management, authentication & authorization |
| **JSON Web Token (JJWT 0.12.5)** | Stateless authorization token creation and verification |
| **Spring Data JPA / Hibernate** | Object-Relational Mapping (ORM) and repository layer |
| **PostgreSQL** | Relational database (permanent system of record) |
| **Redis** | In-memory caching engine for read acceleration |
| **Spring Cache** | Declarative caching abstraction (`@Cacheable`, `@CacheEvict`) |
| **Jakarta Bean Validation** | Declarative constraint validation on incoming DTOs |
| **Lombok** | Boilerplate reduction for models, builders, and constructors |
| **Apache Maven** | Dependency management and build automation |

---

## System Architecture

The application adopts a clean, layered architectural pattern:

```
                      ┌─────────────────────────────┐
                      │    Client (Web / Mobile)    │
                      └──────────────┬──────────────┘
                                     │ HTTP / JSON
                                     ▼
                ┌─────────────────────────────────────────┐
                │        Spring Security Filter Chain     │
                │  ┌───────────────────────────────────┐  │
                │  │     JwtAuthenticationFilter       │  │
                │  └─────────────────┬─────────────────┘  │
                └────────────────────┼────────────────────┘
                                     │ Validated Principal
                                     ▼
                ┌─────────────────────────────────────────┐
                │            Controller Layer             │
                │  (AuthController, AccountController,    │
                │         TransactionController)          │
                └────────────────────┬────────────────────┘
                                     │ DTOs
                                     ▼
                ┌─────────────────────────────────────────┐
                │              Service Layer              │
                │   (AuthService, AccountService, Cache)  │
                └───────┬─────────────────────────┬───────┘
                        │                         │
             Read/Evict │                         │ Repository Calls
                        ▼                         ▼
            ┌──────────────────────┐   ┌──────────────────────┐
            │     Redis Cache      │   │   Spring Data JPA    │
            │  (In-Memory Key/Val) │   └──────────┬───────────┘
            └──────────────────────┘              │ SQL Queries / Locks
                                                  ▼
                                       ┌──────────────────────┐
                                       │ PostgreSQL Database  │
                                       │  (Source of Truth)   │
                                       └──────────────────────┘
```

---

## Database Design

The relational schema is normalized and enforced with strict database constraints:

```
 +----------------------------------+          +----------------------------------+
 |              users               |          |             accounts             |
 +----------------------------------+          +----------------------------------+
 | id           : BIGSERIAL [PK]    | 1      1 | id             : BIGSERIAL [PK]  |
 | full_name    : VARCHAR NOT NULL  |<-------->| account_number : VARCHAR(10)[UQ] |
 | email        : VARCHAR [UQ, NN]  |          | balance        : DECIMAL(15,2)   |
 | password     : VARCHAR NOT NULL  |          | created_at     : TIMESTAMP NOT NULL
 | created_at   : TIMESTAMP NOT NULL|          | user_id        : BIGINT [FK, UQ] |
 +----------------------------------+          +----------------┬-----------------+
                                                                | 1
                                                                |
                                                                | *
                                               +----------------┴-----------------+
                                               |           transactions           |
                                               +----------------------------------+
                                               | id             : BIGSERIAL [PK]  |
                                               | type           : VARCHAR NOT NULL|
                                               | amount         : DECIMAL(15,2)   |
                                               | timestamp      : TIMESTAMP NOT NULL
                                               | description    : VARCHAR         |
                                               | related_acc_no : VARCHAR(10)     |
                                               | account_id     : BIGINT [FK, NN] |
                                               +----------------------------------+
```

### Entity Relationships
- **User ↔ Account (1 : 1)**: Each user owns exactly one bank account enforced by a database `UNIQUE` constraint on `accounts.user_id`.
- **Account ↔ Transaction (1 : N)**: An account possesses multiple historical transaction records (debits, credits, and transfers).

---

## Core Engineering Highlights

### 1. Transactional Integrity (`@Transactional`)

Financial fund transfers involve multiple dependent operations across two bank accounts:
1. Lock and deduct amount from the Sender's account.
2. Lock and credit amount to the Receiver's account.
3. Persist debit transaction record.
4. Persist credit transaction record.

All steps execute inside a single `@Transactional` boundary. If an unhandled exception or system failure occurs at any point (e.g., database constraint failure during step 4), Spring issues an immediate **rollback**, restoring both accounts to their exact pre-transaction state. **Zero money is ever lost.**

### 2. Pessimistic Concurrency Control

To prevent race conditions during concurrent balance withdrawals (e.g., simultaneous requests attempting to double-spend):
- `findByUserIdWithLock()` and `findByAccountNumberWithLock()` leverage `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
- Translates directly to `SELECT ... FOR UPDATE` in PostgreSQL.
- Thread B attempting to read or modify the same account row must wait until Thread A finishes and releases the lock, eliminating lost-update anomalies.

### 3. Redis Caching & Invalidation Strategy

- **Cache Reads (`@Cacheable`)**: High-traffic endpoints like `GET /api/accounts/me` query Redis first (`accounts::<email>`). On a cache miss, PostgreSQL is queried, the response is serialized to JSON in Redis with a 10-minute TTL, and returned.
- **Cache Invalidation (`@CacheEvict`)**: Any state-altering action (`deposit`, `withdraw`, `transfer`) triggers immediate cache invalidation for the affected account(s), ensuring stale balances are never served.
- **Resilience**: Redis operates strictly as a caching accelerator; if Redis is unavailable, the system safely falls back to PostgreSQL.

### 4. Stateless Security & JWT Pipeline

1. Client sends credentials to `POST /api/auth/login`.
2. `AuthenticationManager` verifies the BCrypt-hashed password against PostgreSQL.
3. `JwtService` issues a digitally signed HMAC-SHA256 JWT containing user claims and expiration.
4. Client provides the token in subsequent requests via `Authorization: Bearer <token>`.
5. `JwtAuthenticationFilter` intercepts the request, validates the cryptographic signature, extracts user details, and populates Spring's `SecurityContextHolder`.

---

## API Documentation

### Public Endpoints (No Authentication Required)

| Method | Endpoint | Description | Status Code |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user and generate a bank account | `201 Created` |
| `POST` | `/api/auth/login` | Authenticate user and receive JWT Bearer token | `200 OK` |

### Protected Banking Endpoints (Requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description | Status Code |
|---|---|---|---|
| `GET` | `/api/accounts/me` | Fetch authenticated user's account details (Redis cached) | `200 OK` |
| `POST` | `/api/accounts/deposit` | Deposit funds into authenticated user's account | `200 OK` |
| `POST` | `/api/accounts/withdraw` | Withdraw funds with balance validation | `200 OK` |
| `POST` | `/api/accounts/transfer` | Atomically transfer funds to another account | `200 OK` |
| `GET` | `/api/transactions` | Retrieve user's transaction history (newest first) | `200 OK` |
| `GET` | `/api/accounts/statement` | Full statement (account info + transaction log) | `200 OK` |

---

## Local Setup & Getting Started

### Prerequisites

Ensure the following are installed locally:
- **Java Development Kit (JDK) 17+**
- **Apache Maven 3.8+**
- **PostgreSQL 14+**
- **Redis Server 7+**

---

### Step 1: Database Setup

Open PostgreSQL CLI (`psql`) or pgAdmin and run:

```sql
CREATE DATABASE banking_core_db;
```

---

### Step 2: Start Redis

Start your local Redis instance:

```bash
# On Linux / macOS / WSL
redis-server

# Verify Redis is running
redis-cli ping
# Expected output: PONG
```

---

### Step 3: Configure Environment

Open `src/main/resources/application.properties` and verify your connection parameters:

```properties
server.port=8080

spring.datasource.url=jdbc:postgresql://localhost:5432/banking_core_db
spring.datasource.username=bank_app_user
spring.datasource.password=v8K#9mP$2xL@7qJw
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

spring.data.redis.host=localhost
spring.data.redis.port=6379
app.cache.ttl=600

jwt.secret=dIN833eW9BeTbaNM7l89TLMrYgfpPy3lLDwjUMmdj+NmBbwTVJRjN5I+5DjLLjKq
jwt.expiration=86400000

logging.level.com.onlinebanking=INFO
logging.level.org.springframework.security=INFO
```

---

### Step 4: Build and Run

From the root project directory:

```bash
# Clean and package the application
mvn clean package -DskipTests

# Run the Spring Boot application
mvn spring-boot:run
```

The application will start on **`http://localhost:8080`**. Tables will be automatically created on startup via Hibernate (`ddl-auto=update`).

---

## Postman Testing Flow

### 1. Register User A
`POST http://localhost:8080/api/auth/register`
```json
{
  "fullName": "Alice Johnson",
  "email": "alice@example.com",
  "password": "password123"
}
```

### 2. Login as User A
`POST http://localhost:8080/api/auth/login`
```json
{
  "email": "alice@example.com",
  "password": "password123"
}
```
*Copy the `token` value from the response.*

### 3. Check Account Balance (User A)
`GET http://localhost:8080/api/accounts/me`  
**Header:** `Authorization: Bearer <ALICE_JWT_TOKEN>`  
*Copy Alice's `accountNumber` from the response.*

### 4. Deposit Funds (User A)
`POST http://localhost:8080/api/accounts/deposit`  
**Header:** `Authorization: Bearer <ALICE_JWT_TOKEN>`
```json
{
  "amount": 10000.00
}
```

### 5. Register & Login as User B
Repeat steps 1-3 for `bob@example.com` to obtain Bob's `accountNumber`.

### 6. Transfer Funds (Alice → Bob)
`POST http://localhost:8080/api/accounts/transfer`  
**Header:** `Authorization: Bearer <ALICE_JWT_TOKEN>`
```json
{
  "receiverAccountNumber": "<BOB_ACCOUNT_NUMBER>",
  "amount": 2500.00
}
```

### 7. View Account Statement (Alice)
`GET http://localhost:8080/api/accounts/statement`  
**Header:** `Authorization: Bearer <ALICE_JWT_TOKEN>`

---

## Project Directory Structure

```
com.onlinebanking.backend/
├── config/
│   ├── RedisConfig.java                 # RedisCacheManager & JSON serialization
│   └── SecurityConfig.java              # SecurityFilterChain & auth beans
├── controller/
│   ├── AccountController.java           # Banking operations endpoints
│   ├── AuthController.java              # User registration & login endpoints
│   └── TransactionController.java       # Transaction history endpoint
├── dto/
│   ├── AccountResponse.java             # Outgoing account view DTO
│   ├── AccountStatementResponse.java    # Account statement + ledger DTO
│   ├── AuthResponse.java                # JWT token response DTO
│   ├── DepositRequest.java              # Deposit payload validation
│   ├── LoginRequest.java                # Login credential payload
│   ├── RegisterRequest.java             # User registration payload
│   ├── TransactionResponse.java         # Transaction detail DTO
│   ├── TransferRequest.java             # Transfer payload validation
│   └── WithdrawRequest.java             # Withdrawal payload validation
├── entity/
│   ├── Account.java                     # Bank account entity (BigDecimal)
│   ├── Transaction.java                 # Transaction ledger record entity
│   ├── TransactionType.java             # Enum: DEPOSIT, WITHDRAWAL, TRANSFER
│   └── User.java                        # User profile entity
├── exception/
│   ├── AccountNotFoundException.java    # 404 domain exception
│   ├── GlobalExceptionHandler.java      # Centralized @RestControllerAdvice
│   ├── InsufficientBalanceException.java# 400 balance validation exception
│   ├── InvalidTransferException.java    # 400 invalid transfer rules
│   └── UserAlreadyExistsException.java  # 409 conflict exception
├── repository/
│   ├── AccountRepository.java           # Account data access & pessimistic locks
│   ├── TransactionRepository.java       # Ledger queries (newest-first)
│   └── UserRepository.java              # User lookup & duplicate email checks
├── security/
│   ├── CustomUserDetailsService.java    # Database user loader for Spring Security
│   ├── JwtAuthenticationFilter.java     # OncePerRequestFilter JWT interceptor
│   └── JwtService.java                  # JJWT token generation and validation
├── service/
│   ├── AccountService.java              # Core banking & cache management logic
│   └── AuthService.java                 # Registration & credential validation
└── OnlineBankingBackendApplication.java # Spring Boot entry point (@EnableCaching)
```

---

🎉 Happy coding, Rishabh! 🎉
