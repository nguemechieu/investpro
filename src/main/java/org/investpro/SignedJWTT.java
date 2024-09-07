package org.investpro;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.Objects;

public class SignedJWTT extends SignedJWT {
    JWSHeader jwsHeader;
    JWTClaimsSet claimsSet;

    public SignedJWTT(JWSHeader jwsHeader, JWTClaimsSet claimsSet) {
        super(
                jwsHeader,
                claimsSet
        );
    }

    @Override
    public String toString() {
        return STR."SignedJWTT{jwsHeader=\{jwsHeader}, claimsSet=\{claimsSet}}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignedJWTT that = (SignedJWTT) o;
        return Objects.equals(jwsHeader, that.jwsHeader) && Objects.equals(claimsSet, that.claimsSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jwsHeader, claimsSet);
    }

    public JWSHeader getJwsHeader() {
        return jwsHeader;
    }

    public void setJwsHeader(JWSHeader jwsHeader) {
        this.jwsHeader = jwsHeader;
    }

    public JWTClaimsSet getClaimsSet() {
        return claimsSet;
    }

    public void setClaimsSet(JWTClaimsSet claimsSet) {
        this.claimsSet = claimsSet;
    }

}