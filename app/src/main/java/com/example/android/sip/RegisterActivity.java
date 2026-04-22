package com.example.android.sip;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etFirstName, etLastName, etEmail;
    private Button btnRegister;
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
        progressBar = findViewById(R.id.reg_progress);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String domain = prefs.getString("domainPref", "sinitpower.de");
        String apiPath = prefs.getString("apiPathPref", "/api.php");
        apiClient.setBaseUrl("https://" + domain + apiPath);

        btnRegister.setOnClickListener(v -> attemptRegister());
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

        // We use a simplified version of createUser in the API client for public registration
        apiClient.register(username, password, firstName, lastName, email, new MagnusApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(RegisterActivity.this, "Registration Successful!", Toast.LENGTH_LONG).show();
                
                // Save to preferences automatically
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(RegisterActivity.this).edit();
                editor.putString("namePref", username);
                editor.putString("passPref", password);
                editor.apply();
                
                finish(); // Close registration and return to main
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
