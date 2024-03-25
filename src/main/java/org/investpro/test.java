package org.investpro;

import javafx.application.Application;
import javafx.stage.Stage;

import static java.lang.System.out;

public class test extends Application {


    public static void main(String[] args) {

        Coinbase exchange = new Coinbase("9935decf-f983-4c83-9621-f086d59fe565",
                "nMHcCAQEEIMhpQ0wrioqvuGXRhrJPly/NiZ6oSCx/mUOFlz9LEnWjoAoGCCqGSM49\\nAwEHoUQDQgAEyvzb0c+LUoKxyZL8WOV2ZmoXWRdLAqz1VHgH8tTjMNhtz7ibaq98\\nD3ArkQ93qR3t+CzHnGFJOZhq54sQ+PpOMA=="
        );                //"organizations/a8dd6d6f-375a-4f4b-b0b0-75f829b998eb/apiKeys/75d310c9-70ce-4998-aa40-33049e08b935","-----BEGIN EC PRIVATE KEY-----\\nMHcCAQEEIDLNOFXWYuYiNdxC3imgWdQYw1qwvRwvj90TCUqiVtsboAoGCCqGSM49\\nAwEHoUQDQgAEfXkywouOMFnUKoeWOixwKkbUPoK9Ho/4gmMstboxI9hXaOrG48+t\\nOwnjOGZ6YYPpKDc9nlNBB6Oc5ixuwOfxtQ==\\n-----END EC PRIVATE KEY-----\\n");
        out.println(exchange.getUserAccountDetails());


    }

    @Override
    public void start(Stage primaryStage) throws Exception {

    }
}

