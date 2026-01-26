package com.example.clothesstoreagent.web;

import com.example.clothesstoreagent.api.PlaygroundReactiveController;
import com.example.clothesstoreagent.entity.Person;
import com.example.clothesstoreagent.service.playground.ReactivePlaygroundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller web tests for reactive return types under Spring MVC.
 *
 * Even though the controller returns {@code Mono}/{@code Flux}, under Spring MVC these are handled
 * asynchronously, so the MockMvc assertions use {@code request().asyncStarted()} + {@code asyncDispatch(...)}.
 */
class PlaygroundReactiveControllerWebTest {

    private MockMvc mockMvc;

    @Mock
    ReactivePlaygroundService reactivePlaygroundService;

    @InjectMocks
    PlaygroundReactiveController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .build();
    }

    @Test
    void shouldReturnPeopleFromMonoEndpoint() throws Exception {
        List<Person> people = List.of(new Person("Devon", 29), new Person("Mina", 34));
        when(reactivePlaygroundService.getPeopleMono()).thenReturn(Mono.just(people));

        MvcResult result = mockMvc.perform(get("/api/people/mono"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Devon"))
                .andExpect(jsonPath("$[1].name").value("Mina"));

        verify(reactivePlaygroundService, times(1)).getPeopleMono();
    }

    @Test
    void shouldReturnPeopleFromFluxEndpoint() throws Exception {
        when(reactivePlaygroundService.getPeopleFlux())
                .thenReturn(Flux.just(new Person("Devon", 29), new Person("Mina", 34)));

        MvcResult result = mockMvc.perform(get("/api/people/flux"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Devon"));

        verify(reactivePlaygroundService, times(1)).getPeopleFlux();
    }

    @Test
    void shouldReturnPersonFromPostMonoEndpoint() throws Exception {
        Person created = new Person("Sachin", 4);
        when(reactivePlaygroundService.addPersonMono(any(Person.class))).thenReturn(Mono.just(created));

        String json = objectMapper.writeValueAsString(new Person("Sachin", 4));

        MvcResult result = mockMvc.perform(post("/api/people/mono")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sachin"))
                .andExpect(jsonPath("$.age").value(4));

        verify(reactivePlaygroundService, times(1)).addPersonMono(any(Person.class));
    }
}
