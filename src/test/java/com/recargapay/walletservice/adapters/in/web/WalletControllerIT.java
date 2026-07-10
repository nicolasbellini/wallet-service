package com.recargapay.walletservice.adapters.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createWallet() throws Exception {
        String userId = UUID.randomUUID().toString();
        String responseBody = mockMvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("userId", userId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseBody).get("walletId").asText());
    }

    @Test
    void fullHappyPathFlow() throws Exception {
        UUID wallet1 = createWallet();
        UUID wallet2 = createWallet();

        mockMvc.perform(post("/api/v1/wallet/" + wallet1 + "/deposit")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("amount", "100.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newBalance").value(100.00));

        Instant betweenDepositAndWithdrawal = Instant.now();
        Thread.sleep(20); // ensure it lands strictly between the two operations' timestamps

        mockMvc.perform(post("/api/v1/wallet/" + wallet1 + "/withdrawal")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("amount", "30.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newBalance").value(70.00));

        mockMvc.perform(post("/api/v1/transfer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceWalletId", wallet1.toString(),
                                "destinationWalletId", wallet2.toString(),
                                "amount", "20.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceBalanceAfter").value(50.00))
                .andExpect(jsonPath("$.destinationBalanceAfter").value(20.00));

        mockMvc.perform(get("/api/v1/wallet/" + wallet1 + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));

        mockMvc.perform(get("/api/v1/wallet/" + wallet1 + "/balance/history")
                        .queryParam("asOf", betweenDepositAndWithdrawal.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void balanceForUnknownWalletReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/" + UUID.randomUUID() + "/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawBeyondBalanceReturns422() throws Exception {
        UUID wallet = createWallet();
        mockMvc.perform(post("/api/v1/wallet/" + wallet + "/deposit")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("amount", "10.00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/wallet/" + wallet + "/withdrawal")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("amount", "10.01"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void depositOfNonPositiveAmountReturns400() throws Exception {
        UUID wallet = createWallet();
        mockMvc.perform(post("/api/v1/wallet/" + wallet + "/deposit")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("amount", "0.00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingSecondWalletForSameUserReturns409() throws Exception {
        String userId = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of("userId", userId));

        mockMvc.perform(post("/api/v1/wallet").contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/wallet").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }
}
