package com.smp.weatherbase;

import static com.smp.weatherbase.Constants.LATITUDE;
import static com.smp.weatherbase.Constants.LOCATION;
import static com.smp.weatherbase.Constants.LONGITUDE;
import static com.smp.weatherbase.Constants.MAX_TIME;
import static com.smp.weatherbase.Constants.SHARED_PREF_NAME;
import static com.smp.weatherbase.Constants.USE_LOCATION;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.forecast.io.utilities.IOUtils;
import com.google.android.gms.location.LocationClient;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

public class UtilityMethods {
	public static final int LOCALITY = 2;
	public static final int ADMIN_AREA = 3;
	public static final int POSTAL_CODE = 6;

	@SuppressLint("NewApi")
	public static void strictModeDisabled() {
		int api = android.os.Build.VERSION.SDK_INT;
		if (api >= android.os.Build.VERSION_CODES.GINGERBREAD) {
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
					.permitAll().build();
			StrictMode.setThreadPolicy(policy);
		}
	}

	public static HttpURLConnection openConnectionWithTimeout(URL url)
			throws IOException {
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setConnectTimeout(MAX_TIME);
		connection.setReadTimeout(MAX_TIME);
		return connection;
	}

	public static boolean isOnline(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfoMob = cm
				.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		NetworkInfo netInfoWifi = cm
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		return ((netInfoMob != null && netInfoMob.isConnectedOrConnecting()) || (netInfoWifi != null && netInfoWifi
				.isConnectedOrConnecting()));
	}

