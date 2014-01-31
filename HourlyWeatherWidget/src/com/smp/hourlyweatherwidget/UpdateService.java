package com.smp.hourlyweatherwidget;

import static com.smp.weatherbase.Constants.*;
import static com.smp.weatherbase.UtilityMethods.*;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.json.JSONObject;

import com.forecast.io.network.responses.INetworkResponse;
import com.forecast.io.network.responses.NetworkResponse;
import com.forecast.io.utilities.IOUtils;
import com.forecast.io.utilities.NetworkUtils;
import com.forecast.io.v2.network.responses.ForecastResponse;
import com.forecast.io.v2.network.services.ForecastService;
import com.forecast.io.v2.network.services.ForecastService.Response;
import com.forecast.io.v2.transfer.DataBlock;
import com.forecast.io.v2.transfer.DataPoint;
import com.forecast.io.v2.transfer.LatLng;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;


import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

public class UpdateService extends IntentService implements GooglePlayServicesClient.ConnectionCallbacks, OnConnectionFailedListener
{
	
	public enum Intensity
	{
		None, VLgt, Lgt, Mod, Hvy
	}
	LocationClient locationClient;
	Location location;
	String locationStr;
	boolean faren, mph;
	int dataOptionIntOne, dataOptionIntTwo;
	CountDownLatch gPlayServicesLatch;

	public UpdateService()
	{
		super("Update Service");
	}

