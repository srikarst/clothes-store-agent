package com.example.clothesstoreagent.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "person", schema = "dbo")
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer age;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Address> addresses = new ArrayList<>();

    protected Person() {
        // for JPA
    }

    public Person(String name, Integer age) {
        this.name = name;
        this.age = age;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public List<Address> getAddresses() {
        return Collections.unmodifiableList(addresses);
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses.clear();
        if (addresses == null) {
            return;
        }
        for (Address address : addresses) {
            addAddress(address);
        }
    }

    public void addAddress(Address address) {
        if (address == null) {
            return;
        }
        if (!this.addresses.contains(address)) {
            this.addresses.add(address);
        }
        if (address.getPerson() != this) {
            address.setPerson(this);
        }
    }

    public void removeAddress(Address address) {
        if (address == null) {
            return;
        }
        this.addresses.remove(address);
        if (address.getPerson() == this) {
            address.setPerson(null);
        }
    }
}
