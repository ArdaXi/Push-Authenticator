package com.ardaxi.authenticator;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

public class SettingsActivity extends PreferenceActivity {
	private SharedPreferences sharedPreferences;
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
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		addPreferencesFromResource(R.xml.preferences);
	}

	@Override
	public boolean onPreferenceTreeClick (PreferenceScreen preferenceScreen, Preference preference)
	{
		switch(Setting.toSetting(preference.getKey()))
		{
		case ENCRYPTION:
			//TODO: Test for encryption
			//TODO: Do this asynchronously
			AccountsDbAdapter.initialize(this);
			final View frame = getLayoutInflater().inflate(R.layout.pin,
					(ViewGroup) findViewById(R.id.pin_root));
			final EditText nameEdit = (EditText) frame.findViewById(R.id.pin_edittext);
			final Builder dialogBuilder = new AlertDialog.Builder(this)
			.setView(frame)
			.setTitle("Enter new PIN")
			.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					CryptoHelper crypto = new CryptoHelper(SettingsActivity.this, nameEdit.getText().toString().toCharArray());
					Cursor cursor = AccountsDbAdapter.getSecrets();
					cursor.moveToFirst();
					while(!cursor.isAfterLast())
					{
						String clientSecret = cursor.getString(cursor.getColumnIndex(AccountsDbAdapter.CLIENT_SECRET_COLUMN));
						String serverSecret = cursor.getString(cursor.getColumnIndex(AccountsDbAdapter.SERVER_SECRET_COLUMN));
						int id = cursor.getInt(cursor.getColumnIndex(AccountsDbAdapter.ID_COLUMN));
						AccountsDbAdapter.updateSecrets(id, crypto.encrypt(clientSecret), crypto.encrypt(serverSecret));
					}
					crypto.close();
				}
			});
			new AlertDialog.Builder(this)
			.setView(frame)
			.setTitle("Enter old PIN")
			.setPositiveButton("Change",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							decrypt(nameEdit);
							dialogBuilder.show();
						}
					})
					.setNegativeButton("Disable", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							decrypt(nameEdit);
						}
					})
					.setNeutralButton("Cancel", null)
					.show();
			return true;
		case LEGAL:
			startActivity(new Intent(this, LegalActivity.class));
			return true;
		}
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

	private void decrypt(final EditText nameEdit) {
		CryptoHelper crypto = new CryptoHelper(SettingsActivity.this, nameEdit.getText().toString().toCharArray());
		Cursor cursor = AccountsDbAdapter.getSecrets();
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			String clientSecret = cursor.getString(cursor.getColumnIndex(AccountsDbAdapter.CLIENT_SECRET_COLUMN));
			String serverSecret = cursor.getString(cursor.getColumnIndex(AccountsDbAdapter.SERVER_SECRET_COLUMN));
			int id = cursor.getInt(cursor.getColumnIndex(AccountsDbAdapter.ID_COLUMN));
			AccountsDbAdapter.updateSecrets(id, crypto.decrypt(clientSecret), crypto.decrypt(serverSecret));
		}
		crypto.close();
	}
}