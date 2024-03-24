package org.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Account {
    private static final Logger logger = LoggerFactory.getLogger(Account.class);

    private final Exchange exchange;
    private String account;
    private String password;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String city;

    public Account(Exchange exchange, String username, String password) {

        this.password = password;
        this.account = username;
        logger.info("account.created", username);
        this.exchange = exchange;


    }

    public Exchange getExchange() {
        return exchange;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
