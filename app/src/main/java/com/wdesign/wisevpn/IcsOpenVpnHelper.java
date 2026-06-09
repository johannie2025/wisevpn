package com.wdesign.wisevpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * IcsOpenVpnHelper — Délègue le tunnel à ics-openvpn via Intent.
 *
 * FLOW :
 *  1. Vérifier si ics-openvpn est installé
 *  2. Si non → ouvrir Play Store / F-Droid
 *  3. Si oui → écrire .ovpn sur disque → lancer Intent d'import
 *  4. ics-openvpn prend le relais (vrai tunnel natif)
 */
public class IcsOpenVpnHelper {

    private static final String TAG = "IcsOpenVpnHelper";

    public static final String ICS_PACKAGE      = "de.blinkt.openvpn";
    public static final String ICS_PLAY_URL     = "https://play.google.com/store/apps/details?id=de.blinkt.openvpn";
    public static final String ICS_FDROID_URL   = "https://f-droid.org/packages/de.blinkt.openvpn/";

    // Request code pour onActivityResult
    public static final int REQUEST_IMPORT = 7001;

    public interface InstallCallback {
        void onInstalled();
        void onNotInstalled();
    }

    // ── Vérification ────────────────────────────────────────────────────

    public static boolean isInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(ICS_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ── Ouvrir Play Store ────────────────────────────────────────────────

    public static void openPlayStore(Context ctx) {
        try {
            // Essayer l'app Play Store native
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + ICS_PACKAGE));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            // Fallback navigateur
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(ICS_PLAY_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    // ── Lancer le tunnel via ics-openvpn ─────────────────────────────────

    /**
     * Écrit le profil .ovpn et lance ics-openvpn pour l'importer + connecter.
     *
     * @param activity  Activity appelante (pour startActivityForResult)
     * @param server    Serveur VPN Gate sélectionné
     */
    public static void startVpn(Activity activity, ServerModel server) {
        try {
            // 1. Décoder base64 → fichier .ovpn
            byte[] decoded = android.util.Base64.decode(
                server.ovpnConfigBase64, android.util.Base64.DEFAULT);
            String config = new String(decoded, "UTF-8");

            // Patch : ajouter nom de profil lisible
            config = "# WiseVPN - " + server.countryLong + " (" + server.ip + ")\n" + config;

            // Écrire sur disque dans cache privé
            File dir  = new File(activity.getCacheDir(), "ovpn");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "wisevpn_" + server.ip.replace(".", "_") + ".ovpn");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(config.getBytes("UTF-8"));
            fos.close();

            // 2. Intent vers ics-openvpn — action CONFIGURE
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName(ICS_PACKAGE,
                "de.blinkt.openvpn.api.ConfirmDialog");

            // Passer le contenu du profil directement (pas besoin de FileProvider)
            intent.putExtra("de.blinkt.openvpn.api.profileName",
                "WiseVPN-" + server.countryShort);
            intent.putExtra("de.blinkt.openvpn.api.profileConfig", config);

            activity.startActivityForResult(intent, REQUEST_IMPORT);
            Log.d(TAG, "ics-openvpn lancé pour " + server.ip);

        } catch (Exception e) {
            Log.e(TAG, "startVpn error: " + e.getMessage());
            // Fallback : ouvrir ics-openvpn directement sans profil
            Intent fallback = activity.getPackageManager()
                .getLaunchIntentForPackage(ICS_PACKAGE);
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(fallback);
            }
        }
    }

    // ── Déconnecter ──────────────────────────────────────────────────────

    public static void stopVpn(Context ctx) {
        try {
            Intent intent = new Intent();
            intent.setClassName(ICS_PACKAGE,
                "de.blinkt.openvpn.api.ExternalOpenVPNService");
            intent.setAction("de.blinkt.openvpn.api.DISCONNECT");
            ctx.startService(intent);
        } catch (Exception e) {
            Log.w(TAG, "stopVpn: " + e.getMessage());
        }
    }
}
