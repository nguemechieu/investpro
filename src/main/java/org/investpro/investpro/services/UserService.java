package org.investpro.investpro.services;


import org.investpro.investpro.dao.UserDao;
import org.investpro.investpro.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public User findUser(Long id) {
        return userDao.findById(id);
    }

    public List<User> getAllUsers() {
        return userDao.findAll();
    }

    public void registerUser(User user) {
        try {
            userDao.save(user);
            logger.info("✅ User registered: {}", user.getUsername());
        } catch (Exception e) {
            logger.error("❌ Failed to register user: {}", e.getMessage(), e);
        }
    }

    public void updateUser(User user) {
        try {
            userDao.update(user);
            logger.info("✅ User updated: {}", user.getUsername());
        } catch (Exception e) {
            logger.error("❌ Failed to update user: {}", e.getMessage(), e);
        }
    }

    public void deleteUser(Long id) {
        try {
            userDao.delete(id);
            logger.info("✅ User deleted with ID: {}", id);
        } catch (Exception e) {
            logger.error("❌ Failed to delete user: {}", e.getMessage(), e);
        }
    }
}
