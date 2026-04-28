package de.sinitpower.sinitphone;

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
import android.net.Uri;
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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinphoneService sipService;
    private boolean bound = false;
    private String pendingCallAddress = null;

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
                        // All requested permissions are handled here.
                        // We start the service regardless, but it will have limited functionality if permissions are denied.
                        startSipService();
                    });

    private final BroadcastReceiver sipReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LinphoneService.ACTION_REGISTRATION_STATE.equals(action)) {
                String state = intent.getStringExtra(LinphoneService.EXTRA_REG_STATE);
                updateRegistrationStatus(state);
                if ("Ok".equalsIgnoreCase(state)) fetchRemoteStatus();
            } else if (LinphoneService.ACTION_CALL_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(LinphoneService.EXTRA_CALL_STATE);
                if (callStatusText != null) {
                    callStatusText.setText(state);
                    callStatusText.setVisibility(View.VISIBLE);
                }
                if (dialpadFragment != null) {
                    dialpadFragment.updateCallButton(isInCall());
                }
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sipService = ((LinphoneService.LocalBinder) binder).getService();
            bound = true;
            
            if (pendingCallAddress != null) {
                sipService.makeCall(pendingCallAddress);
                pendingCallAddress = null;
            }
            updateRegistrationStatus(sipService.getRegistrationState());
            performRegistration();
            fetchRemoteStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
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
            if (id == R.id.navigation_dialpad) loadFragment(dialpadFragment);
            else if (id == R.id.navigation_contacts) loadFragment(contactsFragment);
            else if (id == R.id.navigation_history) loadFragment(new HistoryFragment());
            else if (id == R.id.navigation_settings) loadFragment(new SipSettings.SipPreferenceFragment());
            return true;
        });

        loadFragment(dialpadFragment);
        requestPermissionsAndStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!bound) {
            startSipService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound && sipService != null) {
            sipService.requestStopWhenIdle();
            unbindService(connection);
            bound = false;
            sipService = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(LinphoneService.ACTION_REGISTRATION_STATE);
        filter.addAction(LinphoneService.ACTION_CALL_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sipReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sipReceiver, filter);
        }
        if (bound && sipService != null) {
            updateRegistrationStatus(sipService.getRegistrationState());
        }
        fetchRemoteStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(sipReceiver);
    }

    private void updateRegistrationStatus(String state) {
        if (connectionStatusText == null) return;
        
        int color = Color.GRAY;
        String displayStatus = state;

        if ("Ok".equalsIgnoreCase(state) || "Registered".equalsIgnoreCase(state)) {
            color = Color.GREEN;
            displayStatus = "Online";
        } else if ("Progress".equalsIgnoreCase(state) || "RegistrationProgress".equalsIgnoreCase(state)) {
            color = Color.YELLOW;
            displayStatus = "Connecting...";
        } else if ("Failed".equalsIgnoreCase(state) || "Error".equalsIgnoreCase(state)) {
            color = Color.RED;
            displayStatus = "Registration Failed";
        } else {
            displayStatus = "Offline";
        }

        connectionStatusText.setText(displayStatus);
        if (statusIndicator != null) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            statusIndicator.setBackground(shape);
        }
    }

    public void forceRegister() {
        if (bound && sipService != null) {
            performRegistration();
        }
    }

    private void performRegistration() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean("enabledPref", true);
        if (!enabled) {
            sipService.getCore().clearAccounts();
            return;
        }

        String user = prefs.getString("namePref", "");
        String pass = prefs.getString("passPref", "");
        String domain = prefs.getString("domainPref", "sinitpower.de");
        int port = Integer.parseInt(prefs.getString("portPref", "5060"));
        String transport = prefs.getString("transportPref", "UDP");
        
        if (!user.isEmpty() && !pass.isEmpty()) {
            sipService.registerAccount(user, user, pass, domain, port, transport, true, "stun.l.google.com:19302");
        }
    }

    private void fetchRemoteStatus() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String username = prefs.getString("loginUsernamePref", "");
        if (username.isEmpty()) username = prefs.getString("namePref", "");
        if (username.isEmpty()) return;

        magnusApiClient.getUserInfo(username, new MagnusApiClient.ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                runOnUiThread(() -> {
                    try {
                        String status = data.optString("sip-status", "Offline");
                        String credit = data.optString("credit", "0.00");
                        updateRegistrationStatus(status);
                        balanceText.setText("€ " + String.format("%.2f", Double.parseDouble(credit)));
                    } catch (Exception ignored) {}
                });
            }
            @Override public void onError(String error) {}
        });
    }

    public void makeCall(String number) {
        if (!number.contains("@")) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            number += "@" + prefs.getString("domainPref", "sinitpower.de");
        }
        if (bound && sipService != null) sipService.makeCall(number);
        else pendingCallAddress = number;
    }

    public void dialNumber(String number) {
        navView.setSelectedItemId(R.id.navigation_dialpad);
        getSupportFragmentManager().executePendingTransactions();
        dialpadFragment.setDialNumber(number);
    }

    public void hangUp() {
        if (bound && sipService != null) sipService.hangUp();
    }

    public boolean isInCall() {
        return bound && sipService != null && sipService.isInCall();
    }

    public void setSpeakerEnabled(boolean enabled) {
        if (bound && sipService != null) {
            sipService.setSpeakerEnabled(enabled);
        }
    }

    public boolean isSpeakerEnabled() {
        return bound && sipService != null && sipService.isSpeakerEnabled();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.nav_host_fragment, fragment).commit();
    }

    private void requestPermissionsAndStart() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_CONTACTS);
        }

        if (needed.isEmpty()) {
            startSipService();
        } else {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private void startSipService() {
        if (bound) return;
        Intent intent = new Intent(this, LinphoneService.class);
        ContextCompat.startForegroundService(this, intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
