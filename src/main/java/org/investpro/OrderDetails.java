package org.investpro;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Setter
@Getter
@ToString
@NoArgsConstructor
public class OrderDetails {

    Long id;


    String createTime;


    String instrument;


    String partialFill;


    String positionFill;

    String price;


    String replacesOrderID;


    String state;

    String timeInForce;


    String triggerCondition;

    String type;

    String units;


}