package com.example.tenantshield.agents.models;

public class AgentMessage {

    public enum MessageType {
        USER_INFO_COLLECTED, IMAGES_READY, INSPECTION_COMPLETE,
        COMPLAINT_GENERATED, EXPLANATION_READY, ERROR
    }

    private MessageType type;
    private String senderId;
    private String targetId;
    private Object payload;
    private long timestamp;

    public AgentMessage() {
    }

    public AgentMessage(MessageType type, String senderId, String targetId,
                        Object payload, long timestamp) {
        this.type = type;
        this.senderId = senderId;
        this.targetId = targetId;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
