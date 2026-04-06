package com.smartbank.account.mapper;

import com.smartbank.account.dto.AccountDto;
import com.smartbank.account.dto.CreateAccountRequest;
import com.smartbank.account.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountDto toDto(Account account);

    List<AccountDto> toDtoList(List<Account> accounts);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "currency", defaultValue = "USD")
    Account toEntity(CreateAccountRequest request);
}