	@Override
	public void onConnected(Bundle arg0)
	{
		strictModeDisabled();
		writeCurrentLocation(locationClient, this);
		gPlayServicesLatch.countDown();
	}
	private boolean shouldUpdate(Context context, SharedPreferences pref, boolean fromActivity)
	{
		boolean shouldUpdate = false;
		boolean battery = pref.getBoolean(OPTION_BATTERY, true);
		
		if (battery && !fromActivity)
		{
			long thisTime = System.currentTimeMillis();
			long lastTime = pref.getLong(PREF_LAST_UPDATE_TIME, ERROR_CODE);
			long timeElapsed = thisTime - lastTime;
			if (lastTime == ERROR_CODE || timeElapsed > MINIMUM_UPDATE_MS_BATTERY)
			{
				shouldUpdate = true;
			}
		}
		else
		{
			shouldUpdate = true;
		}
		return shouldUpdate;
	}
	@SuppressLint("InlinedApi")
	@Override
	public void onHandleIntent(Intent intent)
	{
		AppWidgetManager mgr = AppWidgetManager.getInstance(this);
		int[] appWidgetIds = mgr.getAppWidgetIds(new ComponentName(this, WidgetProvider.class));
		Intent configureIntent = new Intent(this, ConfigureActivity.class);
		configureIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[0]);
		PendingIntent configurePendingIntent = PendingIntent.getActivity(this, 0, configureIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		RemoteViews rv = null;

		SharedPreferences pref = getPref(this);
		boolean useLocation = pref.getBoolean(USE_LOCATION, false);
		boolean justDetermined = intent.getBooleanExtra(LOCATION_JUST_DETERMINED, false);
		boolean fromActivity = intent.getBooleanExtra(FROM_CONFIGURE_ACTIVITY, false);
		
		boolean shouldUpdate = shouldUpdate(this, pref, fromActivity);
		
		if (useLocation && shouldUpdate && !justDetermined )
		{
			gPlayServicesLatch = new CountDownLatch(1);
			locationClient = new LocationClient(this, this, this);
			locationClient.connect();
			try
			{
				gPlayServicesLatch.await(TIMEOUT_LATCH, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		double latitude = Double.parseDouble(pref.getString(LATITUDE, "0"));
		double longitude = Double.parseDouble(pref.getString(LONGITUDE, "0"));
		locationStr = pref.getString(LOCATION, "Loading..");
		faren = pref.getBoolean(FARENHEIGHT, true);
		//inches = pref.getBoolean(INCHES, true);
		mph = pref.getBoolean(MPH, true);
		dataOptionIntOne = pref.getInt(DATA_OPTION_ONE, TEMPERATURE);
		dataOptionIntTwo = pref.getInt(DATA_OPTION_TWO, PRECIP_PROBABILITY);
		//shoul
		
		ForecastService.Response response = null;
		if (shouldUpdate)
			response = getResponse(latitude, longitude);
		ForecastResponse newData = null;
		ForecastResponse oldData;

		if (response != null)
		{
			newData = response.getForecast();
		}
		if (newData != null)
		{
			//Log.d("NEWDATA", "LOOK AT TIME STUPID");
			Editor ed = pref.edit();
			ed.putLong(PREF_LAST_UPDATE_TIME, System.currentTimeMillis());
			ed.commit();
			saveData(newData);
			rv = normalLayoutConfigure(configurePendingIntent, newData);
		}
		else if ((oldData = getOldData()) != null)
		{
			rv = normalLayoutConfigure(configurePendingIntent, oldData);
		}
		else
		{
			rv = emptyLayoutConfigure(configurePendingIntent);
		}
		mgr.updateAppWidget(appWidgetIds, rv);
	}

	private RemoteViews normalLayoutConfigure(PendingIntent configurePendingIntent, ForecastResponse data)
	{
		RemoteViews rv;
		rv = buildRemoteViews(this, data);
		rv.setOnClickPendingIntent(R.id.location_dummy, configurePendingIntent);
		Intent linkIntent = new Intent(Intent.ACTION_VIEW);
		linkIntent.setData(Uri.parse(FORECAST_URL));
		linkIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent forecastIntent = PendingIntent.getActivity(this, 0, linkIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		rv.setOnClickPendingIntent(R.id.forecast_dummy, forecastIntent);
		
		return rv;
	}

	private RemoteViews emptyLayoutConfigure(PendingIntent configurePendingIntent)
	{
		RemoteViews rv;
		rv = new RemoteViews(this.getPackageName(), R.layout.empty_widget);
		rv.setOnClickPendingIntent(R.id.empty_layout, configurePendingIntent);
		return rv;
	}

	private void saveData(ForecastResponse newData)
	{
		writeObjectToFile(this, DATA_FILENAME, newData);
	}

	private ForecastResponse getOldData()
	{
		Object obj = readObjectFromFile(this, DATA_FILENAME);
		if (obj != null)
			return (ForecastResponse) obj;
		else
			return null;
	}

	public RemoteViews buildRemoteViews(Context context, ForecastResponse data)
	{
		RemoteViews parent_rv = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
		RemoteViews header_rv = new RemoteViews(context.getPackageName(), R.layout.header_layout);
		parent_rv.removeAllViews(R.id.vertical_layout);
		parent_rv.addView(R.id.vertical_layout, header_rv);
		parent_rv.setTextViewText(R.id.location_text, locationStr);
		Calendar cal = getTopOfHour();
		List<DataPoint> points = data.getHourly().getData();
		ListIterator<DataPoint> itr = points.listIterator();
		String amPm;
		for (int r = 0; r < MAX_ROWS; ++r)
		{
			RemoteViews child_rv = new RemoteViews(context.getPackageName(), R.layout.widget_layout_horizontal);
			child_rv.removeAllViews(R.id.horizontal_layout);
			for (int i = 0; i < MAX_HOURS_ROW; ++i)
			{
				cal.add(Calendar.HOUR, HOUR_ADD_SINGLE);
				int hTime = cal.get(Calendar.HOUR);
				DataPoint point = getDataPoint(cal, points, itr);
				
				if (hTime == 0)
					hTime = 12;
				amPm = cal.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm";
				RemoteViews hour = new RemoteViews(context.getPackageName(), R.layout.hour_button);
				// Log.i("compare", new Date(point.getTime() * 1000) + " " + new
				// Date(cal.getTimeInMillis()) );
				hour.setTextViewText(R.id.time, String.valueOf(hTime) + amPm);
				if (point != null)
				{
					String dataOptionOne = getDataOption(dataOptionIntOne, point);
					String dataOptionTwo = getDataOption(dataOptionIntTwo, point);
					int prob = (int) Math.round(point.getPrecipProbability() * DECIMAL_TO_PERCENT);
					hour.setTextViewText(R.id.data_option_one, dataOptionOne);
					hour.setTextViewText(R.id.data_option_two, dataOptionTwo);
					String icon = point.getIcon();
					if (icon != null)
					{
						setIcon(icon, hour, prob);
					}
				}
				child_rv.addView(R.id.horizontal_layout, hour);
				// no divider for last square
				if (i != MAX_HOURS_ROW - 1)
				{
					RemoteViews hourDivider = new RemoteViews(context.getPackageName(), R.layout.hour_divider);
					child_rv.addView(R.id.horizontal_layout, hourDivider);
				}
			}
			parent_rv.addView(R.id.vertical_layout, child_rv);
			if (r != MAX_ROWS - 1)
			{
				RemoteViews hourDivider = new RemoteViews(context.getPackageName(), R.layout.hour_horizontal_divider);
				parent_rv.addView(R.id.vertical_layout, hourDivider);
			}
		}
		RemoteViews footer_rv = null;

		footer_rv = new RemoteViews(context.getPackageName(), R.layout.bottom_padding);
		parent_rv.addView(R.id.vertical_layout, footer_rv);
		return parent_rv;
	}

	private String getDataOption(int dataOption, DataPoint point)
	{
		String result = "E";
		switch (dataOption)
		{
			case PRECIP_AMOUNT:
				double precipIntensity = point.getPrecipIntensity();
				result = determineIntensity(precipIntensity);
				break;
			case PRECIP_PROBABILITY:
				int prob = (int) Math.round(point.getPrecipProbability() * DECIMAL_TO_PERCENT);
				result = String.format("%d" + PERCENT, prob);
				break;
			case TEMPERATURE:
				result = String.format("%d" + DEGREE + (faren ? FARENHEIGHT_SYMBOL : CELSIUS_SYMBOL),
						faren ? Math.round(point.getTemperature()) : Math.round(fToC(point.getTemperature())));
				break;
			case APPARENT_TEMP:
				result = String.format("%d" + DEGREE + (faren ? FARENHEIGHT_SYMBOL : CELSIUS_SYMBOL),
						faren ? Math.round(point.getApparentTemperature()) : Math.round(fToC(point.getApparentTemperature())));
				break;
			case WIND_SPEED:
				double windSpeed = point.getWindSpeed();
				result = String.format("%d" + (mph ? "mh" : "kh"), mph? Math.round(windSpeed) : Math.round(miToKm(windSpeed)));	
				break;
			default:
				result = "E";
		}
		return result;
	}

	private String determineIntensity(double prec)
	{
		if (prec >= 0.4)
		{
			return Intensity.Hvy.toString();
		}
		if (prec >= 0.1)
		{
			return Intensity.Mod.toString();
		}
		if (prec >= 0.017)
		{
			return Intensity.Lgt.toString();
		}
		if (prec >= 0.002)
		{
			return Intensity.VLgt.toString();
		}
		else
		{
			return Intensity.None.toString();
		}
	}

	private double fToC(double f)
	{
		return (f - 32.0) * (5.0 / 9.0);
	}
	private double inToCm(double in)
	{
		return in * 2.54;
	}
	private double miToKm(double mi)
	{
		return mi * 1.60934;
	}

	private Calendar getTopOfHour()
	{
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		return cal;
	}

	private void setIcon(String icon, RemoteViews hour, int prob)
	{
		if (icon.equals("clear-day"))
			hour.setImageViewResource(R.id.image, R.drawable.clear_day);
		else if (icon.equals("clear-night"))
			hour.setImageViewResource(R.id.image, R.drawable.clear_night);
		else if (icon.equals("rain"))
		{
			if (prob < LOW_CHANCE)
				hour.setImageViewResource(R.id.image, R.drawable.low_rain);
			else
				hour.setImageViewResource(R.id.image, R.drawable.rain);
		}
		else if (icon.equals("snow"))
		{
			if (prob < LOW_CHANCE)
				hour.setImageViewResource(R.id.image, R.drawable.low_snow);
			else
				hour.setImageViewResource(R.id.image, R.drawable.snow);
		}
		else if (icon.equals("sleet"))
			hour.setImageViewResource(R.id.image, R.drawable.sleet);
		else if (icon.equals("wind"))
			hour.setImageViewResource(R.id.image, R.drawable.wind);
		else if (icon.equals("fog"))
			hour.setImageViewResource(R.id.image, R.drawable.fog);
		else if (icon.equals("cloudy"))
			hour.setImageViewResource(R.id.image, R.drawable.cloudy);
		else if (icon.equals("partly-cloudy-day"))
			hour.setImageViewResource(R.id.image, R.drawable.partly_cloudy_day);
		else if (icon.equals("partly-cloudy-night"))
			hour.setImageViewResource(R.id.image, R.drawable.partly_cloudy_night);

	}

	private DataPoint getDataPoint(Calendar dTime, List<DataPoint> points, ListIterator<DataPoint> itr)
	{
		while (itr.hasNext())
		{
			DataPoint point = itr.next();
			long pTime = point.getTime();
			Calendar tTime = Calendar.getInstance();
			tTime.setTime(new Date(pTime * SEC_TO_MS));
			if (tTime.equals(dTime))
			{
				return point;
			}
			else if (tTime.after(dTime))
			{
				itr.previous();
				return null;
			}
		}
		return null;
	}

	private ForecastService.Response getResponse(double latitude, double longitude)
	{
		LatLng latlng = LatLng.newBuilder().
				setLatitude(latitude).
				setLongitude(longitude).
				build();
		ForecastService.Request request = ForecastService.Request.newBuilder(API_KEY)
				.setLatLng(latlng)
				.build();
		INetworkResponse network = getNetworkResponse(request);
		if (network == null || network.getStatus() == NetworkResponse.Status.FAIL)
		{
			return null;
		}

		return (ForecastService.Response) network;
	}

	private INetworkResponse getNetworkResponse(ForecastService.Request request)
	{
		InputStream input = null;
		BufferedOutputStream output = null;
		HttpURLConnection connection = null;
		INetworkResponse response = null;

		try
		{
			response = (INetworkResponse) request.getResponse().newInstance();
			URL url = new URL(request.getUri().toString());
			connection = (HttpURLConnection) url.openConnection();
			connection.setReadTimeout(SOCKET_TIME_OUT);
			connection.setConnectTimeout(CONNECTION_TIME_OUT);
			connection.setRequestMethod(request.getMethod().toString());
			connection.setDoInput(true);

			if (NetworkUtils.Method.POST.equals(request.getMethod()))
			{
				String data = request.getPostBody();
				if (data != null)
				{
					connection.setDoOutput(true);
					connection.setRequestProperty("Content-Type", request.getContentType());
					output = new BufferedOutputStream(connection.getOutputStream());
					output.write(data.getBytes());
					output.flush();
					IOUtils.closeQuietly(output);
				}
			}
			int code = connection.getResponseCode();
			input = (code != HttpStatus.SC_OK) ? connection.getErrorStream() : connection.getInputStream();
			response.onNetworkResponse(new JSONObject(IOUtils.toString(input)));
			IOUtils.closeQuietly(input);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Log.e("Response error", e.toString());
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
			IOUtils.closeQuietly(input);
			IOUtils.closeQuietly(output);
		}
		return response;
	}

	@Override
	public void onDisconnected()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onConnectionFailed(ConnectionResult arg0)
	{
		// TODO Auto-generated method stub

	}

}
