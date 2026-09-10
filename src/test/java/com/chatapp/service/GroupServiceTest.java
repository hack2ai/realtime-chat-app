package com.chatapp.service;

import com.chatapp.database.GroupDAO;
import com.chatapp.database.UserDAO;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupServiceTest {

    @Test
    void joinReturnsTrueWhenMembershipInsertSucceeds() throws ValidationException {
        StubGroupDAO groupDAO = new StubGroupDAO();
        groupDAO.addMemberResult = true;

        GroupService service = new GroupService(groupDAO, new StubUserDAO());

        assertTrue(service.join(7, 42));
        assertTrue(groupDAO.addMemberCalled);
    }

    @Test
    void joinReturnsFalseWhenUserIsAlreadyMember() throws ValidationException {
        StubGroupDAO groupDAO = new StubGroupDAO();
        groupDAO.member = true;

        GroupService service = new GroupService(groupDAO, new StubUserDAO());

        assertFalse(service.join(7, 42));
        assertFalse(groupDAO.addMemberCalled);
    }

    @Test
    void joinReportsCapacityWhenInsertFailsAndMembershipDidNotChange() {
        StubGroupDAO groupDAO = new StubGroupDAO();
        groupDAO.addMemberResult = false;
        groupDAO.member = false;

        GroupService service = new GroupService(groupDAO, new StubUserDAO());

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.join(7, 42));

        assertTrue(exception.getMessage().contains("maximum of 200 members"));
        assertTrue(groupDAO.addMemberCalled);
    }

    @Test
    void joinTreatsConcurrentMembershipAsAlreadyJoined() throws ValidationException {
        StubGroupDAO groupDAO = new StubGroupDAO();
        groupDAO.addMemberResult = false;
        groupDAO.membershipAppearsAfterInsert = true;

        GroupService service = new GroupService(groupDAO, new StubUserDAO());

        assertFalse(service.join(7, 42));
        assertTrue(groupDAO.addMemberCalled);
    }

    @Test
    void isMemberReturnsTrueForCurrentMembership() {
        StubGroupDAO groupDAO = new StubGroupDAO();
        groupDAO.member = true;

        GroupService service = new GroupService(groupDAO, new StubUserDAO());

        assertTrue(service.isMember(42, 7));
    }

    @Test
    void isMemberReturnsFalseWhenMembershipIsAbsent() {
        StubGroupDAO groupDAO = new StubGroupDAO();
        groupDAO.member = false;

        GroupService service = new GroupService(groupDAO, new StubUserDAO());

        assertFalse(service.isMember(42, 7));
    }

    private static final class StubUserDAO extends UserDAO {
        @Override
        public Optional<User> findById(int id) {
            return id == 7 ? Optional.of(new User()) : Optional.empty();
        }
    }

    private static final class StubGroupDAO extends GroupDAO {
        private boolean member;
        private boolean addMemberResult;
        private boolean addMemberCalled;
        private boolean membershipAppearsAfterInsert;

        @Override
        public boolean exists(int groupId) {
            return groupId == 42;
        }

        @Override
        public boolean isMember(int groupId, int userId) {
            return member;
        }

        @Override
        public boolean addMember(int groupId, int userId) {
            addMemberCalled = true;
            if (!addMemberResult && membershipAppearsAfterInsert) {
                member = true;
            }
            return addMemberResult;
        }
    }
}
