package jp.ac.titech.itpro.sql.googlemap001;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.graphics.Color;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.MapFragment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class MainActivity extends FragmentActivity {
    private static LatLng initialPoint = new LatLng(35.681382, 139.766082);
    ArrayList<LatLng> markerPoints;
    private GoogleMap mMap = null;
    private final static String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        markerPoints = new ArrayList<LatLng>();
        final MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap map) {
                mMap = map;
                CameraPosition cameraPos = new CameraPosition.Builder()
                        .target(initialPoint).zoom(10.0f)
                        .bearing(0).build();
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPos));
                mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(LatLng latLng) {
                        markerPoints.add(latLng);
                        MarkerOptions options = new MarkerOptions();
                        options.position(latLng);
                        mMap.addMarker(options);
                    }
                });
            }
        });
        Button swim = (Button)findViewById(R.id.swim_button);
        swim.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                int pointsNumber = markerPoints.size();
                if (pointsNumber >= 2) {
                    PolylineOptions options = new PolylineOptions();
                    options.add(markerPoints.get(pointsNumber - 2), markerPoints.get(pointsNumber - 1));
                    options.color(Color.BLUE);
                    options.width(5);
                    options.geodesic(true);
                    mMap.addPolyline(options);
                }
            }
        });
        Button walk = (Button)findViewById(R.id.walk_button);
        walk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pointNumber = markerPoints.size();
                if (pointNumber >= 2) {
                    RouteSearch(markerPoints.get(pointNumber - 2), markerPoints.get(pointNumber - 1));
                }
            }
        });
    }


    private void RouteSearch(LatLng start, LatLng goal) {
        String url = getDirectionUrl(start, goal);
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.execute(url);
    }

    private String getDirectionUrl(LatLng start, LatLng goal) {
        String origin_parameter = "origin=" + start.latitude + "," + start.longitude;
        String dest_parameter = "destination=" + goal.latitude + "," + goal.longitude;
        String sensor_parameter = "sensor=false";
        String mode_parameter = "mode=walking";
        String key_parameter = "key=INPUT YOUR KEY";
        String parameters = origin_parameter + "&" + dest_parameter + "&" + sensor_parameter + "&language=ja" + "&" + mode_parameter + "&" + key_parameter;
        String output = "json";
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
        return url;
    }

    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(strUrl);
            urlConnection = (HttpURLConnection)url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        } catch(Exception e) {
            Log.d("Error downloading url", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }
    private class DownloadTask extends AsyncTask<String, Void, String>{
        protected String doInBackground(String... url) {
            String data = "";
            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Backgroud Task", e.toString());
            }
            return data;
        }

        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);
        }
    }

    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {
            JSONObject jsonObject;
            List<List<HashMap<String, String>>> routes = null;
            try {
                jsonObject = new JSONObject(jsonData[0]);
                parseJsonOfDirectionAPI parser = new parseJsonOfDirectionAPI();
                routes = parser.parse(jsonObject);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return routes;
        }
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();
            if (result.size() != 0) {
                for (int i = 0; i <result.size(); i++) {
                    points = new ArrayList<LatLng>();
                    lineOptions = new PolylineOptions();
                    List<HashMap<String, String>> path = result.get(i);
                    for (int j = 0; j < path.size(); j++) {
                        HashMap<String, String> point = path.get(j);
                        double lat = Double.parseDouble(point.get("lat"));
                        double lng = Double.parseDouble(point.get("lng"));
                        LatLng position = new LatLng(lat, lng);
                        points.add(position);
                    }
                    lineOptions.addAll(points);
                    lineOptions.width(10);
                    lineOptions.color(0x550000ff);
                }
                mMap.addPolyline(lineOptions);
            }
        }
    }
    public class parseJsonOfDirectionAPI{
        MainActivity ma;
        public List<List<HashMap<String, String>>> parse(JSONObject jsonObject) {
            String temp = "";
            List<List<HashMap<String, String>>> route = new ArrayList<List<HashMap<String,String>>>();
            JSONArray jsonRoutes = null;
            JSONArray jsonLegs = null;
            JSONArray jsonSteps = null;
            try{
                jsonRoutes = jsonObject.getJSONArray("routes");
                for (int i = 0; i < jsonRoutes.length(); i++) {
                    jsonLegs = ((JSONObject)jsonRoutes.get(i)).getJSONArray("legs");
                    //String s_address = (String)((JSONObject)(JSONObject)jsonLegs.get(i)).getString("start_address");
                    List path = new ArrayList<HashMap<String, String>>();
                    for (int j = 0; j < jsonLegs.length(); j++) {
                        jsonSteps = ((JSONObject)jsonLegs.get(j)).getJSONArray("steps");
                        for (int k = 0; k < jsonSteps.length(); k++) {
                            String polyline = (String)((JSONObject)((JSONObject)jsonSteps.get(k)).get("polyline")).get("points");
                            List<LatLng> list = decodePoly(polyline);
                            for (int l = 0; l < list.size(); l++) {
                                HashMap<String, String> hm = new HashMap<String, String>();
                                hm.put("lat", Double.toString(((LatLng)list.get(l)).latitude));
                                hm.put("lng", Double.toString(((LatLng)list.get(l)).longitude));
                                path.add(hm);
                            }
                        }
                    }
                    route.add(path);
                }
            } catch(JSONException e) {
                e.printStackTrace();
            } catch(Exception e) {

            }
            return route;
        }

        private List<LatLng> decodePoly(String encoded) {
            List<LatLng> poly = new ArrayList<LatLng>();
            int index = 0;
            int len = encoded.length();
            int lat = 0;
            int lng = 0;
            while(index < len) {
                int b, shift = 0, result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                lat += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while(b >= 0x20);
                lng += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                LatLng p = new LatLng((((double)lat / 1E5)), (((double)lng / 1E5)));
                poly.add(p);
            }
            return poly;
        }
    }
}
