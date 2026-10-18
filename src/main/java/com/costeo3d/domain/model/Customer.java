package com.costeo3d.domain.model;

public class Customer {

    private Integer id;
    private String name;
    private String phone;
    private String email;
    private boolean active;

    public Customer(String name, String phone, String email) {
        this(null, name, phone, email, true);
    }

    public Customer(Integer id, String name, String phone,
            String email, boolean active) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.active = active;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }
}