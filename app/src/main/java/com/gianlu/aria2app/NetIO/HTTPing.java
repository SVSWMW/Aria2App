package com.gianlu.aria2app.NetIO;

import android.content.Context;
import android.support.annotation.NonNull;

import com.gianlu.aria2app.NetIO.JTA2.Aria2Exception;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.ProfilesManager.ProfilesManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.StatusLine;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.util.EntityUtils;

public class HTTPing extends AbstractClient {
    private static HTTPing httping;
    private final ExecutorService executorService;
    private final CloseableHttpClient client;
    private final URI defaultUri;

    private HTTPing(Context context) throws CertificateException, IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, URISyntaxException {
        this(context, ProfilesManager.get(context).getCurrent(context).getProfile(context));
    }

    public HTTPing(Context context, MultiProfile.UserProfile profile) throws CertificateException, IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, URISyntaxException {
        super(context, profile);
        ErrorHandler.get().unlock();
        this.executorService = Executors.newCachedThreadPool();
        this.client = NetUtils.buildHttpClient(context, profile);
        this.defaultUri = NetUtils.createBaseURI(profile);
    }

    public static HTTPing newInstance(Context context) throws NoSuchAlgorithmException, CertificateException, KeyManagementException, KeyStoreException, IOException, URISyntaxException {
        if (httping == null) httping = new HTTPing(context);
        return httping;
    }

    public static void clear() {
        clearConnectivityListener();
        if (httping != null) httping.clearInternal();
    }

    @Override
    protected void clearInternal() {
        executorService.shutdownNow();
        try {
            client.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void send(final JSONObject request, final IReceived handler) {
        if (!executorService.isShutdown())
            executorService.execute(new RequestProcessor(request, handler));
    }

    @Override
    public void connectivityChanged(@NonNull Context context, @NonNull MultiProfile.UserProfile profile) throws Exception {
        if (httping != null) httping.client.close();
        httping = new HTTPing(context, profile);
    }

    private class RequestProcessor implements Runnable {
        private final JSONObject request;
        private final IReceived listener;

        RequestProcessor(JSONObject request, IReceived listener) {
            this.request = request;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                HttpGet get = NetUtils.createGetRequest(profile, defaultUri, request);
                HttpResponse resp = client.execute(get);
                StatusLine sl = resp.getStatusLine();

                HttpEntity entity = resp.getEntity();
                if (entity != null) {
                    String json = EntityUtils.toString(resp.getEntity());
                    if (json == null || json.isEmpty()) {
                        listener.onException(new NullPointerException("Empty response"));
                    } else {
                        JSONObject obj = new JSONObject(json);
                        if (obj.has("error")) {
                            listener.onException(new Aria2Exception(obj.getJSONObject("error")));
                        } else {
                            listener.onResponse(obj);
                        }
                    }
                } else {
                    listener.onException(new StatusCodeException(sl));
                }

                get.releaseConnection();
            } catch (OutOfMemoryError ex) {
                System.gc();
            } catch (JSONException | IOException | URISyntaxException ex) {
                listener.onException(ex);
            }
        }
    }
}
