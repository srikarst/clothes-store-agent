package com.example.clothesstoreagent.api;

import com.example.clothesstoreagent.entity.Address;
import com.example.clothesstoreagent.service.AddressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/api/addresses")
    public List<Address> list() {
        return addressService.listAll();
    }

    @GetMapping("/api/addresses/{id}")
    public Address getById(@PathVariable Long id) {
        return addressService.getById(id);
    }

    @GetMapping("/api/people/{personId}/address")
    public Address getByPerson(@PathVariable Long personId) {
        return addressService.getPrimaryByPersonId(personId);
    }

    @PutMapping("/api/people/{personId}/address")
    public Address upsertForPerson(@PathVariable Long personId, @RequestBody Address address) {
        return addressService.upsertPrimaryForPerson(personId, address);
    }

    @GetMapping("/api/people/{personId}/addresses")
    public List<Address> listForPerson(@PathVariable Long personId) {
        return addressService.listByPersonId(personId);
    }

    @PostMapping("/api/people/{personId}/addresses")
    public Address createForPerson(@PathVariable Long personId, @RequestBody Address address) {
        return addressService.createForPerson(personId, address);
    }

    @PostMapping("/api/address")
    public void save(@RequestBody Address address) {
        addressService.save(address);
    }

    @DeleteMapping("/api/addresses/{id}")
    public void delete(@PathVariable Long id) {
        addressService.delete(id);
    }
}
