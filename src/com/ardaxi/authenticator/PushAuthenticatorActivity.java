package com.ardaxi.authenticator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.Locale;

import org.apache.http.client.methods.HttpGet;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class PushAuthenticatorActivity extends ListActivity {

	private static final int SCAN_REQUEST = 31337;
	private static final String ZXING_MARKET = 
			"market://search?q=pname:com.google.zxing.client.android";
	private static final String ZXING_DIRECT = 
			"https://zxing.googlecode.com/files/BarcodeScanner3.1.apk";
	private static final String OTP_COM =
			"com.google.android.apps.authenticator";
	private static final String OTP_MARKET = 
			"market://search?q=pname:com.google.android.apps.authenticator";
	private static final String OCRA_SUITE = "OCRA-1:HOTP-SHA1-6:QN06";
	
	private Cursor accountsCursor;
	private boolean encryption;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		AccountsDbAdapter.initialize(this);
		accountsCursor = AccountsDbAdapter.getNames();
		startManagingCursor(accountsCursor);
		setListAdapter(new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, accountsCursor, new String[] { AccountsDbAdapter.NAME_COLUMN }, new int[] { android.R.id.text1 }));
		registerForContextMenu(getListView());
	}
	
	protected void onResume() {
		super.onResume();
		Uri uri = getIntent().getData();
		if (uri != null) {
			parseKey(uri);
			setIntent(new Intent());
		}
		encryption = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("encryption", false);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		getMenuInflater().inflate(R.menu.context_menu, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId())
		{
		case R.id.menu_delete:
			deleteItem((int) info.id);
			return true;
		case R.id.menu_rename:
			renameItem((int) info.id);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_add:
			addItem();
			return true;
		case R.id.menu_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		accountsCursor.moveToPosition(position);
		final String accountId = Integer.toString(accountsCursor.getInt(accountsCursor.getColumnIndex(AccountsDbAdapter.ID_COLUMN)));
		if(!encryption)
		{
			new DownloadAuthRequest().execute(accountId);
			return;
		}
		final View frame = getLayoutInflater().inflate(R.layout.pin,
				(ViewGroup) findViewById(R.id.pin_root));
		final EditText nameEdit = (EditText) frame.findViewById(R.id.pin_edittext);
		new AlertDialog.Builder(this)
		.setView(frame)
		.setTitle("Enter PIN")
		.setPositiveButton(R.string.okay,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						new DownloadAuthRequest().execute(accountId, nameEdit.getText().toString());
					}
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}
	
	private class DownloadAuthRequest extends AsyncTask<String, String, String[]>
	{
		private DialogInterface.OnCancelListener _cancelListener = new DialogInterface.OnCancelListener() {

			public void onCancel(DialogInterface dialog) {
				// TODO Set to false if there is a reliable way to cancel inside doInBackground
				DownloadAuthRequest.this.cancel(true);
			}
		};
		private ProgressDialog dialog;
		private String error = null;
		
		private ProgressDialog getDialog()
		{
			if(dialog == null)
			{
				dialog = ProgressDialog.show(PushAuthenticatorActivity.this, "", "Loading..", true, true);
				dialog.setOnCancelListener(_cancelListener);
			}
			return dialog;
		}

		@Override
		protected void onCancelled()
		{
			getDialog().dismiss();
			if(error != null)
				showAlertDialog(error);
			else
				Toast.makeText(PushAuthenticatorActivity.this, "Y U NO WAIT?", Toast.LENGTH_SHORT).show();
		}

		protected void onPreExecute()
		{
			getDialog();
		}

		@Override
		protected String[] doInBackground(String... params) {
			int columnId = Integer.parseInt(params[0]);
			String url = AccountsDbAdapter.getURL(columnId);
			String user = AccountsDbAdapter.getUser(columnId);
			String clientSecret = AccountsDbAdapter.getClientSecret(columnId);
			String serverSecret = AccountsDbAdapter.getServerSecret(columnId);
			if(params.length > 1)
			{
				publishProgress("Decrypting keys.");
				CryptoHelper crypto = new CryptoHelper(PushAuthenticatorActivity.this, params[1].toCharArray());
				params[1] = null;
				clientSecret = crypto.decrypt(clientSecret);
				serverSecret = crypto.decrypt(serverSecret);
				crypto.close();
			}
			publishProgress("Contacting server.");
			String clientChallenge = Integer.toString(new SecureRandom().nextInt(899999) + 100000);
			AndroidHttpClient client = AndroidHttpClient.newInstance("PushAuthenticator", getApplication());
			url = Uri.parse(url).buildUpon().appendQueryParameter("user", user).appendQueryParameter("client_challenge", clientChallenge).build().toString();
			HttpGet request = new HttpGet(url);
			String httpResponse;
			String serverChallenge;
			String verification;
			String details = null;
			try {
				BufferedReader rd = new BufferedReader(new InputStreamReader(client.execute(request).getEntity().getContent()));
				httpResponse = rd.readLine();
				rd.close();
				client.close();
				Log.d("HTTP", httpResponse);
				JSONObject jsonResponse = new JSONObject(httpResponse);
				if(!jsonResponse.getBoolean("request"))
				{
					error = "No request.";
					cancel(false);
					return null;
				}
				String serverResponse = jsonResponse.getString("response");
				String hexChallenge = Integer.toHexString(Integer.parseInt(clientChallenge));
				String ocra = OCRA.generateOCRA(OCRA_SUITE, serverSecret, "", hexChallenge, "", "", "");
				if(!serverResponse.equals(ocra))
				{
					error = "Server response invalid.";
					cancel(false);
					return null;
				}
				serverChallenge = jsonResponse.getString("challenge");
				verification = jsonResponse.getString("verification");
				if(jsonResponse.has("details")) details = jsonResponse.getString("details");
			} catch (Exception e) {
				e.printStackTrace();
				error = e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
				cancel(false);
				return null;
			}
			return new String[] {serverChallenge, verification, url, clientSecret, details};
		}

		protected void onProgressUpdate(String... progress)
		{
			getDialog().setMessage(MessageFormat.format("{0} {1}", progress[0], PushAuthenticatorActivity.this.getResources().getString(R.string.please_wait)));
		}

		protected void onPostExecute(final String[] result)
		{
			getDialog().dismiss();
			final AlertDialog.Builder builder = new AlertDialog
				.Builder(PushAuthenticatorActivity.this)
				.setTitle("Authentication request")
				.setMessage("Verification: "+result[1])
				.setCancelable(false)
				.setPositiveButton("Approve", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						new SendResponse().execute(result);
					}
				})
				.setNegativeButton("Deny", null);
			if(result[4] != null)
				builder.setNeutralButton("Details", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						new AlertDialog
							.Builder(PushAuthenticatorActivity.this)
							.setTitle("Authentication request")
							.setMessage(result[4])
							.setCancelable(false)
							.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									builder.show();
								}
							})
							.show();
					}
				});
			builder.show();
		}
	}
	
	private class SendResponse extends AsyncTask<String, String, Boolean>
	{
		private DialogInterface.OnCancelListener _cancelListener = new DialogInterface.OnCancelListener() {

			public void onCancel(DialogInterface dialog) {
				// TODO Set to false if there is a reliable way to cancel inside doInBackground
				SendResponse.this.cancel(true);
			}
		};
		private ProgressDialog dialog;
		private String error = null;
		
		private ProgressDialog getDialog()
		{
			if(dialog == null)
			{
				dialog = ProgressDialog.show(PushAuthenticatorActivity.this, "", "Loading..", true, true);
				dialog.setOnCancelListener(_cancelListener);
			}
			return dialog;
		}
		
		@Override
		protected void onCancelled()
		{
			getDialog().dismiss();
			if(error != null)
				showAlertDialog(error);
			else
				Toast.makeText(PushAuthenticatorActivity.this, "Y U NO WAIT?", Toast.LENGTH_SHORT).show();
		}

		protected void onPreExecute()
		{
			getDialog();
		}
		
		@Override
		protected Boolean doInBackground(String... params) {
			String serverChallenge = params[0];
			String url = params[2];
			String clientSecret = params[3];
			publishProgress("Calculating response.");
			String response = getResponse(clientSecret, serverChallenge);
			if(this.isCancelled())
				return false;
			publishProgress("Sending response to server.");
			url = Uri.parse(url).buildUpon().appendQueryParameter("server_challenge", serverChallenge).appendQueryParameter("response", response).build().toString();
			HttpGet request = new HttpGet(url);
			AndroidHttpClient client = AndroidHttpClient.newInstance("PushAuthenticator", PushAuthenticatorActivity.this);
			String httpResponse;
			try {
				BufferedReader rd = new BufferedReader(new InputStreamReader(client.execute(request).getEntity().getContent()));
				httpResponse = rd.readLine();
				rd.close();
			} catch (Exception e) {
				e.printStackTrace();
				error = e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
				cancel(false);
				return false;
			}
			client.close();
			return httpResponse.equals("1");
		}
		
		private String getResponse(String secret, String challenge) {
			String response = null;
			try {
				String hexChallenge = Integer.toHexString(Integer.parseInt(challenge));
				response = OCRA.generateOCRA(OCRA_SUITE, secret, "", hexChallenge, "", "", "");
			} catch (Exception e)
			{
				e.printStackTrace();
				error = e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
				cancel(false);
			}
			return response;
		}
		
		protected void onProgressUpdate(String... progress)
		{
			getDialog().setMessage(MessageFormat.format("{0} {1}", progress[0], PushAuthenticatorActivity.this.getResources().getString(R.string.please_wait)));
		}

		protected void onPostExecute(Boolean result)
		{
			getDialog().dismiss();
			Toast.makeText(PushAuthenticatorActivity.this, result ? "Login successful." : "Login unsuccessful", Toast.LENGTH_SHORT).show();
		}
	}

	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == SCAN_REQUEST && resultCode == Activity.RESULT_OK) {
			String contents = intent.getStringExtra("SCAN_RESULT");
			parseKey(Uri.parse(contents));
		}
	}
	
	private void parseKey(Uri uri)
	{
		boolean valid = true;
		String scheme = uri.getScheme().toLowerCase(Locale.US);
		final String name = uri.getAuthority();
		final String user = uri.getQueryParameter("user");
		final String clientSecret = uri.getQueryParameter("client_secret");
		final String serverSecret = uri.getQueryParameter("server_secret");
		final Uri url;
		try
		{
			url = Uri.parse(uri.getQueryParameter("url"));
		}
		catch (Exception e)
		{
			showAlertDialog(R.string.invalid_code_dialog_message);
			return;
		}
		if(!scheme.equals("ocra"))
		{
			if(scheme.equals("otpauth"))
				showDownloadDialogOTP();
			else
				showAlertDialog(R.string.invalid_code_dialog_message);
			return;
		}
		valid = valid
				&& name.length() > 0
				&& user != null && user.length() > 0
				&& clientSecret != null && clientSecret.length() > 0
				&& serverSecret != null && serverSecret.length() > 0
				&& url.isAbsolute()
				&& url.getScheme().substring(0, 4).equals("http");
		if(!valid)
		{
			showAlertDialog(R.string.invalid_code_dialog_message);
			return;
		}
		if(encryption)
		{
			final View frame = getLayoutInflater().inflate(R.layout.pin,
					(ViewGroup) findViewById(R.id.pin_root));
			final EditText nameEdit = (EditText) frame.findViewById(R.id.pin_edittext);
			new AlertDialog.Builder(this)
			.setView(frame)
			.setTitle("Enter PIN")
			.setPositiveButton(R.string.okay,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							CryptoHelper crypto = new CryptoHelper(PushAuthenticatorActivity.this, nameEdit.getText().toString().toCharArray());
							int accountId = AccountsDbAdapter.addAccount(name, user, crypto.encrypt(clientSecret), crypto.encrypt(serverSecret), url.toString());
							accountsCursor.requery();
							Toast.makeText(PushAuthenticatorActivity.this, R.string.account_added, Toast.LENGTH_SHORT).show();
							new DownloadAuthRequest().execute(Integer.toString(accountId), nameEdit.getText().toString());
							crypto.close();
						}
					})
					.setNegativeButton(R.string.cancel, null)
					.show();
		}
		else
		{
			int accountId = AccountsDbAdapter.addAccount(name, user, clientSecret, serverSecret, url.toString());
			accountsCursor.requery();
			Toast.makeText(PushAuthenticatorActivity.this, R.string.account_added, Toast.LENGTH_SHORT).show();
			new DownloadAuthRequest().execute(Integer.toString(accountId));
		}
	}

	/**
	 * @author © Google 2009
	 */
	private void showDownloadDialogOTP() {
		new AlertDialog
			.Builder(this)
			.setMessage(R.string.otp_dialog_message)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setCancelable(false)
			.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					try
					{
						startActivity(getPackageManager().getLaunchIntentForPackage(OTP_COM));
					}
					catch(ActivityNotFoundException e)
					{
						Intent intent = new Intent(Intent.ACTION_VIEW, 
								Uri.parse(OTP_MARKET));
						try { startActivity(intent); }
						catch (ActivityNotFoundException ex) { // if no Market app
							Toast.makeText(PushAuthenticatorActivity.this, "Couldn't launch Market.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
	}
	
	private void showAlertDialog(int messageId)
	{
		new AlertDialog
		.Builder(this)
		.setTitle(R.string.error)
		.setMessage(messageId)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setCancelable(false)
		.setNegativeButton(R.string.okay, null)
		.show();
	}
	
	private void showAlertDialog(String message)
	{
		new AlertDialog
		.Builder(this)
		.setTitle(R.string.error)
		.setMessage(message)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setCancelable(false)
		.setNegativeButton(R.string.okay, null)
		.show();
	}

	private void addItem()
	{
		Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
		intentScan.putExtra("SCAN_MODE", "QR_CODE_MODE");
		intentScan.putExtra("SAVE_HISTORY", false);
		try { startActivityForResult(intentScan, SCAN_REQUEST); }
		catch (ActivityNotFoundException e) { showDownloadDialogZxing(); }
	}
	
	private void deleteItem(final Integer id)
	{
		String name = AccountsDbAdapter.getName(id);
		new AlertDialog.Builder(this)
		.setTitle(name)
		.setMessage(R.string.delete_dialog_message)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				AccountsDbAdapter.deleteAccount(id);
				accountsCursor.requery();
			}
		})
		.setNegativeButton(R.string.no, null)
		.show();
	}
	
	private void renameItem(final Integer id)
	{
		final String name = AccountsDbAdapter.getName(id);
		final View frame = getLayoutInflater().inflate(R.layout.rename,
				(ViewGroup) findViewById(R.id.rename_root));
		final EditText nameEdit = (EditText) frame.findViewById(R.id.rename_edittext);
        nameEdit.setText(name);
		new AlertDialog.Builder(this)
		.setTitle(String.format(getString(R.string.rename_message), name))
		.setView(frame)
		.setPositiveButton(R.string.submit,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						String newName = nameEdit.getText().toString();
						AccountsDbAdapter.renameAccount(name, newName);
						accountsCursor.requery();
					}
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}
	
	/**
	 * @author © Google 2009
	 */
	private void showDownloadDialogZxing() {
		new AlertDialog.Builder(this)
		.setTitle(R.string.install_dialog_title)
		.setMessage(R.string.install_dialog_message)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setPositiveButton(R.string.install_button, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(Intent.ACTION_VIEW, 
						Uri.parse(ZXING_MARKET));
				try { startActivity(intent); }
				catch (ActivityNotFoundException e) { // if no Market app
					intent = new Intent(Intent.ACTION_VIEW,
							Uri.parse(ZXING_DIRECT));
					startActivity(intent);
				}
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}
}