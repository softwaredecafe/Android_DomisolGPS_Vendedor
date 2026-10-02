package com.example.domisolgpi;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.gms.location.Priority;

public class LocationService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String idVendedor = "0";
    private static final String CHANNEL_ID = "DoMiSol_Tracking_Channel";

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult.getLastLocation() != null) {
                    double lat = locationResult.getLastLocation().getLatitude();
                    double lng = locationResult.getLastLocation().getLongitude();

                    Log.d("GPS_FONDO", "Ubicación detectada en fondo: " + lat + ", " + lng);

                    Intent intentUI = new Intent("ACTUALIZAR_UBICACION");
                    intentUI.putExtra("lat", lat);
                    intentUI.putExtra("lng", lng);
                    LocalBroadcastManager.getInstance(LocationService.this).sendBroadcast(intentUI);

                    if (!idVendedor.equals("0")) {
                        enviarUbicacionAlServidor(lat, lng);
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("ID_VENDEDOR")) {
            idVendedor = intent.getStringExtra("ID_VENDEDOR");
        }

        crearCanalNotificacion();

        Notification notificacion = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DoMiSol Field Control")
                .setContentText("Rastreo de jornada activo")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true) 
                .build();

        startForeground(1, notificacion);
        solicitarUbicacionContinua();

        return START_STICKY;
    }

    private void solicitarUbicacionContinua() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID,
                    "Rastreo de Vendedores",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private void enviarUbicacionAlServidor(double lat, double lng) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.net.URL url = new java.net.URL("http://10.163.121.72:3000/api/ubicacion");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                String jsonInputString = "{\"usuario_id\": " + idVendedor + ", \"latitud\": " + lat + ", \"longitud\": " + lng + "}";

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                Log.d("GPS_FONDO", "Ubicación enviada al servidor. Código HTTP: " + code);

            } catch (Exception e) {
                Log.e("GPS_FONDO", "Error al enviar ubicación: " + e.getMessage());
            }
        });
    }

}