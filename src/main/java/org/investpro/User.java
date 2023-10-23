/**
 * Licensed Techexpert-solutions  by nguemechieu noel martial
 * email at nguemechieu@live.com
 */
//create user account
package org.investpro;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;


import java.io.Serializable;
import java.util.Locale;

@Entity

public class User implements Serializable {


//    profile_bio string, optional	Bio for user's public profile
//    profile_url string, optional	Public profile location if user has one
//    avatar_url string	User's avatar url
//    resource string, constant user
//    resource_path string


    public static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue
    private Long id;
    private String username;
    private String password;
    private String profile_bio;
    private String profile_url;
    private String avatar_url;
    private String resource;
    private String resource_path;
    private String createdAt;
    private String updatedAt;
    private boolean active;
    private String passwordHash;
    private String first_name;
    private String last_name;
    private String middle_name;
    private String gender;
    private String age;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String zip;
    private String country;

    public User(int id, String name, String profile_bio, String profile_url, String avatar_url, String resource, String resource_path) {
        this.profile_bio = profile_bio;
        this.profile_url = profile_url;
        this.avatar_url = avatar_url;
        this.resource = resource;
        this.resource_path = resource_path;

        this.createdAt = String.valueOf(Locale.getDefault());
        this.updatedAt = String.valueOf(Locale.getDefault());
        this.active = true;
        this.passwordHash = "";

        this.username = name;
        this.id = (long) id;
    }

    public User() {

    }

    public User(String username, String password, String email, String firstname, String lastname, String middlename, String gender, String birthdate, String phone, String address, String city, String state, String country, String zipCode) {


        this.username = username;
        this.password = password;

        this.email = email;

        this.phone = phone;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
        this.zip = zipCode;
        this.createdAt = String.valueOf(Locale.getDefault());

        this.updatedAt = String.valueOf(Locale.getDefault());

        this.active = true;
        this.passwordHash = "";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirst_name() {
        return first_name;
    }

    public void setFirst_name(String first_name) {
        this.first_name = first_name;
    }

    public String getLast_name() {
        return last_name;
    }

    public void setLast_name(String last_name) {
        this.last_name = last_name;
    }

    public String getMiddle_name() {
        return middle_name;
    }

    public void setMiddle_name(String middle_name) {
        this.middle_name = middle_name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
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

    public String getProfile_bio() {
        return profile_bio;
    }

    public void setProfile_bio(String profile_bio) {
        this.profile_bio = profile_bio;
    }

    public String getProfile_url() {
        return profile_url;
    }

    public void setProfile_url(String profile_url) {
        this.profile_url = profile_url;
    }

    public String getAvatar_url() {
        return avatar_url;
    }

    public void setAvatar_url(String avatar_url) {
        this.avatar_url = avatar_url;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getResource_path() {
        return resource_path;
    }

    public void setResource_path(String resource_path) {
        this.resource_path = resource_path;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
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


}