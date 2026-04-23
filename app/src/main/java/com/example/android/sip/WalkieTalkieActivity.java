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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class WalkieTalkieActivity extends AppCompatActivity {

    private LinphoneService sipService;
    private boolean bound = false;
    private String pendingCallAddress = null;

    private String lastUsername = null;
    private String lastDomain = null;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> {
                        boolean audioGranted = Boolean.TRUE.equals(results.get(Manifest.permission.RECORD_AUDIO));
                        if (audioGranted) {
                            startAndBindService();
                        } else {
                            updateStatus("Microphone permission required for calls.");
                        }
                    });

    private EditText dialogInput;

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri contactUri = result.getData().getData();
                            String number = getPhoneNumberFromUri(contactUri);
                            if (number != null && dialogInput != null) {
                                dialogInput.setText(number);
                            }
                        }
                    });

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sipService = ((LinphoneService.LocalBinder) binder).getService();
            bound = true;
            registerIfNeeded();
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

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LinphoneService.ACTION_CALL_STATE_CHANGED.equals(action)) {
                onCallStateChanged(
                        intent.getStringExtra(LinphoneService.EXTRA_CALL_STATE),
                        intent.getStringExtra(LinphoneService.EXTRA_CALLER));
            } else if (LinphoneService.ACTION_REGISTRATION_STATE.equals(action)) {
                onRegistrationStateChanged(intent.getStringExtra(LinphoneService.EXTRA_REG_STATE));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure the layout exists or use activity_main for a more modern experience
        try {
            setContentView(R.layout.walkietalkie);
        } catch (Exception e) {
            // Fallback if walkietalkie layout is missing/broken
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(LinphoneService.ACTION_CALL_STATE_CHANGED);
        filter.addAction(LinphoneService.ACTION_REGISTRATION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }

        View btn = findViewById(R.id.callStateImage);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                if (bound && sipService.isInCall()) {
                    sipService.hangUp();
                } else {
                    showCallDialog();
                }
            });
        }

        requestPermissionsAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bound) registerIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {}
        if (bound) {
            unbindService(connection);
            bound = false;
        }
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
            startAndBindService();
        } else {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, LinphoneService.class);
        ContextCompat.startForegroundService(this, intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void registerIfNeeded() {
        if (!bound) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String username = prefs.getString("namePref", "");
        String authId = prefs.getString("authIdPref", "");
        String domain = prefs.getString("domainPref", "sinitpower.de");
        String password = prefs.getString("passPref", "");
        String portStr = prefs.getString("portPref", "5060");
        String transport = prefs.getString("transportPref", "UDP");
        boolean useStun = prefs.getBoolean("stunEnabledPref", true);
        String stunServer = prefs.getString("stunServerPref", "stun.l.google.com:19302");
        int port = 5060;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {}

        if (username.isEmpty() || domain.isEmpty() || password.isEmpty()) {
            return;
        }

        if (username.equals(lastUsername) && domain.equals(lastDomain)) {
            return;
        }

        lastUsername = username;
        lastDomain = domain;
        updateStatus("Registering...");
        sipService.registerAccount(username, authId, password, domain, port, transport, useStun, stunServer);
    }

    private void showCallDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.call_address_dialog, null);
        dialogInput = view.findViewById(R.id.calladdress_edit);
        Button contactsBtn = view.findViewById(R.id.btn_contacts);
        GridLayout dialPad = view.findViewById(R.id.dial_pad);
        Button backspaceBtn = view.findViewById(R.id.btn_backspace);

        if (contactsBtn != null) {
            contactsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                contactPickerLauncher.launch(intent);
            });
        }

        if (dialPad != null) {
            for (int i = 0; i < dialPad.getChildCount(); i++) {
                View child = dialPad.getChildAt(i);
                if (child instanceof Button) {
                    Button b = (Button) child;
                    b.setOnClickListener(v -> {
                        dialogInput.append(b.getText());
                    });
                }
            }
        }

        if (backspaceBtn != null) {
            backspaceBtn.setOnClickListener(v -> {
                String text = dialogInput.getText().toString();
                if (text.length() > 0) {
                    dialogInput.setText(text.substring(0, text.length() - 1));
                    dialogInput.setSelection(dialogInput.getText().length());
                }
            });
        }

        new AlertDialog.Builder(this)
                .setTitle("Call Someone")
                .setView(view)
                .setPositiveButton("Call", (d, w) -> {
                    String addr = dialogInput.getText().toString().trim();
                    if (addr.isEmpty()) return;
                    if (!addr.contains("@")) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        addr += "@" + prefs.getString("domainPref", "sinitpower.de");
                    }
                    if (bound) {
                        sipService.makeCall(addr);
                    } else {
                        pendingCallAddress = addr;
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> dialogInput = null)
                .setOnDismissListener(d -> dialogInput = null)
                .show();
    }

    private String getPhoneNumberFromUri(Uri uri) {
        String number = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (index != -1) number = cursor.getString(index);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return number;
    }

    private void onCallStateChanged(String state, String caller) {
        if (state == null) return;
        switch (state) {
            case "OutgoingProgress":
                updateStatus("Calling " + (caller != null ? caller : "..."));
                break;
            case "OutgoingRinging":
                updateStatus("Ringing...");
                break;
            case "Connected":
            case "StreamsRunning":
                updateStatus("In call: " + (caller != null ? caller : ""));
                break;
            case "End":
            case "Released":
                updateStatus("Ready");
                break;
            case "Error":
                updateStatus("Call failed");
                break;
        }
        updateCallButton(bound && sipService.isInCall());
    }

    private void onRegistrationStateChanged(String state) {
        if (state == null) return;
        switch (state) {
            case "Progress":
                updateStatus("Registering...");
                break;
            case "Ok":
                updateStatus("Ready");
                break;
            case "Failed":
                updateStatus("Registration failed — check settings.");
                break;
            case "None":
            case "Cleared":
                updateStatus("Not registered");
                break;
        }
    }

    public void updateStatus(final String status) {
        runOnUiThread(() -> {
            TextView label = findViewById(R.id.sipLabel);
            if (label != null) label.setText(status);
        });
    }

    private void updateCallButton(boolean inCall) {
        runOnUiThread(() -> {
            ImageView img = findViewById(R.id.callStateImage);
            if (img != null) {
                img.setBackgroundResource(inCall
                        ? R.drawable.btn_speak_pressed
                        : R.drawable.btn_speak_normal);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sipphone_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.CALL_ADDRESS) {
            showCallDialog();
        } else if (id == R.id.SET_SIP_OPTIONS) {
            startActivity(new Intent(this, SipSettings.class));
        } else if (id == R.id.HANG_UP) {
            if (bound) sipService.hangUp();
        }
        return true;
    }
}
