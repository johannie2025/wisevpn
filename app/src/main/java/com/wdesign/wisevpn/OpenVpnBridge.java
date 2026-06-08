package com.wdesign.wisevpn;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;

import java.io.FileReader;

public class OpenVpnBridge {

    private static final String TAG = "OpenVpnBridge";

    public interface StateCallback {
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    private static StateCallback currentCallback;
    private static VpnStatus.StateListener stateListener;

    public static boolean isAvailable() {
        try {
            Class.forName("de.blinkt.openvpn.VpnProfile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void start(Context ctx, String ovpnPath, StateCallback callback) {
        currentCallback = callback;

        try {
            // 1. Parser le fichier .ovpn
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new FileReader(ovpnPath));
            VpnProfile profile = cp.convertProfile();
            profile.mName = "WiseVPN-Gate";

            // VPN Gate ne demande pas de credentials
            profile.mUsername = "";
            profile.mPassword = "";

            // 2. Enregistrer le profil
            ProfileManager pm = ProfileManager.getInstance(ctx);
            pm.addProfile(profile);
            pm.saveProfile(ctx, profile);
            pm.saveProfileList(ctx);

            // 3. Écouter les changements d'état
            stateListener = new VpnStatus.StateListener() {
                @Override
                public void updateState(String state, String logmessage, int localizedResId,
                                        ConnectionStatus level, Intent intent) {
                    Log.d(TAG, "VPN state: " + state + " level: " + level);
                    switch (level) {
                        case LEVEL_CONNECTED:
                            if (callback != null) callback.onConnected();
                            break;
                        case LEVEL_NOTCONNECTED:
                            if (callback != null) callback.onDisconnected();
                            break;
                        case LEVEL_AUTH_FAILED:
                            if (callback != null) callback.onError("Authentification échouée");
                            break;
                        case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
                        case LEVEL_CONNECTING_SERVER_REPLIED:
                            // En cours — pas de callback, WiseVpnService gère l'état CONNECTING
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void setConnectedVPN(String uuid) {
                    Log.d(TAG, "Connected VPN UUID: " + uuid);
                }
            };
            VpnStatus.addStateListener(stateListener);

            // 4. Démarrer le service OpenVPN
            Intent startVPN = new Intent(ctx, OpenVPNService.class);
            startVPN.setAction(OpenVPNService.START_SERVICE);
            startVPN.putExtra(OpenVPNService.EXTRA_UUID, profile.getUUID().toString());
            ctx.startService(startVPN);

            Log.d(TAG, "OpenVPN démarré pour profil: " + profile.mName);

        } catch (Exception e) {
            Log.e(TAG, "Erreur démarrage OpenVPN: " + e.getMessage());
            if (callback != null) callback.onError("Erreur OpenVPN: " + e.getMessage());
        }
    }

    public static void stop() {
        try {
            if (stateListener != null) {
                VpnStatus.removeStateListener(stateListener);
                stateListener = null;
            }
            ProfileManager.setConnectedVpnProfileDisconnected();
        } catch (Exception e) {
            Log.w(TAG, "stop: " + e.getMessage());
        }
        currentCallback = null;
    }
}
