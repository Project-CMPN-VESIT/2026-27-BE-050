package com.example.capstone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.Intent
import android.widget.Toast
import android.widget.Button
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.example.capstone.R
import com.example.capstone.data.local.database.entity.HospitalEntity
import com.example.capstone.data.local.database.entity.ShelterEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

class ShelterClusterer(private val context: Context) {
    private val gridSize = 40 // Very small grid to avoid aggressive clustering

    fun cluster(mapView: MapView, shelters: List<ShelterEntity>, hospitals: List<HospitalEntity>): List<Marker> {
        val projection = mapView.projection
        val bounds = mapView.boundingBox
        val visibleShelters = shelters.filter { bounds.contains(it.latitude, it.longitude) }
        val visibleHospitals = hospitals.filter { bounds.contains(it.latitude, it.longitude) }
        val clusters = mutableMapOf<Pair<Int, Int>, MutableList<Any>>()
        val currentZoom = mapView.zoomLevelDouble

        // Show individual markers much earlier (zoom 13+) to avoid clusters hiding hospitals
        if (currentZoom > 11.5) {
            val markers = mutableListOf<Marker>()
            val shelterIcon = ContextCompat.getDrawable(context, R.drawable.ic_shelter)
            val hospitalIcon = ContextCompat.getDrawable(context, R.drawable.ic_location)
            
            for (shelter in visibleShelters) markers.add(createShelterMarker(mapView, shelter, shelterIcon))
            for (hospital in visibleHospitals) markers.add(createHospitalMarker(mapView, hospital, hospitalIcon))
            return markers
        }
        
        // Add all items to clusters for low zoom levels
        for (shelter in visibleShelters) {
            val geoPoint = GeoPoint(shelter.latitude, shelter.longitude)
            val screenPoint = projection.toPixels(geoPoint, null)
            val key = Pair(screenPoint.x / gridSize, screenPoint.y / gridSize)
            clusters.getOrPut(key) { mutableListOf() }.add(shelter)
        }

        for (hospital in visibleHospitals) {
            val geoPoint = GeoPoint(hospital.latitude, hospital.longitude)
            val screenPoint = projection.toPixels(geoPoint, null)
            val key = Pair(screenPoint.x / gridSize, screenPoint.y / gridSize)
            clusters.getOrPut(key) { mutableListOf() }.add(hospital)
        }
        
        val markers = mutableListOf<Marker>()
        val shelterIcon = ContextCompat.getDrawable(context, R.drawable.ic_shelter)
        val hospitalIcon = ContextCompat.getDrawable(context, R.drawable.ic_location)

        for (clusterItems in clusters.values) {
            if (clusterItems.size == 1) {
                when (val item = clusterItems[0]) {
                    is ShelterEntity -> markers.add(createShelterMarker(mapView, item, shelterIcon))
                    is HospitalEntity -> markers.add(createHospitalMarker(mapView, item, hospitalIcon))
                }
            } else {
                markers.add(createClusterMarker(mapView, clusterItems))
            }
        }
        
        return markers
    }

    private fun createShelterMarker(mapView: MapView, shelter: ShelterEntity, icon: Drawable?): Marker {
        val marker = Marker(mapView)
        marker.position = GeoPoint(shelter.latitude, shelter.longitude)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = icon
        marker.title = shelter.name
        
        val details = "Type: ${shelter.campType}\nDisaster: ${shelter.disasterType}\nCapacity: ${shelter.capacity}\nAddress: ${shelter.address}\n\nTAP BUBBLE TO NAVIGATE"
        marker.snippet = details

        marker.infoWindow = object : MarkerInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, mapView) {
            override fun onOpen(item: Any?) {
                super.onOpen(item)
                val existing = mView.findViewWithTag<Button>("open_maps_button")
                if (existing == null) {
                    val btn = Button(context).apply {
                        text = context.getString(R.string.open_in_maps)
                        tag = "open_maps_button"
                        setOnClickListener {
                            openInGoogleMaps(shelter.latitude, shelter.longitude)
                            close()
                        }
                    }
                    (mView as? ViewGroup)?.addView(btn)
                }
                mView.setOnClickListener {
                    openInGoogleMaps(shelter.latitude, shelter.longitude)
                    close()
                }
            }
        }
        marker.setOnMarkerClickListener { m, _ ->
            m.showInfoWindow()
            mapView.controller.animateTo(m.position)
            true
        }
        return marker
    }

    private fun createHospitalMarker(mapView: MapView, hospital: HospitalEntity, icon: Drawable?): Marker {
        val marker = Marker(mapView)
        marker.position = GeoPoint(hospital.latitude, hospital.longitude)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = icon
        marker.title = hospital.name
        
        val details = "Type: ${hospital.type}\nAddress: ${hospital.address}\nEmergency: ${if (hospital.emergencyServices) "YES" else "NO"}\n\nTAP BUBBLE TO NAVIGATE"
        marker.snippet = details

        marker.infoWindow = object : MarkerInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, mapView) {
            override fun onOpen(item: Any?) {
                super.onOpen(item)
                val existing = mView.findViewWithTag<Button>("open_maps_button")
                if (existing == null) {
                    val btn = Button(context).apply {
                        text = context.getString(R.string.open_in_maps)
                        tag = "open_maps_button"
                        setOnClickListener {
                            openInGoogleMaps(hospital.latitude, hospital.longitude)
                            close()
                        }
                    }
                    (mView as? ViewGroup)?.addView(btn)
                }
                mView.setOnClickListener {
                    openInGoogleMaps(hospital.latitude, hospital.longitude)
                    close()
                }
            }
        }
        marker.setOnMarkerClickListener { m, _ ->
            m.showInfoWindow()
            mapView.controller.animateTo(m.position)
            true
        }
        return marker
    }

    private fun openInGoogleMaps(lat: Double, lon: Double) {
        val packageManager = context.packageManager
        val coords = String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon)
        val googleNavIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$coords")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$coords")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val intentToLaunch = when {
            googleNavIntent.resolveActivity(packageManager) != null -> googleNavIntent
            geoIntent.resolveActivity(packageManager) != null -> geoIntent
            webIntent.resolveActivity(packageManager) != null -> webIntent
            else -> null
        }

        if (intentToLaunch != null) {
            context.startActivity(intentToLaunch)
        } else {
            Toast.makeText(context, R.string.map_open_error, Toast.LENGTH_SHORT).show()
        }
    }



    private fun createClusterMarker(mapView: MapView, clusterItems: List<Any>): Marker {
        val avgLat = clusterItems.map { 
            if (it is ShelterEntity) it.latitude else (it as HospitalEntity).latitude 
        }.average()
        val avgLon = clusterItems.map { 
            if (it is ShelterEntity) it.longitude else (it as HospitalEntity).longitude 
        }.average()
        
        val marker = Marker(mapView)
        marker.position = GeoPoint(avgLat, avgLon)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.icon = createClusterIcon(clusterItems.size)
        marker.title = "${clusterItems.size} Locations"
        marker.setOnMarkerClickListener { _, _ ->
            mapView.controller.zoomIn()
            mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
            true
        }
        return marker
    }

    private fun createClusterIcon(count: Int): BitmapDrawable {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        
        // Draw circle
        paint.color = Color.parseColor("#E91E63") // Material Pink
        paint.style = Paint.Style.FILL
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // Draw border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2.5f, paint)
        
        // Draw text
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.textSize = 40f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val text = count.toString()
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, size / 2f, size / 2f - textBounds.centerY(), paint)
        
        return BitmapDrawable(context.resources, bitmap)
    }
}
