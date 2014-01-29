/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smp.hourlyweatherwidget;

import static com.smp.weatherbase.Constants.*;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public class WidgetProvider extends AppWidgetProvider
{

	private volatile boolean configureUpdate = false;

	@Override
	public void onDeleted(Context context, int[] appWidgetIds)
	{
		super.onDeleted(context, appWidgetIds);
	}

	@Override
	public void onDisabled(Context context)
	{
		Log.i("compare", "on disabled");
		super.onDisabled(context);
	}

	@Override
	public void onEnabled(Context context)
	{

		Log.i("compare", "on enabled");
		super.onEnabled(context);
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.i("compare", "on receive");
		super.onReceive(context, intent);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
	{
		Log.i("compare", "on update");
		if (!configureUpdate)
		{
			Log.i("compare", "updating from on update");
			context.startService(new Intent(context, UpdateService.class));
		}
		configureUpdate = false;
		super.onUpdate(context, appWidgetManager, appWidgetIds);
	}
}