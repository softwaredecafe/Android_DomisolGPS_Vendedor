package com.example.domisolgpi;

import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class WebAppInterface {
    MainActivity mActivity;

    WebAppInterface(MainActivity activity) {
        mActivity = activity;
    }

    @JavascriptInterface
    public void iniciarServicioRastreo(String idVendedor) {
        mActivity.runOnUiThread(() -> mActivity.iniciarRastreoEnFondo(idVendedor));
    }
    @JavascriptInterface
    public void solicitarGPSNativo() {
        mActivity.runOnUiThread(() -> mActivity.buscarUbicacionReal());
    }

    @JavascriptInterface
    public void mostrarMensajeNativo(String mensaje) {
        Toast.makeText(mActivity, "Java dice: " + mensaje, Toast.LENGTH_SHORT).show();
    }
}