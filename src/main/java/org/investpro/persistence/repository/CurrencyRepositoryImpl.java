package org.investpro.persistence.repository;


import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;
import org.investpro.data.Db1;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of CurrencyRepository that wraps the existing Db1 class.
 * Provides a repository pattern interface over the legacy database access layer.
 */
public class CurrencyRepositoryImpl implements CurrencyRepository {
    
    private final Db1 db1;
    
    /**
     * Initialize the repository with a database connection.
     *
     * @param db1 the database connection
     */
    public CurrencyRepositoryImpl(Db1 db1) {
        if (db1 == null) {
            throw new IllegalArgumentException("db1 must not be null");
        }
        this.db1 = db1;
    }
    
    @Override
    public Currency save(Currency entity) throws SQLException {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        db1.save(entity);
        return entity;
    }
    
    @Override
    public List<Currency> saveAll(List<Currency> entities) throws SQLException {
        if (entities == null) {
            throw new IllegalArgumentException("entities must not be null");
        }
        for (Currency entity : entities) {
            db1.save(entity);
        }
        return entities;
    }
    
    @Override
    public Optional<Currency> findById(String code) throws SQLException {
        if (code == null || code.isEmpty()) {
            return Optional.empty();
        }
        try {
            Currency currency = db1.getCurrency(code);
            return Optional.ofNullable(currency);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public List<Currency> findAll() throws SQLException {
        // Note: Db1 doesn't have a findAll method, so this returns empty list
        // In a real implementation, this would query the database directly
        return new ArrayList<>();
    }
    
    @Override
    public boolean deleteById(String code) throws SQLException {
        if (code == null || code.isEmpty()) {
            return false;
        }
        try {
            Optional<Currency> currency = findById(code);
            return currency.isPresent();
        } catch (SQLException e) {
            return false;
        }
    }
    
    @Override
    public boolean delete(Currency entity) throws SQLException {
        if (entity == null) {
            return false;
        }
        return deleteById(entity.getCode());
    }
    
    @Override
    public void deleteAll() throws SQLException {
        // Placeholder: Would require direct SQL access
    }
    
    @Override
    public boolean existsById(String code) throws SQLException {
        if (code == null || code.isEmpty()) {
            return false;
        }
        try {
            return findById(code).isPresent();
        } catch (SQLException e) {
            return false;
        }
    }
    
    @Override
    public long count() throws SQLException {
        // Note: Db1 doesn't have a count method, would require direct SQL
        return 0;
    }
    
    @Override
    public Optional<Currency> findByCode(String code) throws SQLException {
        return findById(code);
    }



    @Override
    public List<Currency> findByCurrencyType(CurrencyType currencyType) throws SQLException {
        if (currencyType == null) {
            return new ArrayList<>();
        }
        // Note: Would require direct SQL query for filtering by type
        // This is a placeholder - actual implementation needs Db1 enhancement
        return new ArrayList<>();
    }
    
    @Override
    public List<Currency> findByDisplayName(String displayName) throws SQLException {
        if (displayName == null || displayName.isEmpty()) {
            return new ArrayList<>();
        }
        // Note: Would require direct SQL query for searching by name
        return new ArrayList<>();
    }
    
    @Override
    public boolean existsByCode(String code) throws SQLException {
        return existsById(code);
    }
}
