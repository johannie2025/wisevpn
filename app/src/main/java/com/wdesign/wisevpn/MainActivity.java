package com.wdesign.wisevpn;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 100;

    // UI
    private RecyclerView  recycler;
    private ProgressBar   progressBar;
    private TextView      tvStatus, tvStatusDetail, tvBtnLabel;
    private Button        btnConnect;
    private View          statusCard;

    // Data
    private ServerAdapter   adapter;
    private List<ServerModel> servers = new ArrayList<>();
    private ServerModel     selectedServer;
    private ServerModel     connectedServer;

    // State
    private String currentState = WiseVpnService.STATE_DISCONNECTED;

    // Broadcast receiver pour états VPN
    private final BroadcastReceiver vpnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String state = intent.getStringExtra(WiseVpnService.EXTRA_STATE);
            String error = intent.getStringExtra(WiseVpnService.EXTRA_ERROR_MSG);
            updateUiState(state, error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupRecycler();
        setupButtons();
        loadServers();
    }

    private void bindViews() {
        recycler       = findViewById(R.id.recycler_servers);
        progressBar    = findViewById(R.id.progress_bar);
        tvStatus       = findViewById(R.id.tv_status);
        tvStatusDetail = findViewById(R.id.tv_status_detail);
        btnConnect     = findViewById(R.id.btn_connect);
        statusCard     = findViewById(R.id.status_card);
    }

    private void setupRecycler() {
        adapter = new ServerAdapter(servers, server -> {
            selectedServer = server;
            updateConnectButton();
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
    }

    private void setupButtons() {
        btnConnect.setOnClickListener(v -> {
            if (currentState.equals(WiseVpnService.STATE_CONNECTED) ||
                currentState.equals(WiseVpnService.STATE_CONNECTING)) {
                disconnectVpn();
            } else {
                if (selectedServer == null && !servers.isEmpty()) {
                    selectedServer = servers.get(0); // auto-select meilleur serveur
                    adapter.setSelected(0);
                }
                if (selectedServer == null) {
                    Toast.makeText(this, "Sélectionnez un serveur", Toast.LENGTH_SHORT).show();
                    return;
                }
                requestVpnPermission();
            }
        });

        Button btnRefresh = findViewById(R.id.btn_refresh);
        btnRefresh.setOnClickListener(v -> loadServers());
    }

    private void loadServers() {
        progressBar.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        tvStatus.setText("Chargement des serveurs VPN Gate...");

        VpnGateApi.fetchServers(new VpnGateApi.Callback() {
            @Override
            public void onSuccess(List<ServerModel> result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    recycler.setVisibility(View.VISIBLE);
                    servers.clear();
                    servers.addAll(result);
                    adapter.notifyDataSetChanged();
                    tvStatus.setText(result.size() + " serveurs disponibles");

                    // Auto-sélect meilleur serveur
                    if (!servers.isEmpty()) {
                        selectedServer = servers.get(0);
                        adapter.setSelected(0);
                    }
                    updateConnectButton();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Erreur: " + message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void requestVpnPermission() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            // Android demande permission VPN à l'utilisateur
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            // Permission déjà accordée
            connectVpn(selectedServer);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                connectVpn(selectedServer);
            } else {
                Toast.makeText(this, "Permission VPN refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectVpn(ServerModel server) {
        // Écrire config .ovpn sur disque
        String ovpnPath = OvpnConfigWriter.write(this, server);
        if (ovpnPath == null) {
            Toast.makeText(this, "Erreur lecture config VPN", Toast.LENGTH_SHORT).show();
            return;
        }

        connectedServer = server;
        updateUiState(WiseVpnService.STATE_CONNECTING, null);

        Intent intent = new Intent(this, WiseVpnService.class);
        intent.setAction(WiseVpnService.ACTION_CONNECT);
        intent.putExtra(WiseVpnService.EXTRA_OVPN_PATH,   ovpnPath);
        intent.putExtra(WiseVpnService.EXTRA_SERVER_IP,   server.ip);
        intent.putExtra(WiseVpnService.EXTRA_SERVER_NAME, server.countryLong);
        startService(intent);
    }

    private void disconnectVpn() {
        Intent intent = new Intent(this, WiseVpnService.class);
        intent.setAction(WiseVpnService.ACTION_DISCONNECT);
        startService(intent);
    }

    private void updateUiState(String state, String errorMsg) {
        currentState = state;
        switch (state) {
            case WiseVpnService.STATE_CONNECTING:
                tvStatus.setText("Connexion en cours...");
                tvStatusDetail.setText(connectedServer != null ?
                    connectedServer.getCountryFlag() + " " + connectedServer.countryLong : "");
                btnConnect.setText("Annuler");
                btnConnect.setBackgroundTintList(
                    getColorStateList(android.R.color.holo_orange_dark));
                statusCard.setBackgroundTintList(
                    getColorStateList(android.R.color.holo_orange_light));
                break;

            case WiseVpnService.STATE_CONNECTED:
                tvStatus.setText("✅ VPN Connecté");
                tvStatusDetail.setText(connectedServer != null ?
                    connectedServer.getCountryFlag() + " " + connectedServer.countryLong
                    + "  •  " + connectedServer.ip : "");
                btnConnect.setText("Déconnecter");
                btnConnect.setBackgroundTintList(
                    getColorStateList(android.R.color.holo_red_dark));
                statusCard.setBackgroundTintList(
                    getColorStateList(android.R.color.holo_green_light));
                break;

            case WiseVpnService.STATE_DISCONNECTED:
                tvStatus.setText("🔴 Non connecté");
                tvStatusDetail.setText("Sélectionnez un serveur et appuyez sur Connecter");
                btnConnect.setText("Connecter");
                btnConnect.setBackgroundTintList(
                    getColorStateList(android.R.color.holo_blue_dark));
                statusCard.setBackgroundTintList(
                    getColorStateList(android.R.color.darker_gray));
                connectedServer = null;
                break;

            case WiseVpnService.STATE_ERROR:
                tvStatus.setText("❌ Erreur VPN");
                tvStatusDetail.setText(errorMsg != null ? errorMsg : "Erreur inconnue");
                btnConnect.setText("Réessayer");
                btnConnect.setBackgroundTintList(
                    getColorStateList(android.R.color.holo_blue_dark));
                statusCard.setBackgroundTintList(
                    getColorStateList(android.R.color.holo_red_light));
                break;
        }
        updateConnectButton();
    }

    private void updateConnectButton() {
        boolean canConnect = selectedServer != null &&
            currentState.equals(WiseVpnService.STATE_DISCONNECTED);
        boolean isConnectedOrConnecting =
            currentState.equals(WiseVpnService.STATE_CONNECTED) ||
            currentState.equals(WiseVpnService.STATE_CONNECTING);
        btnConnect.setEnabled(canConnect || isConnectedOrConnecting);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(vpnReceiver,
            new IntentFilter(WiseVpnService.BROADCAST_STATE),
            Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(vpnReceiver); } catch (Exception ignored) {}
    }
}
