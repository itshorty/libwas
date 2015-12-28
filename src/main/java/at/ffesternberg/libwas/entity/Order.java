package at.ffesternberg.libwas.entity;

import java.util.Date;
import java.util.List;

public class Order {
    private long key;
    private String origin;
    private Date received;
    private Date watchout;
    private Date finished;
    private String operationId;
    private int alarmlevel;
    private String name;
    private String location;
    private String operation;
    private String caller;
    private String info;
    private OrderStatus status;
    private List<String> fireDepartments;
    
    
    public Order() {
	}

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Date getReceived() {
        return received;
    }

    public void setReceived(Date received) {
        this.received = received;
    }

    public Date getWatchout() {
        return watchout;
    }

    public void setWatchout(Date watchout) {
        this.watchout = watchout;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public int getAlarmlevel() {
        return alarmlevel;
    }

    public void setAlarmlevel(int alarmlevel) {
        this.alarmlevel = alarmlevel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<String> getFireDepartments() {
        return fireDepartments;
    }

    public void setFireDepartments(List<String> fireDepartments) {
        this.fireDepartments = fireDepartments;
    }

    public long getKey() {
        return key;
    }

    public void setKey(long key) {
        this.key = key;
    }

    public Date getFinished() {
        return finished;
    }

    public void setFinished(Date finished) {
        this.finished = finished;
    }
}
