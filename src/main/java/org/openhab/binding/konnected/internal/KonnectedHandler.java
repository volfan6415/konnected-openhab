/**
 * Copyright (c) 2014,2018 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.konnected.internal;

import static org.openhab.binding.konnected.internal.KonnectedBindingConstants.Zone_1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.konnected.internal.servelet.KonnectedHTTPServelet;
import org.openhab.binding.konnected.internal.servelet.KonnectedModuleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link KonnectedHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Zachary Christiansen - Initial contribution
 */
@NonNullByDefault
public class KonnectedHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(KonnectedHandler.class);

    @Nullable
    private KonnectedConfiguration config;
    private KonnectedHTTPServelet webHookServlet;
    private KonnectedPutSettingsTimer putSettingsTimer;
    private List<String> sensors;
    private List<String> actuators;

    public KonnectedHandler(Thing thing, KonnectedHTTPServelet webHookServlet) {
        super(thing);
        this.webHookServlet = webHookServlet;
        this.putSettingsTimer = new KonnectedPutSettingsTimer();
        this.sensors = new LinkedList<String>();
        this.actuators = new LinkedList<String>();

    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(Zone_1)) {
            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    public void handleWebHookEvent(String pin, String State) {

        String channelid = "Zone_" + pin;
        logger.debug("The channelid of the event is: {}", channelid);
        StringType channelstate = new StringType(State);
        updateState(channelid, channelstate);

    }

    public void initializeChannelStates() {

        this.sensors.forEach(item -> {
            Map<String, String> properties = this.thing.getProperties();
            String Host = properties.get("ipAddress");
            // seting it up to wait 30 seconds before sending the put request
            KonnectedHTTPUtils http = new KonnectedHTTPUtils();
            try {
                String data = http.doGet(Host + "/device", item);
                Gson gson = new Gson();
                KonnectedModuleEvent event = gson.fromJson(data, KonnectedModuleEvent.class);
                handleWebHookEvent(event.getPin(), event.getState());
            }

            catch (IOException e) {
                logger.debug("Getting the state of the pin failed: {}", e);

            }
            logger.debug("The Sensor String is: {}", item);

        });

    }

    @Override
    public void initialize() {
        // TODO: Initialize the thing. If done set status to ONLINE to indicate proper working.
        // Long running initialization should be done asynchronously in background.
        config = getConfigAs(KonnectedConfiguration.class);
        Map<String, String> properties = this.thing.getProperties();
        String Host = properties.get("ipAddress");
        logger.debug("The Thing configurations are : {}", this.thing.getConfiguration().toString());
        logger.debug("Setting up Konnected Module WebHook");
        webHookServlet.activate(this);
        updateStatus(ThingStatus.ONLINE);

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work
        // as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public void dispose() {
        logger.debug("Running dispose()");

        // if (getWebHookURI() != null) {
        logger.debug("Releasing Konnected WebHook");
        webHookServlet.deactivate();
        // }
    }

    @Override
    public synchronized void channelLinked(ChannelUID channel) {
        // adds linked channels to list based on last value of Channel ID
        // which is set to a number
        logger.debug("Channel {} has been linked", channel.getId());
        sensors.add("{\"pin\":" + channel.getId().substring((channel.getId().length() - 1)) + "}");
        logger.debug(sensors.toString());
        updateKonnectedModule();

    }

    @Override
    public synchronized void channelUnlinked(ChannelUID channel) {
        logger.debug("Channel {} has been unlinked", channel.toString());
        sensors.remove("{\"pin\":" + channel.getId().substring((channel.getId().length() - 1)) + "}");
        logger.debug(sensors.toString());
        updateKonnectedModule();

    }

    private String contructSettingsPayload() {
        String hostPath = "";
        try {
            hostPath = getHostName() + webHookServlet.getPath();
            logger.debug("The host path is: {}", hostPath);
        }

        catch (UnknownHostException e) {
            logger.debug("Unable to obtain hostname: {}", e);
            return "none";

        }

        String authToken = config.Auth_Token;
        logger.debug("The Auth_Token is: {}", authToken);
        logger.debug("The Sensor String is: {}", sensors.toString());
        logger.debug("The Actuator String is: {}", actuators.toString());
        String payload = "{\"sensors\":" + sensors.toString() + ",\"actuators\": " + actuators.toString()
                + ",\"token\": \"" + authToken + "\",\"apiUrl\": \"http://" + hostPath + "\"}";
        logger.debug("The payload is: {}", payload);
        return payload;
    }

    protected String getHostName() throws UnknownHostException {
        // returns the local address of the openHAB server
        InetAddress addr = InetAddress.getLocalHost();
        String hostname = addr.getHostAddress() + ":8080";
        return hostname;
    }

    private void updateKonnectedModule() {
        Map<String, String> properties = this.thing.getProperties();
        String Host = properties.get("ipAddress");
        String payload = contructSettingsPayload();
        // seting it up to wait 30 seconds before sending the put request
        logger.debug("creating new timer");
        putSettingsTimer = putSettingsTimer.startTimer(Host + "/settings", payload, this);

    }

}
