package com.chatapp.server;

import com.chatapp.exception.AuthenticationException;
import com.chatapp.exception.ValidationException;
import com.chatapp.model.User;
import com.chatapp.model.dto.AttachmentDTOs.*;
import com.chatapp.model.dto.AuthDTOs.AuthFailedResponse;
import com.chatapp.model.dto.AuthDTOs.LoginRequest;
import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.AuthDTOs.RegisterRequest;
import com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse;
import com.chatapp.model.dto.ChatDTOs.*;
import com.chatapp.model.dto.GroupDTOs.*;
import com.chatapp.service.AttachmentService;
import com.chatapp.service.AuthenticationService;
import com.chatapp.service.ChatService;
import com.chatapp.service.GroupService;
import com.chatapp.service.LoginRateLimiter;
import com.chatapp.service.MessageSearchService;
import com.chatapp.service.RequestRateLimiter;
import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Handles one client's connection lifecycle and protocol dispatch. */
public class ClientHandler implements Runnable {
    private static final Logger logger=LoggerFactory.getLogger(ClientHandler.class);
    private static final String RATE_LIMIT_FAILURE="Too many failed login attempts. Please try again later.";
    private static final String IP_RATE_LIMIT_FAILURE="Too many login attempts from this network. Please try again later.";
    private static final String REQUEST_RATE_LIMIT_FAILURE="Too many requests. Please slow down and try again.";
    private static final String UPLOAD_RATE_LIMIT_FAILURE="Too many file uploads. Please try again later.";
    private static final String REGISTRATION_RATE_LIMIT_FAILURE="Too many registration attempts. Please try again later.";
    private static final int MAX_PROTOCOL_ERRORS=5;
    private static final LoginRateLimiter LOGIN_RATE_LIMITER=new LoginRateLimiter();
    private static final RequestRateLimiter LOGIN_IP_RATE_LIMITER=new RequestRateLimiter(30,Duration.ofMinutes(1),10_000);
    private static final RequestRateLimiter REQUEST_RATE_LIMITER=new RequestRateLimiter(60,Duration.ofSeconds(10),10_000);
    private static final RequestRateLimiter UPLOAD_RATE_LIMITER=new RequestRateLimiter(6,Duration.ofMinutes(1),10_000);
    private static final RequestRateLimiter REGISTRATION_RATE_LIMITER=new RequestRateLimiter(5,Duration.ofMinutes(1),10_000);
    private static final RequestRateLimiter PING_RATE_LIMITER=new RequestRateLimiter(30,Duration.ofSeconds(10),10_000);
    private static final int OUTBOUND_QUEUE_CAPACITY=256;
    private final Socket socket; private final ChatServer server; private final AuthenticationService authService;
    private final ChatService chatService; private final GroupService groupService; private final MessageSearchService messageSearchService=new MessageSearchService();
    private final AttachmentService attachmentService=new AttachmentService();
    private final MessageCodec codec=new MessageCodec(); private DataInputStream in; private DataOutputStream out;
    private final BlockingQueue<OutboundMessage> outbound=new ArrayBlockingQueue<>(OUTBOUND_QUEUE_CAPACITY);
    private final AtomicBoolean closed=new AtomicBoolean(); private final AtomicInteger protocolErrors=new AtomicInteger();
    private volatile Thread writerThread; private volatile Thread sessionExpiryThread;
    private volatile int authenticatedUserId=-1; private volatile String authenticatedUsername; private volatile String sessionToken;
    private record OutboundMessage(MessageType type,Object payload) {}
    public ClientHandler(Socket socket,ChatServer server,AuthenticationService authService){this(socket,server,authService,new ChatService(),new GroupService());}
    public ClientHandler(Socket socket,ChatServer server,AuthenticationService authService,ChatService chatService){this(socket,server,authService,chatService,new GroupService());}
    public ClientHandler(Socket socket,ChatServer server,AuthenticationService authService,ChatService chatService,GroupService groupService){this.socket=socket;this.server=server;this.authService=authService;this.chatService=chatService;this.groupService=groupService;}
    @Override public void run(){try{in=new DataInputStream(socket.getInputStream());out=new DataOutputStream(socket.getOutputStream());startWriter();messageLoop();}catch(SocketTimeoutException e){if(!closed.get())logger.info("Authentication timeout for {}",socket.getRemoteSocketAddress());}catch(IOException e){if(!closed.get())logger.warn("I/O error on {}: {}",socket.getRemoteSocketAddress(),e.getMessage());}finally{cleanup();}}
    private void startWriter(){writerThread=Thread.startVirtualThread(()->{try{while(!closed.get()){OutboundMessage message=outbound.take();if(message.type()==null)continue;writeNow(message.type(),message.payload());}}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(IOException e){if(!closed.get())logger.warn("Output error on {}: {}",socket.getRemoteSocketAddress(),e.getMessage());cleanup();}});}
    private void messageLoop()throws IOException{while(!socket.isClosed()){final Envelope envelope;try{envelope=codec.read(in);}catch(EOFException e){logger.info("Client disconnected: {}",socket.getRemoteSocketAddress());return;}catch(SocketTimeoutException e){if(authenticatedUserId==-1)return;throw e;}catch(RuntimeException e){logger.warn("Invalid protocol message from {}; closing connection",socket.getRemoteSocketAddress());return;}if(envelope==null||envelope.getType()==null){sendError("Invalid message envelope.");continue;}try{dispatch(envelope);}catch(Exception e){logger.error("Error handling {} from {}",envelope.getType(),socket.getRemoteSocketAddress(),e);sendError("An internal error occurred processing your request.");}}}
    private void dispatch(Envelope envelope)throws Exception{switch(envelope.getType()){
        case PING->{if(PING_RATE_LIMITER.allow(buildAddressRateKey()))send(MessageType.PONG,null);}
        case C2S_REGISTER->handleRegister(envelope); case C2S_LOGIN->handleLogin(envelope); case C2S_LOGOUT->handleLogout();
        case C2S_REQUEST_USER_LIST->requireAuth(()->send(MessageType.S2C_USER_LIST,new UserListResponse(chatService.listUsers(authenticatedUserId))));
        case C2S_PRIVATE_MESSAGE->requireAuth(()->handlePrivateMessage(envelope)); case C2S_REQUEST_PRIVATE_HISTORY->requireAuth(()->handlePrivateHistory(envelope));
        case C2S_SEARCH_PRIVATE_MESSAGES->requireAuth(()->handlePrivateSearch(envelope)); case C2S_MESSAGE_READ->requireAuth(()->handleMessageRead(envelope));
        case C2S_TYPING_START->requireAuth(()->handleTyping(envelope,MessageType.S2C_TYPING_START)); case C2S_TYPING_STOP->requireAuth(()->handleTyping(envelope,MessageType.S2C_TYPING_STOP));
        case C2S_UPLOAD_PRIVATE_FILE->requireAuth(()->handleFileUpload(envelope)); case C2S_DOWNLOAD_PRIVATE_FILE->requireAuth(()->handleFileDownload(envelope));
        case C2S_CREATE_GROUP->requireAuth(()->handleCreateGroup(envelope)); case C2S_JOIN_GROUP->requireAuth(()->handleJoinGroup(envelope)); case C2S_LEAVE_GROUP->requireAuth(()->handleLeaveGroup(envelope));
        case C2S_GROUP_MESSAGE->requireAuth(()->handleGroupMessage(envelope)); case C2S_REQUEST_GROUP_LIST->requireAuth(()->send(MessageType.S2C_GROUP_LIST,new GroupListResponse(groupService.list(authenticatedUserId)))); case C2S_REQUEST_GROUP_HISTORY->requireAuth(()->handleGroupHistory(envelope));
        default->sendError("Unsupported message type: "+envelope.getType());}}
    @FunctionalInterface private interface HandlerAction{void run()throws Exception;}
    private void requireAuth(HandlerAction action)throws Exception{if(authenticatedUserId==-1){sendError("You must log in before sending this message type.");return;}if(!REQUEST_RATE_LIMITER.allow("user:"+authenticatedUserId)){sendError(REQUEST_RATE_LIMIT_FAILURE);return;}if(!isSessionCurrent()){return;}action.run();}
    private boolean isSessionCurrent(){try{if(authService.validateSession(sessionToken)!=authenticatedUserId){closeConnection();return false;}return true;}catch(AuthenticationException e){logger.info("Session expired for user {}; closing connection",authenticatedUserId);closeConnection();return false;}}
    private void startSessionExpiryWatcher(LocalDateTime expiresAt,int userId,String token){Thread watcher=Thread.startVirtualThread(()->{try{long delayMillis=Math.max(1,ChronoUnit.MILLIS.between(LocalDateTime.now(),expiresAt));Thread.sleep(delayMillis);if(authenticatedUserId==userId&&token.equals(sessionToken)&&!closed.get()){logger.info("Session expired for user {}; closing connection",userId);closeConnection();}}catch(InterruptedException e){Thread.currentThread().interrupt();}});sessionExpiryThread=watcher;}
    private void handleRegister(Envelope envelope)throws IOException{if(!REGISTRATION_RATE_LIMITER.allow(buildAddressRateKey())){send(MessageType.S2C_REGISTER_FAILED,new AuthFailedResponse(REGISTRATION_RATE_LIMIT_FAILURE));return;}RegisterRequest req=codec.unwrap(envelope,RegisterRequest.class);if(req==null){sendError("Invalid registration request.");return;}try{User created=authService.register(req.getUsername(),req.getEmail(),req.getPassword(),req.getConfirmPassword());send(MessageType.S2C_REGISTER_SUCCESS,new RegisterSuccessResponse(created.getId(),created.getUsername()));}catch(ValidationException e){send(MessageType.S2C_REGISTER_FAILED,new AuthFailedResponse(e.getMessage()));}}
    private void handleLogin(Envelope envelope)throws IOException{if(authenticatedUserId!=-1){sendError("This connection is already authenticated.");return;}LoginRequest req=codec.unwrap(envelope,LoginRequest.class);if(req==null){sendError("Invalid login request.");return;}String addressRateKey=buildAddressRateKey();if(!LOGIN_IP_RATE_LIMITER.allow(addressRateKey)){send(MessageType.S2C_LOGIN_FAILED,new AuthFailedResponse(IP_RATE_LIMIT_FAILURE));return;}String identifier=req.getUsernameOrEmail();String rateKey=buildRateKey(identifier);if(!LOGIN_RATE_LIMITER.allow(rateKey)){send(MessageType.S2C_LOGIN_FAILED,new AuthFailedResponse(RATE_LIMIT_FAILURE));return;}try{AuthenticationService.LoginResult result=authService.login(identifier,req.getPassword());authenticatedUserId=result.user().getId();authenticatedUsername=result.user().getUsername();sessionToken=result.sessionToken();if(!server.registerClient(authenticatedUserId,this)){authenticatedUserId=-1;authenticatedUsername=null;authService.logout(result.sessionToken());send(MessageType.S2C_LOGIN_FAILED,new AuthFailedResponse("This account is already connected."));return;}socket.setSoTimeout(0);startSessionExpiryWatcher(result.expiresAt(),result.user().getId(),result.sessionToken());LOGIN_RATE_LIMITER.recordSuccess(rateKey);send(MessageType.S2C_LOGIN_SUCCESS,new LoginSuccessResponse(result.user().getId(),result.user().getUsername(),result.user().getRole().name(),result.sessionToken()));logger.info("User authenticated from {}",socket.getRemoteSocketAddress());}catch(AuthenticationException e){LOGIN_RATE_LIMITER.recordFailure(rateKey);send(MessageType.S2C_LOGIN_FAILED,new AuthFailedResponse(e.getMessage()));}}
    private String buildAddressRateKey(){String address=socket.getInetAddress()==null?"unknown":socket.getInetAddress().getHostAddress();return "ip:"+address;}
    private String buildRateKey(String identifier){return buildAddressRateKey()+"|"+(identifier==null?"":identifier.strip().toLowerCase(Locale.ROOT));}
    public String getUsername(){return authenticatedUsername;}
    private void handlePrivateMessage(Envelope envelope)throws IOException,ValidationException{PrivateMessageRequest req=codec.unwrap(envelope,PrivateMessageRequest.class);if(req==null){sendError("Invalid private message request.");return;}PrivateMessageEvent event=chatService.sendPrivateMessage(authenticatedUserId,req.getReceiverId(),req.getMessage());ClientHandler recipient=server.getHandler(event.getReceiverId());if(recipient!=null){chatService.markDelivered(event.getReceiverId(),event.getMessageId());event.setStatus("DELIVERED");recipient.sendAsync(MessageType.S2C_PRIVATE_MESSAGE,event);send(MessageType.S2C_MESSAGE_DELIVERED,event);}else send(MessageType.S2C_PRIVATE_MESSAGE,event);}
    private void handlePrivateHistory(Envelope envelope)throws IOException,ValidationException{PrivateHistoryRequest req=codec.unwrap(envelope,PrivateHistoryRequest.class);if(req==null){sendError("Invalid history request.");return;}send(MessageType.S2C_PRIVATE_HISTORY,new PrivateHistoryResponse(req.getOtherUserId(),chatService.history(authenticatedUserId,req.getOtherUserId(),req.getLimit(),req.getBeforeMessageId())));}
    private void handlePrivateSearch(Envelope envelope)throws IOException,ValidationException{SearchPrivateMessagesRequest req=codec.unwrap(envelope,SearchPrivateMessagesRequest.class);if(req==null){sendError("Invalid private search request.");return;}var results=messageSearchService.searchPrivate(authenticatedUserId,req.getQuery(),req.getLimit()).stream().map(r->new PrivateSearchResult(r.messageId(),r.senderId(),r.senderUsername(),r.receiverId(),r.message(),r.sentAt())).toList();send(MessageType.S2C_PRIVATE_SEARCH_RESULTS,new PrivateSearchResultsResponse(req.getQuery().strip(),results));}
    private void handleFileUpload(Envelope envelope)throws IOException,ValidationException{if(!UPLOAD_RATE_LIMITER.allow("user:"+authenticatedUserId)){sendError(UPLOAD_RATE_LIMIT_FAILURE);return;}PrivateFileUploadRequest req=codec.unwrap(envelope,PrivateFileUploadRequest.class);if(req==null){sendError("Invalid file upload request.");return;}PrivateFileEvent event=attachmentService.upload(authenticatedUserId,req.getReceiverId(),req.getFileName(),req.getContentType(),req.getDataBase64(),authenticatedUsername);ClientHandler recipient=server.getHandler(req.getReceiverId());if(recipient!=null)recipient.sendAsync(MessageType.S2C_PRIVATE_FILE,event);send(MessageType.S2C_PRIVATE_FILE,event);}
    private void handleFileDownload(Envelope envelope)throws IOException,ValidationException{PrivateFileDownloadRequest req=codec.unwrap(envelope,PrivateFileDownloadRequest.class);if(req==null){sendError("Invalid file download request.");return;}var file=attachmentService.download(authenticatedUserId,req.getFileId());var m=file.metadata();send(MessageType.S2C_PRIVATE_FILE_DOWNLOAD,new PrivateFileDownloadResponse(m.id(),m.fileName(),m.contentType(),m.sizeBytes(),m.sha256(),file.dataBase64()));}
    private void handleMessageRead(Envelope envelope)throws IOException,ValidationException{MessageReadRequest req=codec.unwrap(envelope,MessageReadRequest.class);if(req==null){sendError("Invalid read receipt.");return;}if(!chatService.markRead(authenticatedUserId,req.getMessageId()))return;OptionalInt senderId=chatService.findMessageSender(authenticatedUserId,req.getMessageId());if(senderId.isPresent()){ClientHandler sender=server.getHandler(senderId.getAsInt());if(sender!=null)sender.sendAsync(MessageType.S2C_MESSAGE_READ,req);}}
    private void handleTyping(Envelope envelope,MessageType type)throws IOException{PrivateMessageRequest req=codec.unwrap(envelope,PrivateMessageRequest.class);if(req==null||req.getReceiverId()<=0||req.getReceiverId()==authenticatedUserId||!chatService.userExists(req.getReceiverId())){sendError("Invalid typing recipient.");return;}ClientHandler recipient=server.getHandler(req.getReceiverId());if(recipient!=null)recipient.sendAsync(type,new TypingEvent(authenticatedUserId,authenticatedUsername,req.getReceiverId()));}
    private void handleCreateGroup(Envelope envelope)throws IOException,ValidationException{CreateGroupRequest req=codec.unwrap(envelope,CreateGroupRequest.class);if(req==null){sendError("Invalid group creation request.");return;}GroupSummary group=groupService.create(authenticatedUserId,req.getName());send(MessageType.S2C_GROUP_CREATED,new GroupCreatedResponse(group));}
    private void handleJoinGroup(Envelope envelope)throws IOException,ValidationException{GroupJoinRequest req=codec.unwrap(envelope,GroupJoinRequest.class);if(req==null){sendError("Invalid group join request.");return;}groupService.join(authenticatedUserId,req.getGroupId());send(MessageType.S2C_GROUP_LIST,new GroupListResponse(groupService.list(authenticatedUserId)));}
    private void handleLeaveGroup(Envelope envelope)throws IOException,ValidationException{GroupJoinRequest req=codec.unwrap(envelope,GroupJoinRequest.class);if(req==null){sendError("Invalid group leave request.");return;}groupService.leave(authenticatedUserId,req.getGroupId());send(MessageType.S2C_GROUP_LIST,new GroupListResponse(groupService.list(authenticatedUserId)));}
    private void handleGroupMessage(Envelope envelope)throws IOException,ValidationException{GroupMessageRequest req=codec.unwrap(envelope,GroupMessageRequest.class);if(req==null){sendError("Invalid group message request.");return;}GroupMessageEvent event=groupService.sendMessage(authenticatedUserId,authenticatedUsername,req.getGroupId(),req.getMessage());for(int memberId:groupService.members(req.getGroupId())){ClientHandler member=server.getHandler(memberId);if(member!=null)member.sendAsync(MessageType.S2C_GROUP_MESSAGE,event);}}
    private void handleGroupHistory(Envelope envelope)throws IOException,ValidationException{GroupHistoryRequest req=codec.unwrap(envelope,GroupHistoryRequest.class);if(req==null){sendError("Invalid group history request.");return;}send(MessageType.S2C_GROUP_HISTORY,new GroupHistoryResponse(req.getGroupId(),groupService.history(authenticatedUserId,req.getGroupId(),req.getLimit(),req.getBeforeMessageId())));}
    void send(MessageType type,Object payload)throws IOException{if(closed.get())throw new IOException("Client connection is closed");if(!outbound.offer(new OutboundMessage(type,payload)))throw new IOException("Client outbound queue is full");}
    void sendAsync(MessageType type,Object payload){if(closed.get()||!outbound.offer(new OutboundMessage(type,payload))){logger.warn("Outbound queue full or client closed for user {}; closing connection",authenticatedUserId);cleanup();}}
    private void writeNow(MessageType type,Object payload)throws IOException{if(out==null)throw new IOException("Client output stream is not initialized");codec.write(out,codec.wrap(type,payload));}
    private void sendError(String message){int errors=protocolErrors.incrementAndGet();try{send(MessageType.S2C_ERROR,new AuthFailedResponse(message));}catch(IOException e){logger.debug("Unable to send error response to {}",socket.getRemoteSocketAddress());return;}if(errors>=MAX_PROTOCOL_ERRORS){logger.info("Protocol error budget exhausted for {}; closing connection",socket.getRemoteSocketAddress());closeConnection();}}
    private void handleLogout(){if(sessionToken!=null)authService.logout(sessionToken);closeConnection();}
    private void cleanup(){if(!closed.compareAndSet(false,true))return;Thread watcher=sessionExpiryThread;if(watcher!=null)watcher.interrupt();if(authenticatedUserId!=-1){authService.logout(sessionToken);server.deregisterClient(authenticatedUserId,this);}server.handlerClosed(this);try{socket.close();}catch(IOException ignored){}Thread writer=writerThread;if(writer!=null)writer.interrupt();outbound.clear();}
    void closeConnection(){try{socket.close();}catch(IOException ignored){}cleanup();}
}
