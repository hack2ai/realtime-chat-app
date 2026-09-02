package com.chatapp.client;

import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.ChatDTOs.*;
import com.chatapp.model.dto.GroupDTOs.*;
import com.chatapp.socket.protocol.Envelope;
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
import java.util.Optional;
import java.util.function.Consumer;

/** Professional JavaFX desktop client for the real-time chat application. */
public final class ChatClientApp extends Application {
    private final ChatClientConnection connection = new ChatClientConnection(this::onEnvelope);
    private final ObservableList<UserSummary> users = FXCollections.observableArrayList();
    private final ObservableList<GroupSummary> groups = FXCollections.observableArrayList();
    private final ListView<String> messages = new ListView<>();
    private UserSummary selectedUser; private GroupSummary selectedGroup; private LoginSuccessResponse session;
    private Label conversationTitle; private TextField composer;
    private final String host = System.getProperty("chatapp.server.host", "localhost");
    private final int port = Integer.getInteger("chatapp.server.port", 5050);

    @Override public void start(Stage stage) {
        stage.setTitle("Real-Time Chat"); stage.setMinWidth(960); stage.setMinHeight(640);
        stage.setScene(new Scene(loginView(stage), 980, 680)); stage.show();
    }
    private Parent loginView(Stage stage) {
        TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Sign in", loginForm(stage))); tabs.getTabs().add(new Tab("Create account", registerForm()));
        Label title = new Label("Real-Time Chat"); title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Secure messaging over a Java TCP protocol"); subtitle.getStyleClass().add("brand-subtitle");
        Label server = new Label("Server: " + host + ":" + port); server.getStyleClass().add("muted");
        VBox root = new VBox(18, new VBox(4, title, subtitle), tabs, server); root.setAlignment(Pos.CENTER); root.setPadding(new Insets(50,120,50,120)); root.getStyleClass().add("auth-shell"); return root;
    }
    private VBox loginForm(Stage stage) {
        TextField identity = new TextField(); identity.setPromptText("Username or email"); PasswordField password = new PasswordField(); password.setPromptText("Password");
        Button submit = primary("Sign in"); Label feedback = new Label(); feedback.getStyleClass().add("error");
        Runnable action = () -> { if(identity.getText().isBlank()||password.getText().isBlank()){feedback.setText("Enter both fields.");return;} submit.setDisable(true); feedback.setText("Connecting…");
            runAsync(() -> { ensureConnected(); return connection.login(identity.getText().trim(), password.getText()).join(); }, result -> { session=result; stage.setScene(new Scene(dashboard(stage),1180,760)); requestData(); }, error -> {feedback.setText(errorText(error));submit.setDisable(false);}); };
        submit.setOnAction(e->action.run()); password.setOnAction(e->action.run()); return card(identity,password,submit,feedback);
    }
    private VBox registerForm() {
        TextField username=new TextField(); username.setPromptText("Username"); TextField email=new TextField(); email.setPromptText("Email"); PasswordField password=new PasswordField(); password.setPromptText("Password"); PasswordField confirm=new PasswordField(); confirm.setPromptText("Confirm password");
        Button submit=primary("Create account"); Label feedback=new Label(); feedback.getStyleClass().add("error");
        submit.setOnAction(e->{if(username.getText().isBlank()||email.getText().isBlank()||password.getText().isBlank()||confirm.getText().isBlank()){feedback.setText("Complete every field.");return;} submit.setDisable(true); feedback.setText("Creating account…");
            runAsync(() -> {ensureConnected(); return connection.register(username.getText().trim(),email.getText().trim(),password.getText(),confirm.getText()).join();}, result->{feedback.setText("Account created. You can now sign in.");submit.setDisable(false);}, error->{feedback.setText(errorText(error));submit.setDisable(false);});});
        return card(username,email,password,confirm,submit,feedback);
    }
    private VBox card(Control... controls){VBox box=new VBox(12,controls);box.setMaxWidth(520);box.setPadding(new Insets(24));box.getStyleClass().add("auth-card");return box;}
    private BorderPane dashboard(Stage stage){BorderPane root=new BorderPane();root.getStyleClass().add("dashboard");root.setTop(topBar(stage));root.setLeft(sidebar());root.setCenter(chatPane());return root;}
    private HBox topBar(Stage stage){Label title=new Label("Real-Time Chat");title.getStyleClass().add("top-title");Label account=new Label(session.getUsername());account.getStyleClass().add("account");Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);Label online=new Label("● Connected");online.getStyleClass().add("connected");Button logout=new Button("Sign out");logout.setOnAction(e->{try{connection.send(MessageType.C2S_LOGOUT,null);}catch(IOException ignored){}connection.close();stage.setScene(new Scene(loginView(stage),980,680));});HBox bar=new HBox(14,title,spacer,online,account,logout);bar.setAlignment(Pos.CENTER_LEFT);bar.setPadding(new Insets(14,18,14,18));bar.getStyleClass().add("top-bar");return bar;}
    private VBox sidebar(){ListView<UserSummary> people=new ListView<>(users);people.setPlaceholder(new Label("No users online"));people.setCellFactory(v->new ListCell<>(){protected void updateItem(UserSummary u,boolean empty){super.updateItem(u,empty);setText(empty||u==null?null:u.getUsername()+"  •  "+u.getStatus());}});people.setOnMouseClicked(e->{UserSummary u=people.getSelectionModel().getSelectedItem();if(u!=null)openUser(u);});
        ListView<GroupSummary> groupView=new ListView<>(groups);groupView.setPlaceholder(new Label("No groups yet"));groupView.setCellFactory(v->new ListCell<>(){protected void updateItem(GroupSummary g,boolean empty){super.updateItem(g,empty);setText(empty||g==null?null:"# "+g.getName()+"  •  "+g.getMemberCount());}});groupView.setOnMouseClicked(e->{GroupSummary g=groupView.getSelectionModel().getSelectedItem();if(g!=null)openGroup(g);});
        Button create=new Button("+ New group");create.setMaxWidth(Double.MAX_VALUE);create.setOnAction(e->createGroup());Button join=new Button("Join by ID");join.setMaxWidth(Double.MAX_VALUE);join.setOnAction(e->joinGroup());
        VBox box=new VBox(8,section("PEOPLE"),people,section("GROUPS"),groupView,create,join);VBox.setVgrow(people,Priority.ALWAYS);VBox.setVgrow(groupView,Priority.ALWAYS);box.setPrefWidth(270);box.setPadding(new Insets(14));box.getStyleClass().add("sidebar");return box;}
    private VBox chatPane(){conversationTitle=new Label("Select a conversation");conversationTitle.getStyleClass().add("chat-title");Label hint=new Label("Messages are persisted by the server");hint.getStyleClass().add("muted");VBox header=new VBox(3,conversationTitle,hint);header.setPadding(new Insets(18));composer=new TextField();composer.setPromptText("Write a message…");Button send=primary("Send");send.setOnAction(e->sendMessage());composer.setOnAction(e->send.fire());HBox input=new HBox(10,composer,send);HBox.setHgrow(composer,Priority.ALWAYS);input.setPadding(new Insets(14));VBox pane=new VBox(header,messages,input);VBox.setVgrow(messages,Priority.ALWAYS);pane.getStyleClass().add("chat-pane");return pane;}
    private void openUser(UserSummary u){selectedUser=u;selectedGroup=null;conversationTitle.setText(u.getUsername());messages.getItems().clear();try{connection.send(MessageType.C2S_REQUEST_PRIVATE_HISTORY,new PrivateHistoryRequest(u.getUserId(),100,0));}catch(IOException e){system(e.getMessage());}}
    private void openGroup(GroupSummary g){selectedGroup=g;selectedUser=null;conversationTitle.setText("# "+g.getName());messages.getItems().clear();try{connection.send(MessageType.C2S_REQUEST_GROUP_HISTORY,new GroupHistoryRequest(g.getGroupId(),100,0));}catch(IOException e){system(e.getMessage());}}
    private void sendMessage(){String text=composer.getText().trim();if(text.isEmpty())return;try{if(selectedUser!=null)connection.send(MessageType.C2S_PRIVATE_MESSAGE,new PrivateMessageRequest(selectedUser.getUserId(),text));else if(selectedGroup!=null)connection.send(MessageType.C2S_GROUP_MESSAGE,new GroupMessageRequest(selectedGroup.getGroupId(),text));else{system("Select a conversation first.");return;}composer.clear();}catch(IOException e){system(e.getMessage());}}
    private void createGroup(){TextInputDialog d=new TextInputDialog();d.setTitle("New group");d.setHeaderText("Create a group");d.setContentText("Group name:");Optional<String> v=d.showAndWait();v.map(String::trim).filter(s->!s.isEmpty()).ifPresent(n->{try{connection.send(MessageType.C2S_CREATE_GROUP,new CreateGroupRequest(n));}catch(IOException e){system(e.getMessage());}});}
    private void joinGroup(){TextInputDialog d=new TextInputDialog();d.setTitle("Join group");d.setHeaderText("Enter numeric group ID");d.setContentText("Group ID:");d.showAndWait().ifPresent(id->{try{connection.send(MessageType.C2S_JOIN_GROUP,new GroupJoinRequest(Integer.parseInt(id.trim())));}catch(NumberFormatException e){system("Group ID must be a number.");}catch(IOException e){system(e.getMessage());}});}
    private void requestData(){try{connection.send(MessageType.C2S_REQUEST_USER_LIST,null);connection.send(MessageType.C2S_REQUEST_GROUP_LIST,null);}catch(IOException e){system(e.getMessage());}}
    private void onEnvelope(Envelope e){Platform.runLater(()->{switch(e.getType()){
        case S2C_USER_LIST->users.setAll(codec().unwrap(e,UserListResponse.class).getUsers());
        case S2C_USER_ONLINE,S2C_USER_OFFLINE->{try{connection.send(MessageType.C2S_REQUEST_USER_LIST,null);}catch(IOException ignored){}}
        case S2C_GROUP_LIST->groups.setAll(codec().unwrap(e,GroupListResponse.class).getGroups());
        case S2C_GROUP_CREATED->{GroupSummary g=codec().unwrap(e,GroupCreatedResponse.class).getGroup();groups.add(g);openGroup(g);}
        case S2C_PRIVATE_HISTORY->{PrivateHistoryResponse r=codec().unwrap(e,PrivateHistoryResponse.class);if(selectedUser!=null&&selectedUser.getUserId()==r.getOtherUserId()){messages.getItems().clear();r.getMessages().forEach(this::privateMessage);}}
        case S2C_PRIVATE_MESSAGE->privateMessage(codec().unwrap(e,PrivateMessageEvent.class));
        case S2C_GROUP_HISTORY->{GroupHistoryResponse r=codec().unwrap(e,GroupHistoryResponse.class);if(selectedGroup!=null&&selectedGroup.getGroupId()==r.getGroupId()){messages.getItems().clear();r.getMessages().forEach(this::groupMessage);}}
        case S2C_GROUP_MESSAGE->groupMessage(codec().unwrap(e,GroupMessageEvent.class));
        case S2C_ERROR->system("Server error"); default->{}}
    });}
    private void privateMessage(PrivateMessageEvent m){if(selectedUser==null||(m.getSenderId()!=selectedUser.getUserId()&&m.getReceiverId()!=selectedUser.getUserId()))return;messages.getItems().add((m.getSenderId()==session.getUserId()?"You":selectedUser.getUsername())+": "+m.getMessage());messages.scrollTo(messages.getItems().size()-1);}
    private void groupMessage(GroupMessageEvent m){if(selectedGroup==null||m.getGroupId()!=selectedGroup.getGroupId())return;messages.getItems().add((m.getSenderId()==session.getUserId()?"You":m.getSenderUsername())+": "+m.getMessage());messages.scrollTo(messages.getItems().size()-1);}
    private void system(String text){messages.getItems().add("System: "+text);}
    private void ensureConnected()throws IOException{if(!connection.isConnected())connection.connect(host,port);}
    private MessageCodec codec(){return new MessageCodec();}
    private static Label section(String s){Label l=new Label(s);l.getStyleClass().add("section-label");return l;}
    private static Button primary(String s){Button b=new Button(s);b.setDefaultButton(true);b.getStyleClass().add("primary");return b;}
    private static String errorText(Throwable t){Throwable c=t;if(c instanceof java.util.concurrent.CompletionException&&c.getCause()!=null)c=c.getCause();return c.getMessage()==null?"Request failed.":c.getMessage();}
    private static <T> void runAsync(Supplier<T> work,Consumer<T> ok,Consumer<Throwable> fail){Thread.ofVirtual().start(()->{try{T v=work.get();Platform.runLater(()->ok.accept(v));}catch(Throwable e){Platform.runLater(()->fail.accept(e));}});}
    private interface Supplier<T>{T get()throws Exception;}
    @Override public void stop(){connection.close();}
}
