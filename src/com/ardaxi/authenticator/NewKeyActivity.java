package com.ardaxi.authenticator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

import javax.crypto.KeyAgreement;

import org.apache.http.client.methods.HttpGet;
import org.spongycastle.jce.spec.ECParameterSpec;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

public class NewKeyActivity extends Activity {
	private static final String OTP_COM =
			"com.google.android.apps.authenticator";
	private static final String OTP_MARKET = 
			"market://search?q=pname:com.google.android.apps.authenticator";
	
	private boolean encryption;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(getIntent().getData() != null)
			return;
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
							CryptoHelper crypto = new CryptoHelper(NewKeyActivity.this, nameEdit.getText().toString().toCharArray());
							int accountId = AccountsDbAdapter.addAccount(name, user, crypto.encrypt(clientSecret), crypto.encrypt(serverSecret), url.toString());
							Toast.makeText(NewKeyActivity.this, R.string.account_added, Toast.LENGTH_SHORT).show();
							//TODO: new DownloadAuthRequest().execute(Integer.toString(accountId), nameEdit.getText().toString());
							crypto.close();
						}
					})
					.setNegativeButton(R.string.cancel, null)
					.show();
		}
		else
		{
			int accountId = AccountsDbAdapter.addAccount(name, user, clientSecret, serverSecret, url.toString());
			Toast.makeText(NewKeyActivity.this, R.string.account_added, Toast.LENGTH_SHORT).show();
			//TODO: new DownloadAuthRequest().execute(Integer.toString(accountId));
		}
		startActivity(new Intent(this, PushAuthenticatorActivity.class));
	}
	
	private class NegotiateKey extends AsyncTask<String, String, String[]>
	{
		@Override
		protected String[] doInBackground(String... args) {
			KeyPair keyPair = null;
			String url = args[0];
			String id = args[1];
			String name = args[2];
			String user = args[3];
			byte[] serverByte = new byte[20];
			byte[] clientByte = new byte[20];
			try {
				KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDH", "SC");
				ECGenParameterSpec ecParamSpec = new ECGenParameterSpec("secp224k1");
				kpg.initialize(ecParamSpec);
				keyPair = kpg.generateKeyPair();
				String pubKeyC = new String(Base64.encode(keyPair.getPublic().getEncoded()));
				url = Uri.parse(url).buildUpon().appendQueryParameter("id", id).appendQueryParameter("pubkey", pubKeyC).build().toString();
				AndroidHttpClient client = AndroidHttpClient.newInstance("PushAuthenticator", getApplication());
				HttpGet request = new HttpGet(url);
				BufferedReader rd = new BufferedReader(new InputStreamReader(client.execute(request).getEntity().getContent()));
				String pubKeyS = rd.readLine();
				rd.close();
				client.close();
				X509EncodedKeySpec p8ks = new X509EncodedKeySpec(Base64.decode(pubKeyS));
				PublicKey serverKey = KeyFactory.getInstance("ECDH", "SC").generatePublic(p8ks);
				KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", "SC");
				keyAgreement.init(keyPair.getPublic());
				keyAgreement.doPhase(serverKey, true);
				byte[] secret = keyAgreement.generateSecret();
				byte[] hash = MessageDigest.getInstance("SHA-1").digest(secret);
				System.arraycopy(hash, 0, serverByte, 0, 20);
				System.arraycopy(hash, 20, clientByte, 0, 20);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return new String[] { name, user, new String(Hex.encode(clientByte)), new String(Hex.encode(serverByte)), url };
		}
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
							Toast.makeText(NewKeyActivity.this, "Couldn't launch Market.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
	}
}
