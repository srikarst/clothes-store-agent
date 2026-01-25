package com.example.clothesstoreagent.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.clothesstoreagent.entity.Person;
import com.example.clothesstoreagent.service.playground.ReactivePlaygroundService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class PlaygroundReactiveController {

    private final ReactivePlaygroundService reactivePlaygroundService;

    public PlaygroundReactiveController(ReactivePlaygroundService reactivePlaygroundService) {
        this.reactivePlaygroundService = reactivePlaygroundService;
    }

    /**
     * Spring MVC can return Mono and run it asynchronously.
     */
    @GetMapping("/api/people/mono")
    public Mono<List<Person>> getPeopleMono() {
        return reactivePlaygroundService.getPeopleMono();
    }

    /**
     * Convenience endpoint when you want Flux operators.
     * Returned JSON will be an array (not streaming) under Spring MVC.
     */
    @GetMapping("/api/people/flux")
    public Flux<Person> getPeopleFlux() {
        return reactivePlaygroundService.getPeopleFlux();
    }

    /**
     * Example of streaming with Server-Sent Events (SSE).
     */
    @GetMapping(value = "/api/people/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Person> getPeopleSse() {
        return reactivePlaygroundService.getPeopleFlux();
    }

    @PostMapping("/api/people/mono")
    public Mono<Person> addPersonMono(@RequestBody Person person) {
        return reactivePlaygroundService.addPersonMono(person);
    }
}
