package com.jmcristobal.sipphone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.jmcristobal.sipphone.R;

public class Main extends Activity implements OnClickListener {
	private static final String STUN_SERVER = "stun.counterpath.com";

	private static final String TAG = "MainActivity";

	private static final String SAMPLE_ALREADY_SETUP = "sample_already_setup";

	private long existingProfileId = SipProfile.INVALID_ID;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initilizeGui();
		configureSipStackPreferences();
	}

	private void initilizeGui() {
		setContentView(R.layout.main);
		// Bind view buttons
		((Button) findViewById(R.id.start_btn)).setOnClickListener(this);
		((Button) findViewById(R.id.save_acc_btn)).setOnClickListener(this);
		initializeAccountFromStoredSettings();
	}

	private void initializeAccountFromStoredSettings() {
		// Get current account if any
		Cursor c = getContentResolver().query(SipProfile.ACCOUNT_URI,
				new String[] { SipProfile.FIELD_ID, SipProfile.FIELD_ACC_ID, SipProfile.FIELD_REG_URI }, null, null,
				SipProfile.FIELD_PRIORITY + " ASC");
		if (c != null) {
			try {
				if (c.moveToFirst()) {
					SipProfile foundProfile = new SipProfile(c);
					existingProfileId = foundProfile.id;
					((TextView) findViewById(R.id.field_user)).setText(foundProfile.getSipUserName() + "@"
							+ foundProfile.getSipDomain());
				}
			} catch (Exception e) {
				Log.e(TAG, "Some problem occured while accessing cursor", e);
			} finally {
				c.close();
			}
		}
	}

	private void configureSipStackPreferences() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean alreadySetup = prefs.getBoolean(SAMPLE_ALREADY_SETUP, false);
		if (!alreadySetup) {
			SipConfigManager.setPreferenceStringValue(this, SipConfigManager.LOG_LEVEL, "4");
			SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.LOG_USE_DIRECT_FILE, false);
			SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.ICON_IN_STATUS_BAR, true);
			SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.ENABLE_STUN, true);
			SipConfigManager.setPreferenceStringValue(this, SipConfigManager.STUN_SERVER, STUN_SERVER);
		}
	}

	@Override
	public void onClick(View clickedView) {
		int clickedId = clickedView.getId();
		if (clickedId == R.id.start_btn) {
			startSipStack();
		} else if (clickedId == R.id.save_acc_btn) {
			saveAccount();
		}
	}

	private void startSipStack() {
		Intent it = new Intent(SipManager.INTENT_SIP_SERVICE);
		it.putExtra(SipManager.EXTRA_OUTGOING_ACTIVITY, new ComponentName(Main.this, Main.class));
		startService(it);
	}

	private void saveAccount() {
		String pwd = ((EditText) findViewById(R.id.field_password)).getText().toString();
		String fullUser = ((EditText) findViewById(R.id.field_user)).getText().toString();
		String[] splitUser = fullUser.split("@");
		String error = getValidAccountFieldsError(fullUser, pwd, splitUser);
		if (TextUtils.isEmpty(error)) {
			saveAccountSettingsInDatabase(pwd, fullUser, splitUser);
		} else {
			showAlertMessage(getResources().getString(R.string.invalid_settings), error);
		}
	}

	private String getValidAccountFieldsError(String user, String password, String[] splitUser) {
		if (TextUtils.isEmpty(user)) {
			return "Empty user";
		}
		if (TextUtils.isEmpty(password)) {
			return "Empty password";
		}
		if (splitUser.length != 2) {
			return "Invalid user, should be user@domain";
		}
		return "";
	}

	private void saveAccountSettingsInDatabase(String pwd, String fullUser, String[] splitUser) {
		SipProfile builtProfile = buildSipProfile(pwd, fullUser, splitUser);
		ContentValues builtValues = builtProfile.getDbContentValues();
		if (existingProfileId != SipProfile.INVALID_ID) {
			getContentResolver().update(ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, existingProfileId),
					builtValues, null, null);
		} else {
			Uri savedUri = getContentResolver().insert(SipProfile.ACCOUNT_URI, builtValues);
			if (savedUri != null) {
				existingProfileId = ContentUris.parseId(savedUri);
			}
		}
	}

	private SipProfile buildSipProfile(String pwd, String fullUser, String[] splitUser) {
		SipProfile builtProfile = new SipProfile();
		builtProfile.display_name = fullUser;
		builtProfile.id = existingProfileId;
		builtProfile.acc_id = "<sip:" + fullUser + ">";
		builtProfile.reg_uri = "sip:" + splitUser[1];
		builtProfile.realm = "*";
		builtProfile.username = splitUser[0];
		builtProfile.data = pwd;
		builtProfile.proxies = new String[] { "sip:" + splitUser[1] };
		return builtProfile;
	}

	private void showAlertMessage(String title, String error) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(error).setTitle(title).setCancelable(false)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	protected void onDestroy() {
		disconnect(false);
		super.onDestroy();
	}

	private void disconnect(boolean quit) {
		Log.d(TAG, "Disconnecting...");
		Intent intent = new Intent(SipManager.ACTION_OUTGOING_UNREGISTER);
		intent.putExtra(SipManager.EXTRA_OUTGOING_ACTIVITY, new ComponentName(this, Main.class));
		sendBroadcast(intent);
		if (quit) {
			finish();
		}
	}

}