package com.example.currentplacedetailsonmap;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;

import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An activity that displays a map showing the place at the device's current location.
 */
public class MapsActivityCurrentPlace extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final String TAG = MapsActivityCurrentPlace.class.getSimpleName();
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // The entry point to the Places API.
    private PlacesClient mPlacesClient;

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // Used for selecting the current place.
    private static final int M_MAX_ENTRIES = 5;
    private String[] mLikelyPlaceNames;
    private String[] mLikelyPlaceAddresses;
    private List[] mLikelyPlaceAttributions;
    private LatLng[] mLikelyPlaceLatLngs;

    ////////////////////////////////////////////////////////////////////////////////
    //////////// Important Values for your localization and bluetooth //////////////
    ////////////////////////////////////////////////////////////////////////////////
    // Selected current place
    private LatLng markerLatLng;
    private String markerSnippet;
    private String markerPlaceName;

    // New Bluetooth Devices Number
    private int btDevicesCount;
    ////////////////////////////////////////////////////////////////////////////////

    //Firebase database
    private DatabaseReference mDatabase;

    //Locations list
    private Map<String, Object> locations;

    //tf audio
    private static boolean mAudioPermissionGranted;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 200;
    private static final int SAMPLE_RATE = 44000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final String LABEL_FILENAME = "file:///android_asset/cough_labels.txt";
    private static final String MODEL_FILENAME = "file:///android_asset/audio.tflite";

    private Interpreter tfLite;

    private List<String> labels = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps);

        // Construct a PlacesClient
        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        mPlacesClient = Places.createClient(this);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //setup Firebase
        mDatabase = FirebaseDatabase.getInstance().getReference();

        //Request audio permissions
        getLocationPermission();
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);

        //read labels
        String actualLabelFilename = LABEL_FILENAME.split("file:///android_asset/", -1)[1];
        Log.i("audio", "Reading labels from: " + actualLabelFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open(actualLabelFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
                Log.i("audio", line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }
        //load model
        String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
        Log.i("audio", "Reading model from: " + actualModelFilename);
        try {
            tfLite = new Interpreter(loadModelFile(getAssets(), actualModelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //Resize model inputs
        tfLite.resizeInput(0, new int[] {RECORDING_LENGTH, 1});
        tfLite.resizeInput(1, new int[] {1});
    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Sets up the options menu.
     *
     * @param menu The options menu.
     * @return Boolean.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.current_place_menu, menu);
        return true;
    }

    /**
     * Handles a click on the menu option to get a place.
     *
     * @param item The menu item to handle.
     * @return Boolean.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.activate_audio) {
            //Activate audio scanning and classification model
            startRecording();
        } else if (item.getItemId() == R.id.nearby_devices) {
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            getDeviceLocation();
        }
        return true;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    ////////////////////////////////////////////////////////////////////////////
                    //////////////////////   WRITE YOUR CODE HERE ! ////////////////////////////
                    ////////////////////////////////////////////////////////////////////////////
                    // Example:
                    btDevicesCount = data.getExtras()
                            .getInt(DeviceListActivity.EXTRA_DEVICE_COUNT);

                    //Add a marker to indicate the recently scanned datapoint
                    LatLng last = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
                    mMap.addMarker(new MarkerOptions().position(last)
                            .title("Placed at: " + Calendar.getInstance().getTime() + "\nDevices: " + btDevicesCount)
                            .icon(BitmapDescriptorFactory
                                    .defaultMarker(90-Math.min((btDevicesCount*10), 90))));
                    Log.d(TAG, "Device number:" + btDevicesCount);
                    //Write the new location out to firebase
                    writeNewLoc(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude(), System.currentTimeMillis(), "", btDevicesCount);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                }
        }
    }

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;


        // TEST
//        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) findViewById(R.id.map), false);

                TextView title = infoWindow.findViewById(R.id.title);
                title.setText(marker.getTitle());

                TextView snippet = infoWindow.findViewById(R.id.snippet);
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Prompt the user for permission.
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        //Once we've got the current location and all that, setup our firebase sync
        mDatabase.child("locations").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                locations = (Map<String,Object>) dataSnapshot.getValue();
                //drawSquare();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // uh oh spaghetti-o
            }
        });
    }


    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                Log.e("loc", "perm granted");
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            if (mLastKnownLocation != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(mLastKnownLocation.getLatitude(),
                                                mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
            //Request audio case
            case PERMISSIONS_REQUEST_RECORD_AUDIO: {
                mAudioPermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
            }
        }
        Log.e("perms", ""+mLocationPermissionGranted + "," + mAudioPermissionGranted);
        updateLocationUI();
    }

    /**
     * Prompts the user to select the current place from a list of likely places, and shows the
     * current place on the map - provided the user has granted location permission.
     */
    private void showCurrentPlace() {
        if (mMap == null) {
            return;
        }

        if (mLocationPermissionGranted) {
            // Use fields to define the data types to return.
            List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS,
                    Place.Field.LAT_LNG);

            // Use the builder to create a FindCurrentPlaceRequest.
            FindCurrentPlaceRequest request =
                    FindCurrentPlaceRequest.newInstance(placeFields);

            // Get the likely places - that is, the businesses and other points of interest that
            // are the best match for the device's current location.
            @SuppressWarnings("MissingPermission") final Task<FindCurrentPlaceResponse> placeResult =
                    mPlacesClient.findCurrentPlace(request);
            placeResult.addOnCompleteListener(new OnCompleteListener<FindCurrentPlaceResponse>() {
                @Override
                public void onComplete(@NonNull Task<FindCurrentPlaceResponse> task) {
                    if (task.isSuccessful() && task.getResult() != null) {
                        FindCurrentPlaceResponse likelyPlaces = task.getResult();

                        // Set the count, handling cases where less than 5 entries are returned.
                        int count;
                        if (likelyPlaces.getPlaceLikelihoods().size() < M_MAX_ENTRIES) {
                            count = likelyPlaces.getPlaceLikelihoods().size();
                        } else {
                            count = M_MAX_ENTRIES;
                        }

                        int i = 0;
                        mLikelyPlaceNames = new String[count];
                        mLikelyPlaceAddresses = new String[count];
                        mLikelyPlaceAttributions = new List[count];
                        mLikelyPlaceLatLngs = new LatLng[count];

                        for (PlaceLikelihood placeLikelihood : likelyPlaces.getPlaceLikelihoods()) {
                            // Build a list of likely places to show the user.
                            mLikelyPlaceNames[i] = placeLikelihood.getPlace().getName();
                            mLikelyPlaceAddresses[i] = placeLikelihood.getPlace().getAddress();
                            mLikelyPlaceAttributions[i] = placeLikelihood.getPlace()
                                    .getAttributions();
                            mLikelyPlaceLatLngs[i] = placeLikelihood.getPlace().getLatLng();

                            i++;
                            if (i > (count - 1)) {
                                break;
                            }
                        }

                        // Show a dialog offering the user the list of likely places, and add a
                        // marker at the selected place.
                        MapsActivityCurrentPlace.this.openPlacesDialog();
                    } else {
                        Log.e(TAG, "Exception: %s", task.getException());
                    }
                }
            });
        } else {
            // The user has not granted permission.
            Log.i(TAG, "The user did not grant location permission.");

            // Add a default marker, because the user hasn't selected a place.
            mMap.addMarker(new MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(mDefaultLocation)
                    .snippet(getString(R.string.default_info_snippet)));

            // Prompt the user for permission.
            getLocationPermission();
        }
    }

    /**
     * Displays a form allowing the user to select a place from a list of likely places.
     */
    private void openPlacesDialog() {
        // Ask the user to choose the place where they are now.
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The "which" argument contains the position of the selected item.
                markerLatLng = mLikelyPlaceLatLngs[which];
                markerSnippet = mLikelyPlaceAddresses[which];
                markerPlaceName = mLikelyPlaceNames[which];

                if (mLikelyPlaceAttributions[which] != null) {
                    markerSnippet = markerSnippet + "\n" + mLikelyPlaceAttributions[which];
                }

                // Add a marker for the selected place, with an info window
                // showing information about that place.
                mMap.addMarker(new MarkerOptions()
                        .title(markerPlaceName)
                        .position(markerLatLng)
                        .snippet(markerSnippet));

                // Position the map's camera at the location of the marker.
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                        DEFAULT_ZOOM));
            }
        };

        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pick_place)
                .setItems(mLikelyPlaceNames, listener)
                .show();
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    //Adding new location to the firebase dataset
    public void writeNewLoc(double lat, double lng, double datetime, String user, long devices){
        Loc loc = new Loc(lat, lng, datetime, user, devices);
        String key = mDatabase.push().getKey();
        mDatabase.child("locations").child(key).setValue(loc);
    }

    //Variables for heatmaps and markers
    TileOverlay tol;
    private boolean markerOn = false;
    private boolean heatmapOn = false;
    ArrayList<Marker> markerList;

    //Methods to hide or show the heatmap or raw markers
    public void showHeatmap(){
        ArrayList<WeightedLatLng> data = new ArrayList<WeightedLatLng>();
        for(Map.Entry<String, Object> entry: locations.entrySet()){
            Map loc = (Map) entry.getValue();
            LatLng last = new LatLng((Double)loc.get("lat"), (Double)loc.get("lng"));
            data.add(new WeightedLatLng(last, (Long)loc.get("devices")));
        }
        HeatmapTileProvider mProvider = new HeatmapTileProvider.Builder()
                .weightedData(data)
                .build();
        tol = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }
    public void removeHeatmap(){
        if(tol != null)
            tol.remove();
    }
    public void showMarkers(){
        markerList = new ArrayList<Marker>();
        for(Map.Entry<String, Object> entry: locations.entrySet()){
            Map loc = (Map) entry.getValue();
            LatLng last = new LatLng((Double)loc.get("lat"), (Double)loc.get("lng"));
            Marker mark = mMap.addMarker(new MarkerOptions().position(last)
                    .title("Placed at: " + (Long)loc.get("datetime") + "\nDevices: " + (Long)loc.get("devices"))
                    .icon(BitmapDescriptorFactory
                            .defaultMarker(90-Math.min((Long)loc.get("devices"), 90))));
            markerList.add(mark);
        }
    }
    public void removeMarkers(){
        for(Marker m: markerList){
            m.remove();
        }
    }
    public void toggleMarker(View view){
        if(markerOn) {
            markerOn = false;
            Button but = (Button)findViewById(R.id.markerButton);
            but.setText(R.string.markers_off);
            removeMarkers();
        } else{
            markerOn = true;
            Button but = (Button)findViewById(R.id.markerButton);
            but.setText(R.string.markers_on);
            showMarkers();
        }
    }
    public void toggleHeatmap(View view){
        if(heatmapOn){
            heatmapOn = false;
            Button but = (Button)findViewById(R.id.heatmapButton);
            but.setText(R.string.heatmap_off);
            removeHeatmap();
        } else {
            heatmapOn = true;
            Button but = (Button)findViewById(R.id.heatmapButton);
            but.setText(R.string.heatmap_on);
            showHeatmap();
        }
    }


    //Variables for drawing the grid
    private double gridSize = 0.0007;
    private int gridNum = 200;
    //Draw the grid squares around the user
    public void drawSquare(){
        double lat = mLastKnownLocation.getLatitude();
        double lng = mLastKnownLocation.getLongitude();
        double[][] grid = gridbinLocations();
        for(int i = -gridNum/2; i < gridNum/2-2; i++){
            for(int j = -gridNum/2; j < gridNum/2-2; j++){
                PolygonOptions rectOptions = new PolygonOptions()
                        .add(new LatLng(lat+(gridSize*(i-1)), lng+(gridSize*(j-1))),
                                new LatLng(lat+(gridSize*i), lng+(gridSize*(j-1))),
                                new LatLng(lat+(gridSize*i), lng+(gridSize*j)),
                                new LatLng(lat+(gridSize*(i-1)), lng+(gridSize*j)));
                Random r = new Random();
                int g = r.nextInt(255);
                Log.d("grid", ""+i+", "+j+","+(i+gridNum/2)+","+(j+gridNum/2) +","+grid[i+(gridNum/2)][j+(gridNum/2)]);
                rectOptions.fillColor(Color.argb(127,(int)(255*(grid[i+(gridNum/2)][j+(gridNum/2)]/5)),255-(int)(255*(grid[i+(gridNum/2)][j+(gridNum/2)]/5)),0));
                rectOptions.strokeColor(Color.argb(0,0,0,0));
                if(grid[i+(gridNum/2)][j+(gridNum/2)] > 0)
                    mMap.addPolygon((rectOptions));
            }
        }
    }

    //Slot all data points into grid squares
    public double[][] gridbinLocations(){
        double[][] grid = new double[gridNum][gridNum];
        double[][] count = new double[gridNum][gridNum];
        double lat = mLastKnownLocation.getLatitude();
        double lng = mLastKnownLocation.getLongitude();
        double botLat = lat - gridSize*gridNum/2;
        double botLng = lng - gridSize*gridNum/2;
        for(Map.Entry<String, Object> entry: locations.entrySet()){
            Map loc = (Map) entry.getValue();
            double currLat = (Double)loc.get("lat");
            double currLng = (Double)loc.get("lng");

            int i = (int)((currLat-botLat)/gridSize)+1;
            int j = (int)((currLng-botLng)/gridSize)+1;

            grid[i][j] += Math.max(0.01,(Long)loc.get("devices"));
            count[i][j] += 1;
        }

        for(int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                grid[i][j] = grid[i][j]/count[i][j];
            }
        }
        return grid;
    }

    // Temp variables needed for audio and recording
    short[] audioBuffer;
    AudioRecord record;
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    //Begin recording
    private void startRecording() {
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE;
        }
        audioBuffer = new short[bufferSize];

        record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        record.startRecording();

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopRecording();
            }
        }, 1500);
    }

    //Stop recording and measure the amount of coughing and sneezing detected in the audio sample
    //Update progress bar accordingly
    //Created with help from: https://github.com/tensorflow/docs/blob/master/site/en/r1/tutorials/sequences/audio_recognition.md
    private void stopRecording() {
        int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
        int maxLength = recordingBuffer.length;
        int newRecordingOffset = recordingOffset + numberRead;
        int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
        int firstCopyLength = numberRead - secondCopyLength;
        // We store off all the data for the recognition thread to access. The ML
        // thread will copy out of this buffer into its own, while holding the
        // lock, so this should be thread safe.
        recordingBufferLock.lock();
        try {
            System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
            System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
            recordingOffset = newRecordingOffset % maxLength;
        } finally {
            recordingBufferLock.unlock();
        }

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
        float[][] outputScores = new float[1][labels.size()];
        int[] sampleRateList = new int[] {SAMPLE_RATE};

        recordingBufferLock.lock();
        try {
            maxLength = recordingBuffer.length;
            firstCopyLength = maxLength - recordingOffset;
            secondCopyLength = recordingOffset;
            System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
            System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
        } finally {
            recordingBufferLock.unlock();
        }
        record.stop();
        record.release();
        record = null;

        // We need to feed in float values between -1.0f and 1.0f, so divide the
        // signed 16-bit inputs.
        for (int i = 0; i < RECORDING_LENGTH; ++i) {
            floatInputBuffer[i][0] = recordingBuffer[i] / 32767.0f;
        }

        Object[] inputArray = {floatInputBuffer, sampleRateList};
        Log.w("size", floatInputBuffer.length + ", " + recordingBuffer.length + ", " + sampleRateList.length);
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputScores);

        // Run the model.
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        for(int i =0; i < 4; i++)
            Log.e("audio", "" + i + ": " + outputScores[0][i]);

        ProgressBar but = (ProgressBar)findViewById(R.id.coughBar);
        but.setMax(100);
        but.setScaleY(3f);
        but.setProgress((int)(outputScores[0][3]*100));

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startRecording();
            }
        }, 1500);
    }

    //Method to assist in loading in the tensorflow model
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //Location class for firebase
    public class Loc {

        public double lat;
        public double lng;
        public double datetime;
        public String user;
        public long devices;


        public Loc() {
            // Default constructor required for calls to DataSnapshot.getValue(User.class)
        }

        public Loc(double lat, double lng, double datetime, String user, long devices) {
            this.lat = lat;
            this.lng = lng;
            this.datetime = datetime;
            this.user = user;
            this.devices = devices;
        }

    }
}
