package com.example.appmap;

import com.google.firebase.database.Exclude;

import java.util.HashMap;
import java.util.Map;

public class Location {
    public String id;
    public String title;
    public String descripcion;
    public String estado;
    public double latitude;
    public double longitude;
    public boolean es_publico;
    public Map<String, Float> ratings = new HashMap<>();
    public Map<String, String> comments = new HashMap<>();

    public Location() {
        // Default constructor required for calls to DataSnapshot.getValue(Location.class)
    }

    public Location(String id, String title, String descripcion, String estado, double latitude, double longitude, boolean es_publico) {
        this.id = id;
        this.title = title;
        this.descripcion = descripcion;
        this.estado = estado;
        this.latitude = latitude;
        this.longitude = longitude;
        this.es_publico = es_publico;
    }

    @Exclude
    public float getAverageRating() {
        if (ratings == null || ratings.isEmpty()) {
            return 0.0f;
        }
        float sum = 0.0f;
        for (float rating : ratings.values()) {
            sum += rating;
        }
        return sum / ratings.size();
    }
}
