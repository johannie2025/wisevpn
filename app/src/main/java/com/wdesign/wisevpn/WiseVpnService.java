package com.wdesign.wisevpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * WiseVpnService — Service VPN Android.
 *
 * ARCHITECTURE :
 *  - Pour un vrai tunnel OpenVPN, intégrer ics-openvpn (voir README).
 *  - Ce service fournit la plomberie VpnService Android complète
 *    et délègue le tunnel à OpenVpnBridge (wrapper ics-openvpn).
 *  - En mode DEMO (sans ics-openvpn), il crée une interface tun factice
 *    pour tester le flow Android sans crash.
 */
public class WiseVpnService extends VpnService {

    private static final String TAG = "WiseVpnService";
    public  static final String ACTION_CONNECT    = "com.wdesign.wisevpn.CONNECT";
    public  static final String ACTION_DISCONNECT = "com.wdesign.wisevpn.DISCONNECT";
    public  static final String EXTRA_OVPN_PATH   = "ovpn_path";
    public  static final String EXTRA_SERVER_IP   = "server_ip";
    public  static final String EXTRA_SERVER_NAME = "server_name";

    private static final String NOTIF_CHANNEL = "wisevpn_channel";
    private static final int    NOTIF_ID      = 1001;

    // State broadcast
    public static final String BROADCAST_STATE  = "com.wdesign.wisevpn.STATE";
    public static final String EXTRA_STATE      = "state";
    public static final String STATE_CONNECTING = "CONNECTING";
    public static final String STATE_CONNECTED  = "CONNECTED";
    public static final String STATE_DISCONNECTED = "DISCONNECTED";
    public static final String STATE_ERROR      = "ERROR";
    public static final String EXTRA_ERROR_MSG  = "error_msg";

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private volatile boolean running = false;

    // Binder pour MainActivity
    public class LocalBinder extends Binder {
        WiseVpnService getService() { return WiseVpnService.this; }
    }
    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            String ovpnPath  = intent.getStringExtra(EXTRA_OVPN_PATH);
            String serverIp  = intent.getStringExtra(EXTRA_SERVER_IP);
            String serverName= intent.getStringExtra(EXTRA_SERVER_NAME);
            startVpn(ovpnPath, serverIp, serverName);
        } else if (ACTION_DISCONNECT.equals(action)) {
            stopVpn();
        }
        return START_STICKY;
    }

    private void startVpn(String ovpnPath, String serverIp, String serverName) {
        broadcastState(STATE_CONNECTING, null);
        startForeground(NOTIF_ID, buildNotification("Connexion à " + serverName + "...", false));

        // Essayer ics-openvpn d'abord (si intégré)
        boolean icsBridgeAvailable = OpenVpnBridge.isAvailable();

        if (icsBridgeAvailable) {
            Log.d(TAG, "Lancement via OpenVpnBridge (ics-openvpn)");
            OpenVpnBridge.start(this, ovpnPath, new OpenVpnBridge.StateCallback() {
                @Override public void onConnected() {
                    broadcastState(STATE_CONNECTED, null);
                    updateNotification("VPN connecté — " + serverName, true);
                }
                @Override public void onDisconnected() {
                    broadcastState(STATE_DISCONNECTED, null);
                    stopSelf();
                }
                @Override public void onError(String msg) {
                    broadcastState(STATE_ERROR, msg);
                    stopSelf();
                }
            });
        } else {
            // Mode FALLBACK : tunnel TUN minimal pour démo/debug
            Log.w(TAG, "ics-openvpn non disponible, mode fallback TUN");
            startFallbackTunnel(serverIp, serverName);
        }
    }

    /**
     * Fallback : crée une interface tun Android sans vrai tunnel OpenVPN.
     * Utile pour tester le flow UI. Tout le trafic est intercepté mais
     * non tunnelé vers le serveur VPN Gate.
     * ⚠ Remplacer par ics-openvpn pour production.
     */
    private void startFallbackTunnel(String serverIp, String serverName) {
        try {
            Builder builder = new Builder();
            builder.setSession("WiseVPN")
                   .addAddress("10.0.0.2", 24)
                   .addDnsServer("8.8.8.8")
                   .addDnsServer("8.8.4.4")
                   .addRoute("0.0.0.0", 0)          // router tout le trafic
                   .setMtu(1500)
                   .setBlocking(true);

            // Exclure notre propre app du VPN pour éviter boucle
            try { builder.addDisallowedApplication(getPackageName()); } catch (Exception ignored) {}

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                broadcastState(STATE_ERROR, "Impossible d'établir l'interface VPN");
                return;
            }

            running = true;
            broadcastState(STATE_CONNECTED, null);
            updateNotification("VPN actif — " + serverName + " [mode demo]", true);

            // Thread keepalive minimal
            vpnThread = new Thread(() -> {
                Log.d(TAG, "Tunnel fallback actif");
                try {
                    FileInputStream  in  = new FileInputStream(vpnInterface.getFileDescriptor());
                    FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
                    ByteBuffer packet = ByteBuffer.allocate(32767);
                    while (running) {
                        // Lire paquets entrants (non routés réellement)
                        int len = in.read(packet.array());
                        if (len > 0) packet.limit(len);
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    if (running) Log.e(TAG, "Tunnel error: " + e.getMessage());
                }
            }, "WiseVPN-Tunnel");
            vpnThread.start();

        } catch (Exception e) {
            Log.e(TAG, "startFallbackTunnel error: " + e.getMessage());
            broadcastState(STATE_ERROR, e.getMessage());
        }
    }

    public void stopVpn() {
        running = false;
        OpenVpnBridge.stop();
        if (vpnThread != null) { vpnThread.interrupt(); vpnThread = null; }
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (IOException ignored) {}
            vpnInterface = null;
        }
        broadcastState(STATE_DISCONNECTED, null);
        stopForeground(true);
        stopSelf();
    }

    private void broadcastState(String state, String errorMsg) {
        Intent i = new Intent(BROADCAST_STATE);
        i.putExtra(EXTRA_STATE, state);
        if (errorMsg != null) i.putExtra(EXTRA_ERROR_MSG, errorMsg);
        sendBroadcast(i);
    }

    // ── Notifications ──────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL, "WiseVPN", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Statut connexion VPN");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text, boolean connected) {
        Intent pi = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, pi,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("WiseVPN")
            .setContentText(text)
            .setSmallIcon(connected ? android.R.drawable.ic_lock_lock : android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text, boolean connected) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text, connected));
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
