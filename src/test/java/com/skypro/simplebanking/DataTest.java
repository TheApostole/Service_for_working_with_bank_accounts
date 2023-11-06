package com.skypro.simplebanking;
import com.skypro.simplebanking.dto.BankingUserDetails;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.AccountCurrency;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import net.minidev.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component

public class DataTest {
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    public DataTest(
            UserRepository userRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${test.database.users-number}") int number) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        createUsers(number);
    }

    private void createUsers(int size) {
        List<User> userList = new ArrayList<>();

        while (size > userList.size()) {
            String userName = "User_" + (userList.size() + 1);
            String password = "User_" + (userList.size() + 1) + "_password";
            int refNumber = 10 * (userList.size() + 1);

            User user = new User();
            user.setUsername(userName);
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
            createAccounts(user, refNumber);
            userList.add(user);
        }
    }

    private void createAccounts(User user, int refNumber) {
        user.setAccounts(new ArrayList<>());
        for (
                AccountCurrency currency : AccountCurrency.values()) {
            Account account = new Account();
            account.setUser(user);
            account.setAccountCurrency(currency);
            account.setAmount((long) (currency.ordinal() + refNumber));
            user.getAccounts().add(account);
            accountRepository.save(account);
        }
    }

    public JSONObject getNewUser() {
        long UserNumber = userRepository.findAll().stream()
                .min((e1, e2) -> e2.getId().compareTo(e1.getId()))
                .orElseThrow()
                .getId()
                + 1L;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username", "User_" + UserNumber);
        jsonObject.put("password", "User_" + UserNumber + "_password");
        return jsonObject;
    }

    public JSONObject getTransferRequest(Account account1, Account account2) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fromAccountId", account1.getId());
        jsonObject.put("toAccountId", account2.getId());
        jsonObject.put("toUserId", account2.getUser().getId());
        jsonObject.put("amount", account1.getAmount() / 2);
        return jsonObject;
    }

    public Account findRandomAccountButExclude(Account... accounts) {
        List<Account> inputAccounts = Arrays.stream(accounts).toList();
        List<Account> dbAccounts = accountRepository.findAll();
        if (inputAccounts.size() > 0) {
            dbAccounts = dbAccounts.stream()
                    .filter(e -> !inputAccounts.contains(e))
                    .filter(e -> e.getAccountCurrency().equals(inputAccounts.get(0).getAccountCurrency()))
                    .toList();
        }
        return dbAccounts.get(random.nextInt(dbAccounts.size()));
    }

    public User findRandomUser() {
        List<User> users = userRepository.findAll();
        return users.get(random.nextInt(users.size()));
    }

    public List<Account> findTwoRandomAccountsWithDifferentCurrency() {
        List<Account> randomAccounts = new ArrayList<>();
        List<Account> accounts = accountRepository.findAll();
        randomAccounts.add(accounts.get(random.nextInt(accounts.size())));
        accounts = accounts
                .stream()
                .filter(e -> !e.equals(randomAccounts.get(0)))
                .filter(e -> !e.getAccountCurrency().equals(randomAccounts.get(0).getAccountCurrency()))
                .collect(Collectors.toList());
        randomAccounts.add(accounts.get(random.nextInt(accounts.size())));
        return randomAccounts;
    }

    public BankingUserDetails getAuthUser(long id) {
        User user = userRepository.findById(id).orElseThrow();
        return new BankingUserDetails(user.getId(), user.getUsername(), user.getPassword(), false);
    }
}