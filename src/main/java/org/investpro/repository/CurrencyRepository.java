package org.investpro.repository;


import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Currency entities with domain-specific operations.
 */
public interface CurrencyRepository extends Repository<Currency, String> {
    
    /**
     * Find a currency by its code.
     *
     * @param code the currency code
     * @return optional containing the currency if found
     * @throws SQLException if database operation fails
     */
    Optional<Currency> findByCode(String code) throws SQLException;
    
    /**
     * Find all currencies of a specific type.
     *
     * @param currencyType the currency type (FIAT or CRYPTO)
     * @return list of currencies matching the type
     * @throws SQLException if database operation fails
     */
    List<Currency> findByCurrencyType(CurrencyType currencyType) throws SQLException;


    /**
     * Find currencies by display name (partial match).
     *
     * @param displayName the display name to search for
     * @return list of matching currencies
     * @throws SQLException if database operation fails
     */
    List<Currency> findByDisplayName(String displayName) throws SQLException;
    
    /**
     * Check if a currency code exists.
     *
     * @param code the currency code
     * @return true if currency exists, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean existsByCode(String code) throws SQLException;
}
