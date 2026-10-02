package com.example.domisolgpi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int BACKGROUND_LOCATION_REQUEST_CODE = 101;

    private String currentVendedorId = "";

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double lat = intent.getDoubleExtra("lat", 0);
            double lng = intent.getDoubleExtra("lng", 0);
            enviarUbicacionAWeb(lat, lng);
        }
    };

    private final ActivityResultLauncher<IntentSenderRequest> resolucionGPS =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "GPS encendido correctamente", Toast.LENGTH_SHORT).show();
                    iniciarServicioRastreo();
                } else {
                    Toast.makeText(this, "El GPS es obligatorio para iniciar tu ruta", Toast.LENGTH_LONG).show();
                }
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new android.webkit.WebChromeClient());

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");
        webView.loadUrl("file:///android_asset/vendedorLogin.html");

        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, new IntentFilter("ACTUALIZAR_UBICACION"));

        verificarPermisos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarPermisoFondo();
    }

    private void verificarPermisos() {
        String[] permisos;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permisos = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permisos, PERMISSION_REQUEST_CODE);
        } else {
            verificarPermisoFondo();
        }
    }

    private void verificarPermisoFondo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                mostrarDialogoPermisoObligatorio();
            }
        }
    }

    private void mostrarDialogoPermisoObligatorio() {
        new AlertDialog.Builder(this)
                .setTitle("Configuración de GPS Obligatoria")
                .setMessage("Para que DoMiSol GPI funcione y valide tus visitas, requiere acceso a tu ubicación 'Todo el tiempo'.\n\nPor favor, ve a la configuración y cambia el permiso de ubicación a 'Permitir todo el tiempo'.")
                .setPositiveButton("Ir a Ajustes", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setCancelable(false) 
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                
                verificarPermisoFondo();
            } else {
                Toast.makeText(this, "El permiso de ubicación es requerido", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void buscarUbicacionReal() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            verificarSiElGPSEstaEncendido();
        } else {
            verificarPermisos();
        }
    }

    public void iniciarRastreoEnFondo(String idVendedor) {
        this.currentVendedorId = idVendedor; 

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            verificarPermisoFondo();
            return;
        }

        verificarSiElGPSEstaEncendido();
    }

    private void verificarSiElGPSEstaEncendido() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, response -> iniciarServicioRastreo());

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolucionGPS.launch(new IntentSenderRequest.Builder(resolvable.getResolution()).build());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void iniciarServicioRastreo() {
        Intent serviceIntent = new Intent(this, LocationService.class);

        if(currentVendedorId != null && !currentVendedorId.isEmpty()){
            serviceIntent.putExtra("ID_VENDEDOR", currentVendedorId);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void enviarUbicacionAWeb(double lat, double lng) {
        new Thread(() -> {
            String direccionTexto = "Buscando dirección...";
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                List<Address> direcciones = geocoder.getFromLocation(lat, lng, 1);
                if (direcciones != null && !direcciones.isEmpty()) {
                    direccionTexto = direcciones.get(0).getAddressLine(0).replace("'", "");
                }
            } catch (Exception e) {
                direccionTexto = "Coordenadas actualizadas";
            }

            final String finalDireccion = direccionTexto;

            runOnUiThread(() -> {
                String cmd = "javascript:actualizarUbicacionReal(" + lat + ", " + lng + ", '" + finalDireccion + "');";
                webView.evaluateJavascript(cmd, null);
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver);
    }
}