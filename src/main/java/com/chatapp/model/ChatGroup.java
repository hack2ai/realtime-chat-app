package com.chatapp.model;

import java.time.LocalDateTime;

/**
 * Domain model for a chat group, mapping to the {@code chat_groups} table.
 *
 * <p>Named {@code ChatGroup} rather than {@code Group} to avoid colliding
 * with {@code java.awt.Group}-style ambiguity and to keep grep/search
 * results in this codebase unambiguous.
 */
public class ChatGroup {

    private int id;
    private String groupName;
    private String description;
    private int createdBy;
    private LocalDateTime createdAt;

    public ChatGroup() {
        // Required no-arg constructor for Gson and JDBC row mapping.
    }

    public ChatGroup(String groupName, String description, int createdBy) {
        this.groupName = groupName;
        this.description = description;
        this.createdBy = createdBy;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ChatGroup{id=%d, name='%s'}".formatted(id, groupName);
    }
}
