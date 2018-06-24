package org.openhab.binding.konnected.internal;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class exists so that there is a delay while all of the
//items are linked to the channel and so that the put request
//does not go out until there has been a "calming" in the adding
// in the linking of the channels
public class KonnectedPutSettingsTimer {

    private Timer timer;
    private final Logger logger = LoggerFactory.getLogger(KonnectedHandler.class);

    public KonnectedPutSettingsTimer() {

        this.timer = new Timer();
    }

    public KonnectedPutSettingsTimer startTimer(String urlAddress, String payload, KonnectedHandler Handler) {
        this.timer.cancel();
        this.timer = new Timer();
        KonnectedTimerdoPut timerTask = new KonnectedTimerdoPut(urlAddress, payload, Handler);
        // set a new timer to run in 30 seconds
        try {
            logger.debug("setting a timeer with url: {} and payload: {}", urlAddress, payload);
            this.timer.schedule(timerTask, TimeUnit.SECONDS.toMillis(30));
        } catch (Exception e) {
            logger.debug("The timer encountered an exception: {}", e);
        }
        return this;
    }

    public void Cancel() {
        this.timer.cancel();
        // reset the timer so it can be recreated
        this.timer = new Timer();
    }

    class KonnectedTimerdoPut extends TimerTask {

        private String payload;
        private String urlAddress;
        private KonnectedHTTPUtils http;
        private KonnectedHandler handler;
        private final Logger logger = LoggerFactory.getLogger(KonnectedHandler.class);

        public KonnectedTimerdoPut(String urlAddress, String payload, KonnectedHandler konnectedhandler) {
            this.payload = payload;
            this.urlAddress = urlAddress;
            this.http = new KonnectedHTTPUtils();
            this.handler = konnectedhandler;
        }

        @Override
        public void run() {
            try {
                String response = http.doPut(urlAddress, payload);
                logger.debug("The resposne of the put request was: {}", response);
            }

            catch (IOException e) {
                logger.debug("The put request encountered and exception: {}", e);
            }
            timer.cancel(); // Terminate the timer thread
            handler.initializeChannelStates();

        }
    }
}
