package com.chatapp.client;

import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.ChatDTOs.*;
import com.chatapp.model.dto.GroupDTOs.*;
import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageCodec;
import com.chatapp.socket.protocol.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Professional JavaFX desktop client for the real-time chat application. */
public final class ChatClientApp extends Application {
    private final ChatClientConnection connection = new ChatClientConnection(this::onEnvelope);
    private final MessageCodec codec = new MessageCodec();
    private final ObservableList<UserSummary> users = FXCollections.observableArrayList();
    private final ObservableList<GroupSummary> groups = FXCollections.observableArrayList();
    private final ListView<String> messages = new ListView<>();
    private final Map<Long, Integer> renderedMessageRows = new HashMap<>();
    private final ScheduledExecutorService typingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-client-typing"); t.setDaemon(true); return t;
    });
    private UserSummary selectedUser;
    private GroupSummary selectedGroup;
    private LoginSuccessResponse session;
    private Label conversationTitle;
    private Label typingLabel;
    private TextField composer;
    private Label connectionStatus;
    private ScheduledFuture<?> typingStopTask;
    private boolean typingActive;
    private final String host = System.getProperty("chatapp.server.host", "localhost");
    private final int port = Integer.getInteger("chatapp.server.port", 5050);

    @Override public void start(Stage stage) {
        stage.setTitle("Real-Time Chat"); stage.setMinWidth(960); stage.setMinHeight(640);
        stage.setScene(styledScene(loginView(stage), 980, 680)); stage.show();
    }

    private Scene styledScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        var css = getClass().getResource("/chat.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }

    private Parent loginView(Stage stage) {
        TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Sign in", loginForm(stage))); tabs.getTabs().add(new Tab("Create account", registerForm()));
        Label title = new Label("Real-Time Chat"); title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Secure messaging over a Java TCP protocol"); subtitle.getStyleClass().add("brand-subtitle");
        Label server = new Label("Server: " + host + ":" + port); server.getStyleClass().add("muted");
        VBox root = new VBox(18, new VBox(4, title, subtitle), tabs, server);
        root.setAlignment(Pos.CENTER); root.setPadding(new Insets(50,120,50,120)); root.getStyleClass().add("auth-shell"); return root;
    }

    private VBox loginForm(Stage stage) {
        TextField identity = new TextField(); identity.setPromptText("Username or email");
        PasswordField password = new PasswordField(); password.setPromptText("Password");
        Button submit = primary("Sign in"); Label feedback = new Label(); feedback.getStyleClass().add("error");
        Runnable action = () -> { if(identity.getText().isBlank()||password.getText().isBlank()){feedback.setText("Enter both fields.");return;}
            submit.setDisable(true); feedback.setText("Connecting…");
            runAsync(() -> { ensureConnected(); return connection.login(identity.getText().trim(),password.getText()).join(); }, result -> {
                session=result; stage.setScene(styledScene(dashboard(stage),1180,760)); requestData();
            }, error -> { feedback.setText(errorText(error)); submit.setDisable(false); }); };
        submit.setOnAction(e->action.run()); password.setOnAction(e->action.run()); return card(identity,password,submit,feedback);
    }

    private VBox registerForm() {
        TextField username=new TextField(); username.setPromptText("Username"); TextField email=new TextField(); email.setPromptText("Email");
        PasswordField password=new PasswordField(); password.setPromptText("Password"); PasswordField confirm=new PasswordField(); confirm.setPromptText("Confirm password");
        Button submit=primary("Create account"); Label feedback=new Label(); feedback.getStyleClass().add("error");
        submit.setOnAction(e->{ if(username.getText().isBlank()||email.getText().isBlank()||password.getText().isBlank()||confirm.getText().isBlank()){feedback.setText("Complete every field.");return;}
            submit.setDisable(true); feedback.setText("Creating account…"); runAsync(()->{ensureConnected();return connection.register(username.getText().trim(),email.getText().trim(),password.getText(),confirm.getText()).join();},
                result->{feedback.getStyleClass().remove("error");feedback.setText("Account created. You can now sign in.");submit.setDisable(false);},error->{feedback.setText(errorText(error));submit.setDisable(false);}); });
        return card(username,email,password,confirm,submit,feedback);
    }

    private VBox card(Control... controls){VBox box=new VBox(12,controls);box.setMaxWidth(520);box.setPadding(new Insets(24));box.getStyleClass().add("auth-card");return box;}

    private BorderPane dashboard(Stage stage){BorderPane root=new BorderPane();root.getStyleClass().add("dashboard");root.setTop(topBar(stage));root.setLeft(sidebar());root.setCenter(chatPane());return root;}

    private HBox topBar(Stage stage){
        Label title=new Label("Real-Time Chat");title.getStyleClass().add("top-title"); Label account=new Label(session.getUsername());account.getStyleClass().add("account");
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);connectionStatus=new Label("● Connected");connectionStatus.getStyleClass().add("connected");
        Button logout=new Button("Sign out"); logout.setOnAction(e->{stopTyping();try{if(connection.isConnected())connection.send(MessageType.C2S_LOGOUT,null);}catch(IOException ignored){}finally{connection.close();}session=null;selectedUser=null;selectedGroup=null;stage.setScene(styledScene(loginView(stage),980,680));});
        HBox bar=new HBox(14,title,spacer,connectionStatus,account,logout);bar.setAlignment(Pos.CENTER_LEFT);bar.setPadding(new Insets(14,18,14,18));bar.getStyleClass().add("top-bar");return bar;
    }

    private VBox sidebar(){
        ListView<UserSummary> people=new ListView<>(users);people.setPlaceholder(new Label("No users online"));
        people.setCellFactory(v->new ListCell<>(){protected void updateItem(UserSummary u,boolean empty){super.updateItem(u,empty);setText(empty||u==null?null:u.getUsername()+"  •  "+u.getStatus());}});
        people.setOnMouseClicked(e->{UserSummary u=people.getSelectionModel().getSelectedItem();if(u!=null)openUser(u);});
        ListView<GroupSummary> groupView=new ListView<>(groups);groupView.setPlaceholder(new Label("No groups yet"));
        groupView.setCellFactory(v->new ListCell<>(){protected void updateItem(GroupSummary g,boolean empty){super.updateItem(uOrNull(g,empty),empty);setText(empty||g==null?null:"# "+g.getName()+"  •  "+g.getMemberCount());}});
        groupView.setOnMouseClicked(e->{GroupSummary g=groupView.getSelectionModel().getSelectedItem();if(g!=null)openGroup(g);});
        Button create=new Button("+ New group");create.setMaxWidth(Double.MAX_VALUE);create.setOnAction(e->createGroup());Button join=new Button("Join by ID");join.setMaxWidth(Double.MAX_VALUE);join.setOnAction(e->joinGroup());
        VBox box=new VBox(8,section("PEOPLE"),people,section("GROUPS"),groupView,create,join);VBox.setVgrow(people,Priority.ALWAYS);VBox.setVgrow(groupView,Priority.ALWAYS);box.setPrefWidth(270);box.setPadding(new Insets(14));box.getStyleClass().add("sidebar");return box;
    }

    private static GroupSummary uOrNull(GroupSummary value, boolean empty) { return empty ? null : value; }

    private VBox chatPane(){
        conversationTitle=new Label("Select a conversation");conversationTitle.getStyleClass().add("chat-title");
        typingLabel=new Label();typingLabel.getStyleClass().add("typing");typingLabel.setManaged(false);typingLabel.setVisible(false);
        Label hint=new Label("Messages are persisted by the server");hint.getStyleClass().add("muted");
        VBox header=new VBox(3,conversationTitle,typingLabel,hint);header.setPadding(new Insets(18));
        composer=new TextField();composer.setPromptText("Write a message…");composer.textProperty().addListener((obs,oldValue,newValue)->handleTypingInput(newValue));
        Button send=primary("Send");send.setOnAction(e->sendMessage());composer.setOnAction(e->send.fire());
        HBox input=new HBox(10,composer,send);HBox.setHgrow(composer,Priority.ALWAYS);input.setPadding(new Insets(14));
        VBox pane=new VBox(header,messages,input);VBox.setVgrow(messages,Priority.ALWAYS);pane.getStyleClass().add("chat-pane");return pane;
    }

    private void openUser(UserSummary u){stopTyping();selectedUser=u;selectedGroup=null;conversationTitle.setText(u.getUsername());clearConversation();try{connection.send(MessageType.C2S_REQUEST_PRIVATE_HISTORY,new PrivateHistoryRequest(u.getUserId(),100,0));}catch(IOException e){system(e.getMessage());}}
    private void openGroup(GroupSummary g){stopTyping();selectedGroup=g;selectedUser=null;conversationTitle.setText("# "+g.getName());clearConversation();try{connection.send(MessageType.C2S_REQUEST_GROUP_HISTORY,new GroupHistoryRequest(g.getGroupId(),100,0));}catch(IOException e){system(e.getMessage());}}
    private void clearConversation(){messages.getItems().clear();renderedMessageRows.clear();setTypingVisible(false);}

    private void sendMessage(){String text=composer.getText().trim();if(text.isEmpty())return;stopTyping();try{if(selectedUser!=null)connection.send(MessageType.C2S_PRIVATE_MESSAGE,new PrivateMessageRequest(selectedUser.getUserId(),text));else if(selectedGroup!=null)connection.send(MessageType.C2S_GROUP_MESSAGE,new GroupMessageRequest(selectedGroup.getGroupId(),text));else{system("Select a conversation first.");return;}composer.clear();}catch(IOException e){system(e.getMessage());setConnectionState(false);}}

    private void handleTypingInput(String value){
        if(selectedUser==null||session==null||!connection.isConnected())return;
        if(value==null||value.isBlank()){stopTyping();return;}
        try {
            if(!typingActive){connection.send(MessageType.C2S_TYPING_START,new PrivateMessageRequest(selectedUser.getUserId(),""));typingActive=true;}
            if(typingStopTask!=null)typingStopTask.cancel(false);
            typingStopTask=typingScheduler.schedule(this::stopTyping,900,TimeUnit.MILLISECONDS);
        } catch(IOException e){setConnectionState(false);}
    }

    private void stopTyping(){
        if(typingStopTask!=null){typingStopTask.cancel(false);typingStopTask=null;}
        if(typingActive&&selectedUser!=null&&connection.isConnected()){
            try{connection.send(MessageType.C2S_TYPING_STOP,new PrivateMessageRequest(selectedUser.getUserId(),""));}catch(IOException ignored){}
        }
        typingActive=false;
    }

    private void createGroup(){TextInputDialog d=new TextInputDialog();d.setTitle("New group");d.setHeaderText("Create a group");d.setContentText("Group name:");d.showAndWait().map(String::trim).filter(s->!s.isEmpty()).ifPresent(name->{try{connection.send(MessageType.C2S_CREATE_GROUP,new CreateGroupRequest(name));}catch(IOException e){system(e.getMessage());}});}
    private void joinGroup(){TextInputDialog d=new TextInputDialog();d.setTitle("Join group");d.setHeaderText("Enter numeric group ID");d.setContentText("Group ID:");d.showAndWait().ifPresent(id->{try{connection.send(MessageType.C2S_JOIN_GROUP,new GroupJoinRequest(Integer.parseInt(id.trim())));}catch(NumberFormatException e){system("Group ID must be a number.");}catch(IOException e){system(e.getMessage());}});}
    private void requestData(){try{connection.send(MessageType.C2S_REQUEST_USER_LIST,null);connection.send(MessageType.C2S_REQUEST_GROUP_LIST,null);}catch(IOException e){setConnectionState(false);}}

    private void onEnvelope(Envelope envelope){Platform.runLater(()->{try{switch(envelope.getType()){
        case S2C_USER_LIST->users.setAll(codec.unwrap(envelope,UserListResponse.class).getUsers());
        case S2C_USER_ONLINE,S2C_USER_OFFLINE->requestData();
        case S2C_GROUP_LIST->groups.setAll(codec.unwrap(envelope,GroupListResponse.class).getGroups());
        case S2C_GROUP_CREATED->{GroupSummary group=codec.unwrap(envelope,GroupCreatedResponse.class).getGroup();groups.removeIf(g->g.getGroupId()==group.getGroupId());groups.add(group);openGroup(group);}
        case S2C_PRIVATE_HISTORY->{PrivateHistoryResponse r=codec.unwrap(envelope,PrivateHistoryResponse.class);if(selectedUser!=null&&selectedUser.getUserId()==r.getOtherUserId()){clearConversation();r.getMessages().forEach(this::privateMessage);markHistoryRead(r);}}
        case S2C_PRIVATE_MESSAGE->privateMessage(codec.unwrap(envelope,PrivateMessageEvent.class));
        case S2C_MESSAGE_DELIVERED->updateMessageStatus(codec.unwrap(envelope,PrivateMessageEvent.class),"DELIVERED");
        case S2C_MESSAGE_READ->updateMessageRead(codec.unwrap(envelope,MessageReadRequest.class));
        case S2C_TYPING_START->{TypingEvent t=codec.unwrap(envelope,TypingEvent.class);if(selectedUser!=null&&t!=null&&t.getUserId()==selectedUser.getUserId())setTypingVisible(true);}
        case S2C_TYPING_STOP->{TypingEvent t=codec.unwrap(envelope,TypingEvent.class);if(selectedUser!=null&&t!=null&&t.getUserId()==selectedUser.getUserId())setTypingVisible(false);}
        case S2C_GROUP_HISTORY->{GroupHistoryResponse r=codec.unwrap(envelope,GroupHistoryResponse.class);if(selectedGroup!=null&&selectedGroup.getGroupId()==r.getGroupId()){clearConversation();r.getMessages().forEach(this::groupMessage);}}
        case S2C_GROUP_MESSAGE->groupMessage(codec.unwrap(envelope,GroupMessageEvent.class));
        case S2C_ERROR->{setConnectionState(connection.isConnected());system("Server rejected the request.");}
        default->{}}
    }catch(RuntimeException e){system("Received an invalid server response.");}});}

    private void markHistoryRead(PrivateHistoryResponse response){if(session==null||response.getMessages()==null)return;for(PrivateMessageEvent m:response.getMessages()){if(m.getReceiverId()==session.getUserId()&&m.getMessageId()>0)sendReadReceipt(m.getMessageId());}}
    private void sendReadReceipt(long messageId){try{connection.send(MessageType.C2S_MESSAGE_READ,new MessageReadRequest(messageId));}catch(IOException e){setConnectionState(false);}}

    private void privateMessage(PrivateMessageEvent m){
        if(m==null||selectedUser==null||session==null||(m.getSenderId()!=selectedUser.getUserId()&&m.getReceiverId()!=selectedUser.getUserId()))return;
        String sender=m.getSenderId()==session.getUserId()?"You":selectedUser.getUsername();String status=m.getSenderId()==session.getUserId()?statusSuffix(m.getStatus()):"";
        int row=messages.getItems().size();messages.getItems().add(sender+": "+m.getMessage()+status);if(m.getSenderId()==session.getUserId()&&m.getMessageId()>0)renderedMessageRows.put(m.getMessageId(),row);
        messages.scrollTo(row);if(m.getReceiverId()==session.getUserId()&&m.getSenderId()!=session.getUserId()&&m.getMessageId()>0)sendReadReceipt(m.getMessageId());
    }

    private void updateMessageStatus(PrivateMessageEvent event,String status){if(event==null||event.getMessageId()<=0)return;Integer row=renderedMessageRows.get(event.getMessageId());if(row==null)return;String current=messages.getItems().get(row);messages.getItems().set(row,stripStatus(current)+"  ✓ delivered");}
    private void updateMessageRead(MessageReadRequest request){if(request==null)return;Integer row=renderedMessageRows.get(request.getMessageId());if(row==null)return;String current=messages.getItems().get(row);messages.getItems().set(row,stripStatus(current)+"  ✓✓ read");}
    private static String stripStatus(String text){return text.replaceAll("\\s+✓{1,2} (?:delivered|read)$","");}
    private static String statusSuffix(String status){if("READ".equalsIgnoreCase(status))return "  ✓✓ read";if("DELIVERED".equalsIgnoreCase(status))return "  ✓ delivered";return "  ✓ sent";}

    private void groupMessage(GroupMessageEvent m){if(m==null||selectedGroup==null||session==null||m.getGroupId()!=selectedGroup.getGroupId())return;String sender=m.getSenderId()==session.getUserId()?"You":m.getSenderUsername();int row=messages.getItems().size();messages.getItems().add(sender+": "+m.getMessage());messages.scrollTo(row);}
    private void setTypingVisible(boolean visible){if(typingLabel==null)return;typingLabel.setVisible(visible);typingLabel.setManaged(visible);if(visible)typingLabel.setText(selectedUser==null?"Typing…":selectedUser.getUsername()+" is typing…");}
    private void setConnectionState(boolean connected){if(connectionStatus==null)return;connectionStatus.setText(connected?"● Connected":"● Disconnected");connectionStatus.getStyleClass().removeAll("connected","disconnected");connectionStatus.getStyleClass().add(connected?"connected":"disconnected");if(!connected)stopTyping();}
    private void system(String text){if(text!=null&&!text.isBlank())messages.getItems().add("System: "+text);}
    private void ensureConnected()throws IOException{if(!connection.isConnected())connection.connect(host,port);setConnectionState(true);}
    private static Label section(String text){Label l=new Label(text);l.getStyleClass().add("section-label");return l;}
    private static Button primary(String text){Button b=new Button(text);b.setDefaultButton(true);b.getStyleClass().add("primary");return b;}
    private static String errorText(Throwable t){Throwable c=t;while((c instanceof java.util.concurrent.CompletionException||c instanceof java.util.concurrent.ExecutionException)&&c.getCause()!=null)c=c.getCause();return c.getMessage()==null?"Request failed.":c.getMessage();}
    private static <T> void runAsync(Supplier<T> work,Consumer<T> success,Consumer<Throwable> failure){Thread.ofVirtual().start(()->{try{T value=work.get();Platform.runLater(()->success.accept(value));}catch(Throwable error){Platform.runLater(()->failure.accept(error));}});}
    private interface Supplier<T>{T get()throws Exception;}
    @Override public void stop(){stopTyping();typingScheduler.shutdownNow();connection.close();}
}
