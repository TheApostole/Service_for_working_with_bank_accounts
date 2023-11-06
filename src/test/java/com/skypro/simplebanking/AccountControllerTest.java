package com.skypro.simplebanking;
import com.skypro.simplebanking.dto.BankingUserDetails;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.repository.AccountRepository;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
@Testcontainers
@AutoConfigureMockMvc

//@WithMockUser
class AccountControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private DataTest d;
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withUsername("banking")
            .withPassword("super-safe-pass");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void getUserAccount() throws Exception {
        Account randomAccount = d.findRandomAccountButExclude();
        BankingUserDetails authUser = d.getAuthUser(randomAccount.getUser().getId());

        mockMvc.perform(get("/account/{id}", randomAccount.getId())
                        .with(user(authUser)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(randomAccount.getId()),
                        jsonPath("$.amount").value(randomAccount.getAmount()),
                        jsonPath("$.currency").value(randomAccount.getAccountCurrency().name())
                );
    }

    @Test
    void getUserAccount_AuthUserAndAccountUserIdMismatch_AccountNotFound() throws Exception {
        Account account1 = d.findRandomAccountButExclude();
        Account account2 = d.findRandomAccountButExclude(account1);
        account1.setUser(account2.getUser());

        BankingUserDetails authUser = d.getAuthUser(account1.getUser().getId());

        mockMvc.perform(get("/account/{id}", account1.getId())
                        .with(user(authUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void depositToAccount() throws Exception {
        Account randomAccount = d.findRandomAccountButExclude();
        BankingUserDetails authUser = d.getAuthUser(randomAccount.getUser().getId());
        long depositAmount = randomAccount.getAmount() / 2;
        long expectedAmount = randomAccount.getAmount() + depositAmount;

        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", depositAmount);

        mockMvc.perform(post("/account/deposit/{id}", randomAccount.getId())
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceChangeRequest.toString()))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(randomAccount.getId()),
                        jsonPath("$.amount").value(expectedAmount),
                        jsonPath("$.currency").value(randomAccount.getAccountCurrency().name()));

        long actualAmount = accountRepository.findById(randomAccount.getId()).orElseThrow().getAmount();
        assertEquals(expectedAmount, actualAmount);
    }

    @Test
    void depositToAccount_NegativeAmount_BadRequest() throws Exception {
        Account randomAccount = d.findRandomAccountButExclude();
        BankingUserDetails authUser = d.getAuthUser(randomAccount.getUser().getId());
        long depositAmount = randomAccount.getAmount() / 2 * (-1);

        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", depositAmount);

        String expectedErrorMessage = "Amount should be more than 0";
        mockMvc.perform(post("/account/deposit/{id}", randomAccount.getId())
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceChangeRequest.toString()))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$").value(expectedErrorMessage));
    }

    @Test
    void depositToAccount_WrongPairUserIdAndAccountId_AccountNotFound() throws Exception {
        Account account1 = d.findRandomAccountButExclude();
        Account account2 = d.findRandomAccountButExclude(account1);


        BankingUserDetails authUser = d.getAuthUser(account1.getUser().getId());
        long depositAmount = account1.getAmount() / 2;

        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", depositAmount);

        mockMvc.perform(post("/account/deposit/{id}", account2.getId())
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceChangeRequest.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawFromAccount() throws Exception {
        Account randomAccount = d.findRandomAccountButExclude();
        BankingUserDetails authUser = d.getAuthUser(randomAccount.getUser().getId());
        long withdrawAmount = randomAccount.getAmount() / 2;
        long expectedAmount = randomAccount.getAmount() - withdrawAmount;

        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", withdrawAmount);

        mockMvc.perform(post("/account/withdraw/{id}", randomAccount.getId())
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceChangeRequest.toString()))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(randomAccount.getId()),
                        jsonPath("$.amount").value(expectedAmount),
                        jsonPath("$.currency").value(randomAccount.getAccountCurrency().name()));

        long actualAmount = accountRepository.findById(randomAccount.getId()).orElseThrow().getAmount();
        assertEquals(expectedAmount, actualAmount);
    }

    @Test
    void withdrawFromAccount_NotEnoughFunds_BadRequest() throws Exception {
        Account randomAccount = d.findRandomAccountButExclude();
        BankingUserDetails authUser = d.getAuthUser(randomAccount.getUser().getId());
        long withdrawAmount = randomAccount.getAmount() * 2;

        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", withdrawAmount);

        String expectedErrorMessage = "Cannot withdraw " + withdrawAmount + " " + randomAccount.getAccountCurrency().name();
        mockMvc.perform(post("/account/withdraw/{id}", randomAccount.getId())
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceChangeRequest.toString()))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$").value(expectedErrorMessage));
    }

    @Test
    void withdrawFromAccount_NegativeAmount_BadRequest() throws Exception {
        Account randomAccount = d.findRandomAccountButExclude();
        BankingUserDetails authUser = d.getAuthUser(randomAccount.getUser().getId());
        long withdrawAmount = randomAccount.getAmount() * (-1);

        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", withdrawAmount);

        String expectedErrorMessage = "Amount should be more than 0";
        mockMvc.perform(post("/account/withdraw/{id}", randomAccount.getId())
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceChangeRequest.toString()))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$").value(expectedErrorMessage));
    }

    @Test
    void withdrawFromAccount_WrongPairUserIdAndAccountId_AccountNotFound() throws Exception {
        Account account1 = d.findRandomAccountButExclude();
        Account account2 = d.findRandomAccountButExclude(account1);

        BankingUserDetails authUser = d.getAuthUser(account1.getUser().getId());
        long withdrawAmount = account1.getAmount() / 2;

        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", withdrawAmount);

        mockMvc.perform(post("/account/withdraw/{id}", account2.getId())
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(balanceChangeRequest.toString()))
                .andExpect(status().isNotFound());
    }
}