package com.smartbank.account.service;

import com.smartbank.account.dto.AccountDto;
import com.smartbank.account.dto.BalanceUpdateRequest;
import com.smartbank.account.dto.CreateAccountRequest;
import com.smartbank.account.entity.Account;
import com.smartbank.account.entity.AccountStatus;
import com.smartbank.account.entity.AccountType;
import com.smartbank.account.exception.AccountNotFoundException;
import com.smartbank.account.exception.InsufficientBalanceException;
import com.smartbank.account.mapper.AccountMapper;
import com.smartbank.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    private Account account;
    private AccountDto accountDto;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(1L)
                .accountNumber("SB1234567890ABCD")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.valueOf(1000))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .userId(100L)
                .version(0)
                .createdAt(LocalDateTime.now())
                .build();

        accountDto = AccountDto.builder()
                .id(1L)
                .accountNumber("SB1234567890ABCD")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.valueOf(1000))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .userId(100L)
                .createdAt(account.getCreatedAt())
                .build();
    }

    @Nested
    @DisplayName("createAccount")
    class CreateAccountTests {

        @Test
        @DisplayName("should create account with auto-generated account number")
        void shouldCreateAccount() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountHolderName("John Doe")
                    .accountType(AccountType.SAVINGS)
                    .currency("USD")
                    .build();

            Account mappedEntity = Account.builder()
                    .accountHolderName("John Doe")
                    .accountType(AccountType.SAVINGS)
                    .currency("USD")
                    .build();

            when(accountMapper.toEntity(request)).thenReturn(mappedEntity);
            when(accountRepository.save(any(Account.class))).thenReturn(account);
            when(accountMapper.toDto(account)).thenReturn(accountDto);

            AccountDto result = accountService.createAccount(request, 100L);

            assertThat(result).isNotNull();
            assertThat(result.getAccountNumber()).isEqualTo("SB1234567890ABCD");
            assertThat(result.getUserId()).isEqualTo(100L);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            Account savedAccount = captor.getValue();
            assertThat(savedAccount.getUserId()).isEqualTo(100L);
            assertThat(savedAccount.getAccountNumber()).startsWith("SB");
            assertThat(savedAccount.getAccountNumber()).hasSize(16);
        }

        @Test
        @DisplayName("should default currency to USD when not provided")
        void shouldDefaultCurrencyToUsd() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountHolderName("Jane Doe")
                    .accountType(AccountType.CHECKING)
                    .build();

            Account mappedEntity = Account.builder()
                    .accountHolderName("Jane Doe")
                    .accountType(AccountType.CHECKING)
                    .build();

            when(accountMapper.toEntity(request)).thenReturn(mappedEntity);
            when(accountRepository.save(any(Account.class))).thenReturn(account);
            when(accountMapper.toDto(account)).thenReturn(accountDto);

            accountService.createAccount(request, 100L);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue().getCurrency()).isEqualTo("USD");
        }
    }

    @Nested
    @DisplayName("getAccountById")
    class GetAccountByIdTests {

        @Test
        @DisplayName("should return account when found")
        void shouldReturnAccount() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(accountMapper.toDto(account)).thenReturn(accountDto);

            AccountDto result = accountService.getAccountById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getAccountNumber()).isEqualTo("SB1234567890ABCD");
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountById(999L))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    @Nested
    @DisplayName("getAccountsByUserId")
    class GetAccountsByUserIdTests {

        @Test
        @DisplayName("should return all accounts for user")
        void shouldReturnUserAccounts() {
            List<Account> accounts = List.of(account);
            List<AccountDto> dtos = List.of(accountDto);

            when(accountRepository.findByUserId(100L)).thenReturn(accounts);
            when(accountMapper.toDtoList(accounts)).thenReturn(dtos);

            List<AccountDto> result = accountService.getAccountsByUserId(100L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        void shouldReturnEmptyList() {
            when(accountRepository.findByUserId(999L)).thenReturn(List.of());
            when(accountMapper.toDtoList(List.of())).thenReturn(List.of());

            List<AccountDto> result = accountService.getAccountsByUserId(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatusTests {

        @Test
        @DisplayName("should activate account")
        void shouldActivateAccount() {
            account.setStatus(AccountStatus.INACTIVE);
            AccountDto activatedDto = AccountDto.builder()
                    .id(1L)
                    .status(AccountStatus.ACTIVE)
                    .build();

            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(account);
            when(accountMapper.toDto(any(Account.class))).thenReturn(activatedDto);

            AccountDto result = accountService.updateStatus(1L, AccountStatus.ACTIVE);

            assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(accountRepository).save(account);
        }

        @Test
        @DisplayName("should deactivate account")
        void shouldDeactivateAccount() {
            AccountDto deactivatedDto = AccountDto.builder()
                    .id(1L)
                    .status(AccountStatus.INACTIVE)
                    .build();

            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(account);
            when(accountMapper.toDto(any(Account.class))).thenReturn(deactivatedDto);

            AccountDto result = accountService.updateStatus(1L, AccountStatus.INACTIVE);

            assertThat(result.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        }

        @Test
        @DisplayName("should throw when account not found")
        void shouldThrowWhenNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.updateStatus(999L, AccountStatus.ACTIVE))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateBalance")
    class UpdateBalanceTests {

        @Test
        @DisplayName("should credit balance")
        void shouldCreditBalance() {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(500))
                    .type(BalanceUpdateRequest.TransactionType.CREDIT)
                    .build();

            Account updatedAccount = Account.builder()
                    .id(1L)
                    .accountNumber("SB1234567890ABCD")
                    .balance(BigDecimal.valueOf(1500))
                    .build();

            AccountDto updatedDto = AccountDto.builder()
                    .balance(BigDecimal.valueOf(1500))
                    .build();

            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);
            when(accountMapper.toDto(updatedAccount)).thenReturn(updatedDto);

            AccountDto result = accountService.updateBalance("SB1234567890ABCD", request);

            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        }

        @Test
        @DisplayName("should debit balance")
        void shouldDebitBalance() {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(300))
                    .type(BalanceUpdateRequest.TransactionType.DEBIT)
                    .build();

            Account updatedAccount = Account.builder()
                    .id(1L)
                    .accountNumber("SB1234567890ABCD")
                    .balance(BigDecimal.valueOf(700))
                    .build();

            AccountDto updatedDto = AccountDto.builder()
                    .balance(BigDecimal.valueOf(700))
                    .build();

            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);
            when(accountMapper.toDto(updatedAccount)).thenReturn(updatedDto);

            AccountDto result = accountService.updateBalance("SB1234567890ABCD", request);

            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(700));
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException on overdraft")
        void shouldThrowOnOverdraft() {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(2000))
                    .type(BalanceUpdateRequest.TransactionType.DEBIT)
                    .build();

            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.updateBalance("SB1234567890ABCD", request))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient balance");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow debit of exact balance amount")
        void shouldAllowExactBalanceDebit() {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(1000))
                    .type(BalanceUpdateRequest.TransactionType.DEBIT)
                    .build();

            Account zeroed = Account.builder()
                    .id(1L)
                    .accountNumber("SB1234567890ABCD")
                    .balance(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE)
                    .build();

            AccountDto zeroedDto = AccountDto.builder()
                    .balance(BigDecimal.ZERO)
                    .build();

            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(zeroed);
            when(accountMapper.toDto(zeroed)).thenReturn(zeroedDto);

            AccountDto result = accountService.updateBalance("SB1234567890ABCD", request);

            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should throw IllegalStateException when account is FROZEN")
        void shouldRejectBalanceUpdateOnFrozenAccount() {
            account.setStatus(AccountStatus.FROZEN);
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(100))
                    .type(BalanceUpdateRequest.TransactionType.DEBIT)
                    .build();

            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.updateBalance("SB1234567890ABCD", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FROZEN");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalStateException when account is CLOSED")
        void shouldRejectBalanceUpdateOnClosedAccount() {
            account.setStatus(AccountStatus.CLOSED);
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(100))
                    .type(BalanceUpdateRequest.TransactionType.CREDIT)
                    .build();

            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.updateBalance("SB1234567890ABCD", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CLOSED");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalStateException when account is INACTIVE")
        void shouldRejectBalanceUpdateOnInactiveAccount() {
            account.setStatus(AccountStatus.INACTIVE);
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(100))
                    .type(BalanceUpdateRequest.TransactionType.CREDIT)
                    .build();

            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.updateBalance("SB1234567890ABCD", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INACTIVE");

            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAccountByNumber")
    class GetAccountByNumberTests {

        @Test
        @DisplayName("should return account by account number")
        void shouldReturnByNumber() {
            when(accountRepository.findByAccountNumber("SB1234567890ABCD")).thenReturn(Optional.of(account));
            when(accountMapper.toDto(account)).thenReturn(accountDto);

            AccountDto result = accountService.getAccountByNumber("SB1234567890ABCD");

            assertThat(result.getAccountNumber()).isEqualTo("SB1234567890ABCD");
        }

        @Test
        @DisplayName("should throw when account number not found")
        void shouldThrowWhenNotFound() {
            when(accountRepository.findByAccountNumber("INVALID")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountByNumber("INVALID"))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }
}
