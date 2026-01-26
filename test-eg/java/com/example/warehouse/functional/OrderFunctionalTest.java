package com.example.warehouse.functional;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import com.example.warehouse.dto.OrderDTO;
import com.example.warehouse.dto.OrderDTO.OrderDTOItem;
import com.example.warehouse.dto.OrderResponseDTO;
import com.example.warehouse.dto.OrderResponseDTO.OrderItemResponseDTO;
import com.example.warehouse.dto.StockUpdateDTO;
import com.example.warehouse.entity.item.ConsumerItem;
import com.example.warehouse.exception.invalidorder.OutOfStockException;
import com.example.warehouse.service.storagemanager.StorageManager;
import com.example.warehouse.util.TokenUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = {
                "server.port=8086"
}, classes = WarehouseApplication.class)
@DirtiesContext
public class OrderFunctionalTest {

        @Autowired
        TestRestTemplate restTemplate;

        @Autowired
        StorageManager storageManager;

        @BeforeEach
        public void setUp() {
                // Generate the token without calling any authentication endpoint
                String token = TokenUtil.generateToken();
                assertNotNull(token);

                // Prepare headers with the token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                StockUpdateDTO stockUpdateDTO = new StockUpdateDTO(
                                List.of(new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id1"), 25),
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id2"), 25)));
                HttpEntity<StockUpdateDTO> request = new HttpEntity<>(stockUpdateDTO, headers);
                restTemplate.postForEntity("/stock", request, StockUpdateDTO.class);
        }

        @AfterEach
        public void cleanUp() throws OutOfStockException {
                storageManager.pullItemsFromStorage("id1",
                                storageManager.getItemCount("id1"));
                storageManager.pullItemsFromStorage("id2",
                                storageManager.getItemCount("id2"));
        }

        @Test
        public void shouldPlaceOrderWhenValidOrderIsPlaced() {
                // Generate the token without calling any authentication endpoint
                String token = TokenUtil.generateToken();
                assertNotNull(token);

                // Prepare headers with the token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                OrderDTO order = new OrderDTO(Set.of(new OrderDTOItem(new ConsumerItem("id1"), 22),
                                new OrderDTOItem(new ConsumerItem("id2"), 23)));
                HttpEntity<OrderDTO> orderRequest = new HttpEntity<>(order, headers);
                ResponseEntity<OrderResponseDTO> response = restTemplate.postForEntity("/order", orderRequest,
                                OrderResponseDTO.class);

