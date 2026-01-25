package com.example.clothesstoreagent.service;

import com.example.clothesstoreagent.entity.Address;
import com.example.clothesstoreagent.entity.Person;
import com.example.clothesstoreagent.repository.AddressRepository;
import com.example.clothesstoreagent.repository.PersonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final PersonRepository personRepository;

    public AddressService(AddressRepository addressRepository, PersonRepository personRepository) {
        this.addressRepository = addressRepository;
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public List<Address> listAll() {
        return addressRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Address getById(Long id) {
        return addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    @Transactional(readOnly = true)
    public List<Address> listByPersonId(Long personId) {
        if (!personRepository.existsById(personId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found");
        }
        return addressRepository.findAllByPersonId(personId);
    }

    @Transactional
    public Address createForPerson(Long personId, Address incoming) {
        if (incoming == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address body is required");
        }

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));

        Address address = new Address();
        address.setCity(incoming.getCity());
        address.setPerson(person);
        return addressRepository.save(address);
    }

    @Transactional(readOnly = true)
    public Address getPrimaryByPersonId(Long personId) {
        if (!personRepository.existsById(personId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found");
        }
        return addressRepository.findFirstByPersonIdOrderByIdAsc(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found for person"));
    }

    @Transactional
    public Address upsertPrimaryForPerson(Long personId, Address incoming) {
        if (incoming == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address body is required");
        }

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));

        Address address = addressRepository.findFirstByPersonIdOrderByIdAsc(personId)
                .orElseGet(Address::new);

        address.setCity(incoming.getCity());
        address.setPerson(person);
        return addressRepository.save(address);
    }

    public void save(Address address) {
        if (address != null) addressRepository.save(address);
    }

    @Transactional
    public void delete(Long id) {
        if (!addressRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }
        addressRepository.deleteById(id);
    }
}
