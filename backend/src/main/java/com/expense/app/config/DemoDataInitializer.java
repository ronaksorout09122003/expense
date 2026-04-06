package com.expense.app.config;

import com.expense.app.dto.TransactionDtos;
import com.expense.app.entity.Account;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.Category;
import com.expense.app.entity.Counterparty;
import com.expense.app.enums.CounterpartyType;
import com.expense.app.enums.TransactionType;
import com.expense.app.repository.AccountRepository;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.CategoryRepository;
import com.expense.app.repository.CounterpartyRepository;
import com.expense.app.service.TransactionService;
import com.expense.app.service.UserBootstrapService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DemoDataInitializer {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserBootstrapService userBootstrapService;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final TransactionService transactionService;

    @Bean
    CommandLineRunner seedDemoData(@Value("${app.seed-demo:true}") boolean seedDemo) {
        return args -> {
            if (!seedDemo || appUserRepository.existsByEmailIgnoreCase("demo@ledgerlocal.app")) {
                return;
            }

            AppUser user = new AppUser();
            user.setFullName("Demo User");
            user.setEmail("demo@ledgerlocal.app");
            user.setMobile("9999999999");
            user.setCurrencyCode("INR");
            user.setTimezone("Asia/Kolkata");
            user.setPasswordHash(passwordEncoder.encode("demo1234"));
            user = appUserRepository.save(user);
            userBootstrapService.initializeDefaults(user);

            if (!transactionService.findAllVisibleTransactions(user.getId()).isEmpty()) {
                return;
            }

            Map<String, Account> accounts = accountRepository.findByUserIdAndDeletedAtIsNullOrderByNameAsc(user.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(Account::getName, account -> account));
            Map<String, Category> categories = categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(user.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(Category::getName, category -> category, (left, right) -> left));

            Counterparty grocery = createCounterparty(user, "Sharma Grocery", CounterpartyType.MERCHANT);
            Counterparty rajHotel = createCounterparty(user, "Raj Hotel", CounterpartyType.MERCHANT);
            Counterparty metro = createCounterparty(user, "Metro Counter", CounterpartyType.MERCHANT);
            Counterparty ram = createCounterparty(user, "Ram", CounterpartyType.PERSON);

            transactionService.create(user.getId(), new TransactionDtos.TransactionRequest(
                TransactionType.INCOME,
                new BigDecimal("28000"),
                OffsetDateTime.now().minusDays(12),
                null,
                accounts.get("Main Bank").getId(),
                categories.get("Salary").getId(),
                null,
                "Monthly salary credited",
                null,
                "SAL-2026-03",
                null,
                null,
                false,
                1,
                null
            ));

            transactionService.create(user.getId(), new TransactionDtos.TransactionRequest(
                TransactionType.TRANSFER,
                new BigDecimal("4000"),
                OffsetDateTime.now().minusDays(10),
                accounts.get("Main Bank").getId(),
                accounts.get("Cash").getId(),
                null,
                null,
                "Cash withdrawal",
                null,
                null,
                null,
                null,
                false,
                1,
                null
            ));

            transactionService.create(user.getId(), new TransactionDtos.TransactionRequest(
                TransactionType.EXPENSE,
                new BigDecimal("540"),
                OffsetDateTime.now().minusDays(5),
                accounts.get("Cash").getId(),
                null,
                categories.get("Vegetables").getId(),
                grocery.getId(),
                "Vegetables and milk",
                null,
                null,
                "Local market",
                null,
                false,
                1,
                null
            ));

            transactionService.create(user.getId(), new TransactionDtos.TransactionRequest(
                TransactionType.EXPENSE,
                new BigDecimal("160"),
                OffsetDateTime.now().minusDays(3),
                accounts.get("Cash").getId(),
                null,
                categories.get("Tea").getId(),
                rajHotel.getId(),
                "Tea and snacks with team",
                null,
                null,
                null,
                null,
                false,
                1,
                null
            ));

            transactionService.create(user.getId(), new TransactionDtos.TransactionRequest(
                TransactionType.EXPENSE,
                new BigDecimal("250"),
                OffsetDateTime.now().minusDays(2),
                accounts.get("UPI").getId(),
                null,
                categories.get("Metro").getId(),
                metro.getId(),
                "Metro recharge",
                null,
                null,
                null,
                null,
                false,
                1,
                null
            ));

            TransactionDtos.TransactionDetailResponse lend = transactionService.create(user.getId(), new TransactionDtos.TransactionRequest(
                TransactionType.LEND,
                new BigDecimal("1000"),
                OffsetDateTime.now().minusDays(4),
                accounts.get("Cash").getId(),
                null,
                null,
                ram.getId(),
                "Given for bike petrol",
                LocalDate.now().plusDays(5),
                null,
                null,
                null,
                false,
                1,
                null
            ));

            transactionService.create(user.getId(), new TransactionDtos.TransactionRequest(
                TransactionType.REPAYMENT_IN,
                new BigDecimal("400"),
                OffsetDateTime.now().minusDays(1),
                null,
                accounts.get("Cash").getId(),
                null,
                ram.getId(),
                "Partial return",
                null,
                null,
                null,
                lend.id(),
                false,
                1,
                null
            ));
        };
    }

    private Counterparty createCounterparty(AppUser user, String name, CounterpartyType type) {
        Counterparty counterparty = new Counterparty();
        counterparty.setUser(user);
        counterparty.setName(name);
        counterparty.setCounterpartyType(type);
        counterparty.setActive(true);
        return counterpartyRepository.save(counterparty);
    }
}
