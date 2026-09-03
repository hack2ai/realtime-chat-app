package com.chatapp.service;

import com.chatapp.database.GroupDAO;
import com.chatapp.database.UserDAO;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.dto.GroupDTOs.GroupMessageEvent;
import com.chatapp.model.dto.GroupDTOs.GroupSummary;
import com.chatapp.util.ValidationUtil;

import java.util.List;

/** Business rules for group creation, membership, and messaging. */
public class GroupService {
    private final GroupDAO groupDAO;
    private final UserDAO userDAO;

    public GroupService() { this(new GroupDAO(), new UserDAO()); }
    public GroupService(GroupDAO groupDAO, UserDAO userDAO) { this.groupDAO = groupDAO; this.userDAO = userDAO; }

    public GroupSummary create(int ownerId, String name) throws ValidationException {
        requireUser(ownerId);
        ValidationUtil.validateGroupName(name);
        return groupDAO.create(ownerId, name.strip());
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
        ValidationUtil.validateMessageContent(message);
        return groupDAO.insertMessage(groupId, userId, username, message.strip());
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
}
