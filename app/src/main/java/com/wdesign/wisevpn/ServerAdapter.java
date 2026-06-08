package com.wdesign.wisevpn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.VH> {

    public interface OnServerClick {
        void onClick(ServerModel server);
    }

    private final List<ServerModel> servers;
    private final OnServerClick     listener;
    private int selectedPosition = -1;

    public ServerAdapter(List<ServerModel> servers, OnServerClick listener) {
        this.servers  = servers;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.item_server, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ServerModel s = servers.get(position);
        h.flag.setText(s.getCountryFlag());
        h.country.setText(s.countryLong);
        h.ip.setText(s.ip);
        h.ping.setText("⚡ " + s.getPingLabel());
        h.speed.setText("↑ " + s.getSpeedLabel());
        h.sessions.setText("👥 " + s.numVpnSessions);

        h.itemView.setSelected(position == selectedPosition);
        h.itemView.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = h.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            listener.onClick(s);
        });
    }

    @Override public int getItemCount() { return servers.size(); }

    public void setSelected(int position) {
        int prev = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(prev);
        notifyItemChanged(selectedPosition);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView flag, country, ip, ping, speed, sessions;
        VH(View v) {
            super(v);
            flag     = v.findViewById(R.id.tv_flag);
            country  = v.findViewById(R.id.tv_country);
            ip       = v.findViewById(R.id.tv_ip);
            ping     = v.findViewById(R.id.tv_ping);
            speed    = v.findViewById(R.id.tv_speed);
            sessions = v.findViewById(R.id.tv_sessions);
        }
    }
}
