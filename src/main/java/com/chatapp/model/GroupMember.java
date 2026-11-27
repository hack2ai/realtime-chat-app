package com.chatapp.model;

import java.time.LocalDateTime;

/**
 * Domain model for a group membership record, mapping to the
 * {@code group_members} table. This is a join table between
 * {@link ChatGroup} and {@link User} with its own per-membership role
 * (a user can be a regular MEMBER of one group and an ADMIN of another).
 */
public class GroupMember {

    public enum MemberRole { MEMBER, ADMIN }

    private int id;
    private int groupId;
    private int userId;
    private MemberRole role;
    private LocalDateTime joinedAt;

    public GroupMember() {
        // Required no-arg constructor for Gson and JDBC row mapping.
    }

    public GroupMember(int groupId, int userId, MemberRole role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public boolean isGroupAdmin() {
        return role == MemberRole.ADMIN;
    }

    @Override
    public String toString() {
        return "GroupMember{groupId=%d, userId=%d, role=%s}".formatted(groupId, userId, role);
    }
}
