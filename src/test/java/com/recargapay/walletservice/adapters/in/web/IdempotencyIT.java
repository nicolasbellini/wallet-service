package com.recargapay.walletservice.adapters.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIT {

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

    @Test
    void retriedDepositWithSameKeyIsAppliedOnlyOnce() throws Exception {
        UUID wallet = createWallet();
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of("amount", "100.00"));

        MvcResult first = mockMvc.perform(post("/api/v1/wallet/" + wallet + "/deposit")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/wallet/" + wallet + "/deposit")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/wallet/" + wallet + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00)); // not 200.00 - only applied once
    }

    @Test
    void withoutKeyEachRequestIsAppliedIndependently() throws Exception {
        UUID wallet = createWallet();
        String body = objectMapper.writeValueAsString(Map.of("amount", "100.00"));

        mockMvc.perform(post("/api/v1/wallet/" + wallet + "/deposit").contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/wallet/" + wallet + "/deposit").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wallet/" + wallet + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00)); // applied twice, as expected with no key
    }

    @Test
    void reusingKeyForADifferentWalletIsRejected() throws Exception {
        UUID wallet1 = createWallet();
        UUID wallet2 = createWallet();
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of("amount", "10.00"));

        mockMvc.perform(post("/api/v1/wallet/" + wallet1 + "/deposit")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/wallet/" + wallet2 + "/deposit")
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
