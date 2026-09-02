package com.chatapp.client;

import com.chatapp.model.dto.AuthDTOs.LoginSuccessResponse;
import com.chatapp.model.dto.AuthDTOs.RegisterSuccessResponse;
import com.chatapp.model.dto.ChatDTOs.PrivateMessageEvent;
import com.chatapp.model.dto.ChatDTOs.PrivateMessageRequest;
import com.chatapp.model.dto.ChatDTOs.UserListResponse;
import com.chatapp.model.dto.ChatDTOs.UserSummary;
import com.chatapp.model.dto.GroupDTOs.CreateGroupRequest;
import com.chatapp.model.dto.GroupDTOs.GroupJoinRequest;
import com.chatapp.model.dto.GroupDTOs.GroupListResponse;
import com.chatapp.model.dto.GroupDTOs.GroupMessageEvent;
import com.chatapp.model.dto.GroupDTOs.GroupMessageRequest;
import com.chatapp.model.dto.GroupDTOs.GroupSummary;
import com.chatapp.socket.protocol.Envelope;
import com.chatapp.socket.protocol.MessageType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Desktop JavaFX client for the real-time chat application. */
public final class ChatClientApp extends Application {
    private final ChatClientConnection connection = new ChatClientConnection(this::onEnvelope);
    private final ObservableList<UserSummary> users = FXCollections.observableArrayList();
    private final ObservableList<GroupSummary> groups = FXCollections.observableArrayList();
    private final ListView<String> conversation = new ListView<>();
    private ListView<UserSummary> userList;
    private ListView<GroupSummary> groupList;
    private TextField messageInput;
    private Label chatTitle;
    private Label statusLabel;
    private UserSummary selectedUser;
    private GroupSummary selectedGroup;
    private LoginSuccessResponse session;

    private final String host = System.getProperty("chatapp.server.host", "localhost");
    private final int port = Integer.getInteger("chatapp.server.port", 5050);

    @Override
    public void start(Stage stage) {
        stage.setTitle("Real-Time Chat");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(new Scene(buildLoginView(stage), 980, 680));
        stage.show();
    }

    private Parent buildLoginView(Stage stage) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Sign in", buildLoginForm(stage)));
        tabs.getTabs().add(new Tab("Create account", buildRegisterForm()));

