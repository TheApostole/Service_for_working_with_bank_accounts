package com.skypro.simplebanking;
import com.skypro.simplebanking.dto.BankingUserDetails;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
@AutoConfigureMockMvc
@Testcontainers

class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
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
    @Transactional
    void createUser(@Value("${app.security.admin-token}") String adminToken) throws Exception {
        JSONObject newUserJson = dataTest.getNewUser();

        mockMvc.perform(post("/user/")
                        .header("X-SECURITY-ADMIN-KEY", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserJson.toString()))
                .andExpect(status().isOk());

        assertTrue(userRepository.findByUsername(newUserJson.getAsString("username")).isPresent());

        accountRepository.findAll().forEach(System.out::println);
    }

    @Test
    void createUser_UnderNonAdminRights_Forbidden() throws Exception {
        User randomUser = dataTest.findRandomUser();
        BankingUserDetails authUser = dataTest.getAuthUser(randomUser.getId());

        JSONObject newUserJson = dataTest.getNewUser();

        mockMvc.perform(post("/user/")
                        .with(user(authUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserJson.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void createUser_WhichIsAlreadyExist_BadRequest(@Value("${app.security.admin-token}") String adminToken) throws Exception {
        User randomUser = dataTest.findRandomUser();
        JSONObject existingUserJson = new JSONObject();
        existingUserJson.put("username", randomUser.getUsername());
        existingUserJson.put("password", randomUser.getPassword());;

        mockMvc.perform(post("/user/")
                        .header("X-SECURITY-ADMIN-KEY", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(existingUserJson.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getAllUsers() throws Exception {
        mockMvc.perform(get("/user/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAllUsers_UnauthorizedRequest_Unauthorized() throws Exception {
        mockMvc.perform(get("/user/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfile() throws Exception {
        User randomUser = dataTest.findRandomUser();
        BankingUserDetails authUser = dataTest.getAuthUser(randomUser.getId());

        mockMvc.perform(get("/user/me")
                        .with(user(authUser)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(randomUser.getId()),
                        jsonPath("$.username").value(randomUser.getUsername())
                );
    }

    @Test
    void getMyProfile_UnderAdminRights_Forbidden(@Value("${app.security.admin-token}") String adminToken) throws Exception {
        mockMvc.perform(get("/user/me")
                        .header("X-SECURITY-ADMIN-KEY", adminToken))
                .andExpect(status().isForbidden());
    }
}