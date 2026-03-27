package com.smartbank.account.service;

import com.smartbank.account.dto.AccountDto;
import com.smartbank.account.dto.BalanceUpdateRequest;
import com.smartbank.account.dto.CreateAccountRequest;
import com.smartbank.account.entity.Account;
import com.smartbank.account.entity.AccountStatus;
import com.smartbank.account.exception.AccountNotFoundException;
import com.smartbank.account.exception.InsufficientBalanceException;
import com.smartbank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountDto createAccount(CreateAccountRequest request) {
        Account account = Account.builder()
                .accountNumber(generateAccountNumber())
                .accountHolderName(request.getAccountHolderName())
                .accountType(request.getAccountType())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .userId(request.getUserId())
                .build();

        Account saved = accountRepository.save(account);
        log.info("Account created: {}", saved.getAccountNumber());
        return toDto(saved);
    }

    public AccountDto getAccountByNumber(String accountNumber) {
        return toDto(findByAccountNumber(accountNumber));
    }

    public AccountDto getAccountById(Long id) {
        return toDto(accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id)));
    }

    public List<AccountDto> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AccountDto updateBalance(String accountNumber, BalanceUpdateRequest request) {
        Account account = findByAccountNumber(accountNumber);

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
        return toDto(updated);
    }

    @Transactional
    public AccountDto updateStatus(String accountNumber, AccountStatus status) {
        Account account = findByAccountNumber(accountNumber);
        account.setStatus(status);
        return toDto(accountRepository.save(account));
    }

    private Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    private String generateAccountNumber() {
        return "SB" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    private AccountDto toDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .userId(account.getUserId())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
