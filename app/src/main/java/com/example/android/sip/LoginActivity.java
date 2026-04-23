package com.example.android.sip;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin, btnSkip;
    private ImageButton btnServerSettings;
    private TextView tvRegisterLink;
    private ProgressBar progressBar;
    private final MagnusApiClient apiClient = new MagnusApiClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Removed auto-login check because MainActivity is now the entry point.

        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.login_username);
        etPassword = findViewById(R.id.login_password);
        btnLogin = findViewById(R.id.btn_login);
        btnSkip = findViewById(R.id.btn_skip_login);
        btnServerSettings = findViewById(R.id.btn_server_settings);
        tvRegisterLink = findViewById(R.id.tv_register_link);
        progressBar = findViewById(R.id.login_progress);

        updateApiUrl();

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnSkip.setOnClickListener(v -> {
            // If we came from MainActivity/Settings, just go back.
            // If this was somehow the launcher, go to MainActivity.
            if (isTaskRoot()) {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        });
        btnServerSettings.setOnClickListener(v -> showServerSettingsDialog());
        tvRegisterLink.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private void updateApiUrl() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String domain = prefs.getString("domainPref", "sinitpower.de");
        String apiPath = prefs.getString("apiPathPref", "/api.php");
        apiClient.setBaseUrl("https://" + domain + apiPath);
    }

    private void showServerSettingsDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_settings, null);
        EditText etDomain = dialogView.findViewById(R.id.edit_domain);
        EditText etPath = dialogView.findViewById(R.id.edit_api_path);
        
        etDomain.setText(prefs.getString("domainPref", "sinitpower.de"));
        etPath.setText(prefs.getString("apiPathPref", "/api.php"));

        new AlertDialog.Builder(this)
                .setTitle("Server Settings")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String domain = etDomain.getText().toString().trim();
                    String path = etPath.getText().toString().trim();
                    
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("domainPref", domain);
                    editor.putString("apiPathPref", path);
                    editor.apply();
                    
                    updateApiUrl();
                    Toast.makeText(this, "Settings updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username and password required", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        apiClient.login(username, password, new MagnusApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this).edit();
                editor.putString("namePref", username);
                editor.putString("passPref", password);
                editor.putBoolean("enabledPref", true);
                editor.apply();
                
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Login Failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
