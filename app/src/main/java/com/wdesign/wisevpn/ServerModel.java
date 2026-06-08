package com.wdesign.wisevpn;

public class ServerModel {
    public String hostName;
    public String ip;
    public String countryLong;
    public String countryShort;
    public int numVpnSessions;
    public long ping;
    public long speed;         // bps
    public long totalUsers;
    public String ovpnConfigBase64;  // config OpenVPN encodée base64
    public boolean supportsOpenVpnTcp;
    public boolean supportsOpenVpnUdp;
    public int openVpnTcpPort;
    public int openVpnUdpPort;

    public ServerModel() {}

    public String getSpeedLabel() {
        if (speed >= 1_000_000_000) return String.format("%.1f Gbps", speed / 1_000_000_000.0);
        if (speed >= 1_000_000)     return String.format("%.1f Mbps", speed / 1_000_000.0);
        if (speed >= 1_000)         return String.format("%.1f Kbps", speed / 1_000.0);
        return speed + " bps";
    }

    public String getPingLabel() {
        return ping + " ms";
    }

    public String getCountryFlag() {
        // Emoji drapeau depuis code pays ISO
        if (countryShort == null || countryShort.length() != 2) return "🌐";
        int flagOffset = 0x1F1E6 - 'A';
        int firstChar  = countryShort.toUpperCase().charAt(0) + flagOffset;
        int secondChar = countryShort.toUpperCase().charAt(1) + flagOffset;
        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }
}
