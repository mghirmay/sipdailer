package com.example.android.sip;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BalanceClient {
    private static final String BASE_URL = "https://sinitpower.de/get_balance.php?username=";
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface BalanceCallback {
        void onSuccess(String balance);
        void onError(String error);
    }

    public void getBalance(String username, BalanceCallback callback) {
        String url = BASE_URL + username;
        Log.d("BalanceClient", "Fetching balance from: " + url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("BalanceClient", "Failed to fetch balance", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseData = response.body().string();
                        Log.d("BalanceClient", "Response: " + responseData);
                        
                        JSONObject json = new JSONObject(responseData);
                        if (json.has("balance")) {
                            String balance = json.getString("balance");
                            mainHandler.post(() -> callback.onSuccess(balance));
                        } else {
                            mainHandler.post(() -> callback.onError("No balance field"));
                        }
                    } catch (Exception e) {
                        Log.e("BalanceClient", "JSON Parse error", e);
                        mainHandler.post(() -> callback.onError("Parse error"));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Server error: " + response.code()));
                }
            }
        });
    }
}
