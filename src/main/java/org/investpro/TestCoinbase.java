package org.investpro;


import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jose.*;


import java.security.interfaces.ECPrivateKey;
import java.util.Map;
import java.time.Instant;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.Security;
import java.util.concurrent.ConcurrentHashMap;

import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.testcontainers.shaded.org.bouncycastle.openssl.PEMKeyPair;
import org.testcontainers.shaded.org.bouncycastle.openssl.PEMParser;
import org.testcontainers.shaded.org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class TestCoinbase extends Application {
    private static final Logger logger = LoggerFactory.getLogger(TestCoinbase.class);

    public static void main(String[] args) throws Exception {
        // Register BouncyCastle as a security providers
        Security.addProvider(new BouncyCastleProvider());

        // Load environment variables
        Dotenv dotenv = Dotenv.load();
        String privateKeyPEM = dotenv.get("COINBASE_PRIVATE_API_KEY").replace("\\n", "\n");
        String name = dotenv.get("COINBASE_API_KEY_NAME");

        // create a header object
        Map<String, Object> header = new ConcurrentHashMap<>();
        header.put("alg", "ES256");
        header.put("typ", "JWT");
        header.put("kid", name);
        header.put("nonce", String.valueOf(Instant.now().getEpochSecond()));

        // create uri string for current request
        String requestMethod = "GET";
        String url = "api.coinbase.com/api/v3/brokerage/accounts";
        String uri = STR."\{requestMethod} \{url}";

        // create data object
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("iss", "coinbase-cloud");
        data.put("nbf", Instant.now().getEpochSecond());
        data.put("exp", Instant.now().getEpochSecond() + 120);
        data.put("sub", name);
        data.put("uri", uri);

        // Load private key
        PEMParser pemParser = new PEMParser(new StringReader(privateKeyPEM));
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        Object object = pemParser.readObject();
        PrivateKey privateKey;

        if (object instanceof PrivateKey) {
            privateKey = (PrivateKey) object;
        } else if (object instanceof PEMKeyPair) {
            privateKey = converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
        } else {
            throw new Exception("Unexpected private key format");
        }
        pemParser.close();

        // Convert to ECPrivateKey
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        ECPrivateKey ecPrivateKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
        logger.info("Private key loaded successfully");
        logger.info("JWT claims set created successfully");
        logger.info("JWT created successfully");

        // create JWT
        JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            claimsSetBuilder.claim(entry.getKey(), entry.getValue());
        }
        JWTClaimsSet claimsSet = claimsSetBuilder.build();

        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.ES256).customParams(header).build();

        logger.info(jwsHeader.toString());
        SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);

        // Sign the JWT using the private key
        JWSSigner signer = new ECDSASigner(ecPrivateKey);
        signedJWT.sign(signer);

        // Print the signed JWT as a string

        System.out.println("Signed JWT:");
        System.out.println(STR."Header: \{signedJWT.getHeader()}");


        String sJWT = signedJWT.serialize();
        System.out.println(sJWT);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        System.out.println("Hello, Coinbase!");

        // TODO: Implement Coinbase API calls here


    }
}