        VBox shell = new VBox(18, brandHeader(), tabs, connectionHint());
        shell.setAlignment(Pos.CENTER);
        shell.setPadding(new Insets(48, 120, 48, 120));
        shell.getStyleClass().add("auth-shell");
        return shell;
    }

    private VBox brandHeader() {
        Label title = new Label("Real-Time Chat");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Secure, low-latency messaging over a Java TCP protocol");
        subtitle.getStyleClass().add("brand-subtitle");
        return new VBox(5, title, subtitle);
    }

    private VBox connectionHint() {
        Label label = new Label("Server: " + host + ":" + port);
        label.getStyleClass().add("muted");
        return new VBox(label);
    }

    private VBox buildLoginForm(Stage stage) {
        TextField identity = new TextField();
        identity.setPromptText("Username or email");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Button login = primaryButton("Sign in");
        Label feedback = new Label();
        feedback.getStyleClass().add("error");

        login.setOnAction(e -> {
            if (identity.getText().isBlank() || password.getText().isBlank()) {
                feedback.setText("Enter your username/email and password.");
                return;
            }
            login.setDisable(true);
            feedback.setText("Connecting…");
            CompletableSupport.run(() -> {
                ensureConnected();
                return connection.login(identity.getText().trim(), password.getText());
            }, result -> {
                session = result;
                stage.setScene(new Scene(buildDashboard(stage), 1180, 760));
                requestInitialData();
            }, error -> {
                feedback.setText(message(error));
                login.setDisable(false);
            });
        });
        password.setOnAction(e -> login.fire());
        return form(identity, password, login, feedback);
    }

    private VBox buildRegisterForm() {
        TextField username = new TextField(); username.setPromptText("Username");
        TextField email = new TextField(); email.setPromptText("Email");
        PasswordField password = new PasswordField(); password.setPromptText("Password");
        PasswordField confirm = new PasswordField(); confirm.setPromptText("Confirm password");
        Button register = primaryButton("Create account");
        Label feedback = new Label(); feedback.getStyleClass().add("error");

        register.setOnAction(e -> {
            if (username.getText().isBlank() || email.getText().isBlank()
                    || password.getText().isBlank() || confirm.getText().isBlank()) {
                feedback.setText("Complete every field."); return;
            }
            register.setDisable(true); feedback.setText("Creating account…");
            CompletableSupport.run(() -> {
                ensureConnected();
                return connection.register(username.getText().trim(), email.getText().trim(), password.getText(), confirm.getText());
            }, result -> feedback.setText("Account created for " + result.getUsername() + ". You can now sign in."),
                    error -> { feedback.setText(message(error)); register.setDisable(false); });
        });
        return form(username, email, password, confirm, register, feedback);
    }

    private VBox form(Control... controls) {
        VBox box = new VBox(12, controls);
        box.setMaxWidth(520);
        box.setPadding(new Insets(24));
        box.getStyleClass().add("auth-card");
        return box;
    }

    private BorderPane buildDashboard(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dashboard");
        root.setTop(buildTopBar(stage));
        root.setLeft(buildSidebar());
        root.setCenter(buildChatPane());
        return root;
    }

    private HBox buildTopBar(Stage stage) {
        Label title = new Label("Real-Time Chat"); title.getStyleClass().add("top-title");
        Label account = new Label(session.getUsername()); account.getStyleClass().add("account");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLabel = new Label("● Connected"); statusLabel.getStyleClass().add("connected");
        Button logout = new Button("Sign out");
        logout.setOnAction(e -> {
            try { connection.send(MessageType.C2S_LOGOUT, null); } catch (IOException ignored) { }
            connection.close();
            stage.setScene(new Scene(buildLoginView(stage), 980, 680));
        });
        HBox bar = new HBox(14, title, spacer, statusLabel, account, logout);
        bar.setAlignment(Pos.CENTER_LEFT); bar.setPadding(new Insets(14, 18, 14, 18));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private VBox buildSidebar() {
        userList = new ListView<>(users);
        userList.setPlaceholder(new Label("No other users online"));
        userList.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(UserSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getUsername() + "  •  " + item.getStatus());
            }
        });
        userList.setOnMouseClicked(e -> {
            selectedUser = userList.getSelectionModel().getSelectedItem();
            if (selectedUser != null) {
                selectedGroup = null; chatTitle.setText(selectedUser.getUsername()); conversation.getItems().clear();
                try { connection.send(MessageType.C2S_REQUEST_PRIVATE_HISTORY,
                        new com.chatapp.model.dto.ChatDTOs.PrivateHistoryRequest(selectedUser.getUserId(), 100, 0)); }
                catch (IOException ex) { showSystem(ex.getMessage()); }
            }
        });

        groupList = new ListView<>(groups);
        groupList.setPlaceholder(new Label("No groups yet"));
        groupList.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(GroupSummary item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "# " + item.getName() + "  •  " + item.getMemberCount());
            }
        });
        groupList.setOnMouseClicked(e -> {
            selectedGroup = groupList.getSelectionModel().getSelectedItem();
            if (selectedGroup != null) {
                selectedUser = null; chatTitle.setText("# " + selectedGroup.getName()); conversation.getItems().clear();
                try { connection.send(MessageType.C2S_REQUEST_GROUP_HISTORY,
                        new com.chatapp.model.dto.GroupDTOs.GroupHistoryRequest(selectedGroup.getGroupId(), 100, 0)); }
                catch (IOException ex) { showSystem(ex.getMessage()); }
            }
        });

        Button create = new Button("+ New group");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> createGroup());
        Button join = new Button("Join by ID");
        join.setMaxWidth(Double.MAX_VALUE);
        join.setOnAction(e -> joinGroup());
        Label people = sectionLabel("PEOPLE");
        Label group = sectionLabel("GROUPS");
        VBox pane = new VBox(8, people, userList, group, groupList, create, join);
        VBox.setVgrow(userList, Priority.ALWAYS); VBox.setVgrow(groupList, Priority.ALWAYS);
        pane.setPrefWidth(270); pane.setPadding(new Insets(14)); pane.getStyleClass().add("sidebar");
        return pane;
    }

    private VBox buildChatPane() {
        chatTitle = new Label("Select a conversation"); chatTitle.getStyleClass().add("chat-title");
        Label hint = new Label("Messages are persisted by the server"); hint.getStyleClass().add("muted");
        VBox header = new VBox(3, chatTitle, hint); header.setPadding(new Insets(18));
        messageInput = new TextField(); messageInput.setPromptText("Write a message…");
        Button send = primaryButton("Send"); send.setOnAction(e -> sendMessage()); messageInput.setOnAction(e -> send.fire());
        HBox composer = new HBox(10, messageInput, send); HBox.setHgrow(messageInput, Priority.ALWAYS);
        composer.setPadding(new Insets(14));
        VBox pane = new VBox(header, conversation, composer); VBox.setVgrow(conversation, Priority.ALWAYS);
        pane.getStyleClass().add("chat-pane");
        return pane;
    }

    private void requestInitialData() {
        try {
            connection.send(MessageType.C2S_REQUEST_USER_LIST, null);
            connection.send(MessageType.C2S_REQUEST_GROUP_LIST, null);
        } catch (IOException e) { showSystem(e.getMessage()); }
    }

    private void sendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;
        try {
            if (selectedUser != null) connection.send(MessageType.C2S_PRIVATE_MESSAGE,
                    new PrivateMessageRequest(selectedUser.getUserId(), text));
            else if (selectedGroup != null) connection.send(MessageType.C2S_GROUP_MESSAGE,
                    new GroupMessageRequest(selectedGroup.getGroupId(), text));
            else { showSystem("Select a person or group first."); return; }
            messageInput.clear();
        } catch (IOException e) { showSystem(e.getMessage()); }
    }

    private void createGroup() {
        TextInputDialog dialog = new TextInputDialog(); dialog.setTitle("New group"); dialog.setHeaderText("Create a group"); dialog.setContentText("Group name:");
        Optional<String> value = dialog.showAndWait();
        value.map(String::trim).filter(s -> !s.isEmpty()).ifPresent(name -> {
            try { connection.send(MessageType.C2S_CREATE_GROUP, new CreateGroupRequest(name)); }
            catch (IOException e) { showSystem(e.getMessage()); }
        });
    }

    private void joinGroup() {
        TextInputDialog dialog = new TextInputDialog(); dialog.setTitle("Join group"); dialog.setHeaderText("Enter the numeric group ID"); dialog.setContentText("Group ID:");
        Optional<String> value = dialog.showAndWait();
        value.ifPresent(id -> {
            try { connection.send(MessageType.C2S_JOIN_GROUP, new GroupJoinRequest(Integer.parseInt(id.trim()))); }
            catch (NumberFormatException e) { showSystem("Group ID must be a number."); }
            catch (IOException e) { showSystem(e.getMessage()); }
        });
    }

    private void onEnvelope(Envelope envelope) {
        Platform.runLater(() -> {
            switch (envelope.getType()) {
                case S2C_USER_LIST -> users.setAll(codec().unwrap(envelope, UserListResponse.class).getUsers());
                case S2C_USER_ONLINE, S2C_USER_OFFLINE -> requestUserList();
                case S2C_GROUP_LIST -> groups.setAll(codec().unwrap(envelope, GroupListResponse.class).getGroups());
                case S2C_GROUP_CREATED -> {
                    GroupSummary group = codec().unwrap(envelope, com.chatapp.model.dto.GroupDTOs.GroupCreatedResponse.class).getGroup();
                    groups.add(group); selectedGroup = group; selectedUser = null; chatTitle.setText("# " + group.getName());
                }
                case S2C_PRIVATE_HISTORY -> {
                    var response = codec().unwrap(envelope, com.chatapp.model.dto.ChatDTOs.PrivateHistoryResponse.class);
                    if (selectedUser != null && selectedUser.getUserId() == response.getOtherUserId()) {
                        conversation.getItems().clear(); response.getMessages().forEach(this::addPrivateMessage);
                    }
                }
                case S2C_PRIVATE_MESSAGE -> addPrivateMessage(codec().unwrap(envelope, PrivateMessageEvent.class));
                case S2C_GROUP_HISTORY -> {
                    var response = codec().unwrap(envelope, com.chatapp.model.dto.GroupDTOs.GroupHistoryResponse.class);
                    if (selectedGroup != null && selectedGroup.getGroupId() == response.getGroupId()) {
                        conversation.getItems().clear(); response.getMessages().forEach(this::addGroupMessage);
                    }
                }
                case S2C_GROUP_MESSAGE -> addGroupMessage(codec().unwrap(envelope, GroupMessageEvent.class));
                case S2C_ERROR -> showSystem("Connection error");
                default -> { }
            }
        });
    }

    private void addPrivateMessage(PrivateMessageEvent event) {
        if (selectedUser == null || (event.getSenderId() != selectedUser.getUserId() && event.getReceiverId() != selectedUser.getUserId())) return;
        conversation.getItems().add((event.getSenderId() == session.getUserId() ? "You" : selectedUser.getUsername()) + ": " + event.getMessage());
        conversation.scrollTo(conversation.getItems().size() - 1);
    }

    private void addGroupMessage(GroupMessageEvent event) {
        if (selectedGroup == null || event.getGroupId() != selectedGroup.getGroupId()) return;
        conversation.getItems().add((event.getSenderId() == session.getUserId() ? "You" : event.getSenderUsername()) + ": " + event.getMessage());
        conversation.scrollTo(conversation.getItems().size() - 1);
    }

    private void requestUserList() {
        try { connection.send(MessageType.C2S_REQUEST_USER_LIST, null); } catch (IOException ignored) { }
    }

    private com.chatapp.socket.protocol.MessageCodec codec() { return new com.chatapp.socket.protocol.MessageCodec(); }
    private void showSystem(String text) { conversation.getItems().add("System: " + text); }

    private void ensureConnected() throws IOException {
        if (!connection.isConnected()) connection.connect(host, port);
    }

    private static String message(Throwable error) {
        Throwable cause = error;
        if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "Request failed." : cause.getMessage();
    }

    private static Label sectionLabel(String text) { Label l = new Label(text); l.getStyleClass().add("section-label"); return l; }
    private static Button primaryButton(String text) { Button b = new Button(text); b.setDefaultButton(true); b.getStyleClass().add("primary"); return b; }

    @Override public void stop() { connection.close(); }

    /** Small JavaFX-safe bridge for running blocking network setup off the UI thread. */
    private static final class CompletableSupport {
        static <T> void run(ThrowingSupplier<java.util.concurrent.CompletableFuture<T>> work,
                            java.util.function.Consumer<T> success,
                            java.util.function.Consumer<Throwable> failure) {
            Thread.ofVirtual().start(() -> {
                try { T value = work.get().join(); Platform.runLater(() -> success.accept(value)); }
                catch (Throwable e) { Platform.runLater(() -> failure.accept(e)); }
            });
        }
        interface ThrowingSupplier<T> { T get() throws Exception; }
    }
}
