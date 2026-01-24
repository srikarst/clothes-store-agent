package com.example.clothesstoreagent.api;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        for (Person person : people) {
            try {
                ExecutorService executor = Executors.newFixedThreadPool(10);
                Future<Integer> future = executor.submit(() -> {
                    playgroundService.addPerson(person);
                    if (true) throw new RuntimeException("some exception");
                    return 2;
                });
                System.out.println(future.get());
            } catch(InterruptedException exception) {
                System.out.println(exception.getMessage());
            } catch(ExecutionException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }
}
