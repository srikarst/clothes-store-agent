package com.example.warehouse.functional;

import com.example.warehouse.WarehouseApplication;
import com.example.warehouse.dto.StockUpdateDTO;
import com.example.warehouse.util.TokenUtil;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
        "server.port=8086"
}, classes = WarehouseApplication.class)
@DirtiesContext
public class WelcomeFunctionalTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    public void shouldSayWelcome() {
        // Generate the token without calling any authentication endpoint
        String token = TokenUtil.generateToken();
        assertNotNull(token);

        // Prepare headers with the token
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<StockUpdateDTO> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/", HttpMethod.GET, request, String.class);
        assertThat(response.getBody()).isEqualTo("Hello! welcome to my warehouse");
    }

}