package com.chatapp.service;

import com.chatapp.database.AttachmentDAO;
import com.chatapp.database.AttachmentDAO.AttachmentRecord;
import com.chatapp.database.UserDAO;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.dto.AttachmentDTOs.PrivateFileEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/** Business rules and authorization for private attachments. */
public final class AttachmentService {
    private static final int MAX_DOWNLOADS_PER_MINUTE = 12;
    private final AttachmentStorageService storage;
    private final AttachmentDAO dao;
    private final UserDAO userDAO;
    private final RequestRateLimiter downloadRateLimiter =
            new RequestRateLimiter(MAX_DOWNLOADS_PER_MINUTE, Duration.ofMinutes(1), 10_000);

    public AttachmentService() { this(new AttachmentStorageService(), new AttachmentDAO(), new UserDAO()); }
    public AttachmentService(AttachmentStorageService storage, AttachmentDAO dao, UserDAO userDAO) {
        this.storage=storage; this.dao=dao; this.userDAO=userDAO;
    }

    public PrivateFileEvent upload(int senderId, int receiverId, String fileName, String contentType, String dataBase64, String senderUsername)
            throws ValidationException {
        requireParticipant(senderId, receiverId);
        byte[] bytes=AttachmentStorageService.decodeBase64(dataBase64);
        AttachmentStorageService.StoredFile stored=storage.store(fileName,contentType,bytes);
        try {
            AttachmentRecord record=new AttachmentRecord(stored.fileId(),senderId,receiverId,stored.fileName(),stored.contentType(),stored.sizeBytes(),stored.sha256(),LocalDateTime.now());
            dao.insert(record);
            return new PrivateFileEvent(record.id(),senderId,receiverId,senderUsername,record.fileName(),record.contentType(),record.sizeBytes(),record.sha256(),record.createdAt());
        } catch (RuntimeException e) {
            storage.delete(stored.fileId());
            throw e;
        }
    }

    public DownloadedFile download(int userId, String fileId) throws ValidationException {
        if (userId <= 0 || !downloadRateLimiter.allow(Integer.toString(userId))) {
            throw new ValidationException("Too many file downloads. Please try again later.");
        }
        AttachmentRecord record=dao.findForUser(fileId,userId).orElseThrow(()->new ValidationException("Attachment not found."));
        byte[] bytes=storage.load(record.id());
        if(bytes.length!=record.sizeBytes() || !record.sha256().equalsIgnoreCase(sha256(bytes))) throw new ValidationException("Attachment integrity check failed.");
        return new DownloadedFile(record,Base64.getEncoder().encodeToString(bytes));
    }

    public record DownloadedFile(AttachmentRecord metadata,String dataBase64) {}

    private void requireParticipant(int senderId,int receiverId)throws ValidationException{
        if(senderId<=0||receiverId<=0||senderId==receiverId)throw new ValidationException("Invalid attachment recipient.");
        if(userDAO.findById(senderId).isEmpty()||userDAO.findById(receiverId).isEmpty())throw new ValidationException("User does not exist.");
    }
    private static String sha256(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
