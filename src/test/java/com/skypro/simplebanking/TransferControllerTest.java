package com.skypro.simplebanking;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skypro.simplebanking.dto.BankingUserDetails;
import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
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
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
@AutoConfigureMockMvc
@Testcontainers

class TransferControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private DataTest dataTest;

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
    void transfer() throws Exception {
        Account sourceAccount = dataTest.findRandomAccountButExclude();
        Account destinationAccount = dataTest.findRandomAccountButExclude(sourceAccount);

        JSONObject transferRequest = dataTest.getTransferRequest(sourceAccount, destinationAccount);
        BankingUserDetails authUser = dataTest.getAuthUser(sourceAccount.getUser().getId());

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest.toString())
                        .with(user(authUser)))
                .andExpect(status().isOk());

        assertEquals(transferRequest.getAsNumber("amount"), sourceAccount.getAmount() - accountRepository.findById(sourceAccount.getId()).orElseThrow().getAmount());
        assertEquals(transferRequest.getAsNumber("amount"), accountRepository.findById(destinationAccount.getId()).orElseThrow().getAmount() - destinationAccount.getAmount());
    }

    @Test
    void transfer_NotEnoughFunds_BadRequest() throws Exception {
        Account sourceAccount = dataTest.findRandomAccountButExclude();
        Account destinationAccount = dataTest.findRandomAccountButExclude(sourceAccount);

        JSONObject transferRequest = dataTest.getTransferRequest(sourceAccount, destinationAccount);
        transferRequest.put("amount", (long)transferRequest.getAsNumber("amount") * 3);
        BankingUserDetails authUser = dataTest.getAuthUser(sourceAccount.getUser().getId());

        String expectedErrorMessage = "Cannot withdraw " + transferRequest.getAsNumber("amount") + " " + sourceAccount.getAccountCurrency().name();
        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest.toString())
                        .with(user(authUser)))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$").value(expectedErrorMessage));
    }

    @Test
    void transfer_NegativeAmount_BadRequest() throws Exception {
        Account sourceAccount = dataTest.findRandomAccountButExclude();
        Account destinationAccount = dataTest.findRandomAccountButExclude(sourceAccount);

        JSONObject transferRequest = dataTest.getTransferRequest(sourceAccount, destinationAccount);
        transferRequest.put("amount", (long)transferRequest.getAsNumber("amount") * (-1));
        BankingUserDetails authUser = dataTest.getAuthUser(sourceAccount.getUser().getId());

        String expectedErrorMessage = "Amount should be more than 0";
        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest.toString())
                        .with(user(authUser)))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$").value(expectedErrorMessage));
    }

    @Test
    void transfer_CurrencyMismatch_BadRequest() throws Exception {
        List<Account> twoRandomAccounts = dataTest.findTwoRandomAccountsWithDifferentCurrency();

        JSONObject transferRequest = dataTest.getTransferRequest(twoRandomAccounts.get(0), twoRandomAccounts.get(1));
        BankingUserDetails authUser = dataTest.getAuthUser(twoRandomAccounts.get(0).getUser().getId());

        String expectedErrorMessage = "Account currencies should be same";
        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest.toString())
                        .with(user(authUser)))
                .andExpectAll(
                        status().isBadRequest(),
                        jsonPath("$").value(expectedErrorMessage));
    }

    @Test
    void transfer_UserIdAccountIdMismatch_AccountNotFound() throws Exception {
        Account sourceAccount = dataTest.findRandomAccountButExclude();
        Account destinationAccount = dataTest.findRandomAccountButExclude(sourceAccount);
        Account userProviderAccount = dataTest.findRandomAccountButExclude(sourceAccount, destinationAccount);
        destinationAccount.setUser(userProviderAccount.getUser());

        JSONObject transferRequest = dataTest.getTransferRequest(sourceAccount, destinationAccount);
        BankingUserDetails authUser = dataTest.getAuthUser(sourceAccount.getUser().getId());

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest.toString())
                        .with(user(authUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_WrongPairUserIdAndAccountId_WithdrawPart_AccountNotFound() throws Exception {
        Account sourceAccount = dataTest.findRandomAccountButExclude();
        Account destinationAccount = dataTest.findRandomAccountButExclude(sourceAccount);
        Account idProviderAccount = dataTest.findRandomAccountButExclude(sourceAccount, destinationAccount);
        sourceAccount.setId(idProviderAccount.getId());

        JSONObject transferRequest = dataTest.getTransferRequest(sourceAccount, destinationAccount);
        BankingUserDetails authUser = dataTest.getAuthUser(sourceAccount.getUser().getId());

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest.toString())
                        .with(user(authUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_WrongPairUserIdAndAccountId_DepositPart_AccountNotFound() throws Exception {
        Account sourceAccount = dataTest.findRandomAccountButExclude();
        Account destinationAccount = dataTest.findRandomAccountButExclude(sourceAccount);
        Account idProviderAccount = dataTest.findRandomAccountButExclude(sourceAccount, destinationAccount);
        destinationAccount.setId(idProviderAccount.getId());

        JSONObject transferRequest = dataTest.getTransferRequest(sourceAccount, destinationAccount);
        BankingUserDetails authUser = dataTest.getAuthUser(sourceAccount.getUser().getId());

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest.toString())
                        .with(user(authUser)))
                .andExpect(status().isNotFound());
    }
}