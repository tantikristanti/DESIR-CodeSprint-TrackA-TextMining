package org.dariah.desir.grobid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.dariah.desir.data.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;

/**
 * Created by lfoppiano on 17/08/16.
 */

@Service
public class GrobidClient {

    private String grobidAPI = "http://traces1.inria.fr/grobid/api";

    private static final Logger LOGGER = LoggerFactory.getLogger(GrobidClient.class);

   /* public GrobidClient(Configuration configuration) {
        this.configuration = configuration;
    }*/

    public void ping() throws RuntimeException {
        URL url = null;
        HttpURLConnection conn = null;

        try {
            url = new URL(this.grobidAPI + "/isalive");
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Error while connecting to GROBID service. HTTP error: " + conn.getResponseCode());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while connecting to GROBID service", e);
        }
    }

    public Page getPageDimension(InputStream inputStream) {

        String json = null;
        Page page = new Page();
        try {
            URL url = new URL(this.grobidAPI + "/referenceAnnotations");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            InputStreamBody inputStreamBody = new InputStreamBody(inputStream, "input");
            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT)
                    .addPart("input", inputStreamBody)
                    .build();
            conn.setRequestProperty("Content-Type", entity.getContentType().getValue());
            try (OutputStream out = conn.getOutputStream()) {
                entity.writeTo(out);
            }

            if (conn.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new HttpRetryException("Failed : HTTP error code : "
                        + conn.getResponseCode(), conn.getResponseCode());
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode() + " " + IOUtils.toString(conn.getErrorStream(), "UTF-8"));
            }

            InputStream in = conn.getInputStream();
            json = IOUtils.toString(in, "UTF-8");
            IOUtils.closeQuietly(in);
            conn.disconnect();

            JsonParser parser = new JsonParser();
            JsonObject root = (JsonObject) parser.parse(json);
            JsonArray arrayPages = (JsonArray) root.get("pages");

            final JsonObject firstElement = (JsonObject) arrayPages.get(0);
            String width = firstElement.get("page_width").getAsString();
            String height = firstElement.get("page_height").getAsString();

            page.setHeight(Double.parseDouble(height));
            page.setWidth(Double.parseDouble(width));


        } catch (IOException e) {
            e.printStackTrace();
        }
        return page;
    }

    public String processFulltextDocument(InputStream inputStream) {

        String tei = null;
        try {
            URL url = new URL(this.grobidAPI + "/processFulltextDocument");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            InputStreamBody inputStreamBody = new InputStreamBody(inputStream, "input");
            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT)
                    .addPart("input", inputStreamBody)
                    .addPart("teiCoordinates", new StringBody("persName"))
                    .addPart("teiCoordinates", new StringBody("ref"))
                    .addPart("teiCoordinates", new StringBody("biblStruct"))
                    .addPart("consolidateHeader", new StringBody("1"))
                    .addPart("consolidateCitations", new StringBody("1"))
                    .build();
            conn.setRequestProperty("Content-Type", entity.getContentType().getValue());
            try (OutputStream out = conn.getOutputStream()) {
                entity.writeTo(out);
            }

            if (conn.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new HttpRetryException("Failed : HTTP error code : "
                        + conn.getResponseCode(), conn.getResponseCode());
            }

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode() + " " + IOUtils.toString(conn.getErrorStream(), "UTF-8"));
            }

            InputStream in = conn.getInputStream();
            tei = IOUtils.toString(in, "UTF-8");
            IOUtils.closeQuietly(in);

            conn.disconnect();

        } catch (ConnectException | HttpRetryException e) {
            LOGGER.error(e.getMessage(), e.getCause());
            try {
                Thread.sleep(20000);
                processFulltextDocument(inputStream);
            } catch (InterruptedException ex) {
            }
        } catch (SocketTimeoutException e) {
            throw new RuntimeException("Grobid processing timed out.", e);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e.getCause());
        }
        return tei;
    }

    public String getHeader(String filepath) {

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filepath);
        } catch (FileNotFoundException e) {
            // throw new DataException("File " + filepath + " not found ", e);
        }
        return processFulltextDocument(inputStream);
    }
}