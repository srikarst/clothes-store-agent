package com.example.clothesstoreagent.repository;

import com.example.clothesstoreagent.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByPersonId(Long personId);

    Optional<Address> findFirstByPersonIdOrderByIdAsc(Long personId);
}
