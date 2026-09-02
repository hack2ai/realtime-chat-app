package com.chatapp.model.dto;

/** Server-to-client notification payload for lightweight UI feedback. */
public final class NotificationDTO {
    private NotificationDTO() {}

    public static class NotificationEvent {
        private String title;
        private String message;
        private String level;

        public NotificationEvent() {}

        public NotificationEvent(String title, String message, String level) {
            this.title = title;
            this.message = message;
            this.level = level;
        }

        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getLevel() { return level; }

        public void setTitle(String value) { title = value; }
        public void setMessage(String value) { message = value; }
        public void setLevel(String value) { level = value; }
    }
}
