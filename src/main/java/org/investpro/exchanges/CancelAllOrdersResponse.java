package org.investpro.exchanges;

import lombok.Getter;
import lombok.Setter;
import org.investpro.Order;

@Getter
@Setter
public class CancelAllOrdersResponse {

    private String result;
    private String error;


   int count;
    String message;
    String timestamp;
    String status;
    public CancelAllOrdersResponse() {}

   Order order;
}
