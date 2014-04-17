package org.unlucky.gpsmover.app;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.unlucky.gpsmover.app.db.FavoriteLocation;
import org.unlucky.gpsmover.app.db.FavoritesHelper;
import org.unlucky.gpsmover.app.util.Common;

import java.sql.SQLException;
import java.util.Map;

public class MainActivity extends FragmentActivity
        implements View.OnClickListener, GotoLocationDialogFragment.GotoLocationDialogListener,
        AddLocationDialogFragment.AddLocationDialogListener,
        FavLocationDialogFragment.FavLocationDialogListener {
    private static final int UPDATE_INTERVAL_TIME = 1000; // 1s
    private static final int REQ_SETTINGS = 1;

    private boolean isServiceBind = false;
    private float current_zoomLevel = 1.0f;

    private LatLng current_location;
    private GoogleMap mMap;
    //private GoogleMapOptions options = new GoogleMapOptions();
    private MarkerOptions markerOpt = new MarkerOptions();
    private Marker marker;
    private GPSMoverService gpsMoverService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gpsMoverService = null;
        initMap();
        initUI();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_SETTINGS) {
            // TODO: handle return from settings
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClass(this, SettingsActivity.class);
            startActivityForResult(intent, REQ_SETTINGS);
            return true;
        } else if (id == R.id.action_add_location) {
            AddLocationDialogFragment dialog = new AddLocationDialogFragment();
            dialog.show(getSupportFragmentManager(), AddLocationDialogFragment.class.getName());
            return true;
        } else if (id == R.id.action_goto_location) {
            GotoLocationDialogFragment dialog = new GotoLocationDialogFragment();
            dialog.show(getSupportFragmentManager(), GotoLocationDialogFragment.class.getName());
            return true;
        } else if (id == R.id.action_about) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_btn:
                Intent intent = new Intent();
                intent.setClass(this, GPSMoverService.class);
                intent.putExtra("longitude", current_location.longitude);
                intent.putExtra("latitude", current_location.latitude);
                startService(intent);
                bindService(intent, conn, BIND_AUTO_CREATE);
                Toast.makeText(this, "Start Fake Location", Toast.LENGTH_SHORT).show();
                break;
            case R.id.stop_btn:
                handler.removeCallbacks(updateLocationThread);
                if (isServiceBind) {
                    unbindService(conn);
                    isServiceBind = false;
                }
                if (isServiceRunning(GPSMoverService.class.getName())) {
                    Intent intent1 = new Intent();
                    intent1.setClass(this, GPSMoverService.class);
                    stopService(intent1);
                    Toast.makeText(this, "Stop Fake Location", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.zoom_in_btn:
                mMap.animateCamera(CameraUpdateFactory.zoomIn());
                break;
            case R.id.zoom_out_btn:
                mMap.animateCamera(CameraUpdateFactory.zoomOut());
                break;
            case R.id.mode_btn:
                if (mMap.getMapType() == GoogleMap.MAP_TYPE_NORMAL) {
                    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                } else if (mMap.getMapType() == GoogleMap.MAP_TYPE_HYBRID) {
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                } else {
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                }
                break;
            case R.id.search_btn:
                break;
            case R.id.history_btn:
                break;
            case R.id.fav_btn:
                FavLocationDialogFragment dialog = new FavLocationDialogFragment();
                dialog.show(getSupportFragmentManager(), FavLocationDialogFragment.class.getName());
                break;
            default:
                break;
        }

    }

    /**
     * initialize UI in activity
     */
    private void initUI() {
        final Button start_btn = (Button)findViewById(R.id.start_btn);
        start_btn.setOnClickListener(this);
        final Button stop_btn = (Button)findViewById(R.id.stop_btn);
        stop_btn.setOnClickListener(this);
        final Button zoom_in_btn = (Button)findViewById(R.id.zoom_in_btn);
        zoom_in_btn.setOnClickListener(this);
        final Button zoom_out_btn = (Button)findViewById(R.id.zoom_out_btn);
        zoom_out_btn.setOnClickListener(this);
        final ImageButton mode_btn = (ImageButton)findViewById(R.id.mode_btn);
        mode_btn.setOnClickListener(this);
        final ImageButton search_btn = (ImageButton)findViewById(R.id.search_btn);
        search_btn.setOnClickListener(this);
        final ImageButton history_btn = (ImageButton)findViewById(R.id.history_btn);
        history_btn.setOnClickListener(this);
        final ImageButton fav_btn = (ImageButton)findViewById(R.id.fav_btn);
        fav_btn.setOnClickListener(this);
    }

    /**
     * initialize map in activity
     */
    private void initMap() {
        mMap = ((SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapview)).getMap();
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.getUiSettings().setTiltGesturesEnabled(false);

        // init a marker
        current_location = new LatLng(30.0, 120.0);
        markerOpt.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        marker = mMap.addMarker(markerOpt.position(current_location)
                .title(getString(R.string.map_marker_title))
                .snippet(String.format(getString(R.string.map_marker_snippet),
                        current_location.latitude, current_location.longitude)));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(current_location));
    }

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }

    public int pxToDp(int px) {
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        int dp = Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return dp;
    }

    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            gpsMoverService = ((GPSMoverService.MyBinder)service).getService();
            isServiceBind = true;
            handler.post(updateLocationThread);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            gpsMoverService = null;
            isServiceBind = false;
        }
    };

    Handler handler = new Handler();

    Runnable updateLocationThread = new Runnable() {
        @Override
        public void run() {
            LatLng prev = current_location;
            if (gpsMoverService != null) {
                current_location = gpsMoverService.getCurrentLatLng();
            }
            // calc distance
            Location a = new Location("prev");
            a.setLatitude(prev.latitude);
            a.setLongitude(prev.longitude);
            Location b = new Location("current");
            b.setLatitude(current_location.latitude);
            b.setLongitude(current_location.longitude);
            Common.log("lat=" + (b.getLatitude()-a.getLatitude())
                    + ",lng=" + (b.getLongitude()-a.getLongitude())
                    + ",dist=" + b.distanceTo(a));

            updateMapMarker(current_location, false);
            handler.postDelayed(updateLocationThread, UPDATE_INTERVAL_TIME);
        }
    };

    private void updateMapMarker(LatLng pos, boolean needZoom) {
        marker.setPosition(pos);
        marker.setTitle(getString(R.string.map_marker_title));
        marker.setSnippet(String.format(getString(R.string.map_marker_snippet),
                pos.latitude, pos.longitude));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
        if (needZoom) {
            mMap.animateCamera(CameraUpdateFactory.zoomTo(current_zoomLevel));
        }
    }

    /**
     * check whether the service is running
     * @param name name of service
     * @return true - service is running<br/>
     *         false - service is not running
     */
    private boolean isServiceRunning(String name) {
        ActivityManager manager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getClassName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        String tag = dialog.getTag();
        if (AddLocationDialogFragment.class.getName().equals(tag)) {
            String title = ((AddLocationDialogFragment)dialog).getEditText();
            try {
                FavoritesHelper.getInstance(this).open().insertFavoriteLocation(
                        new FavoriteLocation(title, current_location.latitude,
                                current_location.longitude, mMap.getCameraPosition().zoom));
            } catch (SQLException e) {
                e.printStackTrace();
            }
            dialog.dismiss();
        } else if (GotoLocationDialogFragment.class.getName().equals(tag)) {
            String[] text_array = ((GotoLocationDialogFragment)dialog).getEditText().split(",");
            if (text_array.length == 2) {
                try {
                    double lat = Double.valueOf(text_array[0]);
                    double lng = Double.valueOf(text_array[1]);
                    current_location = new LatLng(lat, lng);
                    updateMapMarker(current_location, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(this, getString(R.string.error_format), Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        }
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        dialog.dismiss();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Map<String, Object> selectedItem = (Map<String, Object>)parent.getItemAtPosition(position);
        FavoriteLocation selectedFavorite = (FavoriteLocation)selectedItem.get("favorite");
        double lat = selectedFavorite.getLatitude();
        double lng = selectedFavorite.getLongitude();
        current_zoomLevel = selectedFavorite.getZoomLevel();
        updateMapMarker(new LatLng(lat, lng), true);
    }
}
