package com.smp.hourlyweatherwidget;

import com.google.android.gms.location.LocationClient;
import com.smp.hourlyweatherwidget.ConfigureActivity.ContextHoldingAsyncTask;

import android.content.Context;
import android.os.AsyncTask;
import static com.smp.weatherbase.UtilityMethods.*;

public class LocationAsyncTask extends AsyncTask<Void, Void, Boolean> implements ContextHoldingAsyncTask
{
	@Override
	public void releaseReferences()
	{
		listener = null;
		locationClient = null;
	}
	@Override
	protected void onPostExecute(Boolean result)
	{
		if (listener != null) listener.onLocationComplete(result);
		listener = null;
		locationClient = null;
		super.onPostExecute(result);
	}
	
	private OnLocationCompleteListener listener;
	private LocationClient locationClient;
	private Context context;
	
	public interface OnLocationCompleteListener
	{
		void onLocationComplete(boolean success);
	}
	
	public LocationAsyncTask(OnLocationCompleteListener listener, LocationClient locationClient)
	{
		this.listener = listener;
		context = ((Context) listener).getApplicationContext();
		this.locationClient = locationClient;
	}
	@Override
	protected Boolean doInBackground(Void... args)
	{
		return writeCurrentLocation(locationClient, context);	
	}	
}
