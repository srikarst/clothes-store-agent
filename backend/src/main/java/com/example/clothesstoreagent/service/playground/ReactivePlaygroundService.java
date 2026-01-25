package com.example.clothesstoreagent.service.playground;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.clothesstoreagent.entity.Person;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ReactivePlaygroundService {

    private final PlaygroundService playgroundService;

    public ReactivePlaygroundService(@Qualifier("DB") PlaygroundService playgroundService) {
        this.playgroundService = playgroundService;
    }

    public Mono<List<Person>> getPeopleMono() {
        return Mono.fromCallable(playgroundService::getPeople)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Convenience API when you want Flux composition.
     * Note: because the underlying service is blocking, this materializes the list first.
     */
    public Flux<Person> getPeopleFlux() {
        return getPeopleMono().flatMapMany(Flux::fromIterable);
    }

    public Mono<Person> addPersonMono(Person person) {
        return Mono.fromCallable(() -> playgroundService.addPerson(person))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
