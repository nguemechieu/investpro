package org.investpro.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Generic service interface providing common CRUD operations.
 * Services act as a facade between repositories and business logic.
 *
 * @parameter <T> the entity type
 * @parameter <ID> the primary key type
 */
public interface CrudService<T, ID> {
    
    /**
     * Create or update an entity.
     *
     * @param entity the entity to save
     * @return the saved entity
     * @throws SQLException if database operation fails
     */
    T save(T entity) throws SQLException, ClassNotFoundException;
    
    /**
     * Create or update multiple entities.
     *
     * @param entities the entities to save
     * @return list of saved entities
     * @throws SQLException if database operation fails
     */
    List<T> saveAll(List<T> entities) throws SQLException, ClassNotFoundException;
    
    /**
     * Find an entity by ID.
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
     * Delete an entity by ID.
     *
     * @param id the entity ID
     * @return true if entity was deleted, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean delete(ID id) throws SQLException;
    
    /**
     * Check if entity exists.
     *
     * @param id the entity ID
     * @return true if exists, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean exists(ID id) throws SQLException;
    
    /**
     * Count all entities.
     *
     * @return the count of entities
     * @throws SQLException if database operation fails
     */
    long count() throws SQLException;
}
