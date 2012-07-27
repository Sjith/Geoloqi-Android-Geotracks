package com.geoloqi.trips.ui;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.geoloqi.android.sdk.LQException;
import com.geoloqi.android.sdk.LQSession;
import com.geoloqi.android.sdk.LQSession.OnRunApiRequestListener;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;
import com.geoloqi.trips.R;

/**
 * This activity class creates a new geonote using data
 * returned from the {@link MapPickerActivity} class.
 * 
 * @author Tristan Waddington
 */
public class EditGeonoteActivity extends SherlockActivity implements
        OnClickListener, OnItemSelectedListener {
    private static final String TAG = "EditGeonoteActivity";
    private static final int PICK_GEONOTE_REQUEST = 0;
    
    private String mTriggerOn;
    private double mLatitude = 0;
    private double mLongitude = 0;
    private double mSpan = 0;
    
    private ProgressDialog mProgressDialog;
    
    private LQService mService;
    private boolean mBound;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set our layout
        setContentView(R.layout.edit_geonote);
        
        // Set our onclick listeners
        ((TextView) findViewById(R.id.pick_on_map_button)).setOnClickListener(this);
        ((TextView) findViewById(R.id.submit_button)).setOnClickListener(this);
        ((Spinner) findViewById(R.id.trigger_on)).setOnItemSelectedListener(this);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Bind to the tracking service so we can call public methods on it
        Intent intent = new Intent(this, LQService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        // Hide the progress bar
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        
        // Unbind from LQService
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch (resultCode) {
        case RESULT_OK:
            if (data != null) {
                double lat = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0);
                double lng = data.getDoubleExtra(MapPickerActivity.EXTRA_LNG, 0);
                double span = data.getDoubleExtra(MapPickerActivity.EXTRA_SPAN, 0);
                handleMapPickerResult(lat, lng, span);
            }
            break;
        case RESULT_CANCELED:
            // Finish and return to the previous Activity
            finish();
            break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.pick_on_map_button:
            // Start the map picker activity so the user can
            // select a region for the new geonote.
            Intent intent = new Intent(this, MapPickerActivity.class);
            intent.setAction(Intent.ACTION_PICK);
            startActivityForResult(intent, PICK_GEONOTE_REQUEST);
            break;
        case R.id.submit_button:
            if (mBound && mService != null) {
                LQSession session = mService.getSession();
                if (session != null) {
                    // Get our message text
                    String text = ((TextView) findViewById(R.id.text)).getText().toString();
                    
                    // Validate our user input
                    if (TextUtils.isEmpty(text)) {
                        Toast.makeText(this, R.string.invalid_message_error,
                                Toast.LENGTH_LONG).show();
                    } else {
                        try {
                            JSONObject data = new JSONObject();
                            data.put("text", text);
                            data.put("latitude", mLatitude);
                            data.put("longitude", mLongitude);
                            data.put("span_longitude", mSpan);
                            
                            if (!TextUtils.isEmpty(mTriggerOn)) {
                                data.put("trigger_on", mTriggerOn);
                            }
                            
                            // Show a progress dialog
                            mProgressDialog = ProgressDialog.show(this, null, 
                                    getString(R.string.loading_message), true);
                            
                            // Perform the request
                            session.runPostRequest("geonote/create", data,
                                    new OnGeonoteCreateListener());
                        } catch (JSONException e) {
                            // Pass
                        }
                    }
                }
            }
            break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
            long id) {
        String triggerOn = (String) parent.getItemAtPosition(position);
        if (!TextUtils.isEmpty(triggerOn)) {
            mTriggerOn = triggerOn.toLowerCase();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Pass
    }

    /**
     * Handle the result returned from the {@link MapPickerActivity}.
     * 
     * @param lat
     * @param lng
     * @param span
     */
    private void handleMapPickerResult(double lat, double lng, double span) {
        mLatitude = lat;
        mLongitude = lng;
        mSpan = span;
        
        // Show the selected lat/long
        TextView location = (TextView) findViewById(R.id.region_lat_long);
        location.setText(String.format("%s,%s", lat, lng));
        
        // Display the selected region
        ViewGroup region = (ViewGroup) findViewById(R.id.region);
        region.setVisibility(View.VISIBLE);
        
        // Enable the submit button
        findViewById(R.id.submit_button).setEnabled(true);
    }

    /**
     * Display the best region name for the area selected by
     * the user.
     */
    private void showBestRegionName() {
        boolean validRegion = ((mLatitude + mLongitude) != 0);
        
        if (validRegion) {
            LQSession session = mService.getSession();
            
            if (session == null) {
                // Bail!
                // TODO: This is a huge hack. We should always return a valid
                //       session from LQService.
                return;
            }
            
            // Get our views
            final TextView regionName = (TextView) findViewById(R.id.region_name);
            
            // Build our query string
            Map<String, String> args = new HashMap<String, String>();
            args.put("latitude", String.valueOf(mLatitude));
            args.put("longitude", String.valueOf(mLongitude));
            
            // Perform the request
            session.runGetRequest("location/context", args, null, new OnRunApiRequestListener() {
                @Override
                public void onSuccess(LQSession session, JSONObject json,
                        Header[] headers) {
                    String best = json.optString("best_name");
                    if (!TextUtils.isEmpty(best)) {
                        regionName.setText(best);
                    }
                }
                @Override
                public void onFailure(LQSession session, LQException e) {
                    // Pass
                }
                @Override
                public void onComplete(LQSession session, JSONObject json,
                        Header[] headers, StatusLine status) {
                    // Pass
                }
            });
        }
    }

    /**
     * A basic implementation of {@link OnRunApiRequestListener}
     * that handles the server response when creating a new geonote.
     * 
     * @author Tristan Waddington
     */
    private class OnGeonoteCreateListener implements OnRunApiRequestListener {
        @Override
        public void onComplete(LQSession session, JSONObject json,
                Header[] headers, StatusLine status) {
            // Hide the progress bar
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            
            // Log the error
            Log.e(TAG, status.toString());
            
            // Notify the user
            Toast.makeText(EditGeonoteActivity.this, json.optString("error_description"),
                    Toast.LENGTH_LONG).show();
        }
        @Override
        public void onFailure(LQSession session, LQException e) {
            // Hide the progress bar
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            
            // Log the error
            Log.e(TAG, e.getMessage(), e.getWrappedException());
            
            // Notify the user
            Toast.makeText(EditGeonoteActivity.this, e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        @Override
        public void onSuccess(LQSession session, JSONObject json, Header[] headers) {
            // Hide the progress bar
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            
            // Start the main activity with the geonotes list visible.
            Intent intent = new Intent(EditGeonoteActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_CURRENT_ITEM, 2);
            startActivity(intent);
            
            // Finish the edit activity
            finish();
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // We've bound to LocalService, cast the IBinder and get LocalService instance.
                LQBinder binder = (LQBinder) service;
                mService = binder.getService();
                mBound = true;
                
                // Show the selected region name
                showBestRegionName();
            } catch (ClassCastException e) {
                // Pass
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };
}
