package com.geoloqi.trips.ui;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.MenuItem.OnMenuItemClickListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockMapActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.geoloqi.trips.Constants;
import com.geoloqi.trips.R;
import com.geoloqi.trips.maps.MapPickerOverlay;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

/**
 * This Activity is designed to help the user select a region on
 * a {@link MapView}. It should be invoked using
 * {@link Activity#startActivityForResult} and will return an Intent
 * with extra values describing the region's center point and longitude span.
 * 
 * @author Tristan Waddington
 */
public class MapPickerActivity extends SherlockMapActivity {
    public static final String EXTRA_LAT = "com.geoloqi.trips.extra.LAT";
    public static final String EXTRA_LNG = "com.geoloqi.trips.extra.LNG";
    public static final String EXTRA_SPAN = "com.geoloqi.trips.extra.SPAN";
    public static final String EXTRA_ZOOM = "com.geoloqi.trips.extra.ZOOM";
    
    private static final String TAG = "MapPickerActivity";
    
    /** The default zoom level to display. */
    private static final int DEFAULT_ZOOM_LEVEL = 15;
    
    /** The default center point to display. */
    private static final GeoPoint DEFAULT_MAP_CENTER =
            new GeoPoint(45516290, -122675943);
    
    /** The initial map zoom. */
    private int mMapZoom = DEFAULT_ZOOM_LEVEL;
    
    /** The initial map center. */
    private GeoPoint mMapCenter = DEFAULT_MAP_CENTER;
    
    private MapView mMapView;
    private MapController mMapController;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set some defaults.
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        
        // Configure our MapView.
        mMapView = new MapView(this, Constants.GOOGLE_MAPS_KEY);
        mMapView.setClickable(true);
        mMapView.setBuiltInZoomControls(true);
        
        // Get our list overlays
        List<Overlay> overlayList = mMapView.getOverlays();
        
        // Add our overlay to the map
        overlayList.add(new MapPickerOverlay(this));
        
        // Get our MapController.
        mMapController = mMapView.getController();
        
        // Insert the MapView into the layout.
        setContentView(mMapView);
        
        // Display the contextual action bar
        startActionMode(mActionModeCallback);
        
        // Restore our saved map state
        if (savedInstanceState != null) {
            int lat = savedInstanceState.getInt(EXTRA_LAT, 0);
            int lng = savedInstanceState.getInt(EXTRA_LNG, 0);
            
            if ((lat + lng) != 0) {
                mMapCenter = new GeoPoint(lat, lng);
            }
            mMapZoom = savedInstanceState.getInt(EXTRA_ZOOM, DEFAULT_ZOOM_LEVEL);
        } else {
            Location location = getLastKnownLocation();
            if (location != null) {
                // Set the map center to the device's last known location
                mMapCenter = new GeoPoint((int) (location.getLatitude() * 1e6),
                        (int) (location.getLongitude() * 1e6));
            }
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Set our map center and zoom level
        mMapController.setCenter(mMapCenter);
        mMapController.setZoom(mMapZoom);
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // Persist our map position
        outState.putInt(EXTRA_LAT,
                mMapView.getMapCenter().getLatitudeE6());
        outState.putInt(EXTRA_LNG,
                mMapView.getMapCenter().getLongitudeE6());
        outState.putInt(EXTRA_ZOOM,
                mMapView.getZoomLevel());
    }
    
    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
    
    /**
     * Get the last known {@link Location} from the GPS provider. If the
     * GPS provider is disabled, query the network provider.
     * 
     * @return the last known location; otherwise null.
     */
    private Location getLastKnownLocation() {
        Location location;
        LocationManager locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        
        // Attempt to get the last known GPS location
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            location = locationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER);
        } else {
            location = locationManager.getLastKnownLocation(
                    LocationManager.NETWORK_PROVIDER);
        }
        return location;
    }

    /**
     * Provide a callback for our contextual action bar. This menu
     * callback replaces the typical {@link OnMenuItemClickListener}
     * implementation.
     */
    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.setTitle("Done?");
            
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.map_picker_menu, menu);
            return true;
        }
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch(item.getItemId()) {
            case R.id.menu_my_location:
                Location location = getLastKnownLocation();
                if (location != null) {
                    // Set the map center to the device's last known location
                    mMapCenter = new GeoPoint((int) (location.getLatitude() * 1e6),
                            (int) (location.getLongitude() * 1e6));
                    mMapController.animateTo(mMapCenter);
                }
                return true;
            }
            return false;
        }
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            // Get the map center
            GeoPoint center = mMapView.getMapCenter();
            
            // Calculate our selection values
            double lat = center.getLatitudeE6() / 1e6;
            double lng = center.getLongitudeE6() / 1e6;
            double span = (mMapView.getLongitudeSpan() / 1e6) *
                    MapPickerOverlay.FRACTIONAL_GRADIENT_VALUE;
            
            Intent data = new Intent();
            data.putExtra(EXTRA_LAT, lat);
            data.putExtra(EXTRA_LNG, lng);
            data.putExtra(EXTRA_SPAN, span);
            setResult(RESULT_OK, data);
            finish();
        }
    };
}
