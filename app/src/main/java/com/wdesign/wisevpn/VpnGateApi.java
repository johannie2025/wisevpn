package com.wdesign.wisevpn;

import android.os.AsyncTask;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class VpnGateApi {

    private static final String TAG = "VpnGateApi";
    // API publique VPN Gate — retourne CSV
    private static final String API_URL = "https://www.vpngate.net/api/iphone/";
    // Mirror de secours
    private static final String MIRROR_URL = "http://168.126.63.1/api/iphone/";

    public interface Callback {
        void onSuccess(List<ServerModel> servers);
        void onError(String message);
    }

    public static void fetchServers(Callback callback) {
        new FetchTask(callback).execute();
    }

    @SuppressWarnings("deprecation")
    private static class FetchTask extends AsyncTask<Void, Void, List<ServerModel>> {
        private final Callback callback;
        private String errorMsg;

        FetchTask(Callback cb) { this.callback = cb; }

        @Override
        protected List<ServerModel> doInBackground(Void... v) {
            List<ServerModel> list = fetchFrom(API_URL);
            if (list == null || list.isEmpty()) {
                Log.w(TAG, "API principale échouée, tentative mirror...");
                list = fetchFrom(MIRROR_URL);
            }
            if (list == null) {
                errorMsg = "Impossible de contacter VPN Gate. Vérifiez votre connexion.";
                return null;
            }
            // Trier par ping croissant, puis vitesse décroissante
            Collections.sort(list, (a, b) -> {
                if (a.ping != b.ping) return Long.compare(a.ping, b.ping);
                return Long.compare(b.speed, a.speed);
            });
            return list;
        }

        private List<ServerModel> fetchFrom(String urlStr) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(20_000);
                conn.setRequestProperty("User-Agent", "WiseVPN/1.0 Android");

                if (conn.getResponseCode() != 200) return null;

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                List<ServerModel> servers = new ArrayList<>();
                String line;
                boolean headerSkipped = false;

                while ((line = br.readLine()) != null) {
                    // Ignorer commentaires et header
                    if (line.startsWith("*") || line.trim().isEmpty()) continue;
                    if (!headerSkipped) { headerSkipped = true; continue; } // ligne header CSV

                    ServerModel s = parseCsvLine(line);
                    if (s != null && s.supportsOpenVpnTcp) servers.add(s);
                }
                br.close();
                conn.disconnect();
                return servers;

            } catch (Exception e) {
                Log.e(TAG, "fetchFrom error: " + e.getMessage());
                return null;
            }
        }

        /**
         * Format CSV VPN Gate :
         * #HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,
         * Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,
         * OpenVPN_ConfigData_Base64
         */
        private ServerModel parseCsvLine(String line) {
            try {
                // Le champ OpenVPN_ConfigData_Base64 peut contenir des virgules encodées
                // On split sur max 15 colonnes
                String[] cols = line.split(",", 15);
                if (cols.length < 15) return null;

                ServerModel s = new ServerModel();
                s.hostName      = cols[0].trim();
                s.ip            = cols[1].trim();
                s.ping          = parseLong(cols[3]);
                s.speed         = parseLong(cols[4]);
                s.countryLong   = cols[5].trim();
                s.countryShort  = cols[6].trim();
                s.numVpnSessions= parseInt(cols[7]);
                s.totalUsers    = parseLong(cols[9]);
                s.ovpnConfigBase64 = cols[14].trim();

                // Détecter support OpenVPN depuis la config base64
                s.supportsOpenVpnTcp = !s.ovpnConfigBase64.isEmpty();
                s.supportsOpenVpnUdp = s.supportsOpenVpnTcp;
                s.openVpnTcpPort = 1194;
                s.openVpnUdpPort = 1194;

                // Extraire ports depuis config si possible
                if (s.supportsOpenVpnTcp) {
                    String decoded = new String(android.util.Base64.decode(
                        s.ovpnConfigBase64, android.util.Base64.DEFAULT));
                    for (String l : decoded.split("\n")) {
                        if (l.startsWith("remote ")) {
                            String[] parts = l.trim().split("\\s+");
                            if (parts.length >= 3) {
                                try { s.openVpnTcpPort = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
                            }
                            if (parts.length >= 4 && parts[3].equalsIgnoreCase("udp")) {
                                s.supportsOpenVpnUdp = true;
                                s.openVpnUdpPort = s.openVpnTcpPort;
                            }
                        }
                    }
                }
                return s;

            } catch (Exception e) {
                Log.w(TAG, "parseCsvLine skip: " + e.getMessage());
                return null;
            }
        }

        private long parseLong(String s) {
            try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
        }
        private int parseInt(String s) {
            try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
        }

        @Override
        protected void onPostExecute(List<ServerModel> result) {
            if (result == null) callback.onError(errorMsg != null ? errorMsg : "Erreur inconnue");
            else callback.onSuccess(result);
        }
    }
}
