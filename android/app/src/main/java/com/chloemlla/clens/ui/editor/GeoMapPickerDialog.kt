package com.chloemlla.clens.ui.editor

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

/**
 * Lightweight OSM/Leaflet map picker hosted in WebView.
 * Tap map or drag marker to choose a point; confirm returns lat/lng.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GeoMapPickerDialog(
    initialLat: Double,
    initialLng: Double,
    onDismiss: () -> Unit,
    onConfirm: (lat: Double, lng: Double) -> Unit,
) {
    var lat by remember(initialLat) { mutableStateOf(initialLat.coerceIn(-90.0, 90.0)) }
    var lng by remember(initialLng) { mutableStateOf(initialLng.coerceIn(-180.0, 180.0)) }

    BackHandler(onBack = onDismiss)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("地图选点") },
        text = {
            Column {
                Text("点击地图或拖动标记选择坐标。需要网络加载 OSM 图块。")
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onPicked(pickedLat: Double, pickedLng: Double) {
                                        lat = pickedLat
                                        lng = pickedLng
                                    }
                                },
                                "ClensGeo",
                            )
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL(
                                "https://unpkg.com/",
                                buildLeafletHtml(lat, lng),
                                "text/html",
                                "utf-8",
                                null,
                            )
                        }
                    },
                    update = { /* keep instance */ },
                )
                Text(
                    text = String.format(Locale.US, "当前：lat=%.6f, lng=%.6f", lat, lng),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(lat, lng) }) { Text("使用该点") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun buildLeafletHtml(lat: Double, lng: Double): String {
    val safeLat = String.format(Locale.US, "%.6f", lat.coerceIn(-90.0, 90.0))
    val safeLng = String.format(Locale.US, "%.6f", lng.coerceIn(-180.0, 180.0))
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0"/>
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>html,body,#map{height:100%;margin:0;padding:0}</style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var map = L.map('map').setView([$safeLat, $safeLng], 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '&copy; OpenStreetMap'
            }).addTo(map);
            var marker = L.marker([$safeLat, $safeLng], {draggable:true}).addTo(map);
            function emit() {
              var p = marker.getLatLng();
              if (window.ClensGeo && ClensGeo.onPicked) {
                ClensGeo.onPicked(p.lat, p.lng);
              }
            }
            map.on('click', function(e) {
              marker.setLatLng(e.latlng);
              emit();
            });
            marker.on('dragend', emit);
            emit();
          </script>
        </body>
        </html>
    """.trimIndent()
}