	public static Object readObjectFromFile(Context context, String fileName) {
		Object result = null;
		FileInputStream fis = null;
		try {
			fis = context.openFileInput(fileName);
			ObjectInputStream objectIn = new ObjectInputStream(fis);
			return objectIn.readObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (fis != null)
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		return result;
	}

	public static void writeObjectToFile(Context context, String fileName,
			Object obj) {
		FileOutputStream fos = null;
		try {
			fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
			ObjectOutputStream objectOut = new ObjectOutputStream(fos);
			objectOut.writeObject(obj);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static JSONObject getJSONObjectFromAddress(String address) {
		HttpGet httpGet = new HttpGet(address);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response;
		StringBuilder stringBuilder = new StringBuilder();
		HttpEntity entity = null;
		InputStream stream = null;
		JSONObject jsonObject = new JSONObject();

		try {
			response = client.execute(httpGet);
			entity = response.getEntity();
			stream = entity.getContent();
			int b;
			while ((b = stream.read()) != -1) {
				stringBuilder.append((char) b);
			}

			jsonObject = new JSONObject(stringBuilder.toString());
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			// Log.e(MyGeocoder.class.getName(),
			// "Error calling Google geocode webservice.", e);
		} catch (IOException e) {
			e.printStackTrace();
			// Log.e(MyGeocoder.class.getName(),
			// "Error calling Google geocode webservice.", e);
		} catch (JSONException e) {
			e.printStackTrace();
			// Log.e(MyGeocoder.class.getName(),
			// "Error parsing Google geocode webservice response.", e);
		} finally {
			client.getConnectionManager().shutdown();
			IOUtils.closeQuietly(stream);
		}
		return jsonObject;

	}

	public static Address getLocationInfo(String address) {
		address = address.replace("\n", " ").replace(" ", "%20");
		String requestString = "http://maps.google.com/maps/api/geocode/json?address="
				+ address + "&ka&sensor=false";
		JSONObject jsonObject = getJSONObjectFromAddress(requestString);
		return getGeoPoint(jsonObject);
	}

	public static Address getGeoPoint(JSONObject jsonObject) {
		double lon = 0, lat = 0;
		String localityStr = "", adminStr = "", postalStr = "";

		try {
			JSONArray results = jsonObject.getJSONArray("results");
			JSONObject result = results.getJSONObject(0);
			lon = result.getJSONObject("geometry").getJSONObject("location")
					.getDouble("lng");
			lat = result.getJSONObject("geometry").getJSONObject("location")
					.getDouble("lat");
			JSONArray addComponents = result.getJSONArray("address_components");
			JSONObject locality = addComponents.optJSONObject(0);
			if (locality != null) {
				localityStr = locality.optString("long_name");
				if (localityStr.matches("[0-9]+")) {
					locality = addComponents.optJSONObject(1);
					localityStr = locality.optString("long_name");
				}
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Address addy = new Address(Locale.getDefault());
		addy.setLatitude(lat);
		addy.setLongitude(lon);
		Log.i("compare", localityStr + " " + adminStr + " " + postalStr);
		addy.setLocality(localityStr);
		return addy;
	}

	public static List<Address> getFromLocation(double lat, double lng,
			int maxResult) {
		String address = String
				.format(Locale.getDefault(),
						"http://maps.googleapis.com/maps/api/geocode/json?latlng=%1$f,%2$f&sensor=true&language="
								+ Locale.getDefault().getCountry(), lat, lng);

		JSONObject jsonObject = getJSONObjectFromAddress(address);
		List<Address> retList = new ArrayList<Address>();

		try {
			if ("OK".equalsIgnoreCase(jsonObject.getString("status"))) {
				JSONArray results = jsonObject.getJSONArray("results");
				for (int i = 0; i < results.length(); i++) {
					String adminStr = "", localityStr = "", postalStr = "";
					JSONObject result = results.getJSONObject(i);
					JSONArray addComponents = result
							.getJSONArray("address_components");
					JSONObject locality = addComponents.optJSONObject(LOCALITY);
					if (locality != null)
						localityStr = locality.optString("long_name");
					JSONObject admin = addComponents.optJSONObject(ADMIN_AREA);
					if (admin != null)
						adminStr = admin.optString("long_name");
					JSONObject postal = addComponents
							.optJSONObject(POSTAL_CODE);
					if (postal != null)
						postalStr = postal.optString("long_name");
					Address addr = new Address(Locale.getDefault());

					addr.setLocality(localityStr);
					addr.setAdminArea(adminStr);
					addr.setPostalCode(postalStr);

					retList.add(addr);
				}
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return retList;
	}

	public static SharedPreferences getPref(Context context) {
		int mode = android.os.Build.VERSION.SDK_INT >= 11 ? Context.MODE_MULTI_PROCESS
				: Context.MODE_PRIVATE;
		return context.getSharedPreferences(SHARED_PREF_NAME, mode);
	}

	public static String getLocationString(Address addy) {
		String location = "Location: ";
		if (addy.getLocality() != null && !addy.getLocality().equals(""))
			location = location + addy.getLocality();
		else if (addy.getAdminArea() != null && !addy.getAdminArea().equals(""))
			location = location + addy.getAdminArea();
		else if (addy.getPostalCode() != null)
			location = location + addy.getPostalCode();

		return location;
	}

	public static void makeNoConnectionToast(Context context) {
		Toast.makeText(context,
				"Problem connecting to services - you must be online.",
				Toast.LENGTH_SHORT).show();
	}

	public static boolean writeCurrentLocation(LocationClient locationClient,
			Context context) {
		Location location = getLocationFromLocationClient(locationClient);
		if (location != null) {
			writeLatLongFromLocation(location, context);
		} else {
			location = getLocationFromLocationManager(context);
		}
		if (location != null) {
			writeLatLongFromLocation(location, context);
		} else {
			Toast.makeText(
					context,
					"You must have location access enabled and be online for Hourly Weather Widget to update. Go to Settings - Location.",
					Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	private static Location getLocationFromLocationClient(
			LocationClient locationClient) {
		Location location = locationClient.getLastLocation();
		locationClient.disconnect();
		return location;
	}

	private static Location getLocationFromLocationManager(Context context) {
		LocationManager locationManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);
		String provider = locationManager.getBestProvider(new Criteria(), true);
		if (provider != null)
			return locationManager.getLastKnownLocation(provider);
		else
			return null;
	}

	public static void writeLatLongFromLocation(Location location,
			Context context) {
		Geocoder geo = new Geocoder(context);
		Address addy = null;
		String latitude = String.valueOf(location.getLatitude());
		String longitude = String.valueOf(location.getLongitude());
		try {
			List<Address> adds = geo.getFromLocation(location.getLatitude(),
					location.getLongitude(), 1);
			if (adds != null && adds.size() > 0)
				addy = adds.get(0);
		} catch (IOException e) {
			List<Address> adds = UtilityMethods.getFromLocation(
					location.getLatitude(), location.getLongitude(), 1);
			if (adds != null && adds.size() > 0) {
				addy = adds.get(0);
			} else {
				makeNoConnectionToast(context);
				e.printStackTrace();
			}
		}
		String locationStr = "Location: Unknown";
		if (addy != null) {
			locationStr = getLocationString(addy);
		}
		writeLatLong(latitude, longitude, locationStr, true, context);
	}

	@SuppressLint("InlinedApi")
	public static void writeLatLong(String latitude, String longitude,
			String location, boolean useLocation, Context context) {
		SharedPreferences pref = getPref(context);
		SharedPreferences.Editor ed = pref.edit();
		ed.putBoolean(USE_LOCATION, useLocation);
		ed.putString(LATITUDE, latitude);
		ed.putString(LONGITUDE, longitude);
		ed.putString(LOCATION, location);
		ed.commit();
	}
}
