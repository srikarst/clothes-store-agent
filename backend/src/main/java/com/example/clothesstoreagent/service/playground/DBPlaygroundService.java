package com.example.clothesstoreagent.service.playground;

import com.example.clothesstoreagent.entity.Person;
import com.example.clothesstoreagent.repository.PersonRepository;

import java.util.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Qualifier("DB")
public class DBPlaygroundService implements PlaygroundService {

    PersonRepository personRepository;

    DBPlaygroundService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Person> getPeople() {
        return personRepository.findAllWithAddresses();
    }

    @Override
    @Transactional
    public Person addPerson(Person person) {
        if (person == null) return null;
        personRepository.save(person);
        return person;
    }
}
