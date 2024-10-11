package org.investpro;


import jakarta.persistence.*;

@Entity
@Table(name = "order_details")
public class OrderDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id;

    @Column(name = "create_time", nullable = false)
    String createTime;

    @Column(name = "instrument", nullable = false)
    String instrument;

    @Column(name = "partial_fill")
    String partialFill;

    @Column(name = "position_fill")
    String positionFill;

    @Column(name = "price", nullable = false)
    String price;

    @Column(name = "replaces_order_id")
    String replacesOrderID;

    @Column(name = "state", nullable = false)
    String state;

    @Column(name = "time_in_force", nullable = false)
    String timeInForce;

    @Column(name = "trigger_condition", nullable = false)
    String triggerCondition;
    @Column(name = "type", nullable = false)
    String type;
    @Column(name = "units", nullable = false)
    String units;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public String getPartialFill() {
        return partialFill;
    }

    public void setPartialFill(String partialFill) {
        this.partialFill = partialFill;
    }

    public String getPositionFill() {
        return positionFill;
    }

    public void setPositionFill(String positionFill) {
        this.positionFill = positionFill;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getReplacesOrderID() {
        return replacesOrderID;
    }

    public void setReplacesOrderID(String replacesOrderID) {
        this.replacesOrderID = replacesOrderID;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public String getTriggerCondition() {
        return triggerCondition;
    }

    public void setTriggerCondition(String triggerCondition) {
        this.triggerCondition = triggerCondition;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }


}