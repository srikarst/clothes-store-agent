package com.example.clothesstoreagent.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.clothesstoreagent.entity.Person;
import com.example.clothesstoreagent.service.playground.PlaygroundService;

@RestController
public class PlaygroundController {

    @Autowired
    @Qualifier("DB")
    PlaygroundService playgroundService;

    @GetMapping("/api/people")
    private List<Person> get() {
        return playgroundService.getPeople();
    }

    @PostMapping("/api/people")
    private void add(@RequestBody List<Person> people) {
        if (people == null) {
            return;
        }
        for (Person person : people) {
            try {
                playgroundService.addPerson(person);
            } catch (RuntimeException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }
}
