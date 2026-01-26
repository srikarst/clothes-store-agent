package com.example.warehouse.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import com.example.warehouse.WarehouseApplication;
import com.example.warehouse.dto.StockUpdateDTO;
import com.example.warehouse.dto.StockUpdateResponseDTO;
import com.example.warehouse.entity.item.ConsumerItem;
import com.example.warehouse.exception.invalidorder.OutOfStockException;
import com.example.warehouse.service.storagemanager.StorageManager;
import com.example.warehouse.util.TokenUtil;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
                "server.port=8086"
}, classes = WarehouseApplication.class)
@DirtiesContext
public class StockFunctionalTest {

        @Autowired
        TestRestTemplate restTemplate;

        @Autowired
        StorageManager storageManager;

        @AfterEach
        public void cleanUp() {
                try {
                        storageManager.pullItemsFromStorage("id1",
                                        storageManager.getItemCount("id1"));
                        storageManager.pullItemsFromStorage("id2",
                                        storageManager.getItemCount("id2"));
                } catch (OutOfStockException ex) {
                        ex.printStackTrace();
                }
        }

        @Test
        public void shouldUpdateStockWithCorrectValues() {
                // Generate the token without calling any authentication endpoint
                String token = TokenUtil.generateToken();
                assertNotNull(token);

                // Prepare headers with the token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                StockUpdateDTO stockUpdateDTO = new StockUpdateDTO(
                                Collections.singletonList(
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id1"), 2)));
                HttpEntity<StockUpdateDTO> stockRequest = new HttpEntity<>(stockUpdateDTO, headers);
                ResponseEntity<StockUpdateResponseDTO> response = restTemplate.postForEntity("/stock",
                                stockRequest, StockUpdateResponseDTO.class);
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertInstanceOf(StockUpdateResponseDTO.class, response.getBody());
                assertEquals("Stock updated successfully", response.getBody().getMessage());
                assertEquals(1, response.getBody().getItemUpdateDetails().size());
                assertEquals(HttpStatus.OK, response.getBody().getItemUpdateDetails().get(0).getHttpStatus());
                assertEquals("Item updated to stock successfully",
                                response.getBody().getItemUpdateDetails().get(0).getMessage());
                assertEquals(2, storageManager.getItemCount(stockUpdateDTO.getItems().get(0).getItem().getId()));
        }

        @Test
        public void shouldPartiallyUpdateStockWhenNotEnoughSpaceForSomeItems() {
                // Generate the token without calling any authentication endpoint
                String token = TokenUtil.generateToken();
                assertNotNull(token);

                // Prepare headers with the token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                StockUpdateDTO stockUpdateDTO = new StockUpdateDTO(
                                List.of(
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id1"), 2),
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id2"), 101)));
                HttpEntity<StockUpdateDTO> stockRequest = new HttpEntity<>(stockUpdateDTO, headers);
                ResponseEntity<StockUpdateResponseDTO> response = restTemplate.postForEntity("/stock",
                                stockRequest, StockUpdateResponseDTO.class);
                assertEquals(HttpStatus.MULTI_STATUS, response.getStatusCode());
                assertInstanceOf(StockUpdateResponseDTO.class, response.getBody());
                assertEquals("Stock update failed for some items", response.getBody().getMessage());
                assertEquals(2, response.getBody().getItemUpdateDetails().size());
                assertEquals(HttpStatus.OK, response.getBody().getItemUpdateDetails().get(0).getHttpStatus());
                assertEquals("Item updated to stock successfully",
                                response.getBody().getItemUpdateDetails().get(0).getMessage());
                assertEquals(HttpStatus.BAD_REQUEST, response.getBody().getItemUpdateDetails().get(1).getHttpStatus());
                assertEquals("There is not enough space in the warehouse to add the stock",
                                response.getBody().getItemUpdateDetails().get(1).getMessage());
                assertEquals(2, storageManager.getItemCount(stockUpdateDTO.getItems().get(0).getItem().getId()));
                assertEquals(0, storageManager.getItemCount(stockUpdateDTO.getItems().get(1).getItem().getId()));
        }

        @Test
        public void shouldFailWhenNotEnoughSpaceForAllItems() {
                // Generate the token without calling any authentication endpoint
                String token = TokenUtil.generateToken();
                assertNotNull(token);

                // Prepare headers with the token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                StockUpdateDTO stockUpdateDTO = new StockUpdateDTO(
                                List.of(
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id1"), 101),
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id2"), 102)));
                HttpEntity<StockUpdateDTO> stockRequest = new HttpEntity<>(stockUpdateDTO, headers);
                ResponseEntity<StockUpdateResponseDTO> response = restTemplate.postForEntity("/stock",
                                stockRequest, StockUpdateResponseDTO.class);
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                assertInstanceOf(StockUpdateResponseDTO.class, response.getBody());
                assertEquals("Stock update failed", response.getBody().getMessage());
                assertEquals(2, response.getBody().getItemUpdateDetails().size());
                assertEquals(HttpStatus.BAD_REQUEST, response.getBody().getItemUpdateDetails().get(0).getHttpStatus());
                assertEquals("There is not enough space in the warehouse to add the stock",
                                response.getBody().getItemUpdateDetails().get(0).getMessage());
                assertEquals(HttpStatus.BAD_REQUEST, response.getBody().getItemUpdateDetails().get(1).getHttpStatus());
                assertEquals("There is not enough space in the warehouse to add the stock",
                                response.getBody().getItemUpdateDetails().get(1).getMessage());
                assertEquals(0, storageManager.getItemCount(stockUpdateDTO.getItems().get(0).getItem().getId()));
                assertEquals(0, storageManager.getItemCount(stockUpdateDTO.getItems().get(1).getItem().getId()));
        }
}
