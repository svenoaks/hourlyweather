package com.smp.hourlyweatherwidget;

import static com.smp.weatherbase.UtilityMethods.*;


import java.io.IOException;
import java.util.List;

import com.smp.hourlyweatherwidget.ConfigureActivity.ContextHoldingAsyncTask;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;

public class GeoCodingAsyncTask extends AsyncTask<String, Void, Boolean> implements ContextHoldingAsyncTask

{
	private OnGeoCodingCompleteListener listener;
	private Context context;
	
	public GeoCodingAsyncTask(OnGeoCodingCompleteListener listener)
	{
		this.listener = listener;
		context = ((Context) listener).getApplicationContext();
	}
	public interface OnGeoCodingCompleteListener
	{
		void onGeoCodingComplete(boolean success);
	}
	@Override
	protected Boolean doInBackground(String... params)
	{
		return reverseGeocodeText(params[0]);
	}
	
	public boolean reverseGeocodeText(String locationEntered)
	{
		String latitude = null, longitude = null, location = "Location: ";
		Geocoder geo = new Geocoder(context);
		Address addy = null;
		if (isOnline(context))
		{
			try
			{
				List<Address> adds = geo.getFromLocationName(
						locationEntered, 1);
				if (adds != null && adds.size() > 0)
					addy = adds.get(0);
			}
			catch (IOException e)
			{
				addy = getLocationInfo(locationEntered);
				e.printStackTrace();
				// return;
			}
		}
		else
		{
			return false;
		}

		if (addy != null)
		{
			latitude = String.valueOf(addy.getLatitude());
			longitude = String.valueOf(addy.getLongitude());
			boolean valid = latitude != null && longitude != null;
			// if (valid && addy.getSubLocality() != null)
			// location = addy.getSubLocality();
			if (valid && ((location = getLocationString(addy)) != null))
				;
			else
			{
				return false;
			}
		}
		else
		{
			return false;
		}
		// write lat, long not using user's location.
		writeLatLong(latitude, longitude, location, false, context);
		return true;
	}
	@Override
	protected void onPostExecute(Boolean result)
	{
		if (listener != null) listener.onGeoCodingComplete(result);
		listener = null;
		super.onPostExecute(result);
	}
	@Override
	public void releaseReferences()
	{
		listener = null;
		context = null;
	}

}
