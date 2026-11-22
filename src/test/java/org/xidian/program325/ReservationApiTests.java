package org.xidian.program325;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousRequestsReceiveTheApiErrorEnvelope() throws Exception {
        mvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anonymousQueryDoesNotCreateASession() throws Exception {
        HttpSession session = mvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andReturn().getRequest().getSession(false);
        assertNull(session);
    }

    @Test
    void unknownLoginRoleIsRejectedWithoutAccessingTheDatabase() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test","password":"secret","role":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void unicodeWhitespaceIsRejectedAsAnEmptyUsername() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"\u00a0\u202f","password":"secret","role":"USER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号不能为空"));
    }

    @Test
    void timezoneOffsetsAreRejectedForReservationInput() throws Exception {
        mvc.perform(get("/api/rooms").param("startTime", "2027-01-01T08:00:00+08:00")
                        .param("endTime", "2027-01-01T09:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"subject":"Meeting","attendees":2,
                                 "startTime":"2027-01-01T08:00:00Z","endTime":"2027-01-01T09:00:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
