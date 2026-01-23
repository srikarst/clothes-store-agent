package com.example.clothesstoreagent.service.playground;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListPlaygroundService implements PlaygroundService{

        List<Person> people = new ArrayList<>(Arrays.asList(new Person("Srikar", 1), new Person("Charan", 2)));

        public List<Person> getPeople() {
            return people;
        }

        public Person addPerson(Person person) {
            people.add(person);
            return person;
        }
    }