package com.chatapp.model.dto;

import java.time.LocalDateTime;

/** Wire payloads for authenticated private-file transfers. */
public final class AttachmentDTOs {
    private AttachmentDTOs() {}

    public static class PrivateFileUploadRequest {
        private int receiverId;
        private String fileName;
        private String contentType;
        private String dataBase64;

        public PrivateFileUploadRequest() {}
        public PrivateFileUploadRequest(int receiverId, String fileName, String contentType, String dataBase64) {
            this.receiverId = receiverId; this.fileName = fileName; this.contentType = contentType; this.dataBase64 = dataBase64;
        }
        public int getReceiverId() { return receiverId; }
        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public String getDataBase64() { return dataBase64; }
        public void setReceiverId(int v) { receiverId = v; }
        public void setFileName(String v) { fileName = v; }
        public void setContentType(String v) { contentType = v; }
        public void setDataBase64(String v) { dataBase64 = v; }
    }

    public static class PrivateFileEvent {
        private String fileId;
        private int senderId;
        private int receiverId;
        private String senderUsername;
        private String fileName;
        private String contentType;
        private long sizeBytes;
        private String sha256;
        private LocalDateTime sentAt;

        public PrivateFileEvent() {}
        public PrivateFileEvent(String fileId, int senderId, int receiverId, String senderUsername,
                                String fileName, String contentType, long sizeBytes, String sha256, LocalDateTime sentAt) {
            this.fileId=fileId; this.senderId=senderId; this.receiverId=receiverId; this.senderUsername=senderUsername;
            this.fileName=fileName; this.contentType=contentType; this.sizeBytes=sizeBytes; this.sha256=sha256; this.sentAt=sentAt;
        }
        public String getFileId(){return fileId;} public int getSenderId(){return senderId;} public int getReceiverId(){return receiverId;}
        public String getSenderUsername(){return senderUsername;} public String getFileName(){return fileName;} public String getContentType(){return contentType;}
        public long getSizeBytes(){return sizeBytes;} public String getSha256(){return sha256;} public LocalDateTime getSentAt(){return sentAt;}
        public void setFileId(String v){fileId=v;} public void setSenderId(int v){senderId=v;} public void setReceiverId(int v){receiverId=v;}
        public void setSenderUsername(String v){senderUsername=v;} public void setFileName(String v){fileName=v;} public void setContentType(String v){contentType=v;}
        public void setSizeBytes(long v){sizeBytes=v;} public void setSha256(String v){sha256=v;} public void setSentAt(LocalDateTime v){sentAt=v;}
    }

    public static class PrivateFileDownloadRequest {
        private String fileId;
        public PrivateFileDownloadRequest() {}
        public PrivateFileDownloadRequest(String fileId){this.fileId=fileId;}
        public String getFileId(){return fileId;} public void setFileId(String v){fileId=v;}
    }

    public static class PrivateFileDownloadResponse {
        private String fileId;
        private String fileName;
        private String contentType;
        private long sizeBytes;
        private String sha256;
        private String dataBase64;
        public PrivateFileDownloadResponse() {}
        public PrivateFileDownloadResponse(String fileId,String fileName,String contentType,long sizeBytes,String sha256,String dataBase64){
            this.fileId=fileId;this.fileName=fileName;this.contentType=contentType;this.sizeBytes=sizeBytes;this.sha256=sha256;this.dataBase64=dataBase64;
        }
        public String getFileId(){return fileId;} public String getFileName(){return fileName;} public String getContentType(){return contentType;}
        public long getSizeBytes(){return sizeBytes;} public String getSha256(){return sha256;} public String getDataBase64(){return dataBase64;}
        public void setFileId(String v){fileId=v;} public void setFileName(String v){fileName=v;} public void setContentType(String v){contentType=v;}
        public void setSizeBytes(long v){sizeBytes=v;} public void setSha256(String v){sha256=v;} public void setDataBase64(String v){dataBase64=v;}
    }
}
