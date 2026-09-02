package com.chatapp.client;

import com.chatapp.model.dto.AttachmentDTOs.PrivateFileDownloadRequest;
import com.chatapp.model.dto.AttachmentDTOs.PrivateFileUploadRequest;
import com.chatapp.model.dto.AuthDTOs.AuthFailedResponse;
import com.chatapp.model.dto.AuthDTOs.LoginRequest;
import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.AuthDTOs.RegisterRequest;
import com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse;
import com.chatapp.model.dto.ChatDTOs.SearchPrivateMessagesRequest;
import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Thread-safe asynchronous transport for the chat application's wire protocol. */
public final class ChatClientConnection implements AutoCloseable {
    private final MessageCodec codec=new MessageCodec(); private final Consumer<Envelope> eventListener; private final Consumer<Boolean> connectionListener;
    private final Object authLock=new Object(); private CompletableFuture<?> pendingAuth; private Class<?> pendingAuthType;
    private Socket socket; private DataInputStream in; private DataOutputStream out; private volatile boolean running;
    public ChatClientConnection(Consumer<Envelope> eventListener){this(eventListener,ignored->{});} public ChatClientConnection(Consumer<Envelope> eventListener,Consumer<Boolean> connectionListener){this.eventListener=eventListener;this.connectionListener=connectionListener;}
    public synchronized void connect(String host,int port)throws IOException{if(running)return;if(host==null||host.isBlank())throw new IllegalArgumentException("Server host is required.");if(port<1||port>65535)throw new IllegalArgumentException("Server port must be between 1 and 65535.");Socket newSocket=new Socket();try{newSocket.connect(new InetSocketAddress(host.trim(),port),5000);newSocket.setTcpNoDelay(true);socket=newSocket;in=new DataInputStream(socket.getInputStream());out=new DataOutputStream(socket.getOutputStream());running=true;notifyConnectionState(true);Thread.ofVirtual().name("chat-client-reader").start(this::readLoop);}catch(IOException|RuntimeException e){try{newSocket.close();}catch(IOException ignored){}socket=null;in=null;out=null;running=false;notifyConnectionState(false);throw e;}}
    public CompletableFuture<LoginSuccessResponse> login(String usernameOrEmail,String password){CompletableFuture<LoginSuccessResponse> future=new CompletableFuture<>();if(!registerPendingAuth(future,LoginSuccessResponse.class))return future;try{send(MessageType.C2S_LOGIN,new LoginRequest(usernameOrEmail,password));}catch(IOException e){clearPendingAuth(future);future.completeExceptionally(e);}return future;}
    public CompletableFuture<RegisterSuccessResponse> register(String username,String email,String password,String confirmPassword){CompletableFuture<RegisterSuccessResponse> future=new CompletableFuture<>();if(!registerPendingAuth(future,RegisterSuccessResponse.class))return future;try{send(MessageType.C2S_REGISTER,new RegisterRequest(username,email,password,confirmPassword));}catch(IOException e){clearPendingAuth(future);future.completeExceptionally(e);}return future;}
    public void searchPrivateMessages(String query,int limit)throws IOException{if(query==null||query.isBlank())throw new IllegalArgumentException("Search text cannot be empty.");send(MessageType.C2S_SEARCH_PRIVATE_MESSAGES,new SearchPrivateMessagesRequest(query.strip(),limit));}
    public void uploadPrivateFile(int receiverId,String fileName,String contentType,String dataBase64)throws IOException{send(MessageType.C2S_UPLOAD_PRIVATE_FILE,new PrivateFileUploadRequest(receiverId,fileName,contentType,dataBase64));}
    public void downloadPrivateFile(String fileId)throws IOException{send(MessageType.C2S_DOWNLOAD_PRIVATE_FILE,new PrivateFileDownloadRequest(fileId));}
    private boolean registerPendingAuth(CompletableFuture<?> future,Class<?> responseType){synchronized(authLock){if(pendingAuth!=null&&!pendingAuth.isDone()){future.completeExceptionally(new IllegalStateException("Another authentication request is in progress."));return false;}if(!running){future.completeExceptionally(new IOException("Not connected."));return false;}pendingAuth=future;pendingAuthType=responseType;return true;}}
    private void clearPendingAuth(CompletableFuture<?> future){synchronized(authLock){if(pendingAuth==future){pendingAuth=null;pendingAuthType=null;}}}
    private void failPendingAuth(Throwable error){CompletableFuture<?> future;synchronized(authLock){future=pendingAuth;pendingAuth=null;pendingAuthType=null;}if(future!=null&&!future.isDone())future.completeExceptionally(error);}
    private void handleAuthResponse(Envelope envelope){CompletableFuture<?> future;Class<?> responseType;synchronized(authLock){future=pendingAuth;responseType=pendingAuthType;pendingAuth=null;pendingAuthType=null;}if(future==null||responseType==null){safeEvent(envelope);return;}if(envelope.getType()==MessageType.S2C_LOGIN_FAILED||envelope.getType()==MessageType.S2C_REGISTER_FAILED){AuthFailedResponse error=codec.unwrap(envelope,AuthFailedResponse.class);future.completeExceptionally(new IllegalStateException(error==null?"Authentication request failed.":error.getReason()));return;}Object response=codec.unwrap(envelope,responseType);if(response==null){future.completeExceptionally(new IOException("Invalid authentication response."));return;}complete(future,response);}
    @SuppressWarnings("unchecked") private static <T> void complete(CompletableFuture<?> future,Object value){((CompletableFuture<T>)future).complete((T)value);}
    private void readLoop(){try{while(running){Envelope envelope=codec.read(in);MessageType type=envelope.getType();if(type==MessageType.S2C_LOGIN_SUCCESS||type==MessageType.S2C_LOGIN_FAILED||type==MessageType.S2C_REGISTER_SUCCESS||type==MessageType.S2C_REGISTER_FAILED)handleAuthResponse(envelope);else safeEvent(envelope);}}catch(IOException e){if(running){failPendingAuth(new IOException("Connection lost while waiting for authentication response.",e));safeEvent(codec.wrap(MessageType.S2C_ERROR,new AuthFailedResponse("Connection lost: "+(e.getMessage()==null?"network error":e.getMessage()))));}}catch(RuntimeException e){if(running){failPendingAuth(new IOException("Client protocol processing failed.",e));safeEvent(codec.wrap(MessageType.S2C_ERROR,new AuthFailedResponse("Client protocol error.")));}}finally{boolean wasRunning=running;running=false;failPendingAuth(new IOException("Connection closed."));if(wasRunning)notifyConnectionState(false);}}
    private void safeEvent(Envelope envelope){try{eventListener.accept(envelope);}catch(RuntimeException ignored){}}
    private void notifyConnectionState(boolean connected){try{connectionListener.accept(connected);}catch(RuntimeException ignored){}}
    public synchronized void send(MessageType type,Object payload)throws IOException{if(!running||out==null)throw new IOException("Not connected.");codec.write(out,codec.wrap(type,payload));}
    public CompletableFuture<Void> sendAsync(MessageType type,Object payload){return CompletableFuture.runAsync(()->{try{send(type,payload);}catch(IOException e){throw new CompletionException(e);}});}
    public boolean isConnected(){return running;}
    @Override public synchronized void close(){boolean wasRunning=running;running=false;failPendingAuth(new IOException("Connection closed."));if(socket!=null)try{socket.close();}catch(IOException ignored){}socket=null;in=null;out=null;if(wasRunning)notifyConnectionState(false);}
}
