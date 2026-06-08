package com.wdesign.wisevpn;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VpnGateApi {

    private static final String TAG = "VpnGateApi";
    private static final String API_URL = "https://www.vpngate.net/api/iphone/";
    private static final String MIRROR_URL = "http://168.126.63.1/api/iphone/";

    // Utilisation d'un Executor moderne à la place d'AsyncTask pour éviter les blocages de threads
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(List<ServerModel> servers);
        void onError(String message);
    }

    public static void fetchServers(Callback callback) {
        executor.execute(() -> {
            String errorMsg = "Erreur de connexion aux serveurs";
            List<ServerModel> list = fetchFrom(API_URL);
            
            if (list == null || list.isEmpty()) {
                Log.w(TAG, "API principale échouée ou bloquée, tentative sur le miroir...");
                list = fetchFrom(MIRROR_URL);
            }

            if (list != null && !list.isEmpty()) {
                final List<ServerModel> resultList = list;
                mainHandler.post(() -> callback.onSuccess(resultList));
            } else {
                mainHandler.post(() -> callback.onError("Impossible de charger la liste des serveurs VPN."));
            }
        });
    }

    private static List<ServerModel> fetchFrom(String urlString) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000); // 15 secondes max pour se connecter
            conn.setReadTimeout(15000);    // 15 secondes max pour lire les données
            
            // CRITIQUE : Ajout d'un User-Agent pour éviter que VPN Gate rejette ou ignore la requête
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "*/*");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Erreur HTTP sur " + urlString + " : " + responseCode);
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            List<ServerModel> servers = new ArrayList<>();
            String line;
            boolean isHeaderSkipped = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Ignorer les lignes de commentaires ou vides
                if (line.startsWith("*") || line.isEmpty()) {
                    continue;
                }
                // Ignorer la ligne d'en-tête du fichier CSV (HostName,IP,Score,etc.)
                if (!isHeaderSkipped) {
                    isHeaderSkipped = true;
                    continue;
                }

                ServerModel s = parseCsvLine(line);
                if (s != null) {
                    servers.add(s);
                }
            }
            return servers;

        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du fetch depuis " + urlString, e);
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static ServerModel parseCsvLine(String line) {
        try {
            String[] tokens = line.split(",");
            if (tokens.length < 15) return null; // S'assurer que la ligne contient toutes les infos

            ServerModel s = new ServerModel();
            s.hostName        = tokens[0];
            s.ip              = tokens[1];
            s.ping            = parseLong(tokens[4]);
            s.speed           = parseLong(tokens[5]);
            s.countryLong     = tokens[6];
            s.countryShort    = tokens[7];
            s.numVpnSessions  = parseInt(tokens[8]);
            s.totalUsers      = parseLong(tokens[13]);
            s.ovpnConfigBase64 = tokens[14]; // Le fichier .ovpn brut en Base64

            // Parsing des ports de secours à partir du fichier de config si présents
            if (s.ovpnConfigBase64 != null && !s.ovpnConfigBase64.isEmpty()) {
                s.supportsOpenVpnTcp = true; // Par défaut VPN Gate fournit principalement du TCP
                try {
                    String decoded = new String(android.util.Base64.decode(
                            s.ovpnConfigBase64, android.util.Base64.DEFAULT));
                    for (String l : decoded.split("\n")) {
                        if (l.startsWith("remote ")) {
                            String[] parts = l.trim().split("\\s+");
                            if (parts.length >= 3) {
                                s.openVpnTcpPort = Integer.parseInt(parts[2]);
                            }
                            if (parts.length >= 4 && parts[3].equalsIgnoreCase("udp")) {
                                s.supportsOpenVpnUdp = true;
                                s.openVpnUdpPort = s.openVpnTcpPort;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            return s;

        } catch (Exception e) {
            Log.w(TAG, "Ligne CSV corrompue ignorée : " + e.getMessage());
            return null;
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
    
    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}