package com.example.tirocinio;

import static android.app.PendingIntent.getActivity;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static java.security.AccessController.getContext;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity  {

    // Nome del file SharedPreferences
    public static final String MyPREFERENCES = "MyPreferences";
    // chiave per l'UUID nello SharedPreferences
    public static final String UUID_KEY = "deviceUUID";
    // Nome del file da cui leggere le informazioni della CPU
    public static final String CPUINFO_FILE_NAME = "/proc/cpuinfo";
    // Handler per gestire l'esecuzione periodica
    Handler handler = new Handler();
    // Intervallo di aggiornamento in millisecondi
    final int UPDATE_INTERVAL = 5000;
    // Runnable per eseguire le funzioni di monitoraggio ogni UPDATE_INTERVAL secondi
    final Runnable r= new Runnable(){
        public void run(){

            getId(null);
            batteryMonitoring(null);
            memoryMonitoring(null);
            networkMonitoring(null);
            cpuMonitoring(null);

            handler.postDelayed(r, UPDATE_INTERVAL);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Avvia il runnable appena l'activity viene creata
        handler.post(r);

    }

    public void getId(PhoneMetrics phoneMetricsIn){
        // Metodo per ottenere e memorizzare l'UUID del dispositivo

        // Ottiene le SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(MyPREFERENCES, MODE_PRIVATE);
        // Recupera l'UUID se già esistente, altrimenti genera un nuovo UUID e lo salva
        String uuid = sharedPreferences.getString(UUID_KEY, null);
        if (uuid == null){
            uuid = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(UUID_KEY, uuid);
            editor.apply();
        }

        TextView battery_view = findViewById(R.id.id);
        battery_view.setText(String.format("Device id: %s", uuid));

        if(phoneMetricsIn != null){
            phoneMetricsIn.setId(uuid);
        }
    }

    public void batteryMonitoring(PhoneMetrics phoneMetricsIn){
        // Metodo per monitorare il livello di batteria

        // Registra un BroadcastReceiver per monitorare lo stato della batteria
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);

        // Ottiene il livello attuale della batteria
        int level = 0;
        int scale = 1;
        if (batteryStatus != null) {
            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        }
        float batteryPct = level * 100 / (float)scale;

        TextView battery_view = findViewById(R.id.battery);
        battery_view.setText(String.format("Available battery: %s", Float.toString(batteryPct)));

        if(phoneMetricsIn != null){
            phoneMetricsIn.setBattery(Float.toString(batteryPct));
        }
    }

    public void memoryMonitoring(PhoneMetrics phoneMetricsIn) {
        // Metodo per monitorare la memoria

        // Utilizza la classe ActivityManager per monitorare la memoria
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        // Memoria RAM disponibile e totale (byte)
        long availableMemory = memoryInfo.availMem;
        long totalMemory = memoryInfo.totalMem;

        TextView memory_view = findViewById(R.id.memory);
        memory_view.setText(String.format("Available memory: %s", Long.toString(availableMemory)));
        memory_view.append("\nTotal memory: " + Long.toString(totalMemory));

        if (phoneMetricsIn != null) {
            phoneMetricsIn.setAvailableMemory(Long.toString(availableMemory));
            phoneMetricsIn.setTotalMemory(Long.toString(totalMemory));
        }
    }

    public void networkMonitoring(PhoneMetrics phoneMetricsIn) {
        // Metodo per monitorare la rete

        // Utilizza la classe ConnectivityManager per monitorare la connessione di rete
        ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
        Network currentNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(currentNetwork);

        // Ricava larghezza di banda di download e upload in kilobit per secondo
        TextView bandwidth_view = findViewById(R.id.netBandwidth);
        if (caps == null){
            bandwidth_view.setText("Downstream bandwidth in Kbps: unknown");
            bandwidth_view.append("\nUpstream bandwidth in Kbps: unknown");
        }
        else{
            int downStreamBandwidth = caps.getLinkDownstreamBandwidthKbps();
            int upStreamBandwidth = caps.getLinkUpstreamBandwidthKbps();
            bandwidth_view.setText(String.format("Downstream bandwidth in Kbps: %s", String.valueOf(downStreamBandwidth)));
            bandwidth_view.append("\nUpstream bandwidth in Kbps: " + String.valueOf(upStreamBandwidth));

            if (phoneMetricsIn != null){
                phoneMetricsIn.setDownstreamBandwidth(String.valueOf(downStreamBandwidth));
                phoneMetricsIn.setUpstreamBandwidth(String.valueOf(upStreamBandwidth));
            }
        }

        // Ricava la forza del segnale (dBm), se disponibile
        TextView signal_strength_view = findViewById(R.id.netSignalStrength);
        if (caps == null){
            signal_strength_view.setText("Signal strength: unknown");
        }
        else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                int signalStrength = caps.getSignalStrength();
                signal_strength_view.setText(String.format("Signal strength (dBm): %s", String.valueOf(signalStrength)));

                if (phoneMetricsIn != null) {
                    phoneMetricsIn.setSignalStrength(String.valueOf(signalStrength));
                }
            }
        }

        // Ricava altre capacità della rete
        TextView capabilities_view = findViewById(R.id.netCapabilities);
        if (caps == null){
            capabilities_view.setText("The network is able to reach internet: unknown");
            capabilities_view.append("\nThe network is not congested: unknown");
            capabilities_view.append("\nThe network is not suspended: unknown");
        }
        else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Boolean isReachingInternet = caps.hasCapability(NET_CAPABILITY_INTERNET);
                Boolean isNotCongested = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);
                Boolean isNotSuspended = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
                capabilities_view.setText(String.format("The network is able to reach internet: %s", String.valueOf(isReachingInternet)));
                capabilities_view.append("\nThe network is not congested: " + String.valueOf(isNotCongested));
                capabilities_view.append("\nThe network is not suspended: " + String.valueOf(isNotSuspended));

                if (phoneMetricsIn != null) {
                    phoneMetricsIn.setIsReachingInternet(String.valueOf(isReachingInternet));
                    phoneMetricsIn.setIsNotCongested(String.valueOf(isNotCongested));
                    phoneMetricsIn.setIsNotSuspended(String.valueOf(isNotSuspended));
                }
            }
        }

    }

    public void cpuMonitoring(PhoneMetrics phoneMetricsIn){
        // Metodo per monitorare la CPU

        // Numero di core disponibili
        int numOfCores = Runtime.getRuntime().availableProcessors();

        // ArrayList per frequenze, dimensioni della cache e flags supportati dei core
        ArrayList<String> cpuFrequencies = new ArrayList<>();
        ArrayList<String> cpuCacheSizes = new ArrayList<>();
        ArrayList<String> cpuFlags = new ArrayList<>();

        try {
            // Legge il file CPUINFO_FILE_NAME
            BufferedReader br = new BufferedReader(new FileReader(CPUINFO_FILE_NAME));
            String line;
            String currentFrequency = null;
            String currentCacheSize = null;
            String currentFlags = null;
            // Scorre riga per riga il file CPUINFO_FILE_NAME
            while ((line = br.readLine()) != null) {
                if (line.startsWith("cpu MHz")) {
                    // Estrae la frequenza (in MHz) e la aggiunge all'ArrayList
                    currentFrequency = line.split(":\\s+", 2)[1].trim();
                    cpuFrequencies.add(currentFrequency);
                }
                else if (line.startsWith("cache size")) {
                    // Estrae la dimensione della cache (in KB) e la aggiunge all'ArrayList
                    currentCacheSize = line.split(":\\s+", 2)[1].trim();
                    cpuCacheSizes.add(currentCacheSize);
                }
                else if (line.startsWith("flags")) {
                    // Estrae i flags e li aggiunge all'ArrayList
                    currentFlags = line.split(":\\s+", 2)[1].trim();
                    cpuFlags.add(currentFlags);
                }
            }
            br.close();
        } catch (IOException e){
            Log.d("CPU INFO", "Errore nella lettura di " + CPUINFO_FILE_NAME);
        }

        TextView cpu_view = findViewById(R.id.cpu);
        cpu_view.setText(String.format("Number of Cores: " + numOfCores));
        // Separate da virgole, si trovano in sequenza le informazioni su frequenza, dimensione dalla cache e flags dei core della cpu (in ordine dal primo all'ultimo)
        cpu_view.append("\nCPU cores frequencies (MHz): " + TextUtils.join(", ", cpuFrequencies));
        cpu_view.append("\nCPU cores cache sizes (KB): " +  TextUtils.join(", ", cpuCacheSizes));
        cpu_view.append("\nCPU cores flags: " + TextUtils.join(", ", cpuFlags));

        if(phoneMetricsIn != null){
            phoneMetricsIn.setNumOfCores(numOfCores);
            phoneMetricsIn.setCpuFrequencies(TextUtils.join(", ", cpuFrequencies));
            phoneMetricsIn.setCpuCacheSizes(TextUtils.join(", ", cpuCacheSizes));
            phoneMetricsIn.setCpuFlags(TextUtils.join(", ", cpuFlags));
        }
    }

    public void sendMetrics(View v){
        // Imposta un listener sul pulsante per inviare i dati
        Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Crea un nuovo oggetto PhoneMetrics
                PhoneMetrics phoneMetrics = new PhoneMetrics();
                // Chiama i metodi di monitoraggio fornendo come parametro l'oggetto phoneMetrics iin cui vengono salvate le metriche del dispositivo
                getId(phoneMetrics);
                batteryMonitoring(phoneMetrics);
                memoryMonitoring(phoneMetrics);
                networkMonitoring(phoneMetrics);
                cpuMonitoring(phoneMetrics);

                // Converte l'oggetto PhoneMetrics in una stringa JSON
                Gson gson = new Gson();
                String phoneMetricsString = gson.toJson(phoneMetrics);

                // Controlla se la rete è disponibile
                ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        // Quando la rete è disponibile, crea un thread per inviare i dati al server
                        ExecutorService executor = Executors.newSingleThreadExecutor();

                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                HttpURLConnection client = null;
                                try {
                                    // Ottiene l'URL dal campo di input
                                    EditText serverUrlText = findViewById(R.id.serverUrl);
                                    URL url = new URL(serverUrlText.getText().toString()); //inserire url
                                    // Apre una connessione HTTP con metodo POST
                                    client = (HttpURLConnection) url.openConnection();
                                    client.setRequestMethod("POST");
                                    //impostazione content type e accept type
                                    client.setRequestProperty("Content-Type", "application/json");
                                    client.setRequestProperty("Accept", "application/json");
                                    // Abilita l'invio dei dati
                                    client.setDoOutput(true);

                                    // creazione di un output stream e posting dei dati
                                    try (OutputStream os = client.getOutputStream()) {
                                        byte[] input = phoneMetricsString.getBytes(StandardCharsets.UTF_8);
                                        os.write(input, 0, input.length);
                                    }

                                    // lettura risposta del server
                                    try (BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {

                                        StringBuilder response = new StringBuilder();
                                        String responseLine = null;
                                        while ((responseLine = br.readLine()) != null) {
                                            response.append(responseLine.trim());
                                        }

                                        // Visualizza la risposta del server in una TextView
                                        TextView serverResponseView = findViewById(R.id.serverResponse);
                                        serverResponseView.setText(responseLine);
                                    }

                                } catch (Exception e) {
                                    Log.d("HTTP_POST", "Errore durante l'invio del json");
                                } finally {
                                    // Chiusura della connessione
                                    if (client != null) {
                                        client.disconnect();
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onUnavailable() {
                        // Quando la connessione è persa, logga il messaggio
                        Log.d("STATO DELLA RETE", "Nessuna connessione attiva");
                    }
                };
            };
        });

    }



}

