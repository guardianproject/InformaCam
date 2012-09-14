package org.witness.informacam.storage;

import org.witness.informacam.utils.Constants.Settings;

import net.sqlcipher.database.SQLiteDatabase;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class DatabaseService extends Service {
	private final IBinder binder = new LocalBinder();
	private static DatabaseService databaseService;
	
	DatabaseHelper dh;
	SQLiteDatabase db;
	
	public class LocalBinder extends Binder {
		public DatabaseService getService() {
			return DatabaseService.this;
		}
	}
	
	public static DatabaseService getInstance() {
		return databaseService;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
	
	@Override
	public void onCreate() {
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(PreferenceManager.getDefaultSharedPreferences(this).getString(Settings.Keys.CURRENT_LOGIN, ""));
		databaseService = this;
	}
	
	@Override
	public void onDestroy() {
		db.close();
		dh.close();
	}

	public DatabaseHelper getHelper() {
		return dh;
	}
	
	public SQLiteDatabase getDb() {
		return db;
	}
}
