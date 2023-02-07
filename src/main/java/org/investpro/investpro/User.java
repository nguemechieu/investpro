/**
 * Licensed Techexpert-solutions  by nguemechieu noel martial
 * email at nguemechieu@live.com
 */
//create user account
package org.investpro.investpro;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User implements Serializable {
    public static final long serialVersionUID = 1L;
    @Column(name = "birthdate")
    private String birthdate;
    @Id
    @Column(name = "id", unique = true, nullable = false)
    private int Id;
    @Column(name = "username", nullable = false, unique = true)
    private String username;
    @Column(name = "gender", nullable = false)
    private String gender;
    @Column(name = "password", nullable = false, unique = true)
    private String password;
    @Column(name = "email", unique = true, nullable = false)
    private String email;
    @Column(name = "phone", nullable = false, unique = true)
    private String phone;
    @Column(name = "firstname", nullable = false, unique = true)
    private String firstname;
    @Column(name = "lastname", nullable = false, unique = true)
    private String lastname;
    @Column(name = "middlename")
    private String middlename;
    @Column(name = "address", unique = true, nullable = false)
    private String address;
    @Column(name = "city")
    private String city;
    @Column(name = "state")
    private String state;
    @Column(name = "country")
    private String country;
    @Column(name = "zip")
    private String zip;
    @Column(name = "role")
    private String role;
    @Column(name = "created_at")
    private String createdAt;
    @Column(name = "updated_at")
    private String updatedAt;
    @Column(name = "active")
    private boolean active;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "password_reset_token")
    private String passwordResetToken;
    @Column(name = "password_reset_sent_at")
    private String passwordResetSentAt;

    public User() {
    }

    public User(String username, String password, String email, String firstname, String lastname, String middlename, String gender, String birthdate, String phone, String address, String city, String state, String country, String zipCode) {

        this.username = username;
        this.password = password;

        this.email = email;
        this.firstname = firstname;
        this.lastname = lastname;
        this.middlename = middlename;
        this.gender = gender;
        this.birthdate = birthdate;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
        this.zip = zipCode;
        this.createdAt = String.valueOf(Locale.getDefault());

        this.updatedAt = String.valueOf(Locale.getDefault());

    }

    public String getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(String birthdate) {
        this.birthdate = birthdate;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public String toString() {
        return "User{" +
                "Id=" + Id +
                ", username='" + username + '\'' +
                ", gender='" + gender + '\'' +
                ", password='" + password + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", firstname='" + firstname + '\'' +
                ", lastname='" + lastname + '\'' +
                ", middlename='" + middlename + '\'' +
                ", address='" + address + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", country='" + country + '\'' +
                ", zip='" + zip + '\'' +
                ", role='" + role + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", active=" + active +
                ", passwordHash='" + passwordHash + '\'' +
                ", passwordResetToken='" + passwordResetToken + '\'' +
                ", passwordResetSentAt='" + passwordResetSentAt + '\'' +
                '}';
    }

    public int getId() {
        return Id;
    }

    public void setId(int id) {
        Id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getMiddlename() {
        return middlename;
    }

    public void setMiddlename(String middlename) {
        this.middlename = middlename;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordResetToken() {
        return passwordResetToken;
    }

    public void setPasswordResetToken(String passwordResetToken) {
        this.passwordResetToken = passwordResetToken;
    }

    public String getPasswordResetSentAt() {
        return passwordResetSentAt;
    }

    public void setPasswordResetSentAt(String passwordResetSentAt) {
        this.passwordResetSentAt = passwordResetSentAt;
    }


}