package org.investpro;

import lombok.Getter;

import java.util.Objects;

@Getter
public class Person {
    private final String name;
    int age;
    String address;
    String phoneNumber;
    String email;

    public Person(String name) {
        this.name = name;
    }

    public Person(String name, int age, String address, String phoneNumber, String email) {
        this.name = name;
        this.age = age;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return name.equals(person.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }


}
