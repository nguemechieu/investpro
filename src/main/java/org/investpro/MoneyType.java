package org.investpro;


import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class MoneyType implements UserType<Money>, MoneyTypes {

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.DECIMAL};
    }

    @Override
    public String getCurrencyCode() {
        return "";
    }

    @Override
    public int getSqlType() {
        return 0;
    }

    @Override
    public Class<Money> returnedClass() {
        return Money.class;
    }

    @Override
    public boolean equals(@NotNull Money x, Money y) {
        return x.equals(y);
    }

    @Override
    public int hashCode(@NotNull Money x) {
        return x.hashCode();
    }

    @Override
    public Money nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) {
        return null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Money value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value != null) {
            st.setBigDecimal(index, value.toBigDecimal());
        } else {
            st.setNull(index, Types.DECIMAL);
        }
    }

    @Override
    public Money deepCopy(Money value) {
        try {
            return value != null ? Money.of(value.toBigDecimal()) : null;
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Money value) {
        return value != null ? value.toBigDecimal() : null;

    }

    @Override
    public Money assemble(Serializable cached, Object owner) {
        return this.deepCopy((Money) cached);
    }

    @Override
    public Money replace(Money original, Money target, Object owner) {
        return original;
    }

    @Override
    public long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
        return UserType.super.getDefaultSqlLength(dialect, jdbcType);
    }

    @Override
    public int getDefaultSqlPrecision(Dialect dialect, JdbcType jdbcType) {
        return UserType.super.getDefaultSqlPrecision(dialect, jdbcType);
    }

    @Override
    public int getDefaultSqlScale(Dialect dialect, JdbcType jdbcType) {
        return UserType.super.getDefaultSqlScale(dialect, jdbcType);
    }

    @Override
    public JdbcType getJdbcType(TypeConfiguration typeConfiguration) {
        return UserType.super.getJdbcType(typeConfiguration);
    }

    @Override
    public BasicValueConverter<Money, Object> getValueConverter() {
        return UserType.super.getValueConverter();
    }
}
