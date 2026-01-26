package com.example.warehouse.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.warehouse.ApplicationExceptionHandler;
import com.example.warehouse.dto.StockUpdateDTO;
import com.example.warehouse.dto.StockUpdateResponseDTO;
import com.example.warehouse.dto.StockUpdateResponseDTO.StockUpdateItemStatusDTO;
import com.example.warehouse.entity.item.ConsumerItem;
import com.example.warehouse.exception.invalidorder.InsufficientStorageException;
import com.example.warehouse.service.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@AutoConfigureMockMvc
public class StockControllerWebTest {

        @Autowired
        private MockMvc mockMvc;

        @Mock
        StockService stockService;

        @InjectMocks
        StockController stockController;

        @BeforeEach
        public void setUp() {
                MockitoAnnotations.openMocks(this);
                mockMvc = MockMvcBuilders.standaloneSetup(stockController)
                                .setControllerAdvice(new ApplicationExceptionHandler())
                                .build();
        }

        @Test
        public void shouldReturnOKWhenCalledWithValidInput() throws Exception {
                StockUpdateDTO stockUpdateDTO = new StockUpdateDTO(
                                Collections.singletonList(
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id1"), 2)));
                doNothing().when(stockService).updateStock(any(), anyInt());
                StockUpdateResponseDTO stockUpdateResponseDTO;
                List<StockUpdateItemStatusDTO> itemUpdateDetails = stockUpdateDTO.getItems().stream()
                                .map(stockUpdateItemDTO -> new StockUpdateItemStatusDTO(
                                                stockUpdateItemDTO.getItem().getId(), HttpStatus.OK,
                                                "Item updated to stock successfully"))
                                .toList();
                stockUpdateResponseDTO = new StockUpdateResponseDTO("Stock updated successfully", itemUpdateDetails);
                String inputJson = new ObjectMapper().writeValueAsString(stockUpdateDTO);
                String outputJson = new ObjectMapper().writeValueAsString(stockUpdateResponseDTO);
                mockMvc.perform(post("/stock")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inputJson))
                                .andExpect(result -> assertEquals(null, result.getResolvedException()))
                                .andExpect(status().isOk())
                                .andExpect(content().json(outputJson, true))
                                .andExpect(jsonPath("$.message").value("Stock updated successfully"))
                                .andExpect(jsonPath("$.itemUpdateDetails.length()").value(1))
                                .andExpect(jsonPath("$.itemUpdateDetails[0].httpStatus")
                                                .value("OK"))
                                .andExpect(jsonPath("$.itemUpdateDetails[0].message")
                                                .value("Item updated to stock successfully"));
                verify(stockService, times(1)).updateStock(any(), anyInt());
        }

        @Test
        public void shouldReturnMultiStatusWhenCalledWithPartialInvalidInput() throws Exception {
                StockUpdateDTO stockUpdateDTO = new StockUpdateDTO(
                                List.of(new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id1"), 2),
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id2"), 101)));
                doNothing().when(stockService).updateStock(any(), eq(2));
                doThrow(new InsufficientStorageException()).when(stockService)
                                .updateStock(any(), eq(101));
                String json = new ObjectMapper().writeValueAsString(stockUpdateDTO);
                mockMvc.perform(post("/stock")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                                .andExpect(status().isMultiStatus())
                                .andExpect(result -> assertNull(

                                                result.getResolvedException()))
                                .andExpect(jsonPath("$.message").value("Stock update failed for some items"))
                                .andExpect(jsonPath("$.itemUpdateDetails.length()").value(2))
                                .andExpect(jsonPath("$.itemUpdateDetails[0].httpStatus")
                                                .value("OK"))
                                .andExpect(jsonPath("$.itemUpdateDetails[0].message")
                                                .value("Item updated to stock successfully"))
                                .andExpect(jsonPath("$.itemUpdateDetails[1].httpStatus")
                                                .value("BAD_REQUEST"))
                                .andExpect(jsonPath("$.itemUpdateDetails[1].message")
                                                .value("There is not enough space in the warehouse to add the stock"));
                verify(stockService, times(2)).updateStock(any(), anyInt());
        }

        @Test
        public void shouldReturnErrorWhenCalledWithInvalidInput() throws Exception {
                StockUpdateDTO stockUpdateDTO = new StockUpdateDTO(
                                List.of(new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id1"), 101),
                                                new StockUpdateDTO.StockUpdateItemDTO(new ConsumerItem("id2"), 102)));
                doThrow(new InsufficientStorageException()).when(stockService)
                                .updateStock(any(), anyInt());
                String json = new ObjectMapper().writeValueAsString(stockUpdateDTO);
                mockMvc.perform(post("/stock")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                                .andExpect(status().isBadRequest())
                                .andExpect(result -> assertNull(

                                                result.getResolvedException()))
                                .andExpect(jsonPath("$.message").value("Stock update failed"))
                                .andExpect(jsonPath("$.itemUpdateDetails.length()").value(2))
                                .andExpect(jsonPath("$.itemUpdateDetails[0].httpStatus")
                                                .value("BAD_REQUEST"))
                                .andExpect(jsonPath("$.itemUpdateDetails[0].message")
                                                .value("There is not enough space in the warehouse to add the stock"))
                                .andExpect(jsonPath("$.itemUpdateDetails[1].httpStatus")
                                                .value("BAD_REQUEST"))
                                .andExpect(jsonPath("$.itemUpdateDetails[1].message")
                                                .value("There is not enough space in the warehouse to add the stock"));
                verify(stockService, times(2)).updateStock(any(), anyInt());
        }
}
