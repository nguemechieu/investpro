package org.investpro.data;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.investpro.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
@Data
@Getter
@Setter
public class Account {
    private static final Logger logger = LoggerFactory.getLogger(Account.class);
    private final String username;
    private final Exchange exchange;
    private String account;
    private String password;
    private String email;
    private String firstName;
    private String lastName;

    @Override
    public String toString() {
        return "Account{" +
                "username='" + username + '\'' +
                ", exchange=" + (exchange != null ? exchange.toString() : "null") +
                ", account='" + account + '\'' +
                ", password='" + (password != null ? "***" : "null") + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", phone='" + phone + '\'' +
                ", address='" + address + '\'' +
                ", city='" + city + '\'' +
                ", telegramToken='" + (telegramToken != null ? "***" : "null") + '\'' +
                ", emailNotification='" + emailNotification + '\'' +
                '}';
    }

    private String phone;
    private String address;
    private String city;
    private String telegramToken;
    private String emailNotification;

    public Account(Exchange exchange, String username, String password) {
        this.username = username;
        this.password = password;
        this.account = username;
        this.exchange = exchange;
        logger.info("Account created for username: {}", username);
        
        // Populate exchange credentials
        this.telegramToken = exchange.getTelegramToken();
        this.emailNotification = exchange.getEmailNotification();
        
        try {
            Account userDetails = exchange.getUserAccountDetails();
            if (userDetails != null) {
                this.email = userDetails.getEmail();
                this.firstName = userDetails.getFirstName();
                this.lastName = userDetails.getLastName();
                this.phone = userDetails.getPhone();
                this.address = userDetails.getAddress();
                this.city = userDetails.getCity();
                logger.info("Account details populated from exchange for username: {}", username);
            } else {
                logger.warn("No account details available from exchange for username: {}", username);
            }
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to fetch account details for username: {}", username, e);
        }
    }
}

