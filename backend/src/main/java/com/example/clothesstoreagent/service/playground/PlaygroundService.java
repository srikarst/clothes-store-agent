package com.example.clothesstoreagent.service.playground;

import java.util.List;

public interface PlaygroundService {

    static class Person {
        private String name;
        private Integer age;

        public String getName() {
            return name;
        }
        
        public Integer getAge() {
            return age;
        }

        public Person(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
    }

    List<Person> getPeople();

    Person addPerson(Person person);
}
