package com.smartbank.transaction.repository;

import com.smartbank.transaction.entity.Transaction;
import com.smartbank.transaction.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByReferenceNumber(String referenceNumber);
    Page<Transaction> findBySourceAccountOrTargetAccount(String sourceAccount, String targetAccount, Pageable pageable);
    List<Transaction> findByStatus(TransactionStatus status);
}
