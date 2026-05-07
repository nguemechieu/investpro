package org.investpro.models;

import lombok.extern.slf4j.Slf4j;


import lombok.Data;
@Data
@Slf4j
class User {
    
    private Long id;
    private String username;
    private String password;
    private String email;
}