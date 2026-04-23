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
    private TextView balanceText;
    private View statusIndicator;
    
    private final MagnusApiClient magnusApiClient = new MagnusApiClient();

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
            
            String rawState = sipService.getRegistrationState();
            Log.d("SipDialerUI", "OnServiceConnected: Raw Registration State: " + rawState);
            
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
        balanceText = findViewById(R.id.balance_text);
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
                loadFragment(new HistoryFragment());
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
        
        boolean useStun = prefs.getBoolean("stunEnabledPref", true);
        String stunServer = prefs.getString("stunServerPref", "stun.l.google.com:19302");
        
        int port = 5060;
        try {
            port = Integer.parseInt(prefs.getString("portPref", "5060"));
        } catch (Exception ignored) {}

        if (!enabled) {
            if (lastEnabled) {
                sipService.getCore().clearAccounts();
                onRegistrationStateChanged("None", null);
                lastEnabled = false;
            }
            return;
        }

        if (username.isEmpty() || password.isEmpty()) return;
        
        if (username.equals(lastUsername) && domain.equals(lastDomain) && enabled == lastEnabled) {
            String state = sipService.getRegistrationState();
            if (state.contains("Ok")) return;
        }

        lastUsername = username;
        lastDomain = domain;
        lastEnabled = enabled;
        sipService.registerAccount(username, authId, password, domain, port, transport, useStun, stunServer);
    }

    private void fetchBalance() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Construct API URL from preferences
        String domain = prefs.getString("domainPref", "sinitpower.de");
        String apiPath = prefs.getString("apiPathPref", "/magnusbillingApi.php");
        String apiUrl = "https://" + domain + apiPath;
        magnusApiClient.setBaseUrl(apiUrl);

        // Use phone number for balance check as requested
        String identifier = prefs.getString("phonePref", "");
        
        // Fallback to username if phone is empty
        if (identifier.isEmpty()) {
            identifier = prefs.getString("namePref", "");
        }
        
        if (identifier.isEmpty()) return;

        magnusApiClient.getBalance(identifier, new MagnusApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String balance) {
                balanceText.setText("Balance: " + balance);
            }

            @Override
            public void onError(String error) {
                balanceText.setText("Balance: --");
            }
        });
    }

    private void onCallStateChanged(String state, String caller) {
        if (state == null || state.equals("Released") || state.equals("End")) {
            callStatusText.setVisibility(View.GONE);
            fetchBalance(); // Fetch balance after a call ends
        } else {
            callStatusText.setVisibility(View.VISIBLE);
            callStatusText.setText(state);
            if (state.equals("StreamsRunning") || state.equals("Connected")) {
                fetchBalance(); // Fetch balance when call is answered/starts
            }
        }
        dialpadFragment.updateCallButton(bound && sipService.isInCall());
    }

    private void onRegistrationStateChanged(String state, String error) {
        if (state == null) return;
        
        String displayText;
        int color;
        
        if (state.contains("Ok") || state.equalsIgnoreCase("Registered")) {
            displayText = "Online";
            color = Color.GREEN;
            fetchBalance(); // Fetch balance on successful registration
        } else if (state.contains("Progress")) {
            displayText = "Connecting...";
            color = Color.YELLOW;
        } else if (state.contains("Failed")) {
            displayText = "Error";
            color = Color.RED;
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
