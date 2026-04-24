package com.example.android.sip;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MagnusApiClient {
    private String baseUrl = "https://sinitpower.de/webAPI/magnusbillingApi.php";
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public void setBaseUrl(String url) {
        this.baseUrl = url;
    }

    public void login(String username, String password, ApiCallback<JSONObject> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("action", "login");
            body.put("username", username);
            body.put("password", password);
        } catch (Exception ignored) {}
        executePostRequest(baseUrl, body, callback::onSuccess, callback::onError);
    }

    public void register(String username, String password, String firstname, String lastname, String email, ApiCallback<JSONObject> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("action", "register");
            body.put("username", username);
            body.put("password", password);
            body.put("firstname", firstname);
            body.put("lastname", lastname);
            body.put("email", email);
        } catch (Exception ignored) {}
        executePostRequest(baseUrl, body, callback::onSuccess, callback::onError);
    }

    public void getBalance(String username, ApiCallback<String> callback) {
        String url = baseUrl + "?action=balance&username=" + username;
        executeRequest(url, json -> {
            if (json.has("balance")) {
                callback.onSuccess(json.getString("balance"));
            } else if (json.has("data") && json.getJSONObject("data").has("credit")) {
                callback.onSuccess(json.getJSONObject("data").getString("credit"));
            } else {
                callback.onError("No balance field");
            }
        }, callback::onError);
    }

    public void getCallHistory(String username, ApiCallback<JSONArray> callback) {
        String url = baseUrl + "?action=call&username=" + username;
        executeRequest(url, json -> {
            if (json.has("data")) {
                callback.onSuccess(json.getJSONArray("data"));
            } else {
                callback.onError("No history found");
            }
        }, callback::onError);
    }

    public void getUserInfo(String username, ApiCallback<JSONObject> callback) {
        String url = baseUrl + "?action=user_info&username=" + username;
        executeRequest(url, json -> {
            if (json.has("data")) {
                callback.onSuccess(json.getJSONObject("data"));
            } else {
                callback.onError("No user data found");
            }
        }, callback::onError);
    }

    private void executePostRequest(String url, JSONObject jsonBody, JsonProcessor processor, ErrorHandler errorHandler) {
        okhttp3.FormBody.Builder formBuilder = new okhttp3.FormBody.Builder();
        java.util.Iterator<String> keys = jsonBody.keys();
        while(keys.hasNext()) {
            String key = keys.next();
            formBuilder.add(key, jsonBody.optString(key));
        }

        Request request = new Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> errorHandler.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleResponse(response, processor, errorHandler);
            }
        });
    }

    private void executeRequest(String url, JsonProcessor processor, ErrorHandler errorHandler) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> errorHandler.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleResponse(response, processor, errorHandler);
            }
        });
    }

    private void handleResponse(Response response, JsonProcessor processor, ErrorHandler errorHandler) throws IOException {
        String responseData = (response.body() != null) ? response.body().string() : "";
        if (response.isSuccessful()) {
            try {
                JSONObject json = new JSONObject(responseData);
                if ("success".equals(json.optString("status"))) {
                    mainHandler.post(() -> {
                        try { processor.process(json); } catch (Exception e) { errorHandler.onError(e.getMessage()); }
                    });
                } else {
                    mainHandler.post(() -> errorHandler.onError(json.optString("message", "Unknown error")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> errorHandler.onError("Invalid server response"));
            }
        } else {
            mainHandler.post(() -> errorHandler.onError("Server error: " + response.code()));
        }
    }

    interface JsonProcessor { void process(JSONObject json) throws Exception; }
    interface ErrorHandler { void onError(String error); }
}
