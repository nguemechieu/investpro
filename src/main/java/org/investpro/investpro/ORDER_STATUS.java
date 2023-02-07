package org.investpro.investpro;

public enum ORDER_STATUS {
    NEW(1),//	The order has been accepted by the engine
    PARTIALLY_FILLED(2),//	Part of the order has been filled
    FILLED(3),//The order has been completed
    CANCELED(4),//	The order has been canceled by the user
    PENDING_CANCEL(5),//	This is currently unused
    REJECTED(6),//	The order was not accepted by the engine and not processed
    EXPIRED(0)//
    ;

    ORDER_STATUS(int i) {
    }
}
