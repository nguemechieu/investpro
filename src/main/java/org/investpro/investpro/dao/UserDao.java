package org.investpro.investpro.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.investpro.investpro.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);
    private final EntityManager em;

    public UserDao(EntityManager em) {
        this.em = em;
    }

    public User findById(Long id) {
        return em.find(User.class, id);
    }

    public List<User> findAll() {
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        return query.getResultList();
    }

    public void save(User user) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(user);
            tx.commit();
            logger.info("✅ User saved: {}", user.getUsername());
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            logger.error("❌ Error saving user: {}", e.getMessage(), e);
        }
    }

    public void update(User user) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.merge(user);
            tx.commit();
            logger.info("✅ User updated: {}", user.getUsername());
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            logger.error("❌ Error updating user: {}", e.getMessage(), e);
        }
    }

    public void delete(Long id) {
        EntityTransaction tx = em.getTransaction();
        try {
            User user = em.find(User.class, id);
            if (user != null) {
                tx.begin();
                em.remove(user);
                tx.commit();
                logger.info("✅ User deleted with id: {}", id);
            } else {
                logger.warn("⚠️ User not found with id: {}", id);
            }
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            logger.error("❌ Error deleting user: {}", e.getMessage(), e);
        }
    }
}
