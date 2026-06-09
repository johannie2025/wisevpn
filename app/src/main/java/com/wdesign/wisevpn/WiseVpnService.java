package com.wdesign.wisevpn;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * WiseVpnService — Gestion des broadcasts d'état VPN.
 * Le vrai tunnel est géré par ics-openvpn (de.blinkt.openvpn).
 */
public class WiseVpnService extends Service {

    public static final String ACTION_CONNECT     = "com.wdesign.wisevpn.CONNECT";
    public static final String ACTION_DISCONNECT  = "com.wdesign.wisevpn.DISCONNECT";
    public static final String EXTRA_OVPN_PATH    = "ovpn_path";
    public static final String EXTRA_SERVER_IP    = "server_ip";
    public static final String EXTRA_SERVER_PORT  = "server_port";
    public static final String EXTRA_SERVER_NAME  = "server_name";
    public static final String EXTRA_OVPN_CONFIG  = "ovpn_config";
    public static final String BROADCAST_STATE    = "com.wdesign.wisevpn.STATE";
    public static final String EXTRA_STATE        = "state";
    public static final String STATE_CONNECTING   = "CONNECTING";
    public static final String STATE_CONNECTED    = "CONNECTED";
    public static final String STATE_DISCONNECTED = "DISCONNECTED";
    public static final String STATE_ERROR        = "ERROR";
    public static final String EXTRA_ERROR_MSG    = "error_msg";

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
}