                assertNotNull(response.getBody());
                assertEquals(HttpStatus.OK, response.getStatusCode());
                Set<OrderItemResponseDTO> orderItemResponses = response.getBody().getOrderItemResponses();
                assertEquals(order.getItems().size(), response.getBody().getOrderItemResponses().size());
                boolean noneNotSuccess = orderItemResponses.stream()
                                .noneMatch(orderItemResponse -> !orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.OK));
                boolean allSuccess = orderItemResponses.stream()
                                .allMatch(orderItemResponse -> orderItemResponse.getHttpStatus().equals(HttpStatus.OK));
                boolean anyNotSuccess = orderItemResponses.stream()
                                .anyMatch(orderItemResponse -> !orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.OK));
                Integer successCount = (int) orderItemResponses.stream()
                                .filter(orderItemResponse -> orderItemResponse.getHttpStatus().equals(HttpStatus.OK))
                                .count();
                assertEquals(true, noneNotSuccess);
                assertEquals(true, allSuccess);
                assertEquals(false, anyNotSuccess);
                assertEquals(order.getItems().size(), successCount);
                assertEquals(3, storageManager.getItemCount("id1"));
                assertEquals(2, storageManager.getItemCount("id2"));
        }

        @Test
        public void shouldPartiallyPlaceOrderWhenOutOfStockForSomeItems() {
                // Generate the token without calling any authentication endpoint
                String token = TokenUtil.generateToken();
                assertNotNull(token);

                // Prepare headers with the token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                OrderDTO order = new OrderDTO(Set.of(new OrderDTOItem(new ConsumerItem("id1"), 22),
                                new OrderDTOItem(new ConsumerItem("id2"), 102)));
                HttpEntity<OrderDTO> orderRequest = new HttpEntity<>(order, headers);
                ResponseEntity<OrderResponseDTO> response = restTemplate.postForEntity("/order", orderRequest,
                                OrderResponseDTO.class);
                assertNotNull(response.getBody());
                assertEquals(HttpStatus.MULTI_STATUS, response.getStatusCode());
                Set<OrderItemResponseDTO> orderItemResponses = response.getBody().getOrderItemResponses();
                assertEquals(order.getItems().size(),
                                response.getBody().getOrderItemResponses().size());
                boolean noneNotSuccess = orderItemResponses.stream()
                                .noneMatch(orderItemResponse -> !orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.OK));
                boolean allSuccess = orderItemResponses.stream()
                                .allMatch(orderItemResponse -> orderItemResponse.getHttpStatus().equals(HttpStatus.OK));
                boolean anyNotSuccess = orderItemResponses.stream()
                                .anyMatch(orderItemResponse -> !orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.OK));
                Integer failedCount = (int) orderItemResponses.stream()
                                .filter(orderItemResponse -> orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.BAD_REQUEST))
                                .count();
                Integer successCount = (int) orderItemResponses.stream()
                                .filter(orderItemResponse -> orderItemResponse.getHttpStatus().equals(HttpStatus.OK))
                                .count();
                assertEquals(false, noneNotSuccess);
                assertEquals(false, allSuccess);
                assertEquals(true, anyNotSuccess);
                assertEquals(1, successCount);
                assertEquals(1, failedCount);
                assertEquals(3, storageManager.getItemCount("id1"));
                assertEquals(25, storageManager.getItemCount("id2"));
        }

        @Test
        public void shouldNotPlaceOrderWhenOutOfStockForSomeItems() {
                // Generate the token without calling any authentication endpoint
                String token = TokenUtil.generateToken();
                assertNotNull(token);

                // Prepare headers with the token
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                headers.setContentType(MediaType.APPLICATION_JSON);
                OrderDTO order = new OrderDTO(Set.of(new OrderDTOItem(new ConsumerItem("id1"), 101),
                                new OrderDTOItem(new ConsumerItem("id2"), 102)));
                HttpEntity<OrderDTO> orderRequest = new HttpEntity<>(order, headers);
                ResponseEntity<OrderResponseDTO> response = restTemplate.postForEntity("/order", orderRequest,
                                OrderResponseDTO.class);
                assertNotNull(response.getBody());
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                Set<OrderItemResponseDTO> orderItemResponses = response.getBody().getOrderItemResponses();
                assertEquals(order.getItems().size(),
                                response.getBody().getOrderItemResponses().size());
                boolean noneNotSuccess = orderItemResponses.stream()
                                .noneMatch(orderItemResponse -> !orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.OK));
                boolean allSuccess = orderItemResponses.stream()
                                .allMatch(orderItemResponse -> orderItemResponse.getHttpStatus().equals(HttpStatus.OK));
                boolean anyNotSuccess = orderItemResponses.stream()
                                .anyMatch(orderItemResponse -> !orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.OK));
                Integer failedCount = (int) orderItemResponses.stream()
                                .filter(orderItemResponse -> orderItemResponse.getHttpStatus()
                                                .equals(HttpStatus.BAD_REQUEST))
                                .count();
                Integer successCount = (int) orderItemResponses.stream()
                                .filter(orderItemResponse -> orderItemResponse.getHttpStatus().equals(HttpStatus.OK))
                                .count();
                assertEquals(false, noneNotSuccess);
                assertEquals(false, allSuccess);
                assertEquals(true, anyNotSuccess);
                assertEquals(0, successCount);
                assertEquals(2, failedCount);
                assertEquals(25, storageManager.getItemCount("id1"));
                assertEquals(25, storageManager.getItemCount("id2"));
        }
}
