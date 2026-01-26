package com.example.clothesstoreagent.web;

import com.example.clothesstoreagent.api.PlaygroundController;
import com.example.clothesstoreagent.entity.Address;
import com.example.clothesstoreagent.entity.Person;
import com.example.clothesstoreagent.service.playground.PlaygroundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaygroundControllerWebTest {

    private MockMvc mockMvc;

    @Mock
    PlaygroundService playgroundService;

    @InjectMocks
    PlaygroundController playgroundController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(playgroundController)
                .build();
    }

    @Test
    void shouldReturnPeopleOnGet() throws Exception {
        Person devon = new Person("Devon", 29);
        devon.addAddress(new Address("Seattle"));
        devon.addAddress(new Address("Portland"));

        Person mina = new Person("Mina", 34);
        mina.addAddress(new Address("San Francisco"));

        when(playgroundService.getPeople()).thenReturn(List.of(devon, mina));

        mockMvc.perform(get("/api/people"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Devon"))
                .andExpect(jsonPath("$[0].addresses.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Mina"))
                .andExpect(jsonPath("$[1].addresses.length()").value(1));

        verify(playgroundService, times(1)).getPeople();
    }

    @Test
    void shouldCallAddPersonForEachPayloadItemOnPost() throws Exception {
        List<Object> payload = List.of(
                objectMapper.readTree("{\"name\":\"Sachin\",\"age\":4,\"addresses\":[{\"city\":\"Mumbai\"},{\"city\":\"Vizag\"}]}"),
                objectMapper.readTree("{\"name\":\"Zara\",\"age\":25,\"addresses\":[]}")
        );

        doReturn(null).when(playgroundService).addPerson(any(Person.class));

        mockMvc.perform(post("/api/people")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(playgroundService, times(2)).addPerson(any(Person.class));
    }
}
