package com.devicehive.websockets.messagebus.local;

import com.devicehive.dao.DeviceDAO;
import com.devicehive.dao.UserDAO;
import com.devicehive.model.Device;
import com.devicehive.model.DeviceCommand;
import com.devicehive.model.DeviceNotification;
import com.devicehive.model.User;
import com.devicehive.websockets.json.GsonFactory;
import com.devicehive.websockets.json.strategies.CommandUpdateExclusionStrategy;
import com.devicehive.websockets.json.strategies.DeviceCommandInsertExclusionStrategy;
import com.devicehive.websockets.json.strategies.NotificationInsertRequestExclusionStrategy;
import com.devicehive.websockets.messagebus.local.subscriptions.dao.CommandSubscriptionDAO;
import com.devicehive.websockets.messagebus.local.subscriptions.dao.CommandUpdatesSubscriptionDAO;
import com.devicehive.websockets.messagebus.local.subscriptions.dao.NotificationSubscriptionDAO;
import com.devicehive.websockets.messagebus.local.subscriptions.model.CommandUpdatesSubscription;
import com.devicehive.websockets.messagebus.local.subscriptions.model.CommandsSubscription;
import com.devicehive.websockets.util.SingletonSessionMap;
import com.devicehive.websockets.util.WebsocketSession;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.Transactional;
import javax.websocket.Session;
import java.util.*;
import java.util.concurrent.locks.Lock;


@Singleton
public class LocalMessageBus {

    private static final Logger logger = LoggerFactory.getLogger(LocalMessageBus.class);


    @Inject
    private UserDAO userDAO;

    @Inject
    private NotificationSubscriptionDAO notificationSubscriptionDAO;

    @Inject
    private CommandSubscriptionDAO commandSubscriptionDAO;

    @Inject
    private SingletonSessionMap sessionMap;

    @Inject
    private CommandUpdatesSubscriptionDAO commandUpdatesSubscriptionDAO;

    @Inject
    private DeviceDAO deviceDAO;


    public LocalMessageBus() {
    }


    /**
     * Sends command to device websocket session
      * @param deviceCommand
     * @return true if command was delivered
     */
    @Transactional
    public void submitCommand(DeviceCommand deviceCommand) {
        CommandsSubscription commandsSubscription = commandSubscriptionDAO.getById(deviceCommand.getDevice().getId());
        Session session = sessionMap.getSession(commandsSubscription.getSessionId());
        if (session == null || !session.isOpen()) {
            return;
        }

        JsonElement deviceCommandJson = GsonFactory.createGson(new DeviceCommandInsertExclusionStrategy()).toJsonTree(deviceCommand, DeviceCommand.class);

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("action", "command/insertSubscription");
        jsonObject.addProperty("deviceGuid", deviceCommand.getDevice().getGuid().toString());
        jsonObject.add("command", deviceCommandJson);

        Lock lock = WebsocketSession.getCommandsSubscriptionsLock(session);
        try {
            lock.lock();
            WebsocketSession.deliverMessages(session, jsonObject);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends command update to client websocket session
     * @param deviceCommand
     * @return true if update was delivered
     */
    @Transactional
    public void submitCommandUpdate(DeviceCommand deviceCommand) {
        CommandUpdatesSubscription commandUpdatesSubscription = commandUpdatesSubscriptionDAO.getById(deviceCommand
                .getDevice().getId());
        Session session = sessionMap.getSession(commandUpdatesSubscription.getSessionId());
        if (session == null || !session.isOpen()) {
              return;
        }
        JsonElement deviceCommandJson = GsonFactory.createGson(new CommandUpdateExclusionStrategy()).toJsonTree(deviceCommand);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("action", "command/update");
        jsonObject.add("command", deviceCommandJson);

        WebsocketSession.deliverMessages(session, jsonObject);
    }

    /**
     * Subscrbes given device to commands
     * @param device
     * @param session
     */
    @Transactional
    public void subscribeForCommands(Device device, Session session) {
        commandSubscriptionDAO.insert(new CommandsSubscription(device.getId(), session.getId()));
    }


    /**
     * Subscrbes given device to commands
     * @param device
     * @param session
     */
    @Transactional
    public void unsubscribeFromCommands(Device device, Session session) {
        commandSubscriptionDAO.deleteByDeviceAndSession(device, session);
    }


    public void subscribeForCommandUpdates(Long commandId, Session session) {
        commandUpdatesSubscriptionDAO.insert(new CommandUpdatesSubscription(commandId,session.getId()));
    }

    /**
     * Sends device notification to clients
     * @param deviceNotification
     */
    @Transactional
    //TODO make this multithreaded ?!
    public void submitNotification(DeviceNotification deviceNotification) {

        JsonElement deviceNotificationJson = GsonFactory.createGson(new NotificationInsertRequestExclusionStrategy()).toJsonTree(deviceNotification);
        JsonObject resultMessage = new JsonObject();
        resultMessage.addProperty("action", "command/insertSubscription");
        resultMessage.addProperty("deviceGuid", deviceNotification.getDevice().getGuid().toString());
        resultMessage.add("notification", deviceNotificationJson);

        Set<Session> delivers = new HashSet();

        List<String> sessionIdsSubscribedForAll = notificationSubscriptionDAO.getSessionIdSubscribedForAll();
        Set<Session> subscribedForAll = new HashSet<>();
        for (String sessionId : sessionIdsSubscribedForAll){
            subscribedForAll.add(sessionMap.getSession(sessionId));
        }
        for (Session session : subscribedForAll) {
            User user = WebsocketSession.getAuthorisedUser(session);
            if (userDAO.hasAccessToNetwork(user, deviceNotification.getDevice().getNetwork())) {
                delivers.add(session);
            }
        }

        Long deviceId = deviceDAO.findByUUID(deviceNotification.getDevice().getGuid()).getId();
        Collection<String> sessionIds = notificationSubscriptionDAO.getSessionIdSubscribedByDevice(deviceId);
        Set<Session> sessions = new HashSet<>();
        for (String sesionId : sessionIds){
            sessions.add(sessionMap.getSession(sesionId));

        }
        if (sessions != null) {
            delivers.addAll(sessions);
        }

        for (Session session : delivers ){
            Lock lock = WebsocketSession.getNotificationSubscriptionsLock(session);
            try {
                lock.lock();
                WebsocketSession.deliverMessages(session, resultMessage);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Subscribes client websocket session to device notifications
     * @param session
     * @param devices
     */
    public void subscribeForNotifications(Session session, Collection<Device> devices) {
        List<Long> list = new ArrayList<Long>(devices.size());
        for (Device device : devices) {
            list.add(device.getId());
        }
        notificationSubscriptionDAO.insertSubscriptions(devices, session);
    }

    /**
     * Unsubscribes client websocket session from device notifications
     * @param session
     * @param devices
     */
    @Transactional
    public void unsubscribeFromNotifications(Session session, Collection<Device> devices) {
        List<Long> list = new ArrayList<Long>(devices.size());
        for (Device device : devices) {
            list.add(device.getId());
        }
        for (Device device: devices){
            notificationSubscriptionDAO.deleteByDeviceAndSession(device, session);
        }
    }


    public void onDeviceSessionClose(Session session) {
        commandSubscriptionDAO.deleteBySession(session.getId());
    }

    public void onClientSessionClose(Session session) {
        commandSubscriptionDAO.deleteBySession(session.getId());
        notificationSubscriptionDAO.deleteBySession(session.getId());
    }


}
