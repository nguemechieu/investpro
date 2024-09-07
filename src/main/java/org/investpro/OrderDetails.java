package org.investpro;


import jakarta.persistence.*;

@Entity
@Table(name = "order_details")
public class OrderDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "create_time", nullable = false)
    private String createTime;

    @Column(name = "instrument", nullable = false)
    private String instrument;

    @Column(name = "partial_fill")
    private String partialFill;

    @Column(name = "position_fill")
    private String positionFill;

    @Column(name = "price", nullable = false)
    private String price;

    @Column(name = "replaces_order_id")
    private String replacesOrderID;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "time_in_force", nullable = false)
    private String timeInForce;

    @Column(name = "trigger_condition", nullable = false)
    private String triggerCondition;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "units", nullable = false)
    private String units;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    // Getters and setters...
    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}