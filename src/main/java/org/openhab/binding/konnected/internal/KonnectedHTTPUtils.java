package org.openhab.binding.konnected.internal;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP Get and Put reqeust class.
 *
 * @author Zachary Christiansen
 */
public class KonnectedHTTPUtils {

    public KonnectedHTTPUtils() {

    }

    public int doPut(String urlAddress, String payload) throws Exception {
        URL url = new URL(urlAddress);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
        osw.write(payload);
        osw.flush();
        osw.close();

        return connection.getResponseCode();
    }

}