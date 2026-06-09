package com.wdesign.wisevpn;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VpnGateApi {

    private static final String TAG = "VpnGateApi";

    // Miroirs VPN Gate — on essaie dans l'ordre
    private static final String[] ENDPOINTS = {
        "https://www.vpngate.net/api/iphone/",
        "https://vpngate.net/api/iphone/",
        "http://www.vpngate.net/api/iphone/"
    };

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(List<ServerModel> servers);
        void onError(String message);
    }

    public static void fetchServers(Callback callback) {
        executor.execute(() -> {
            List<ServerModel> result = null;
            String lastError = "Aucun serveur disponible";

            for (String endpoint : ENDPOINTS) {
                Log.d(TAG, "Tentative: " + endpoint);
                try {
                    result = fetchFrom(endpoint);
                    if (result != null && !result.isEmpty()) break;
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.w(TAG, "Échec " + endpoint + ": " + e.getMessage());
                }
            }

            if (result != null && !result.isEmpty()) {
                // Trier : ping croissant, vitesse décroissante
                Collections.sort(result, (a, b) -> {
                    if (a.ping != b.ping) return Long.compare(a.ping, b.ping);
                    return Long.compare(b.speed, a.speed);
                });
                final List<ServerModel> finalResult = result;
                mainHandler.post(() -> callback.onSuccess(finalResult));
            } else {
                final String finalError = lastError;
                mainHandler.post(() -> callback.onError("Impossible de charger VPN Gate: " + finalError));
            }
        });
    }

    private static List<ServerModel> fetchFrom(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "*/*");
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            Log.d(TAG, urlStr + " → HTTP " + code);
            if (code != 200) return null;

            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));

            List<ServerModel> servers = new ArrayList<>();
            String line;

            /*
             * Format CSV VPN Gate :
             *   Ligne 1 : "*vpn_servers"  (commentaire étoile)
             *   Ligne 2 : "#HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,
             *              NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,
             *              Operator,Message,OpenVPN_ConfigData_Base64"
             *   Ligne 3+ : données
             *   Dernière : "*"
             */
            while ((line = br.readLine()) != null) {
                if (line.startsWith("*")) continue;   // commentaires
                if (line.startsWith("#")) continue;   // header colonnes
                if (line.trim().isEmpty()) continue;

                ServerModel s = parseLine(line);
                if (s != null) servers.add(s);
            }
            br.close();
            Log.d(TAG, "Parsé " + servers.size() + " serveurs depuis " + urlStr);
            return servers;

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static ServerModel parseLine(String line) {
        try {
            // Split limité à 15 pour garder le base64 intact (peut contenir des +/=)
            String[] c = line.split(",", 15);
            if (c.length < 15) return null;

            String b64 = c[14].trim();
            if (b64.isEmpty()) return null;

            ServerModel s   = new ServerModel();
            s.hostName      = c[0].trim();
            s.ip            = c[1].trim();
            s.ping          = toLong(c[3]);
            s.speed         = toLong(c[4]);
            s.countryLong   = c[5].trim();
            s.countryShort  = c[6].trim();
            s.numVpnSessions= (int) toLong(c[7]);
            s.totalUsers    = toLong(c[9]);
            s.ovpnConfigBase64 = b64;
            s.supportsOpenVpnTcp = true;
            s.openVpnTcpPort = 1194;
            s.openVpnUdpPort = 1194;

            // Extraire le port réel depuis la config décodée
            try {
                String cfg = new String(Base64.decode(b64, Base64.DEFAULT), "UTF-8");
                for (String l : cfg.split("\n")) {
                    l = l.trim();
                    if (l.startsWith("remote ")) {
                        String[] p = l.split("\\s+");
                        if (p.length >= 3) s.openVpnTcpPort = (int) toLong(p[2]);
                        s.supportsOpenVpnUdp = p.length >= 4 && "udp".equalsIgnoreCase(p[3]);
                        if (s.supportsOpenVpnUdp) s.openVpnUdpPort = s.openVpnTcpPort;
                        break;
                    }
                }
            } catch (Exception ignored) {}

            // Ignorer serveurs sans IP valide
            if (s.ip.isEmpty() || !s.ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return null;

            return s;

        } catch (Exception e) {
            Log.v(TAG, "Skip ligne: " + e.getMessage());
            return null;
        }
    }

    private static long toLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
}
