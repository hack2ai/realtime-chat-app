package com.chatapp.service;

import com.chatapp.database.GroupDAO;
import com.chatapp.database.UserDAO;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.dto.GroupDTOs.GroupMessageEvent;
import com.chatapp.model.dto.GroupDTOs.GroupSummary;

import java.util.List;

/** Business rules for group creation, membership, and messaging. */
public class GroupService {
    private static final int MAX_GROUP_NAME = 80;
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private final GroupDAO groupDAO;
    private final UserDAO userDAO;

    public GroupService() { this(new GroupDAO(), new UserDAO()); }
    public GroupService(GroupDAO groupDAO, UserDAO userDAO) { this.groupDAO = groupDAO; this.userDAO = userDAO; }

    public GroupSummary create(int ownerId, String name) throws ValidationException {
        requireUser(ownerId);
        return groupDAO.create(ownerId, normalizeName(name));
    }

    public boolean join(int userId, int groupId) throws ValidationException {
        requireUser(userId); requireGroup(groupId);
        if (groupDAO.isMember(groupId, userId)) return false;
        return groupDAO.addMember(groupId, userId);
    }

    public boolean leave(int userId, int groupId) throws ValidationException {
        requireUser(userId); requireGroup(groupId);
        if (!groupDAO.isMember(groupId, userId)) return false;
        if (groupDAO.isOwner(groupId, userId) && groupDAO.adminCount(groupId) <= 1) {
            throw new ValidationException("The group owner must transfer ownership before leaving.");
        }
        return groupDAO.removeMember(groupId, userId);
    }

    public List<GroupSummary> list(int userId) { return groupDAO.findForUser(userId); }
    public List<Integer> members(int groupId) throws ValidationException { requireGroup(groupId); return groupDAO.memberIds(groupId); }

    public GroupMessageEvent sendMessage(int userId, String username, int groupId, String message) throws ValidationException {
        requireMembership(groupId, userId);
        if (message == null || message.isBlank()) throw new ValidationException("Message cannot be empty.");
        String normalized = message.strip();
        if (normalized.length() > MAX_MESSAGE_LENGTH) throw new ValidationException("Message exceeds the " + MAX_MESSAGE_LENGTH + " character limit.");
        return groupDAO.insertMessage(groupId, userId, username, normalized);
    }

    public List<GroupMessageEvent> history(int userId, int groupId, int limit, long beforeId) throws ValidationException {
        requireMembership(groupId, userId);
        return groupDAO.history(groupId, limit, beforeId);
    }

    private void requireUser(int userId) throws ValidationException {
        if (userId <= 0 || userDAO.findById(userId).isEmpty()) throw new ValidationException("User does not exist.");
    }
    private void requireGroup(int groupId) throws ValidationException {
        if (groupId <= 0 || !groupDAO.exists(groupId)) throw new ValidationException("Group does not exist.");
    }
    private void requireMembership(int groupId, int userId) throws ValidationException {
        requireUser(userId); requireGroup(groupId);
        if (!groupDAO.isMember(groupId, userId)) throw new ValidationException("You are not a member of this group.");
    }
    private String normalizeName(String name) throws ValidationException {
        if (name == null || name.isBlank()) throw new ValidationException("Group name cannot be empty.");
        String normalized = name.strip();
        if (normalized.length() > MAX_GROUP_NAME) throw new ValidationException("Group name exceeds the " + MAX_GROUP_NAME + " character limit.");
        return normalized;
    }
}
