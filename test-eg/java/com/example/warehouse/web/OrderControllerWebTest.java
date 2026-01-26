package com.example.warehouse.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.warehouse.ApplicationExceptionHandler;
import com.example.warehouse.dto.OrderDTO;
import com.example.warehouse.dto.OrderDTO.OrderDTOItem;
import com.example.warehouse.dto.OrderResponseDTO;
import com.example.warehouse.dto.OrderResponseDTO.OrderItemResponseDTO;
import com.example.warehouse.dto.OrderStatus;
import com.example.warehouse.entity.item.ConsumerItem;
import com.example.warehouse.entity.order.DefaultOrder;
import com.example.warehouse.entity.order.DefaultOrder.DefaultOrderItem;
import com.example.warehouse.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

// @AutoConfigureMockMvc
@WebMvcTest(controllers = OrderController.class)
public class OrderControllerWebTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private OrderService orderService;

        @MockBean
        private JwtDecoder jwtDecoder;

        private ObjectMapper objectMapper = new ObjectMapper();

        @InjectMocks
        OrderController orderController;

        @BeforeEach
        public void setup() {
                // Setup the JwtDecoder mock so that any token decoding returns a valid Jwt
                Instant now = Instant.now();
                Jwt dummyJwt = new Jwt(
                                "token",
                                now,
                                now.plusSeconds(3600),
                                Map.of("alg", "none"),
                                Map.of("sub", "userId1", "scope", "read write"));
                given(jwtDecoder.decode(anyString())).willReturn(dummyJwt);
                // MockitoAnnotations.openMocks(this);
                // mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                // .setControllerAdvice(new ApplicationExceptionHandler())
                // .build();
        }

        @Test
        @WithMockUser(username = "userId1", roles = { "USER" })
        public void shouldReturnOKWhenCalledWithValidInput() throws Exception {
                OrderDTO order = new OrderDTO(Set.of(
                                new OrderDTOItem(new ConsumerItem("id1"), 22),
                                new OrderDTOItem(new ConsumerItem("id2"), 23)));
                String inputJson = objectMapper.writeValueAsString(order);

                Set<OrderItemResponseDTO> orderItemResponses = order.getItems().stream()
                                .map(orderItem -> new OrderItemResponseDTO(orderItem.getItem().getId(), HttpStatus.OK,
                                                "Placed"))
                                .collect(Collectors.toSet());
                OrderResponseDTO orderResponseDTO = new OrderResponseDTO(order.getId(), orderItemResponses,
                                OrderStatus.SUCCESS);
                doReturn(orderResponseDTO).when(orderService).placeOrder(any(), any());

                String outputJson = objectMapper.writeValueAsString(orderResponseDTO);

                mockMvc.perform(post("/order")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inputJson))
                                .andExpect(result -> assertNull(result.getResolvedException()))
                                .andExpect(status().isOk())
                                .andExpect(content().json(outputJson))
                                .andExpect(jsonPath("$.message").value("Order placed"))
                                .andExpect(jsonPath("$.orderItemResponses.length()").value(2));
                verify(orderService, times(1)).placeOrder(any(), any());
        }

        @Test
        @WithMockUser(username = "userId1", roles = { "USER" })
        public void shouldReturnErrorWhenCalledWithInvalidInput() throws Exception {
                OrderDTO order = new OrderDTO(Set.of(
                                new OrderDTOItem(new ConsumerItem("id1"), 101),
                                new OrderDTOItem(new ConsumerItem("id2"), 102)));
                String inputJson = objectMapper.writeValueAsString(order);

                Set<OrderItemResponseDTO> orderItemResponses = Set.of(
                                new OrderItemResponseDTO("id1", HttpStatus.BAD_REQUEST, "Item out of stock"),
                                new OrderItemResponseDTO("id2", HttpStatus.BAD_REQUEST, "Item out of stock"));
                OrderResponseDTO orderResponseDTO = new OrderResponseDTO(order.getId(), orderItemResponses,
                                OrderStatus.FAILED);
                doReturn(orderResponseDTO).when(orderService).placeOrder(any(), any());
                String outputJson = objectMapper.writeValueAsString(orderResponseDTO);

                mockMvc.perform(post("/order")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inputJson))
                                .andExpect(result -> assertNull(result.getResolvedException()))
                                .andExpect(status().isBadRequest())
                                .andExpect(content().json(outputJson))
                                .andExpect(jsonPath("$.message").value("Order failed"))
                                .andExpect(jsonPath("$.orderItemResponses.length()").value(2));
                verify(orderService, times(1)).placeOrder(any(), any());
        }

        @Test
        @WithMockUser(username = "userId1", roles = { "USER" })
        public void shouldReturnMultiStatusWhenCalledWithPartialInvalidInput() throws Exception {
                // Use DefaultOrder as the input (assuming it serializes similarly to OrderDTO)
                OrderDTO order = new OrderDTO(Set.of(
                                new OrderDTOItem(new ConsumerItem("id1"), 22),
                                new OrderDTOItem(new ConsumerItem("id2"), 101)));
                String inputJson = objectMapper.writeValueAsString(order);

                Set<OrderItemResponseDTO> orderItemResponses = Set.of(
                                new OrderItemResponseDTO("id1", HttpStatus.OK, "Placed"),
                                new OrderItemResponseDTO("id2", HttpStatus.BAD_REQUEST, "Item out of stock"));
                OrderResponseDTO orderResponseDTO = new OrderResponseDTO(order.getId(), orderItemResponses,
                                OrderStatus.PARTIAL_FAILED);
                doReturn(orderResponseDTO).when(orderService).placeOrder(any(), any());
                String outputJson = objectMapper.writeValueAsString(orderResponseDTO);

                mockMvc.perform(post("/order")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inputJson))
                                .andExpect(result -> assertNull(result.getResolvedException()))
                                .andExpect(status().is(207))
                                .andExpect(content().json(outputJson))
                                .andExpect(jsonPath("$.message").value("Order failed for some items"))
                                .andExpect(jsonPath("$.orderItemResponses.length()").value(2));
                verify(orderService, times(1)).placeOrder(any(), any());
        }
}
