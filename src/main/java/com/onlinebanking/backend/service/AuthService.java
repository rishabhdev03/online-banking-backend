package com.onlinebanking.backend.service;

import com.onlinebanking.backend.dto.AuthResponse;
import com.onlinebanking.backend.dto.LoginRequest;
import com.onlinebanking.backend.dto.RegisterRequest;
import com.onlinebanking.backend.entity.Account;
import com.onlinebanking.backend.entity.User;
import com.onlinebanking.backend.exception.UserAlreadyExistsException;
import com.onlinebanking.backend.repository.AccountRepository;
import com.onlinebanking.backend.repository.UserRepository;
import com.onlinebanking.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "User with email " + request.getEmail() + " already exists"
            );
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(encodedPassword)
                .build();

        User savedUser = userRepository.save(user);

        Account account = Account.builder()
                .accountNumber(generateUniqueAccountNumber())
                .balance(BigDecimal.ZERO)
                .user(savedUser)
                .build();

        accountRepository.save(account);

        return "User registered successfully! Account created.";
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + request.getEmail()));

        String jwtToken = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(jwtToken)
                .tokenType("Bearer")
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        Random random = new Random();
        do {
            long number = 1000000000L + (long) (random.nextDouble() * 9000000000L);
            accountNumber = String.valueOf(number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
