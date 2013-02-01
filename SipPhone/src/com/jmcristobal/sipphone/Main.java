package com.jmcristobal.sipphone;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.csipsimple.api.ISipService;
import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;

public class Main extends Activity implements OnClickListener {

	private static final String ANY_REALM = "*";
	private static final String SORT_ORDER = SipProfile.FIELD_PRIORITY + " ASC";
	private static final int ENABLE_ZRTP = 2;
	private static final String SIP_STACK_LOG_LEVEL = "4";
	// private static final String STUN_SERVER = "stun.counterpath.com";
	private static final String TAG = Main.class.toString();
	private long existingProfileId = SipProfile.INVALID_ID;
	private String SIP_ACC_ID_TEMPLATE = "<sip:%s@%s>";
	private String SIP_TEMPLATE = "sip:%s";

	private ISipService service;
	private ServiceConnection connection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			service = ISipService.Stub.asInterface(arg1);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			service = null;
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		configureSipStackPreferences();
	}

	private void configureSipStackPreferences() {
		SipConfigManager.setPreferenceStringValue(this, SipConfigManager.LOG_LEVEL, SIP_STACK_LOG_LEVEL);
		SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.LOG_USE_DIRECT_FILE, false);
		SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.ICON_IN_STATUS_BAR, true);
		// SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.ENABLE_STUN, true);
		// SipConfigManager.setPreferenceStringValue(this, SipConfigManager.STUN_SERVER, STUN_SERVER);
		SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.USE_ANYWAY_IN, true);
		SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.USE_ANYWAY_OUT, true);
		SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.USE_3G_IN, true);
		SipConfigManager.setPreferenceBooleanValue(this, SipConfigManager.USE_3G_OUT, true);
		SipConfigManager.setPreferenceIntegerValue(this, SipConfigManager.USE_ZRTP, ENABLE_ZRTP);
	}

	@Override
	public void onResume() {
		super.onResume();
		startSipStack();
		initializeGui();
		bindService(new Intent(SipManager.INTENT_SIP_SERVICE), connection, Context.BIND_AUTO_CREATE);
	}

	private void initializeGui() {
		setContentView(R.layout.main);
		// Bind view buttons
		((Button) findViewById(R.id.save_acc_btn)).setOnClickListener(this);
		((Button) findViewById(R.id.call_btn)).setOnClickListener(this);
		initializeSipAccountControlsFromStoredSettings();
	}

	private void initializeSipAccountControlsFromStoredSettings() {
		SipProfile foundProfile = getSipAccountFromDataBase();
		((TextView) findViewById(R.id.field_user)).setText(foundProfile.getSipUserName());
		((TextView) findViewById(R.id.field_domain)).setText(foundProfile.getSipDomain());
	}

	private SipProfile getSipAccountFromDataBase() {
		// Get current account if any
		Cursor databasePointer = getContentResolver().query(SipProfile.ACCOUNT_URI,
				new String[] { SipProfile.FIELD_ID, SipProfile.FIELD_ACC_ID, SipProfile.FIELD_REG_URI }, null, null,
				SORT_ORDER);
		SipProfile result = new SipProfile();
		if (databasePointer != null) {
			try {
				if (databasePointer.moveToFirst()) {
					result = new SipProfile(databasePointer);
					existingProfileId = result.id;
				}
			} catch (Exception e) {
				Log.e(TAG, "Some problem occured while accessing cursor", e);
			} finally {
				databasePointer.close();
			}
		}
		return result;
	}

	@Override
	public void onClick(View clickedView) {
		int clickedId = clickedView.getId();
		switch (clickedId) {
		case R.id.save_acc_btn:
			saveAccount();
			break;
		case R.id.call_btn:
			makeCall();
			break;
		default:
			break;
		}
	}

	private void saveAccount() {
		String username = ((EditText) findViewById(R.id.field_user)).getText().toString();
		String domain = ((EditText) findViewById(R.id.field_domain)).getText().toString();
		String password = ((EditText) findViewById(R.id.field_password)).getText().toString();
		String proxy = ((EditText) findViewById(R.id.field_proxy)).getText().toString();
		Account account = new Account();
		account.setUsername(username);
		account.setDomain(domain);
		account.setPassword(password);
		account.setProxy(proxy);
		saveAccountSettingsInDatabase(account);
	}

	private void saveAccountSettingsInDatabase(Account account) {
		SipProfile builtProfile = buildSipProfile(account);
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

	private SipProfile buildSipProfile(Account account) {
		SipProfile builtProfile = new SipProfile();
		builtProfile.id = existingProfileId;
		builtProfile.acc_id = String.format(SIP_ACC_ID_TEMPLATE, account.getUsername(), account.getDomain());
		builtProfile.username = account.getUsername();
		builtProfile.data = account.getPassword();
		builtProfile.display_name = account.getUsername();
		builtProfile.reg_uri = String.format(SIP_TEMPLATE, account.getDomain());
		builtProfile.realm = ANY_REALM;
		builtProfile.scheme = SipProfile.CRED_SCHEME_DIGEST;
		builtProfile.datatype = SipProfile.CRED_DATA_PLAIN_PASSWD;
		if (!TextUtils.isEmpty(account.getProxy()))
			builtProfile.proxies = new String[] { String.format(SIP_TEMPLATE, account.getProxy()) };
		return builtProfile;
	}

	private void makeCall() {
		if (service == null)
			return;
		String to = ((EditText) findViewById(R.id.field_sipaddress)).getText().toString();
		try {
			Long from = existingProfileId;
			service.makeCall(to, from.intValue());
		} catch (RemoteException e) {
			Log.e(TAG, "Service can't be called to make the call");
		}
	}

	private void startSipStack() {
		Thread t = new Thread("StartSip") {
			public void run() {
				Intent it = new Intent(SipManager.INTENT_SIP_SERVICE);
				it.putExtra(SipManager.EXTRA_OUTGOING_ACTIVITY, new ComponentName(Main.this, Main.class));
				startService(it);
			}
		};
		t.start();
	}

	@Override
	protected void onDestroy() {
		try {
			service.forceStopService();
			unbindService(connection);
		} catch (Exception e) {
			// Just ignore that
			Log.w(TAG, "Unable to un bind", e);
		}
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