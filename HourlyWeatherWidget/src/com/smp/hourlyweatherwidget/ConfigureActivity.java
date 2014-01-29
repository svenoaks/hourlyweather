package com.smp.hourlyweatherwidget;

import static com.smp.weatherbase.Constants.*;

import static com.smp.weatherbase.UtilityMethods.*;

import java.io.IOException;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;

public class ConfigureActivity extends FragmentActivity implements
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener

{
	private static final String[] DATA_OPTIONS =
	{ "Precipitation Intensity",
			"Precipitation Probability", "Temperature", "Apparent Temperature",
			"Wind Speed" };
	private static final String[] UPDATE_FREQUENCY =
	{
			"Better Accuracy - every 1 hour", "Battery Saver - every 3 hours" };

	@Override
	protected void onPause()
	{
		super.onPause();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}

	private LocationClient locationClient;
	private CheckBox myLocationCheckBox;
	private EditText locationText;
	private int mAppWidgetId;
	private RadioButton fButton, cButton, mphButton, kphButton;
	private Spinner dataSpinnerOne, dataSpinnerTwo, updateSpinner;
	private int option1, option2;

	@SuppressLint(
	{ "InlinedApi", "NewApi" })
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.i("compare", "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_configure);
		getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		myLocationCheckBox = (CheckBox) findViewById(R.id.check);
		locationText = (EditText) findViewById(R.id.location_text);
		fButton = (RadioButton) findViewById(R.id.faren_button);
		cButton = (RadioButton) findViewById(R.id.celsius_button);
		// inButton = (RadioButton) findViewById(R.id.inches_button);
		// cmButton = (RadioButton) findViewById(R.id.cm_button);
		mphButton = (RadioButton) findViewById(R.id.mph_button);
		kphButton = (RadioButton) findViewById(R.id.kph_button);
		dataSpinnerOne = (Spinner) findViewById(R.id.data_option_one_spinner);
		dataSpinnerTwo = (Spinner) findViewById(R.id.data_option_two_spinner);
		updateSpinner = (Spinner) findViewById(R.id.update_spinner);
		setResult(RESULT_CANCELED);
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null)
		{
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
		}
		if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
		{
			finish();
		}
		int mode = android.os.Build.VERSION.SDK_INT >= 11 ? Context.MODE_MULTI_PROCESS
				: Context.MODE_PRIVATE;
		SharedPreferences pref = this.getSharedPreferences(SHARED_PREF_NAME,
				mode);
		boolean useLocation = pref.getBoolean(USE_LOCATION, false);
		boolean faren = pref.getBoolean(FARENHEIGHT, true);
		// boolean inches = pref.getBoolean(INCHES, true);
		boolean mph = pref.getBoolean(MPH, true);
		if (useLocation)
		{
			locationText.setEnabled(false);
		}
		myLocationCheckBox.setChecked(useLocation);
		if (faren)
			fButton.setChecked(true);
		else
			cButton.setChecked(true);
		// if (inches)
		// inButton.setChecked(true);
		// else
		// cmButton.setChecked(true);
		if (mph)
			mphButton.setChecked(true);
		else
			kphButton.setChecked(true);

		ArrayAdapter<CharSequence> dataAdapterOne = new ArrayAdapter<CharSequence>(
				this, android.R.layout.simple_spinner_item, DATA_OPTIONS);
		dataAdapterOne
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		dataSpinnerOne.setAdapter(dataAdapterOne);

		ArrayAdapter<CharSequence> dataAdapterTwo = new ArrayAdapter<CharSequence>(
				this, android.R.layout.simple_spinner_item, DATA_OPTIONS);
		dataAdapterTwo
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		dataSpinnerTwo.setAdapter(dataAdapterTwo);

		ArrayAdapter<CharSequence> updateAdapter = new ArrayAdapter<CharSequence>(
				this, android.R.layout.simple_spinner_item, UPDATE_FREQUENCY);
		updateAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		updateSpinner.setAdapter(updateAdapter);

		option1 = pref.getInt(DATA_OPTION_ONE, TEMPERATURE);
		option2 = pref.getInt(DATA_OPTION_TWO, PRECIP_PROBABILITY);

		boolean battery = pref.getBoolean(OPTION_BATTERY, true);
		if (battery)
			updateSpinner.setSelection(1);
		else
			updateSpinner.setSelection(0);

		dataSpinnerOne.setSelection(option1);
		dataSpinnerTwo.setSelection(option2);

		strictModeDisabled();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.configure, menu);
		return true;
	}

	public void onCheck()
	{
		SharedPreferences pref = getPref(this);
		SharedPreferences.Editor ed = pref.edit();
		ed.putBoolean(FARENHEIGHT, fButton.isChecked());
		// ed.putBoolean(INCHES, inButton.isChecked());
		ed.putBoolean(MPH, mphButton.isChecked());
		ed.putInt(DATA_OPTION_ONE, dataSpinnerOne.getSelectedItemPosition());
		ed.putInt(DATA_OPTION_TWO, dataSpinnerTwo.getSelectedItemPosition());
		boolean battery = updateSpinner.getSelectedItemPosition() == 1;
		ed.putBoolean(OPTION_BATTERY, battery);
		ed.commit();
		String latitude = null, longitude = null, location = "Location: ";
		if (myLocationCheckBox.isChecked())
		{
			if (servicesConnected())
			{
				locationClient = new LocationClient(this, this, this);
				locationClient.connect();
				return;
			}
			else
			{
				makeLocationToast();
				return;
			}
		}
		else
		{
			String locationEntered = locationText.getText().toString();
			if (locationEntered.length() == 0)
			{
				Toast.makeText(this, "No location entered", Toast.LENGTH_SHORT)
						.show();
				return;
			}
			Geocoder geo = new Geocoder(this);
			Address addy = null;
			if (isOnline(this))
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
				makeNoConnectionToast(this);
				return;
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
					makeLocationToast();
					return;
				}
			}
			else
			{
				makeLocationToast();
				return;
			}

		}
		// write lat, long not using user's location.
		writeLatLong(latitude, longitude, location, false, this);
		updateWidgetAndQuit();
	}

	private void updateWidgetAndQuit()
	{
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);

		Intent service = new Intent(this, UpdateService.class);
		service.putExtra(LOCATION_JUST_DETERMINED, true);
		service.putExtra(FROM_CONFIGURE_ACTIVITY, true);
		this.startService(service);
		finish();
	}

	private boolean servicesConnected()
	{
		// Check that Google Play services is available
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);
		// If Google Play services is available
		if (ConnectionResult.SUCCESS == resultCode)
		{
			// In debug mode, log the status
			Log.d("Location Updates", "Google Play services is available.");
			// Continue
			return true;
			// Google Play services was not available for some reason
		}
		else
		{
			showErrorDialog(resultCode);
			return false;
		}
	}

	private void showErrorDialog(int errorCode)
	{
		Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode,
				this, CONNECTION_FAILURE_RESOLUTION_REQUEST);

		// If Google Play services can provide an error dialog
		if (errorDialog != null)
		{
			ErrorDialogFragment errorFragment = new ErrorDialogFragment();
			// Set the dialog in the DialogFragment
			errorFragment.setDialog(errorDialog);
			// Show the error dialog in the DialogFragment
			errorFragment.show(getSupportFragmentManager(), "Location Updates");
		}
	}

	private void makeLocationToast()
	{
		Toast.makeText(this, "The location could not be determined",
				Toast.LENGTH_SHORT).show();
	}

	public void checkClick(View view)
	{
		if (myLocationCheckBox.isChecked())
		{
			locationText.setEnabled(false);
		}
		else
		{
			locationText.setEnabled(true);
		}
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult)
	{
		if (connectionResult.hasResolution())
		{
			try
			{
				// Start an Activity that tries to resolve the error
				connectionResult.startResolutionForResult(this,
						CONNECTION_FAILURE_RESOLUTION_REQUEST);
				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */
			}
			catch (IntentSender.SendIntentException e)
			{
				// Log the error
				e.printStackTrace();
			}
		}
		else
		{
			/*
			 * If no resolution is available, display a dialog to the user with
			 * the error.
			 */
			showErrorDialog(connectionResult.getErrorCode());
		}
	}

	@Override
	public void onConnected(Bundle arg0)
	{
		if (writeCurrentLocation(locationClient, this))
		{
			updateWidgetAndQuit();
		}
	}

	@Override
	public void onDisconnected()
	{

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_update:
				onCheck();
				break;
		}
		return true;
	}
}
