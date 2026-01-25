package com.example.clothesstoreagent.service.playground;

import com.example.clothesstoreagent.entity.Person;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;


@Service
@Qualifier("list")
public class ListPlaygroundService implements PlaygroundService{

        List<Person> people = new ArrayList<>(Arrays.asList(new Person("Srikar", 1), new Person("Charan", 2)));

        private ListPlaygroundService() {}

        public List<Person> getPeople() {
            return people;
        }

        public Person addPerson(Person person) {
            people.add(person);
            return person;
        }
    }