package com.devicehive.messages;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.devicehive.messages.bus.MessageBus;
import com.devicehive.model.User;

/**
 * Used in pair with {@link MessageType}. It is a container for important information. 
 * This information usually used in {@link MessageBus#subscribe(MessageType, MessageDetails)} method.
 * Not every field is necessary. Any can bu null or empty.
 * It is only business logic can decide what field it needs in current moment.
 *  
 * @author rroschin
 *
 */
public class MessageDetails {

    private List<Long> ids;
    private Timestamp timestamp;
    private String session;
    private Transport transport;
    private User user;

    public static MessageDetails create() {
        return new MessageDetails().transport(Transport.REST);
    }

    public MessageDetails ids(Long... ids) {
        this.ids = new ArrayList<>(Arrays.asList(ids));
        return this;
    }

    public MessageDetails ids(Collection<Long> ids) {
        this.ids = new ArrayList<>(ids);
        return this;
    }

    public MessageDetails timestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public MessageDetails session(String sessionId) {
        this.session = sessionId;
        return this;
    }

    public MessageDetails transport(Transport transport) {
        this.transport = transport;
        return this;
    }

    public MessageDetails user(User user) {
        this.user = user;
        return this;
    }

    public List<Long> ids() {
        return ids;
    }

    public Long id() {
        return ids != null && !ids.isEmpty() ? ids.get(0) : null;
    }

    public Timestamp timestamp() {
        return timestamp;
    }

    public String session() {
        return session;
    }

    public Transport transport() {
        return transport;
    }

    public User user() {
        return user;
    }

}
