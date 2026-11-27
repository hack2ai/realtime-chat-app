package com.chatapp.service;

import com.chatapp.config.AppConfig;
import com.chatapp.exception.ValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Secure local storage for small authenticated chat attachments. */
public final class AttachmentStorageService implements AttachmentStorage {
    public static final long MAX_FILE_BYTES = AttachmentStorage.MAX_FILE_BYTES;
    private static final long MAX_BASE64_CHARS = AttachmentStorage.MAX_BASE64_CHARS;
    private static final Path STORAGE_ROOT = Path.of(AppConfig.getAttachmentStoragePath()).toAbsolutePath().normalize();

    public AttachmentStorageService(){
        try{Files.createDirectories(STORAGE_ROOT);if(!Files.isDirectory(STORAGE_ROOT,LinkOption.NOFOLLOW_LINKS))throw new IOException("Storage path is not a directory.");}
        catch(IOException e){throw new IllegalStateException("Unable to initialize attachment storage.",e);}
    }

    @Override
    public AttachmentStorage.StoredFile store(String fileName,String contentType,byte[] bytes)throws ValidationException{
        String safeName=AttachmentValidator.validateAndNormalizeName(fileName);
        if(bytes==null||bytes.length==0)throw new ValidationException("File is empty.");
        if(bytes.length>MAX_FILE_BYTES)throw new ValidationException("File exceeds the 5 MB limit.");
        String safeType=AttachmentValidator.validateAndNormalizeType(contentType);
        AttachmentValidator.validateContent(safeType,bytes);
        String id=UUID.randomUUID().toString();
        Path target=STORAGE_ROOT.resolve(id+".bin").normalize();
        Path temp=STORAGE_ROOT.resolve("."+id+".tmp").normalize();
        if(!target.getParent().equals(STORAGE_ROOT)||!temp.getParent().equals(STORAGE_ROOT))throw new ValidationException("Invalid attachment path.");
        try{
            Files.write(temp,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);
            return new AttachmentStorage.StoredFile(id,safeName,safeType,bytes.length,sha256(bytes));
        }catch(IOException e){try{Files.deleteIfExists(temp);}catch(IOException ignored){}throw new IllegalStateException("Unable to store attachment.",e);}
    }

    @Override
    public byte[] load(String fileId)throws ValidationException{
        validateId(fileId); Path path=STORAGE_ROOT.resolve(fileId+".bin").normalize();
        if(!path.getParent().equals(STORAGE_ROOT))throw new ValidationException("Invalid attachment path.");
        try{
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new ValidationException("Attachment not found.");
            long size=Files.size(path);if(size>MAX_FILE_BYTES)throw new ValidationException("Stored attachment exceeds the configured limit.");
            try(InputStream input=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
                byte[] bytes=input.readNBytes((int)MAX_FILE_BYTES + 1);
                if(bytes.length>MAX_FILE_BYTES)throw new ValidationException("Stored attachment exceeds the configured limit.");
                return bytes;
            }
        }catch(IOException e){throw new IllegalStateException("Unable to read attachment.",e);}
    }

    @Override
    public void delete(String fileId){try{validateId(fileId);Path path=STORAGE_ROOT.resolve(fileId+".bin").normalize();if(path.getParent().equals(STORAGE_ROOT))Files.deleteIfExists(path);}catch(Exception ignored){}}

    public static byte[] decodeBase64(String data) throws ValidationException { return AttachmentStorage.decodeBase64(data); }

    private static void validateId(String id)throws ValidationException{if(id==null||!id.matches("[0-9a-fA-F-]{36}"))throw new ValidationException("Invalid file id.");}
    private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 is unavailable.",e);}}
}
