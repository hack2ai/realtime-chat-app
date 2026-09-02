package com.chatapp.service;

import com.chatapp.config.AppConfig;
import com.chatapp.exception.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Secure local storage for small authenticated chat attachments. */
public final class AttachmentStorageService {
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 180;
    private static final int MAX_CONTENT_TYPE_LENGTH = 120;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv", "image/jpeg", "image/png", "image/gif", "image/webp",
            "audio/mpeg", "audio/wav", "video/mp4", "application/zip", "application/json",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final Path STORAGE_ROOT = Path.of(AppConfig.getAttachmentStoragePath()).toAbsolutePath().normalize();
    public record StoredFile(String fileId,String fileName,String contentType,long sizeBytes,String sha256) {}

    public AttachmentStorageService(){
        try{Files.createDirectories(STORAGE_ROOT);if(!Files.isDirectory(STORAGE_ROOT,LinkOption.NOFOLLOW_LINKS))throw new IOException("Storage path is not a directory.");}
        catch(IOException e){throw new IllegalStateException("Unable to initialize attachment storage.",e);}
    }

    public StoredFile store(String fileName,String contentType,byte[] bytes)throws ValidationException{
        String safeName=sanitizeName(fileName);
        if(bytes==null||bytes.length==0)throw new ValidationException("File is empty.");
        if(bytes.length>MAX_FILE_BYTES)throw new ValidationException("File exceeds the 5 MB limit.");
        String safeType=normalizeContentType(contentType);
        validateContentType(safeType);
        validateMagicBytes(safeType,bytes);
        String id=UUID.randomUUID().toString();
        Path target=STORAGE_ROOT.resolve(id+".bin").normalize();
        Path temp=STORAGE_ROOT.resolve("."+id+".tmp").normalize();
        if(!target.getParent().equals(STORAGE_ROOT)||!temp.getParent().equals(STORAGE_ROOT))throw new ValidationException("Invalid attachment path.");
        try{
            Files.write(temp,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(id,safeName,safeType,bytes.length,sha256(bytes));
        }catch(IOException e){try{Files.deleteIfExists(temp);}catch(IOException ignored){}throw new IllegalStateException("Unable to store attachment.",e);}
    }

    public byte[] load(String fileId)throws ValidationException{
        validateId(fileId); Path path=STORAGE_ROOT.resolve(fileId+".bin").normalize();
        if(!path.getParent().equals(STORAGE_ROOT))throw new ValidationException("Invalid attachment path.");
        try{
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new ValidationException("Attachment not found.");
            long size=Files.size(path);if(size>MAX_FILE_BYTES)throw new ValidationException("Stored attachment exceeds the configured limit.");
            return Files.readAllBytes(path);
        }catch(IOException e){throw new IllegalStateException("Unable to read attachment.",e);}
    }

    public void delete(String fileId){try{validateId(fileId);Files.deleteIfExists(STORAGE_ROOT.resolve(fileId+".bin").normalize());}catch(Exception ignored){}}

    public static byte[] decodeBase64(String data)throws ValidationException{
        if(data==null||data.isBlank())throw new ValidationException("File data is required.");
        if(data.length()>((MAX_FILE_BYTES+2)/3)*4+4)throw new ValidationException("File exceeds the 5 MB limit.");
        try{byte[] decoded=Base64.getDecoder().decode(data);if(decoded.length>MAX_FILE_BYTES)throw new ValidationException("File exceeds the 5 MB limit.");return decoded;}
        catch(IllegalArgumentException e){throw new ValidationException("File data is not valid Base64.",e);}
    }

    private static void validateId(String id)throws ValidationException{if(id==null||!id.matches("[0-9a-fA-F-]{36}"))throw new ValidationException("Invalid file id.");}
    private static String sanitizeName(String fileName)throws ValidationException{
        if(fileName==null||fileName.isBlank())throw new ValidationException("File name is required."); String name=fileName.strip().replace('\\','/');
        int slash=name.lastIndexOf('/');if(slash>=0)name=name.substring(slash+1);name=name.replaceAll("[\\p{Cntrl}]","_");
        if(name.isBlank()||".".equals(name)||"..".equals(name))throw new ValidationException("Invalid file name.");return name.length()>MAX_NAME_LENGTH?name.substring(0,MAX_NAME_LENGTH):name;
    }
    private static String normalizeContentType(String value){if(value==null||value.isBlank())return "application/octet-stream";String type=value.strip().toLowerCase(Locale.ROOT);return type.length()>MAX_CONTENT_TYPE_LENGTH?type.substring(0,MAX_CONTENT_TYPE_LENGTH):type;}
    private static void validateContentType(String type)throws ValidationException{if(!ALLOWED_TYPES.contains(type))throw new ValidationException("Unsupported attachment type.");}
    private static void validateMagicBytes(String type,byte[] b)throws ValidationException{
        if(type.equals("image/png")&&!startsWith(b,new byte[]{(byte)0x89,0x50,0x4e,0x47}))throw new ValidationException("File content does not match its declared type.");
        if(type.equals("image/jpeg")&&!startsWith(b,new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff}))throw new ValidationException("File content does not match its declared type.");
        if(type.equals("image/gif")&&!startsWithAscii(b,"GIF8"))throw new ValidationException("File content does not match its declared type.");
        if(type.equals("application/pdf")&&!startsWithAscii(b,"%PDF-"))throw new ValidationException("File content does not match its declared type.");
        if(type.equals("application/zip")&&!startsWith(b,new byte[]{0x50,0x4b,0x03,0x04}))throw new ValidationException("File content does not match its declared type.");
    }
    private static boolean startsWith(byte[] value,byte[] prefix){if(value.length<prefix.length)return false;for(int i=0;i<prefix.length;i++)if(value[i]!=prefix[i])return false;return true;}
    private static boolean startsWithAscii(byte[] value,String prefix){return startsWith(value,prefix.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 is unavailable.",e);}}
}
