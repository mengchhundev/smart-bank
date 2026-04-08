package com.smartbank.account.service;

import com.smartbank.account.dto.AccountDto;
import com.smartbank.account.dto.BalanceUpdateRequest;
import com.smartbank.account.dto.CreateAccountRequest;
import com.smartbank.account.entity.Account;
import com.smartbank.account.entity.AccountStatus;
import com.smartbank.account.exception.AccountNotFoundException;
import com.smartbank.account.exception.InsufficientBalanceException;
import com.smartbank.account.mapper.AccountMapper;
import com.smartbank.account.repository.AccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper     accountMapper;
    private final MeterRegistry     meterRegistry;

    private Counter accountCreatedCounter;

    @PostConstruct
    void initMetrics() {
        accountCreatedCounter = Counter.builder("account_created_total")
                .description("Total number of bank accounts created")
                .register(meterRegistry);
    }

    @Transactional
    public AccountDto createAccount(CreateAccountRequest request, Long userId) {
        Account account = accountMapper.toEntity(request);
        account.setAccountNumber(generateAccountNumber());
        account.setUserId(userId);
        if (account.getCurrency() == null) {
            account.setCurrency("USD");
        }

        Account saved = accountRepository.save(account);
        accountCreatedCounter.increment();
        log.info("Account created: {} for userId: {}", saved.getAccountNumber(), userId);
        return accountMapper.toDto(saved);
    }

    public AccountDto getAccountById(Long id) {
        return accountMapper.toDto(findById(id));
    }

    public List<AccountDto> getAccountsByUserId(Long userId) {
        return accountMapper.toDtoList(accountRepository.findByUserId(userId));
    }

    @Transactional
    public AccountDto updateStatus(Long id, AccountStatus status) {
        Account account = findById(id);
        account.setStatus(status);
        Account updated = accountRepository.save(account);
        log.info("Account {} status changed to {}", updated.getAccountNumber(), status);
        return accountMapper.toDto(updated);
    }

    // ── Internal endpoints for inter-service communication ──

    public AccountDto getAccountByNumber(String accountNumber) {
        return accountMapper.toDto(findByAccountNumber(accountNumber));
    }

    @Transactional
    public AccountDto updateBalance(String accountNumber, BalanceUpdateRequest request) {
        Account account = findByAccountNumber(accountNumber);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot perform balance operation on account with status: " + account.getStatus());
        }

        if (request.getType() == BalanceUpdateRequest.TransactionType.DEBIT) {
            if (account.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException("Insufficient balance in account: " + accountNumber);
            }
            account.setBalance(account.getBalance().subtract(request.getAmount()));
        } else {
            account.setBalance(account.getBalance().add(request.getAmount()));
        }

        Account updated = accountRepository.save(account);
        log.info("Balance updated for {}: new balance = {}", accountNumber, updated.getBalance());
        return accountMapper.toDto(updated);
    }

    private Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));
    }

    private Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    private String generateAccountNumber() {
        return "SB" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }
}
