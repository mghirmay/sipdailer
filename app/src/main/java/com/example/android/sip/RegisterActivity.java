package com.example.android.sip;

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

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etFirstName, etLastName, etEmail;
    private Button btnRegister;
    private ImageButton btnServerSettings;
    private ProgressBar progressBar;
    private final MagnusApiClient apiClient = new MagnusApiClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.reg_username);
        etPassword = findViewById(R.id.reg_password);
        etFirstName = findViewById(R.id.reg_firstname);
        etLastName = findViewById(R.id.reg_lastname);
        etEmail = findViewById(R.id.reg_email);
        btnRegister = findViewById(R.id.btn_register);
        btnServerSettings = findViewById(R.id.btn_server_settings_reg);
        progressBar = findViewById(R.id.reg_progress);
        TextView tvLoginLink = findViewById(R.id.tv_login_link);

        updateApiUrl();

        btnRegister.setOnClickListener(v -> attemptRegister());
        btnServerSettings.setOnClickListener(v -> showServerSettingsDialog());
        tvLoginLink.setOnClickListener(v -> finish());
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

    private void attemptRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username and password required", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        apiClient.register(username, password, firstName, lastName, email, new MagnusApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(RegisterActivity.this, "Registration Successful!", Toast.LENGTH_LONG).show();
                
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(RegisterActivity.this).edit();
                editor.putString("namePref", username);
                editor.putString("passPref", password);
                editor.apply();
                
                finish();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Registration Failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
