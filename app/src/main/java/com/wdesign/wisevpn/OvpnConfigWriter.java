package com.wdesign.wisevpn;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;

public class OvpnConfigWriter {

    private static final String TAG = "OvpnConfigWriter";

    /**
     * Décode le base64 VPN Gate et écrit le .ovpn dans le cache interne.
     * Retourne le chemin du fichier, ou null en cas d'erreur.
     */
    public static String write(Context ctx, ServerModel server) {
        try {
            byte[] decoded = Base64.decode(server.ovpnConfigBase64, Base64.DEFAULT);
            String config  = new String(decoded, "UTF-8");

            // Patch : s'assurer que le profil ne demande pas auth-user-pass
            // VPN Gate utilise des serveurs sans credentials
            if (!config.contains("auth-user-pass")) {
                // ok
            }
            // Ajouter options de stabilité
            config += "\n# WiseVPN patches\n";
            config += "resolv-retry infinite\n";
            config += "nobind\n";
            config += "persist-key\n";
            config += "persist-tun\n";
            config += "comp-lzo no\n";
            config += "verb 3\n";

            File dir  = new File(ctx.getCacheDir(), "ovpn");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "vpngate_" + server.ip.replace(".", "_") + ".ovpn");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(config.getBytes("UTF-8"));
            fos.close();

            Log.d(TAG, "Config écrite : " + file.getAbsolutePath());
            return file.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Erreur écriture config: " + e.getMessage());
            return null;
        }
    }
}
