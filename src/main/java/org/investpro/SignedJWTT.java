package org.investpro;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Setter
@Getter
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
        return "SignedJWTT{jwsHeader=" + jwsHeader + "--" + " claimsSet=" + claimsSet;
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

}