package com.example.appmap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private MapView map;
    private LocationManager locationManager;
    private GeoPoint myLocation;
    private Marker myLocationMarker;
    private boolean isFirstLocationUpdate = true;
    private CompassOverlay mCompassOverlay;
    private Spinner distanceFilterSpinner;
    private Spinner statusFilterSpinner;
    private String currentDistanceFilter = "Mundial";
    private String currentStatusFilter = "Todos";
    private DatabaseReference locationsRef;
    private List<Location> allLocations = new ArrayList<>();
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        setContentView(R.layout.activity_main);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        locationsRef = FirebaseDatabase.getInstance().getReference("locations");

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);

        mCompassOverlay = new CompassOverlay(this, new InternalCompassOrientationProvider(this), map);
        mCompassOverlay.enableCompass();
        map.getOverlays().add(mCompassOverlay);

        distanceFilterSpinner = findViewById(R.id.distance_filter_spinner);
        statusFilterSpinner = findViewById(R.id.status_filter_spinner);
        setupFilters();

        FloatingActionButton fab = findViewById(R.id.fab_my_location);
        fab.setOnClickListener(v -> {
            if (myLocation != null) {
                map.getController().animateTo(myLocation);
            }
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
            setupLocation();
        }

        MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                InfoWindow.closeAllInfoWindowsOn(map);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                showAddLocationDialog(p);
                return true;
            }
        };

        MapEventsOverlay OverlayEvents = new MapEventsOverlay(mReceive);
        map.getOverlays().add(0, OverlayEvents);

        loadLocationsFromFirebase();
    }

    private void setupFilters() {
        ArrayAdapter<CharSequence> distanceAdapter = ArrayAdapter.createFromResource(this,
                R.array.distance_filter_array, android.R.layout.simple_spinner_item);
        distanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        distanceFilterSpinner.setAdapter(distanceAdapter);

        ArrayAdapter<CharSequence> statusAdapter = ArrayAdapter.createFromResource(this,
                R.array.status_filter_array, android.R.layout.simple_spinner_item);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusFilterSpinner.setAdapter(statusAdapter);

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentDistanceFilter = distanceFilterSpinner.getSelectedItem().toString();
                currentStatusFilter = statusFilterSpinner.getSelectedItem().toString();
                refreshMarkers();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };

        distanceFilterSpinner.setOnItemSelectedListener(filterListener);
        statusFilterSpinner.setOnItemSelectedListener(filterListener);
    }

    private void setupLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
        }
    }

    @Override
    public void onLocationChanged(android.location.Location location) {
        myLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (isFirstLocationUpdate && map != null) {
            map.getController().setCenter(myLocation);
            map.getController().setZoom(15.0);
            isFirstLocationUpdate = false;
        }
        updateMyLocationMarker(location.getBearing());

        if (currentDistanceFilter.equals("Por Zona (4km)")) {
            refreshMarkers();
        }
    }

    private void updateMyLocationMarker(float bearing) {
        if (map == null) return;
        if (myLocationMarker == null) {
            myLocationMarker = new Marker(map);
            myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            myLocationMarker.setTitle("Mi Ubicación");
            myLocationMarker.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_send));
            map.getOverlays().add(myLocationMarker);
        } else {
            if (!map.getOverlays().contains(myLocationMarker)) {
                map.getOverlays().add(myLocationMarker);
            }
        }
        myLocationMarker.setPosition(myLocation);
        myLocationMarker.setRotation(bearing);
        map.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupLocation();
            }
        }
    }

    private void showAddLocationDialog(final GeoPoint p) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Agregar nueva ubicación");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.edit_location_dialog, null);
        builder.setView(dialogView);

        final EditText editTitle = dialogView.findViewById(R.id.edit_title);
        final EditText editDescription = dialogView.findViewById(R.id.edit_description);
        final Spinner statusSpinner = dialogView.findViewById(R.id.edit_status_spinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.status_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(adapter);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String newTitle = editTitle.getText().toString();
            String newDescription = editDescription.getText().toString();
            String newStatus = statusSpinner.getSelectedItem().toString();
            String id = locationsRef.push().getKey();
            Location newLocation = new Location(id, newTitle, newDescription, newStatus, p.getLatitude(), p.getLongitude(), true);
            saveLocation(newLocation);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveLocation(Location location) {
        locationsRef.child(location.id).setValue(location);
    }

    private void deleteLocation(Location locationToDelete) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Ubicación")
                .setMessage("¿Estás seguro de que quieres eliminar esta ubicación?")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    locationsRef.child(locationToDelete.id).removeValue();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void loadLocationsFromFirebase() {
        locationsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allLocations.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Location location = snapshot.getValue(Location.class);
                    if (location != null) {
                        allLocations.add(location);
                    }
                }
                refreshMarkers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, "Failed to load locations.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshMarkers() {
        if (map == null) return;
        List<org.osmdroid.views.overlay.Overlay> overlaysToKeep = new ArrayList<>();
        for (org.osmdroid.views.overlay.Overlay overlay : map.getOverlays()) {
            if (overlay instanceof MapEventsOverlay || overlay instanceof CompassOverlay || overlay == myLocationMarker) {
                overlaysToKeep.add(overlay);
            }
        }
        map.getOverlays().clear();
        map.getOverlays().addAll(overlaysToKeep);

        for (Location location : allLocations) {
            if (!currentStatusFilter.equals("Todos") && !location.estado.equals(currentStatusFilter)) {
                continue;
            }

            GeoPoint point = new GeoPoint(location.latitude, location.longitude);

            if (currentDistanceFilter.equals("Por Zona (4km)")) {
                if (myLocation == null) continue;
                android.location.Location loc1 = new android.location.Location("");
                loc1.setLatitude(myLocation.getLatitude());
                loc1.setLongitude(myLocation.getLongitude());

                android.location.Location loc2 = new android.location.Location("");
                loc2.setLatitude(point.getLatitude());
                loc2.setLongitude(point.getLongitude());

                if (loc1.distanceTo(loc2) > 4000) {
                    continue;
                }
            }
            addMarker(point, location);
        }
        map.invalidate();
    }

    private void addMarker(GeoPoint p, final Location location) {
        Marker marker = new Marker(map);
        marker.setPosition(p);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle(location.title);
        marker.setInfoWindow(new CustomInfoWindow(R.layout.custom_info_window, map, location));
        map.getOverlays().add(marker);
    }

    private void showEditLocationDialog(final Location location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Ubicación");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.edit_location_dialog, null);
        builder.setView(dialogView);

        final EditText editTitle = dialogView.findViewById(R.id.edit_title);
        final EditText editDescription = dialogView.findViewById(R.id.edit_description);
        final Spinner statusSpinner = dialogView.findViewById(R.id.edit_status_spinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.status_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(adapter);

        editTitle.setText(location.title);
        editDescription.setText(location.descripcion);
        if (location.estado != null) {
            int spinnerPosition = adapter.getPosition(location.estado);
            statusSpinner.setSelection(spinnerPosition);
        }

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            location.title = editTitle.getText().toString();
            location.descripcion = editDescription.getText().toString();
            location.estado = statusSpinner.getSelectedItem().toString();
            saveLocation(location);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showRatingDialog(final Location location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Calificar y Comentar");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.rating_dialog, null);
        builder.setView(dialogView);

        final RatingBar ratingBar = dialogView.findViewById(R.id.rating_bar);
        final EditText commentText = dialogView.findViewById(R.id.comment_text);

        builder.setPositiveButton("Enviar", (dialog, which) -> {
            float rating = ratingBar.getRating();
            String comment = commentText.getText().toString();
            String userId = currentUser.getUid();

            if (rating > 0) {
                location.ratings.put(userId, rating);
            }
            if (!comment.isEmpty()) {
                location.comments.put(userId, comment);
            }
            saveLocation(location);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private class CustomInfoWindow extends InfoWindow {
        private final Location location;

        public CustomInfoWindow(int layoutResId, MapView mapView, Location location) {
            super(layoutResId, mapView);
            this.location = location;
        }

        @Override
        public void onOpen(Object arg0) {
            TextView title = mView.findViewById(R.id.info_title);
            TextView description = mView.findViewById(R.id.info_description);
            TextView status = mView.findViewById(R.id.info_status);
            TextView ratingText = mView.findViewById(R.id.info_rating);
            Button editButton = mView.findViewById(R.id.info_edit_button);
            Button deleteButton = mView.findViewById(R.id.info_delete_button);
            Button rateButton = mView.findViewById(R.id.info_rate_button);

            title.setText(location.title);
            description.setText(location.descripcion);
            status.setText("Estado: " + location.estado);
            ratingText.setText(String.format("Calificación: %.1f", location.getAverageRating()));

            editButton.setOnClickListener(v -> {
                showEditLocationDialog(location);
                close();
            });

            deleteButton.setOnClickListener(v -> {
                deleteLocation(location);
                close();
            });

            rateButton.setOnClickListener(v -> {
                showRatingDialog(location);
                close();
            });
        }

        @Override
        public void onClose() {
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
        if (mCompassOverlay != null) {
            mCompassOverlay.enableCompass();
        }
        if (locationManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
        if (mCompassOverlay != null) {
            mCompassOverlay.disableCompass();
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
}
