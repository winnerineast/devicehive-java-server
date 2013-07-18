package com.devicehive.websockets;


import com.devicehive.exceptions.HiveException;
import com.devicehive.websockets.handlers.HiveMessageHandlers;
import com.devicehive.websockets.handlers.JsonMessageBuilder;
import com.devicehive.websockets.handlers.annotations.Action;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.OptimisticLockException;
import javax.websocket.Session;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

abstract class Endpoint {

    protected static final long MAX_MESSAGE_SIZE = 10240;
    private static final Logger logger = LoggerFactory.getLogger(Endpoint.class);

    protected JsonObject processMessage(HiveMessageHandlers handler, String message, Session session) {
        JsonObject response;

        JsonObject request;
        try {
            request = new JsonParser().parse(message).getAsJsonObject();
        } catch (JsonSyntaxException ex) {
            // Stop processing this request, response with simple error message (status and error fields)
            logger.error("[processMessage] Incorrect message syntax ", ex);
            return JsonMessageBuilder.createErrorResponseBuilder("Incorrect JSON syntax").build();
        }

        try {
            String action = request.getAsJsonPrimitive("action").getAsString();
            logger.debug("[action] Looking for action " + action);
            response = tryExecute(handler, action, request, session);
        } catch (HiveException ex) {
            response = JsonMessageBuilder.createErrorResponseBuilder(ex.getMessage()).build();
        } catch (OptimisticLockException ex) {
            response = JsonMessageBuilder.createErrorResponseBuilder(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("[processMessage] Error processing message ", ex);
            response = JsonMessageBuilder.createErrorResponseBuilder("Internal server error").build();
        }
        return constructFinalResponse(request, response);
    }

    private JsonObject tryExecute(HiveMessageHandlers handler, String action, JsonObject request, Session session)
            throws IllegalAccessException, InvocationTargetException {
        for (final Method method : handler.getClass().getMethods()) {
            if (method.isAnnotationPresent(Action.class)) {

                Action ann = method.getAnnotation(Action.class);
                if (ann.value().equals(action)) {
                    if (ann.needsAuth()) {
                        handler.ensureAuthorised(request, session);
                    }
                    try {
                        return (JsonObject) method.invoke(handler, request, session);
                    } catch (InvocationTargetException e) {
                        invocationTargetExceptionResolve(e);
                    }
                }
            }
        }
        throw new HiveException("Unknown action requested: " + action);
    }

    private void invocationTargetExceptionResolve(InvocationTargetException e) throws InvocationTargetException {
        if (e.getTargetException() instanceof HiveException) {
            throw (HiveException) e.getTargetException();
        }
        if (e.getTargetException() instanceof OptimisticLockException) {
            throw (OptimisticLockException) e.getTargetException();
        }
        throw e;
    }

    private JsonObject constructFinalResponse(JsonObject request, JsonObject response) {
        if (response == null) {
            logger.error("[constructFinalResponse]  response is null ");
            response = JsonMessageBuilder.createErrorResponseBuilder().build();
        }
        JsonObject finalResponse = new JsonMessageBuilder()
                .addAction(request.get(JsonMessageBuilder.ACTION).getAsString())
                .addRequestId(request.get(JsonMessageBuilder.REQUEST_ID))
                .include(response)
                .build();
        return finalResponse;
    }


}
