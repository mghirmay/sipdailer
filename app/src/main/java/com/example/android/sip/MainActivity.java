package com.example.android.sip;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinphoneService sipService;
    private boolean bound = false;
    private String pendingCallAddress = null;

    private String lastUsername = null;
    private String lastDomain = null;
    private boolean lastEnabled = true;

    private DialpadFragment dialpadFragment = new DialpadFragment();
    private ContactsFragment contactsFragment = new ContactsFragment();
    private BottomNavigationView navView;
    private TextView connectionStatusText;
    private TextView callStatusText;
    private View statusIndicator;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> {
                        boolean audioGranted = Boolean.TRUE.equals(results.get(Manifest.permission.RECORD_AUDIO));
                        boolean contactsGranted = Boolean.TRUE.equals(results.get(Manifest.permission.READ_CONTACTS));
                        
                        if (audioGranted) {
                            startAndBindService();
                        }
                        
                        if (contactsGranted && navView.getSelectedItemId() == R.id.navigation_contacts) {
                            loadFragment(contactsFragment);
                        }
                    });

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sipService = ((LinphoneService.LocalBinder) binder).getService();
            bound = true;
            
            // Log current raw state for debugging
            String rawState = sipService.getRegistrationState();
            Log.d("SipDialerUI", "OnServiceConnected: Raw Registration State: " + rawState);
            
            // Only register if we aren't already connected
            if (rawState.contains("None") || rawState.contains("Failed")) {
                registerIfNeeded();
            } else {
                onRegistrationStateChanged(rawState, null);
            }
            
            if (pendingCallAddress != null) {
                sipService.makeCall(pendingCallAddress);
                pendingCallAddress = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (prefs, key) -> {
        if ("enabledPref".equals(key) || "namePref".equals(key) || "authIdPref".equals(key) || "passPref".equals(key) || "domainPref".equals(key) || "portPref".equals(key) || "transportPref".equals(key)) {
            // Force a new registration when settings change
            lastUsername = null;
            registerIfNeeded();
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LinphoneService.ACTION_CALL_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(LinphoneService.EXTRA_CALL_STATE);
                String caller = intent.getStringExtra(LinphoneService.EXTRA_CALLER);
                onCallStateChanged(state, caller);
            } else if (LinphoneService.ACTION_REGISTRATION_STATE.equals(action)) {
                String state = intent.getStringExtra(LinphoneService.EXTRA_REG_STATE);
                String error = intent.getStringExtra(LinphoneService.EXTRA_ERROR_MESSAGE);
                Log.d("SipDialerUI", "Received broadcast reg state: " + state);
                onRegistrationStateChanged(state, error);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectionStatusText = findViewById(R.id.connection_status_text);
        callStatusText = findViewById(R.id.call_status_text);
        statusIndicator = findViewById(R.id.status_indicator);
        navView = findViewById(R.id.bottom_navigation);

        navView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_dialpad) {
                loadFragment(dialpadFragment);
                return true;
            } else if (id == R.id.navigation_contacts) {
                loadFragment(contactsFragment);
                return true;
            } else if (id == R.id.navigation_history) {
                loadFragment(PlaceholderFragment.newInstance("Call History"));
                return true;
            } else if (id == R.id.navigation_settings) {
                loadFragment(new SipSettings.SipPreferenceFragment());
                return true;
            }
            return false;
        });

        loadFragment(dialpadFragment);

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(prefListener);

        IntentFilter filter = new IntentFilter();
        filter.addAction(LinphoneService.ACTION_CALL_STATE_CHANGED);
        filter.addAction(LinphoneService.ACTION_REGISTRATION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }

        requestPermissionsAndStart();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
    }

    private void requestPermissionsAndStart() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS);

        if (needed.isEmpty()) startAndBindService();
        else permissionLauncher.launch(needed.toArray(new String[0]));
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, LinphoneService.class);
        ContextCompat.startForegroundService(this, intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void makeCall(String number) {
        if (!number.contains("@")) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            number += "@" + prefs.getString("domainPref", "sinitpower.de");
        }
        if (bound) sipService.makeCall(number);
        else pendingCallAddress = number;
    }

    public void hangUp() {
        if (bound) sipService.hangUp();
    }

    public boolean isInCall() {
        return bound && sipService.isInCall();
    }

    public void forceRegister() {
        lastUsername = null; 
        registerIfNeeded();
    }

    private void registerIfNeeded() {
        if (!bound || sipService == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean("enabledPref", true);
        String username = prefs.getString("namePref", "");
        String authId = prefs.getString("authIdPref", "");
        String domain = prefs.getString("domainPref", "sinitpower.de");
        String password = prefs.getString("passPref", "");
        String transport = prefs.getString("transportPref", "UDP");
        
        int port = 5060;
        try {
            port = Integer.parseInt(prefs.getString("portPref", "5060"));
        } catch (Exception ignored) {}

        if (!enabled) {
            if (lastEnabled) {
                sipService.getCore().clearAccounts();
                sipService.getCore().clearProxyConfig();
                onRegistrationStateChanged("None", null);
                lastEnabled = false;
            }
            return;
        }

        if (username.isEmpty() || password.isEmpty()) return;
        
        // Skip registration if already connected with same credentials
        if (username.equals(lastUsername) && domain.equals(lastDomain) && enabled == lastEnabled) {
            String state = sipService.getRegistrationState();
            if (state.contains("Ok")) return;
        }

        lastUsername = username;
        lastDomain = domain;
        lastEnabled = enabled;
        sipService.registerAccount(username, authId, password, domain, port, transport);
    }

    private void onCallStateChanged(String state, String caller) {
        if (state == null || state.equals("Released") || state.equals("End")) {
            callStatusText.setVisibility(View.GONE);
        } else {
            callStatusText.setVisibility(View.VISIBLE);
            callStatusText.setText(state);
        }
        dialpadFragment.updateCallButton(bound && sipService.isInCall());
    }

    private void onRegistrationStateChanged(String state, String error) {
        if (state == null) return;
        
        String displayText;
        int color;
        
        // Handles "Ok", "RegistrationOk", "Registered", etc.
        if (state.contains("Ok") || state.equalsIgnoreCase("Registered")) {
            displayText = "Online";
            color = Color.GREEN;
        } else if (state.contains("Progress")) {
            displayText = "Connecting...";
            color = Color.YELLOW;
        } else if (state.contains("Failed")) {
            displayText = "Error";
            color = Color.RED;
            if (error != null) {
                if (error.contains("Connection refused") || error.contains("IO Error")) {
                    Toast.makeText(this, "Network Error: Server rejected connection.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "SIP Error: " + error, Toast.LENGTH_LONG).show();
                }
            }
        } else {
            displayText = "Offline";
            color = Color.GRAY;
        }
        
        connectionStatusText.setText(displayText);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        statusIndicator.setBackground(shape);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(prefListener);
        try {
            unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {}
        if (bound) unbindService(connection);
    }
}
