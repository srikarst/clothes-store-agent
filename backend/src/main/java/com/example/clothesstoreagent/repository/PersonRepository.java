package com.example.clothesstoreagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.example.clothesstoreagent.entity.Person;

@Component
public interface PersonRepository extends JpaRepository<Person, Long> {}
