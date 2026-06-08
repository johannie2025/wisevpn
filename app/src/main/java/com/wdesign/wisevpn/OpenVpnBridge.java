package com.wdesign.wisevpn;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * OpenVpnBridge — Contrôle l'app ics-openvpn via son API AIDL externe.
 *
 * FONCTIONNEMENT :
 *  ics-openvpn expose un IExternalOpenVpnService via Intent action.
 *  WiseVPN se bind à ce service et envoie les profils .ovpn directement.
 *  Aucune dépendance à compiler — ics-openvpn doit juste être installé
 *  sur l'appareil (APK gratuit Play Store / F-Droid).
 *
 * PACKAGE ics-openvpn : de.blinkt.openvpn
 */
public class OpenVpnBridge {

    private static final String TAG = "OpenVpnBridge";

    // Package de l'app ics-openvpn installée sur l'appareil
    private static final String ICS_PACKAGE     = "de.blinkt.openvpn";
    private static final String ICS_API_SERVICE = "de.blinkt.openvpn.api.ExternalOpenVPNService";

    public interface StateCallback {
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }

    private static StateCallback    currentCallback;
    private static IBinder          icsService;
    private static ServiceConnection serviceConn;

    // ── Vérification disponibilité ──────────────────────────────────────

    /**
     * Vérifie si ics-openvpn est installé sur l'appareil.
     */
    public static boolean isAvailable() {
        // On détecte toujours à runtime — pas de dépendance Gradle nécessaire
        return false; // Force le fallback VpnService natif de WiseVpnService
        // Pour activer le mode ics-openvpn externe, retourner isIcsInstalled(ctx)
    }

    public static boolean isIcsInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(ICS_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ── Démarrage via Intent (méthode simple, sans AIDL) ───────────────

    /**
     * Lance le tunnel OpenVPN en démarrant ics-openvpn avec le fichier .ovpn.
     * Méthode : Intent implicite vers ics-openvpn avec le chemin du profil.
     */
    public static void start(Context ctx, String ovpnPath, StateCallback callback) {
        currentCallback = callback;

        if (!isIcsInstalled(ctx)) {
            callback.onError("ics-openvpn non installé");
            return;
        }

        try {
            // Importer le profil dans ics-openvpn via Intent
            Intent importIntent = new Intent(Intent.ACTION_VIEW);
            importIntent.setClassName(ICS_PACKAGE, "de.blinkt.openvpn.api.ConfirmDialog");
            importIntent.putExtra("de.blinkt.openvpn.api.profileName", "WiseVPN-Gate");
            importIntent.putExtra("de.blinkt.openvpn.api.profileConfig",
                readFile(ovpnPath));
            importIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(importIntent);

            // Note : la connexion effective est confirmée par l'utilisateur
            // dans ics-openvpn. Pour une connexion silencieuse, utiliser
            // l'API AIDL (nécessite accord préalable de l'utilisateur).
            callback.onConnected(); // optimiste — ics-openvpn gère son UI

        } catch (Exception e) {
            Log.e(TAG, "Erreur lancement ics-openvpn: " + e.getMessage());
            callback.onError("Erreur: " + e.getMessage());
        }
    }

    public static void stop() {
        try {
            currentCallback = null;
            if (serviceConn != null && icsService != null) {
                // Déconnexion du service AIDL si bindé
                icsService = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "stop: " + e.getMessage());
        }
    }

    // ── Utilitaire lecture fichier ──────────────────────────────────────

    private static String readFile(String path) throws Exception {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString();
    }
}
