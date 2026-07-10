package com.recargapay.walletservice.adapters.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createWallet() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/wallet")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("userId", UUID.randomUUID().toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseBody).get("walletId").asText());
    }

    private void deposit(UUID walletId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/wallet/" + walletId + "/deposit")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("amount", amount))))
                .andExpect(status().isCreated());
    }

    private ResultActions transfer(UUID source, UUID destination, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/transfer")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "sourceWalletId", source.toString(),
                        "destinationWalletId", destination.toString(),
                        "amount", amount))));
    }

    @Test
    void transferBetweenExistingWalletsSucceeds() throws Exception {
        UUID source = createWallet();
        UUID destination = createWallet();
        deposit(source, "100.00");

        mockMvc.perform(post("/api/v1/transfer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceWalletId", source.toString(),
                                "destinationWalletId", destination.toString(),
                                "amount", "40.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceBalanceAfter").value(60.00))
                .andExpect(jsonPath("$.destinationBalanceAfter").value(40.00));

        mockMvc.perform(get("/api/v1/wallet/" + source + "/balance"))
                .andExpect(jsonPath("$.balance").value(60.00));
        mockMvc.perform(get("/api/v1/wallet/" + destination + "/balance"))
                .andExpect(jsonPath("$.balance").value(40.00));
    }

    @Test
    void transferFromNonexistentWalletReturns404() throws Exception {
        UUID destination = createWallet();
        transfer(UUID.randomUUID(), destination, "10.00").andExpect(status().isNotFound());
    }

    @Test
    void transferToNonexistentWalletReturns404() throws Exception {
        UUID source = createWallet();
        deposit(source, "10.00");
        transfer(source, UUID.randomUUID(), "10.00").andExpect(status().isNotFound());
    }

    @Test
    void transferExceedingSourceBalanceReturns422() throws Exception {
        UUID source = createWallet();
        UUID destination = createWallet();
        deposit(source, "10.00");

        transfer(source, destination, "10.01").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transferOfNonPositiveAmountReturns400() throws Exception {
        UUID source = createWallet();
        UUID destination = createWallet();

        transfer(source, destination, "0.00").andExpect(status().isBadRequest());
    }

    @Test
    void transferToSameWalletReturns400() throws Exception {
        UUID wallet = createWallet();
        transfer(wallet, wallet, "1.00").andExpect(status().isBadRequest());
    }

    @Test
    void retriedTransferWithSameIdempotencyKeyIsAppliedOnlyOnce() throws Exception {
        UUID source = createWallet();
        UUID destination = createWallet();
        deposit(source, "100.00");
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceWalletId", source.toString(),
                "destinationWalletId", destination.toString(),
                "amount", "30.00"));

        MvcResult first = mockMvc.perform(post("/api/v1/transfer")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/transfer")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());

        // applied once, not twice: 70.00/30.00, not 40.00/60.00
        mockMvc.perform(get("/api/v1/wallet/" + source + "/balance"))
                .andExpect(jsonPath("$.balance").value(70.00));
        mockMvc.perform(get("/api/v1/wallet/" + destination + "/balance"))
                .andExpect(jsonPath("$.balance").value(30.00));
    }
}
