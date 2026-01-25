package com.example.clothesstoreagent.service.playground;

import com.example.clothesstoreagent.entity.Person;

import java.util.List;

public interface PlaygroundService {

    List<Person> getPeople();

    Person addPerson(Person person);
}
