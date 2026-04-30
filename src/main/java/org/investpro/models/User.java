package org.investpro.models;


import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Data
class User {
    
    private Long id;
    private String username;
    private String password;
    private String email;
    
    private static final Logger logger = LoggerFactory.getLogger(User.class);  

}