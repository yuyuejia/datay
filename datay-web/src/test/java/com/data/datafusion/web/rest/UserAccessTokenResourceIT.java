package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.AuthoritiesConstants;
import com.data.datafusion.service.UserAccessTokenService;
import com.data.datafusion.service.UserService;
import com.data.datafusion.service.dto.AdminUserDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link UserAccessTokenResource} 的集成测试。
 */
@AutoConfigureMockMvc
@IntegrationTest
class UserAccessTokenResourceIT {

    private static final String LOGIN = "access-token-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAccessTokenService userAccessTokenService;

    @BeforeEach
    void setUp() {
        userRepository
            .findOneByLogin(LOGIN)
            .orElseGet(() -> {
                AdminUserDTO user = new AdminUserDTO();
                user.setLogin(LOGIN);
                user.setFirstName("Access");
                user.setLastName("Token");
                user.setEmail("access-token-test@example.com");
                user.setLangKey("zh-cn");
                user.setAuthorities(Set.of(AuthoritiesConstants.USER));
                return userService.createUser(user);
            });
    }

    @AfterEach
    void tearDown() {
        userService.deleteUser(LOGIN);
    }

    @Test
    @WithMockUser(LOGIN)
    void shouldCreateListDisableAndDeleteAccessToken() throws Exception {
        String createBody = """
            {"name":"Claude Desktop","description":"本地客户端"}
            """;
        String responseBody = mockMvc
            .perform(post("/api/account/access-tokens").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.accessToken.name").value("Claude Desktop"))
            .andExpect(jsonPath("$.accessToken.status").value("ENABLED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        String token = response.get("token").asText();
        String tokenId = response.get("accessToken").get("id").asText();

        assertThat(userAccessTokenService.authenticateUser(token)).isPresent();

        mockMvc
            .perform(get("/api/account/access-tokens"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Claude Desktop"))
            .andExpect(jsonPath("$[0].tokenHint").isNotEmpty());

        mockMvc
            .perform(
                patch("/api/account/access-tokens/" + tokenId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"DISABLED\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(userAccessTokenService.authenticateUser(token)).isEmpty();

        mockMvc.perform(delete("/api/account/access-tokens/" + tokenId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/account/access-tokens")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(LOGIN)
    void shouldRotateAccessTokenAndInvalidateOldValue() throws Exception {
        String createBody = """
            {"name":"Cursor","description":"开发环境"}
            """;
        String responseBody = mockMvc
            .perform(post("/api/account/access-tokens").contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        String oldToken = response.get("token").asText();
        String tokenId = response.get("accessToken").get("id").asText();

        String rotateBody = mockMvc
            .perform(post("/api/account/access-tokens/" + tokenId + "/rotate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String newToken = objectMapper.readTree(rotateBody).get("token").asText();

        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(userAccessTokenService.authenticateUser(oldToken)).isEmpty();
        assertThat(userAccessTokenService.authenticateUser(newToken)).isPresent();
    }
}
