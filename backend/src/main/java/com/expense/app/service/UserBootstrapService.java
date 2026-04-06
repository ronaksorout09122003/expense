package com.expense.app.service;

import com.expense.app.entity.Account;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.Category;
import com.expense.app.entity.UserSetting;
import com.expense.app.enums.AccountType;
import com.expense.app.enums.CategoryKind;
import com.expense.app.repository.AccountRepository;
import com.expense.app.repository.CategoryRepository;
import com.expense.app.repository.UserSettingRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBootstrapService {

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserSettingRepository userSettingRepository;

    @Transactional
    public void initializeDefaults(AppUser user) {
        if (userSettingRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }

        Account cash = createAccount(user, "Cash", AccountType.CASH, "#19a05b", BigDecimal.ZERO);
        createAccount(user, "Main Bank", AccountType.BANK, "#155eef", BigDecimal.ZERO);
        createAccount(user, "UPI", AccountType.UPI, "#f79009", BigDecimal.ZERO);

        Map<String, List<String>> expenseCategories = new LinkedHashMap<>();
        expenseCategories.put("Grocery", List.of("Milk", "Vegetables", "Fruits", "Daily Essentials"));
        expenseCategories.put("Food", List.of("Hotel", "Tea", "Snacks", "Cigarette"));
        expenseCategories.put("Travel", List.of("Metro", "Bus", "Petrol", "Diesel"));
        expenseCategories.put("Recharge and Bills", List.of("Mobile Recharge", "Internet", "Electricity"));
        expenseCategories.put("Vehicle", List.of("Bike Service", "Tractor Fuel"));
        expenseCategories.put("Health", List.of("Medicine", "Doctor", "Insurance"));
        expenseCategories.put("Education", List.of("Copy", "Pen", "Books"));
        expenseCategories.put("Home", List.of("Rent", "Maintenance", "Utilities"));
        expenseCategories.put("Personal", List.of("Clothing", "Care", "Subscriptions"));
        expenseCategories.put("Entertainment", List.of("Movies", "Games", "Trips"));
        expenseCategories.put("Miscellaneous", List.of("Emergency", "Gifts", "Other"));

        int sortOrder = 1;
        for (Map.Entry<String, List<String>> entry : expenseCategories.entrySet()) {
            Category parent = createCategory(user, entry.getKey(), null, CategoryKind.EXPENSE, sortOrder++);
            int childOrder = 1;
            for (String childName : entry.getValue()) {
                createCategory(user, childName, parent, CategoryKind.EXPENSE, childOrder++);
            }
        }

        Category income = createCategory(user, "Income", null, CategoryKind.INCOME, 999);
        createCategory(user, "Salary", income, CategoryKind.INCOME, 1);
        createCategory(user, "Gift", income, CategoryKind.INCOME, 2);
        createCategory(user, "Sale", income, CategoryKind.INCOME, 3);

        UserSetting setting = new UserSetting();
        setting.setUser(user);
        setting.setDefaultAccount(cash);
        setting.setDefaultCurrency(user.getCurrencyCode());
        setting.setDateFormat("dd MMM yyyy");
        setting.setBiometricEnabled(false);
        setting.setReminderEnabled(false);
        setting.setSessionTimeoutMinutes(30);
        userSettingRepository.save(setting);
    }

    private Account createAccount(AppUser user, String name, AccountType type, String accentColor, BigDecimal openingBalance) {
        Account account = new Account();
        account.setUser(user);
        account.setName(name);
        account.setAccountType(type);
        account.setOpeningBalance(openingBalance);
        account.setActive(true);
        account.setAccentColor(accentColor);
        return accountRepository.save(account);
    }

    private Category createCategory(AppUser user, String name, Category parent, CategoryKind kind, int sortOrder) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setParentCategory(parent);
        category.setCategoryKind(kind);
        category.setSortOrder(sortOrder);
        category.setActive(true);
        return categoryRepository.save(category);
    }
}
