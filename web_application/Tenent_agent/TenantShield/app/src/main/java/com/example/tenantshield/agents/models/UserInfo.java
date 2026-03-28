package com.example.tenantshield.agents.models;

public class UserInfo {

    private String tenantName;
    private String address;
    private String unitNumber;
    private String complaintDescription;
    private String inspectionRequest;
    private long collectedAt;

    public UserInfo() {
    }

    public UserInfo(String tenantName, String address, String unitNumber,
                    String complaintDescription, String inspectionRequest, long collectedAt) {
        this.tenantName = tenantName;
        this.address = address;
        this.unitNumber = unitNumber;
        this.complaintDescription = complaintDescription;
        this.inspectionRequest = inspectionRequest;
        this.collectedAt = collectedAt;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public void setUnitNumber(String unitNumber) {
        this.unitNumber = unitNumber;
    }

    public String getComplaintDescription() {
        return complaintDescription;
    }

    public void setComplaintDescription(String complaintDescription) {
        this.complaintDescription = complaintDescription;
    }

    public String getInspectionRequest() {
        return inspectionRequest;
    }

    public void setInspectionRequest(String inspectionRequest) {
        this.inspectionRequest = inspectionRequest;
    }

    public long getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(long collectedAt) {
        this.collectedAt = collectedAt;
    }
}
