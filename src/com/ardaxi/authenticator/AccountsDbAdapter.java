package com.ardaxi.authenticator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

public class AccountsDbAdapter {	
	public static final Integer DEFAULT_COUNTER = 0;  // for hotp type
	private static final String TABLE_NAME = "accounts";
	static final String ID_COLUMN = "_id";
	static final String NAME_COLUMN = "name";
	static final String USER_COLUMN = "user";
	static final String URL_COLUMN = "url";
	static final String SECRET_COLUMN = "secret";
	private static final String PATH = "databases";
	private static SQLiteDatabase DATABASE = null;
	private static final String DATABASE_CREATE = String.format(
			"CREATE TABLE IF NOT EXISTS %s" +
					" (%s INTEGER PRIMARY KEY, %s TEXT NOT NULL, %s TEXT NOT NULL, %s TEXT NOT NULL," +
					" %s INTEGER DEFAULT %s)",
					TABLE_NAME, ID_COLUMN, NAME_COLUMN, USER_COLUMN, URL_COLUMN, SECRET_COLUMN, 
					DEFAULT_COUNTER);
	
	private AccountsDbAdapter()
	{
		// Empty
	}
	
	/**
	 * Opens the database, or creates it if it cannot be opened.
	 * @param context The Context in which to work
	 */
	static void initialize(Context context)
	{
		DATABASE = context.openOrCreateDatabase(PATH, Context.MODE_PRIVATE, null);
		DATABASE.execSQL(DATABASE_CREATE);
	}

	/**
	 * Retrieves the names and IDs for all accounts in the database.
	 * @return Cursor over all accounts
	 */
	static Cursor getNames() {
		return DATABASE.query(TABLE_NAME, new String[] {ID_COLUMN, NAME_COLUMN}, null, null, null, null, null, null);
	}
	
	static Cursor getAccount(Integer id)
	{
		return DATABASE.query(TABLE_NAME, null, ID_COLUMN + "= ?",
		        new String[] {id.toString()}, null, null, null);
	}
	
	static void deleteAccount(Integer id)
	{
		DATABASE.delete(TABLE_NAME, ID_COLUMN + "= ?",
		        new String[] {id.toString()});
	}
	
	private static String getColumn(Integer id, String column)
	{
		Cursor c = getAccount(id);
		try {
			if (!(c == null || c.getCount() == 0)) {
				c.moveToFirst();
				return c.getString(c.getColumnIndex(column));
			}
		} finally {
			if (c != null && !c.isClosed()) {
				c.close();
			}
		}
		return null;
	}
	
	static String getURL(Integer id)
	{
		return getColumn(id, URL_COLUMN);
	}
	
	static String getName(Integer id)
	{
		return getColumn(id, NAME_COLUMN);
	}
	
	static String getUser(int id) {
		return getColumn(id, USER_COLUMN);
	}
	
	static String getSecret(int id) {
		return getColumn(id, SECRET_COLUMN);
	}
	
	/**
	 * Save key to database, creating a new user entry.
	 * @param name the entry name.
	 * @param secret the secret key.
	 * @param url the url.
	 */
	static void addAccount(String name, String user, String secret, String url)
	{
		ContentValues values = new ContentValues();
		values.put(NAME_COLUMN, name);
		values.put(USER_COLUMN, user);
		values.put(SECRET_COLUMN, secret);
		values.put(URL_COLUMN, url);
		DATABASE.insert(TABLE_NAME, null, values);
	}
	
	/**
	 * Rename an entry
	 * @param oldName the old name.
	 * @param name the new name.
	 */
	static void renameAccount(String oldName, String name)
	{
		ContentValues values = new ContentValues();
		values.put(NAME_COLUMN, name);
		DATABASE.update(TABLE_NAME, values, 
                NAME_COLUMN + " = " + DatabaseUtils.sqlEscapeString(oldName), null);
	}
}
