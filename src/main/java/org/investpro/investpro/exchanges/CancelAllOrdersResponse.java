package org.investpro.investpro.exchanges;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.Order;

@Getter
@Setter
public class CancelAllOrdersResponse {

    int count;
    String message;
    String timestamp;
    String status;
    Order order;
    private String result;
    private String error;

    public CancelAllOrdersResponse() {
    }
}
