package com.assistia.security;

import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.repository.TicketRepository;
import com.assistia.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthAndTicketSecurityIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String USER_EMAIL = "one@assistia.local";
    private static final String OTHER_EMAIL = "two@assistia.local";
    private static final String TECH_EMAIL = "tech@assistia.local";
    private static final String ADMIN_EMAIL = "admin@assistia.local";
    private static final String SYSTEM_EMAIL = "system@assistia.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userId;
    private Long otherUserId;
    private Long ticketForUser;
    private Long ticketForOtherUser;
    private String passwordHash;

    @BeforeAll
    void createPasswordHash() {
        passwordHash = passwordEncoder.encode(PASSWORD);
    }

    @BeforeEach
    void setUpAccountsAndTickets() {
        ticketRepository.deleteAll();
        userRepository.deleteAll();
        User user = saveUser(USER_EMAIL, "Alice", UserRole.USER, passwordHash);
        User other = saveUser(OTHER_EMAIL, "Bob", UserRole.USER, passwordHash);
        saveUser(TECH_EMAIL, "Taylor", UserRole.TECHNICIAN, passwordHash);
        saveUser(ADMIN_EMAIL, "Avery", UserRole.ADMIN, passwordHash);
        saveUser(SYSTEM_EMAIL, "System", UserRole.ADMIN, null);
        userId = user.getId();
        otherUserId = other.getId();
        ticketForUser = saveTicket(user).getId();
        ticketForOtherUser = saveTicket(other).getId();
    }

    @Test
    void loginMeAndLogoutUseAndInvalidateSession() throws Exception {
        MockHttpSession anonymousSession = new MockHttpSession();
        String oldSessionId = anonymousSession.getId();
        Csrf csrf = getCsrf(anonymousSession);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .session(anonymousSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.getId()).isNotEqualTo(oldSessionId);
        mockMvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Csrf logoutCsrf = getCsrf(authenticatedSession);
        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .cookie(logoutCsrf.cookie())
                        .header(logoutCsrf.headerName(), logoutCsrf.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBadPasswordAndAccountWithoutHashWithSameResponse() throws Exception {
        Csrf csrf = getCsrf(null);
        String badPasswordResponse = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USER_EMAIL, "incorrect-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants invalides."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String systemAccountResponse = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(SYSTEM_EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants invalides."))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(systemAccountResponse).isEqualTo(badPasswordResponse);
    }

    @Test
    void requiresCsrfTokenForLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USER_EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Accès refusé."));
    }

    @Test
    void deniesAnonymousTicketAccess() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentification requise."));
    }

    @Test
    void userCanSeeOnlyOwnTicketsCannotSpoofCreatorAndCannotChangeStatus() throws Exception {
        MockHttpSession session = login(USER_EMAIL);
        mockMvc.perform(get("/api/tickets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].creatorId").value(userId));

        mockMvc.perform(get("/api/tickets/{id}", ticketForOtherUser).session(session))
                .andExpect(status().isNotFound());

        Csrf csrf = getCsrf(session);
        mockMvc.perform(post("/api/tickets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Ticket usurpé",
                                  "description": "Tentative de définir un autre créateur.",
                                  "status": "CLOSED",
                                  "priority": "CRITICAL",
                                  "category": "GENERAL",
                                  "creatorId": %d,
                                  "role": "ADMIN"
                                }
                                """.formatted(otherUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creatorId").value(userId))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));

        Csrf updateCsrf = getCsrf(session);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/tickets/{id}", ticketForUser)
                        .session(session)
                        .cookie(updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Tentative de reclassement",
                                  "description": "La catégorie est réservée aux techniciens.",
                                  "category": "NETWORK"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/tickets/{id}/status", ticketForUser)
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianAndAdminCanManageTicketsWhileUserCannot() throws Exception {
        MockHttpSession technicianSession = login(TECH_EMAIL);
        Csrf technicianCsrf = getCsrf(technicianSession);
        mockMvc.perform(get("/api/tickets").session(technicianSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(patch("/api/tickets/{id}/status", ticketForUser)
                        .session(technicianSession)
                        .cookie(technicianCsrf.cookie())
                        .header(technicianCsrf.headerName(), technicianCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        MockHttpSession adminSession = login(ADMIN_EMAIL);
        Csrf adminCsrf = getCsrf(adminSession);
        mockMvc.perform(patch("/api/tickets/{id}/priority", ticketForOtherUser)
                        .session(adminSession)
                        .cookie(adminCsrf.cookie())
                        .header(adminCsrf.headerName(), adminCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    void onlyAdminCanProvisionNonAdminAccounts() throws Exception {
        MockHttpSession userSession = login(USER_EMAIL);
        Csrf userCsrf = getCsrf(userSession);
        mockMvc.perform(post("/api/admin/users")
                        .session(userSession)
                        .cookie(userCsrf.cookie())
                        .header(userCsrf.headerName(), userCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managedUserBody("new@assistia.local", "USER")))
                .andExpect(status().isForbidden());

        MockHttpSession adminSession = login(ADMIN_EMAIL);
        Csrf adminCsrf = getCsrf(adminSession);
        MvcResult created = mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .cookie(adminCsrf.cookie())
                        .header(adminCsrf.headerName(), adminCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managedUserBody("new@assistia.local", "TECHNICIAN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TECHNICIAN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        Long newUserId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        User provisioned = userRepository.findById(newUserId).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, provisioned.getPasswordHash())).isTrue();

        Csrf secondCsrf = getCsrf(adminSession);
        mockMvc.perform(post("/api/admin/users")
                        .session(adminSession)
                        .cookie(secondCsrf.cookie())
                        .header(secondCsrf.headerName(), secondCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managedUserBody("new-admin@assistia.local", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void systemAccountCannotAuthenticateDespiteLegacyAdminRole() throws Exception {
        User system = userRepository.findByEmailIgnoreCase(SYSTEM_EMAIL).orElseThrow();
        assertThat(system.getPasswordHash()).isNull();
        assertThat(system.getRole()).isEqualTo(UserRole.ADMIN);
    }

    private User saveUser(String email, String firstName, UserRole role, String passwordHash) {
        User user = new User(email, firstName, "Test", role, "IT");
        user.setPasswordHash(passwordHash);
        return userRepository.saveAndFlush(user);
    }

    private Ticket saveTicket(User creator) {
        return ticketRepository.saveAndFlush(new Ticket(
                "Ticket de " + creator.getFirstName(),
                "Description de test",
                TicketStatus.OPEN,
                TicketPriority.MEDIUM,
                TicketCategory.GENERAL,
                creator,
                null));
    }

    private MockHttpSession login(String email) throws Exception {
        Csrf csrf = getCsrf(null);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Csrf getCsrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new Csrf(json.get("headerName").asText(), json.get("token").asText(), cookie);
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    private String managedUserBody(String email, String role) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "email", email,
                "firstName", "New",
                "lastName", "User",
                "department", "IT",
                "role", role,
                "password", PASSWORD));
    }

    private record Csrf(String headerName, String token, Cookie cookie) {
    }
}
