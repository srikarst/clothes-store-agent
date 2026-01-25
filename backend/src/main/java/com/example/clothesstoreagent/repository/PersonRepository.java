package com.example.clothesstoreagent.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import com.example.clothesstoreagent.entity.Person;

@Component
public interface PersonRepository extends JpaRepository<Person, Long> {

	/**
	 * Avoids N+1 when serializing {@link Person#getAddresses()}.
	 */
	@EntityGraph(attributePaths = "addresses")
	@Query("select distinct p from Person p")
	java.util.List<Person> findAllWithAddresses();

	// Alternative (explicit fetch join) for future reference:
	// @Query("select distinct p from Person p left join fetch p.addresses")
	// java.util.List<Person> findAllWithAddresses();
}
