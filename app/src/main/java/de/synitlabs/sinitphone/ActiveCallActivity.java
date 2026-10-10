package de.synitlabs.sinitphone;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ActiveCallActivity extends AppCompatActivity {

    private LinphoneService sipService;
    private boolean bound = false;
    private Button btnSpeaker;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sipService = ((LinphoneService.LocalBinder) binder).getService();
            bound = true;
            updateSpeakerButton();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    private final BroadcastReceiver callStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(LinphoneService.EXTRA_CALL_STATE);
            if ("End".equals(state) || "Released".equals(state) || "Error".equals(state)) {
                finish();
            } else if (state != null) {
                TextView statusText = findViewById(R.id.active_call_status);
                if (statusText != null) statusText.setText(formatState(state));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_active_call);

        String remote = getIntent().getStringExtra(LinphoneService.EXTRA_CALLER);
        String state = getIntent().getStringExtra(LinphoneService.EXTRA_CALL_STATE);

        TextView remoteText = findViewById(R.id.active_call_remote);
        TextView statusText = findViewById(R.id.active_call_status);
        btnSpeaker = findViewById(R.id.btn_speaker);

        if (remoteText != null && remote != null) remoteText.setText(remote);
        if (statusText != null) statusText.setText(formatState(state));

        findViewById(R.id.btn_hang_up).setOnClickListener(v -> {
            if (bound && sipService != null) sipService.hangUp();
        });

        btnSpeaker.setOnClickListener(v -> {
            if (bound && sipService != null) {
                sipService.setSpeakerEnabled(!sipService.isSpeakerEnabled());
                updateSpeakerButton();
            }
        });

        bindService(new Intent(this, LinphoneService.class), connection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter(LinphoneService.ACTION_CALL_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(callStateReceiver, filter);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String remote = intent.getStringExtra(LinphoneService.EXTRA_CALLER);
        String state = intent.getStringExtra(LinphoneService.EXTRA_CALL_STATE);
        TextView remoteText = findViewById(R.id.active_call_remote);
        TextView statusText = findViewById(R.id.active_call_status);
        if (remoteText != null && remote != null) remoteText.setText(remote);
        if (statusText != null) statusText.setText(formatState(state));
    }

    private void updateSpeakerButton() {
        if (btnSpeaker == null || !bound || sipService == null) return;
        boolean on = sipService.isSpeakerEnabled();
        btnSpeaker.setText(on ? "Speaker: On" : "Speaker: Off");
        btnSpeaker.setBackgroundTintList(ColorStateList.valueOf(on ? 0xFF4CAF50 : 0xFF1976D2));
    }

    private String formatState(String state) {
        if (state == null) return "";
        switch (state) {
            case "OutgoingInit":
            case "OutgoingProgress": return "Calling...";
            case "OutgoingRinging": return "Ringing...";
            case "Connected":
            case "StreamsRunning": return "Connected";
            default: return state;
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(callStateReceiver);
        if (bound) unbindService(connection);
    }
}
