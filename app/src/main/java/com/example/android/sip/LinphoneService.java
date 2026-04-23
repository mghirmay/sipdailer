package com.example.android.sip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.linphone.core.*;

public class LinphoneService extends Service {

    public static final String ACTION_CALL_STATE_CHANGED = "com.example.android.sip.CALL_STATE_CHANGED";
    public static final String ACTION_REGISTRATION_STATE = "com.example.android.sip.REGISTRATION_STATE";
    public static final String EXTRA_CALL_STATE = "call_state";
    public static final String EXTRA_CALLER = "caller";
    public static final String EXTRA_REG_STATE = "reg_state";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";

    private static final String CHANNEL_SERVICE = "sip_service";
    private static final String CHANNEL_CALLS = "sip_calls";
    private static final int NOTIF_SERVICE = 1;
    private static final int NOTIF_INCOMING = 2;

    private Core core;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        LinphoneService getService() {
            return LinphoneService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_SERVICE, buildServiceNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_SERVICE, buildServiceNotification());
        }
        initCore();
    }

    private void createNotificationChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_SERVICE, "SIP Service", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel callChannel = new NotificationChannel(
                CHANNEL_CALLS, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH);
        callChannel.setBypassDnd(true);
        nm.createNotificationChannel(callChannel);
    }

    private Notification buildServiceNotification() {
        PendingIntent tap = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("SIP Dialer")
                .setContentText("Running")
                .setContentIntent(tap)
                .build();
    }

    private void initCore() {
        // Reverting to setDebugMode as createLoggingService() is not available in this SDK version
        Factory.instance().setDebugMode(true, "SipDialer");
        core = Factory.instance().createCore(null, null, this);
        core.setUserAgent("SipDialer", "1.0");

        core.addListener(new CoreListenerStub() {
            @Override
            public void onCallStateChanged(@NonNull Core core, @NonNull Call call, Call.State state, @NonNull String message) {
                Log.d("SipDialer", "Call state changed: " + state.name());
                broadcastCallState(state, call);
                if (state == Call.State.IncomingReceived) {
                    showIncomingCallUi(call);
                } else if (state == Call.State.End || state == Call.State.Released) {
                    cancelIncomingCallNotification();
                }
            }

            @Override
            public void onAccountRegistrationStateChanged(@NonNull Core core, @NonNull Account account, RegistrationState state, @NonNull String message) {
                Log.d("SipDialer", "Account Reg state: " + state.name() + " (" + message + ")");
                broadcastRegistrationState(state.name(), message);
            }

            @Override
            public void onRegistrationStateChanged(@NonNull Core core, @NonNull ProxyConfig cfg, RegistrationState state, @NonNull String message) {
                Log.d("SipDialer", "Proxy Reg state: " + state.name() + " (" + message + ")");
                broadcastRegistrationState(state.name(), message);
            }
        });

        core.start();
    }

    public void broadcastRegistrationState(String stateName, String message) {
        Intent intent = new Intent(ACTION_REGISTRATION_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_REG_STATE, stateName);
        intent.putExtra(EXTRA_ERROR_MESSAGE, message);
        sendBroadcast(intent);
    }

    public String getRegistrationState() {
        if (core == null) return "None";
        Account defaultAccount = core.getDefaultAccount();
        if (defaultAccount != null) return defaultAccount.getState().name();
        
        ProxyConfig pc = core.getDefaultProxyConfig();
        if (pc != null) return pc.getState().name();
        
        return "None";
    }

    public void registerAccount(String username, String authId, String password, String domain, int port, String transport, boolean useStun, String stunServer) {
        core.clearAccounts();
        core.clearAllAuthInfo();

        // Configure STUN/NAT settings
        if (useStun && stunServer != null && !stunServer.isEmpty()) {
            core.setNatPolicy(null); // Clear old policy
            NatPolicy natPolicy = core.createNatPolicy();
            natPolicy.setStunServer(stunServer);
            natPolicy.setStunEnabled(true);
            core.setNatPolicy(natPolicy);
        } else {
            core.setNatPolicy(null);
        }

        String effectiveAuthId = (authId == null || authId.isEmpty()) ? username : authId;
        AuthInfo auth = Factory.instance().createAuthInfo(username, effectiveAuthId, password, null, null, domain);
        core.addAuthInfo(auth);

        try {
            AccountParams params = core.createAccountParams();
            Address identity = Factory.instance().createAddress("sip:" + username + "@" + domain);
            if (identity != null) params.setIdentityAddress(identity);

            Address server = Factory.instance().createAddress("sip:" + domain + ":" + port);
            if (server != null) {
                if ("TCP".equalsIgnoreCase(transport)) server.setTransport(TransportType.Tcp);
                else if ("TLS".equalsIgnoreCase(transport)) server.setTransport(TransportType.Tls);
                else server.setTransport(TransportType.Udp);
                params.setServerAddress(server);
            }

            params.setRegisterEnabled(true);

            Account account = core.createAccount(params);
            core.addAccount(account);
            core.setDefaultAccount(account);
        } catch (Exception e) {
            Log.e("SipDialer", "Error creating account", e);
        }
    }

    public void makeCall(String sipAddress) {
        if (core == null) return;
        try {
            Address remote = core.interpretUrl(sipAddress);
            if (remote == null) return;
            CallParams params = core.createCallParams(null);
            if (params != null) {
                params.setMediaEncryption(MediaEncryption.None);
                core.inviteAddressWithParams(remote, params);
            }
        } catch (Exception e) {
            Log.e("SipDialer", "Call failed", e);
        }
    }

    public void answerCall() {
        Call call = core.getCurrentCall();
        if (call != null) {
            try {
                call.accept();
            } catch (Exception ignored) {}
        }
    }

    public void hangUp() {
        if (core == null) return;
        for (Call call : core.getCalls()) {
            try {
                call.terminate();
            } catch (Exception ignored) {}
        }
    }

    public boolean isInCall() {
        return core != null && core.getCallsNb() > 0;
    }

    public Core getCore() {
        return core;
    }

    private void showIncomingCallUi(Call call) {
        String caller = call.getRemoteAddress().asStringUriOnly();
        Intent activityIntent = new Intent(this, IncomingCallActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityIntent.putExtra(EXTRA_CALLER, caller);
        PendingIntent fullScreen = PendingIntent.getActivity(this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_CALLS)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Incoming Call")
                .setContentText(caller)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreen, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();

        getSystemService(NotificationManager.class).notify(NOTIF_INCOMING, notif);
        startActivity(activityIntent);
    }

    private void cancelIncomingCallNotification() {
        getSystemService(NotificationManager.class).cancel(NOTIF_INCOMING);
    }

    private void broadcastCallState(Call.State state, Call call) {
        Intent intent = new Intent(ACTION_CALL_STATE_CHANGED);
        intent.setPackage(getPackageName());
        if (state != null) intent.putExtra(EXTRA_CALL_STATE, state.name());
        if (call != null)
            intent.putExtra(EXTRA_CALLER, call.getRemoteAddress().asStringUriOnly());
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (core != null) {
            core.stop();
            core = null;
        }
        super.onDestroy();
    }
}
