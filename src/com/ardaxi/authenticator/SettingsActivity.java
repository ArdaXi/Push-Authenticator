package com.ardaxi.authenticator;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
	private enum Setting
	{
		ENCRYPTION, LEGAL, NOVALUE;
		
		public static Setting toSetting(String str)
		{
			try {
				return valueOf(str.toUpperCase());
			}
			catch (Exception e)
			{
				return NOVALUE;
			}
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {     
		super.onCreate(savedInstanceState);        
		addPreferencesFromResource(R.xml.preferences);
	}

	@Override
	public boolean onPreferenceTreeClick (PreferenceScreen preferenceScreen, Preference preference)
	{
		switch(Setting.toSetting(preference.getKey()))
		{
		case ENCRYPTION:
			Log.d("Encryption", Boolean.toString(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("encryption", false)));
			return true;
		case LEGAL:
			startActivity(new Intent(this, LegalActivity.class));
			return true;
		}
		// TODO: Ask for PIN when enabling encryption.
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}
}