package org.investpro.service;

import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;
import org.investpro.persistence.repository.CurrencyRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service for Currency domain operations.
 * Provides business logic and validation for currency-related operations.
 */
public class CurrencyService implements CrudService<Currency, String> {
    
    private final CurrencyRepository repository;
    
    /**
     * Initialize the service with a currency repository.
     *
     * @param repository the currency repository
     */
    public CurrencyService(CurrencyRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }
    
    @Override
    public Currency save(Currency currency) throws SQLException, ClassNotFoundException {
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        return repository.save(currency);
    }
    
    @Override
    public List<Currency> saveAll(List<Currency> currencies) throws SQLException {
        if (currencies == null || currencies.isEmpty()) {
            throw new IllegalArgumentException("currencies list must not be null or empty");
        }
        return repository.saveAll(currencies);
    }
    
    @Override
    public Optional<Currency> findById(String code) throws SQLException {
        if (code == null || code.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByCode(code);
    }
    
    @Override
    public List<Currency> findAll() throws SQLException {
        return repository.findAll();
    }
    
    @Override
    public boolean delete(String code) throws SQLException {
        if (code == null || code.isEmpty()) {
            return false;
        }
        return repository.deleteById(code);
    }
    
    @Override
    public boolean exists(String code) throws SQLException {
        if (code == null || code.isEmpty()) {
            return false;
        }
        return repository.existsByCode(code);
    }
    
    @Override
    public long count() throws SQLException {
        return repository.count();
    }
    
    /**
     * Find all currencies of a specific type.
     *
     * @param currencyType the currency type (FIAT or CRYPTO)
     * @return list of currencies of that type
     * @throws SQLException if database operation fails
     */
    public List<Currency> findByCurrencyType(CurrencyType currencyType) throws SQLException {
        if (currencyType == null) {
            throw new IllegalArgumentException("currencyType must not be null");
        }
        return repository.findByCurrencyType(currencyType);
    }
    
    /**
     * Find currencies by display name.
     *
     * @param displayName the display name to search for
     * @return list of matching currencies
     * @throws SQLException if database operation fails
     */
    public List<Currency> findByDisplayName(String displayName) throws SQLException {
        if (displayName == null || displayName.isEmpty()) {
            return List.of();
        }
        return repository.findByDisplayName(displayName);
    }
    
    /**
     * Get all fiat currencies.
     *
     * @return list of fiat currencies
     * @throws SQLException if database operation fails
     */
    public List<Currency> getAllFiatCurrencies() throws SQLException {
        return repository.findByCurrencyType(CurrencyType.FIAT);
    }
    
    /**
     * Get all cryptocurrencies.
     *
     * @return list of cryptocurrencies
     * @throws SQLException if database operation fails
     */
    public List<Currency> getAllCryptocurrencies() throws SQLException {
        return repository.findByCurrencyType(CurrencyType.CRYPTO);
    }
}
