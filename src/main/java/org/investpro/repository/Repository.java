package org.investpro.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Generic repository interface for CRUD operations.
 * Provides a contract for data access across all entity types.
 *
 * @param <T> the entity type
 * @param <ID> the primary key type
 */
public interface Repository<T, ID> {
    
    /**
     * Save or update an entity.
     *
     * @param entity the entity to save
     * @return the saved entity
     * @throws SQLException if database operation fails
     */
    T save(T entity) throws SQLException;
    
    /**
     * Save multiple entities.
     *
     * @param entities the entities to save
     * @return list of saved entities
     * @throws SQLException if database operation fails
     */
    List<T> saveAll(List<T> entities) throws SQLException;
    
    /**
     * Find an entity by its ID.
     *
     * @param id the entity ID
     * @return optional containing the entity if found
     * @throws SQLException if database operation fails
     */
    Optional<T> findById(ID id) throws SQLException;
    
    /**
     * Find all entities.
     *
     * @return list of all entities
     * @throws SQLException if database operation fails
     */
    List<T> findAll() throws SQLException;
    
    /**
     * Delete an entity by its ID.
     *
     * @param id the entity ID
     * @return true if entity was deleted, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean deleteById(ID id) throws SQLException;
    
    /**
     * Delete an entity.
     *
     * @param entity the entity to delete
     * @return true if entity was deleted, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean delete(T entity) throws SQLException;
    
    /**
     * Delete all entities.
     *
     * @throws SQLException if database operation fails
     */
   public void deleteAll() throws SQLException;
    
    /**
     * Check if an entity exists by ID.
     *
     * @param id the entity ID
     * @return true if entity exists, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean existsById(ID id) throws SQLException;
    
    /**
     * Count total number of entities.
     *
     * @return the count
     * @throws SQLException if database operation fails
     */
    long count() throws SQLException;
}
