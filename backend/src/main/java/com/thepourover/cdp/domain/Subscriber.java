package com.thepourover.cdp.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "subscribers", indexes = {
        @Index(name = "idx_subscriber_email", columnList = "email", unique = true)
})
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String status;
    private LocalDate signupDate;
    private String acquisitionSource;
    private LocalDate lastOpenDate;

    @Column(nullable = false)
    private boolean stub = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getSignupDate() {
        return signupDate;
    }

    public void setSignupDate(LocalDate signupDate) {
        this.signupDate = signupDate;
    }

    public String getAcquisitionSource() {
        return acquisitionSource;
    }

    public void setAcquisitionSource(String acquisitionSource) {
        this.acquisitionSource = acquisitionSource;
    }

    public LocalDate getLastOpenDate() {
        return lastOpenDate;
    }

    public void setLastOpenDate(LocalDate lastOpenDate) {
        this.lastOpenDate = lastOpenDate;
    }

    public boolean isStub() {
        return stub;
    }

    public void setStub(boolean stub) {
        this.stub = stub;
    }
}
