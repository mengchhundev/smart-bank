package com.smartbank.account.repository;

import com.smartbank.account.entity.Account;
import com.smartbank.account.entity.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByUserId(Long userId);
    List<Account> findByStatus(AccountStatus status);
    boolean existsByAccountNumber(String accountNumber);
}
