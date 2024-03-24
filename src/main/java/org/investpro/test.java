package org.investpro;

import javafx.application.Application;
import javafx.stage.Stage;

import static java.lang.System.out;

public class test extends Application {


    public static void main(String[] args) {
        out.println("Hello World!");
        Coinbase exchange = new Coinbase("a8dd6d6f-375a-4f4b-b0b0-75f829b998eb", "7c7e538c-8d26-4343-bac1-814ce44e26a3");//"MHcCAQEEIEXosveoAkpTwXhc6UojOmKWlCP9IvaycJ1ZI509pqrvoAoGCCqGSM49AwEHoUQDQgAE3iHOfTS+43E9nTtVgnDQn4Az/xbgoA61WO7rNhqEtq1jpRh5Gahfn5og7biKiHXymAR/buynHTN/sH7ZmCHtAMg==");
        out.println(exchange.getUserAccountDetails());


    }

    @Override
    public void start(Stage primaryStage) throws Exception {

    }
}

