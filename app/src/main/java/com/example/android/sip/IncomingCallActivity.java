package com.example.android.sip;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class IncomingCallActivity extends AppCompatActivity {

    private LinphoneService sipService;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sipService = ((LinphoneService.LocalBinder) binder).getService();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };

    private final BroadcastReceiver callEndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(LinphoneService.EXTRA_CALL_STATE);
            if ("End".equals(state) || "Released".equals(state) || "Error".equals(state)) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen and turn on screen when ringing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        setContentView(R.layout.activity_incoming_call);

        String caller = getIntent().getStringExtra(LinphoneService.EXTRA_CALLER);
        TextView callerText = findViewById(R.id.caller_text);
        if (callerText != null && caller != null) {
            callerText.setText(caller);
        }

        bindService(new Intent(this, LinphoneService.class), connection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter(LinphoneService.ACTION_CALL_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(callEndReceiver, filter);
        }

        findViewById(R.id.btn_answer).setOnClickListener(v -> {
            if (bound) sipService.answerCall();
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(main);
            finish();
        });

        findViewById(R.id.btn_decline).setOnClickListener(v -> {
            if (bound) sipService.hangUp();
            finish();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String caller = intent.getStringExtra(LinphoneService.EXTRA_CALLER);
        TextView callerText = findViewById(R.id.caller_text);
        if (callerText != null && caller != null) {
            callerText.setText(caller);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(callEndReceiver);
        if (bound) {
            unbindService(connection);
        }
    }
}
