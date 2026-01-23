package com.example.clothesstoreagent.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.clothesstoreagent.service.playground.ListPlaygroundService;
import com.example.clothesstoreagent.service.playground.PlaygroundService;
import com.example.clothesstoreagent.service.playground.PlaygroundService.Person;

@RestController
public class PlaygroundController {

    PlaygroundService playgroundService = new ListPlaygroundService();

    @GetMapping("/api/people")
    private List<Person> get() {
        return playgroundService.getPeople();
    }

    @PostMapping("/api/people")
    private void add(@RequestBody List<Person> people) {
        people.parallelStream().forEach(person -> {
            playgroundService.addPerson(person);
        });
    }
}
