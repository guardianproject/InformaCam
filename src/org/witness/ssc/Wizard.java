package org.witness.ssc;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;

import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.ImageConstructor;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.Keys.Device;
import org.witness.informa.utils.InformaConstants.Keys.Owner;
import org.witness.informa.utils.InformaConstants.Keys.TrustedDestinations;
import org.witness.informa.utils.io.DatabaseHelper;
import org.witness.informa.utils.secure.Apg;
import org.witness.informa.utils.secure.MediaHasher;
import org.witness.mods.InformaButton;
import org.witness.mods.InformaEditText;
import org.witness.mods.InformaSpinner;
import org.witness.mods.InformaTextView;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.utils.Selections;
import org.witness.ssc.utils.SelectionsAdapter;
import org.witness.ssc.R;

import com.actionbarsherlock.app.SherlockActivity;
import com.xtralogic.android.logcollector.SendLogActivity;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;

import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class Wizard extends SherlockActivity implements OnClickListener {
	int current;
	int backOffset = 1;
	int nextOffset = 1;
	String orderFile;
		
	LinearLayout frame_content, navigation_holder;
	TextView frame_title, progress;
	InformaButton wizard_next, wizard_back, wizard_done;
	
	private SharedPreferences preferences;
	private SharedPreferences.Editor _ed;
		
	WizardForm wizardForm;
	
	Apg apg;
	DatabaseHelper dh;
	SQLiteDatabase db;
		
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wizard);
		
		SQLiteDatabase.loadLibs(this);
		
		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		_ed = preferences.edit();
		
		current = getIntent().getIntExtra("current", 0);
		
		if(getIntent().hasExtra("wizardRoot"))
			orderFile = getIntent().getStringExtra("wizardRoot");
		else
			orderFile = "order.wizard";
		
		wizardForm = new WizardForm(this, orderFile);
		
		frame_title = (TextView) findViewById(R.id.wizard_frame_title);
		
		frame_content = (LinearLayout) findViewById(R.id.wizard_frame_content);
		navigation_holder = (LinearLayout) findViewById(R.id.wizard_navigation_holder);
		
		wizard_done = (InformaButton) findViewById(R.id.wizard_done);
		wizard_back = (InformaButton) findViewById(R.id.wizard_back);
		wizard_next = (InformaButton) findViewById(R.id.wizard_next);
		
		if(current < wizardForm.frames.length() - 1)
			wizard_next.setOnClickListener(this);
		else {
			wizard_next.setVisibility(View.GONE);
			wizard_back.setVisibility(View.GONE);
			
			wizard_done.setVisibility(View.VISIBLE);
			wizard_done.setOnClickListener(this);
		}
		
		if(current > 0)
			wizard_back.setOnClickListener(this);
		else {
			setMandatory(wizard_back);
		}
		
		progress = (TextView) findViewById(R.id.wizard_progress);
		progress.setText((current + 1) + "/" + wizardForm.frames.length());
		
		try {
			initFrame();
		} catch(JSONException e) {
			Log.e(InformaConstants.TAG, e.toString());
		}
	}
	
	private void sendLog() {
		Intent intent = new Intent(this, SendLogActivity.class);
		startActivity(intent);
	}
	
	@Override
	public void onStop() {
		super.onStop();
	}
	
	public void setMandatory(View v) {
		((InformaButton) v).getBackground().setAlpha(100);
		((InformaButton) v).setClickable(false);
	}
	
	public void enableAction(View v) {
		((InformaButton) v).getBackground().setAlpha(255);
		((InformaButton) v).setClickable(true);
	}
	
	public void disableAction(View v) {
		((InformaButton) v).getBackground().setAlpha(100);
		((InformaButton) v).setClickable(false);
	}
	
	public void initFrame() throws JSONException {
		wizardForm.setFrame(current);
		frame_title.setText(wizardForm.getTitle());

		ArrayList<View> views = wizardForm.getContent();
		for(View v : views)
			frame_content.addView(v);
	}
	
	@SuppressWarnings("unused")
	private void getUserPGP() {
		try {
			apg = Apg.getInstance();
			if(!apg.isAvailable(getApplicationContext()))
				ObscuraConstants.makeToast(this, getResources().getString(R.string.wizard_error_no_apg));
			else {
				apg.selectSecretKey(this);
			}
		} catch(Exception e) {
			sendLog();
		}
	}
	
	@SuppressWarnings("unused")
	private void getTrustedDestinations() {
		try {
			apg = Apg.getInstance();
			if(!apg.isAvailable(getApplicationContext()))
				ObscuraConstants.makeToast(this, getResources().getString(R.string.wizard_error_no_apg));
			else
				apg.selectEncryptionKeys(this, null);
		} catch(Exception e) {
			sendLog();
		}
	}
	
	private void setTrustedDestinations() {	
		try {
			dh = new DatabaseHelper(this);
			db = dh.getWritableDatabase(preferences.getString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, ""));
			
			dh.setTable(db, InformaConstants.Keys.Tables.TRUSTED_DESTINATIONS);
			for(long key : apg.getEncryptionKeys()) {
				String userId = apg.getPublicUserId(this, key);
				String email_ = userId.substring(userId.indexOf("<") + 1);
				String email = email_.substring(0, email_.indexOf(">"));
				String displayName = userId.substring(0, userId.indexOf("<"));
				
				if(userId.indexOf("(") != -1)
					displayName = userId.substring(0, userId.indexOf("("));
				
				ContentValues cv = new ContentValues();
				cv.put(InformaConstants.Keys.TrustedDestinations.KEYRING_ID, key);
				cv.put(InformaConstants.Keys.TrustedDestinations.EMAIL, email);
				cv.put(InformaConstants.Keys.TrustedDestinations.DISPLAY_NAME, displayName);
				
				db.insert(dh.getTable(), null, cv);
			}
			
			db.close();
			dh.close();
			
			if(orderFile.equals("encrypt_routine.wizard")) {
				wizard_next.setVisibility(View.GONE);
				wizard_back.setVisibility(View.GONE);
					
				wizard_done.setVisibility(View.VISIBLE);
				wizard_done.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						finish();
					}
					
				});
			} else 
				enableAction(wizard_next);
			
		} catch(Exception e) {
			Log.d(InformaConstants.TAG, "error : " + e.toString());
			sendLog();
		}
	}
	
	private void setUserPGP() {	
		try {
			dh = new DatabaseHelper(this);
			db = dh.getWritableDatabase(preferences.getString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, ""));
			
			dh.setTable(db, InformaConstants.Keys.Tables.SETUP);
						
			ContentValues cv = new ContentValues();
			cv.put(InformaConstants.Keys.Owner.SIG_KEY_ID, apg.getSignatureKeyId());
			
			int insert = db.update(dh.getTable(), cv, BaseColumns._ID + " = ?", new String[] {Long.toString(1)});
			
			Log.d(InformaConstants.TAG, "updated user pgp: " + insert);
			//if(insert != 0)
			enableAction(wizard_next);
			
			db.close();
			dh.close();
		} catch(Exception e) {
			Log.d(InformaConstants.TAG, "error : " + e.toString());
			sendLog();
		}
	}
	
	private long getPublicTimestamp(long ts) {
		//TODO public timestamp?
		return ts;
	}
	
	@SuppressWarnings("unused")
	private void saveDBPW(String pw) {
		try {
			_ed.putString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, pw).commit();
		} catch(Exception e) {
			sendLog();
		}
	}
	
	@SuppressWarnings("unused")
	private void setDBPWCache(ArrayList<Selections> cacheSelection) {
		try {
			for(Selections s : cacheSelection) {
				if(s.getSelected())
					_ed.putString(InformaConstants.Keys.Settings.DB_PASSWORD_CACHE_TIMEOUT, String.valueOf(cacheSelection.indexOf(s) + 200)).commit();
			}
		} catch(Exception e) {
			sendLog();
		}
	}
	
	@SuppressWarnings("unused")
	private void setDefaultImageHandling(ArrayList<Selections> imageHandlingSelection) {
		try {
			for(Selections s : imageHandlingSelection) {
				if(s.getSelected())
					_ed.putString(InformaConstants.Keys.Settings.DEFAULT_IMAGE_HANDLING, String.valueOf(imageHandlingSelection.indexOf(s) + 300)).commit();
			}
		} catch(Exception e) {
			sendLog();
		}
		
		if(!preferences.getBoolean(InformaConstants.Keys.Settings.WITH_ENCRYPTION, false))
			backOffset += 3;
	}
	
	@SuppressWarnings("unused")
	private void setEncryptionValues(ArrayList<Selections> encryptionSelection) {
		String[] encryptionValues = getResources().getStringArray(R.array.enable_encryption_values);
		try {
			for(Selections s: encryptionSelection) {
				if(s.getSelected()) {
					_ed.putBoolean(InformaConstants.Keys.Settings.WITH_ENCRYPTION, Boolean.parseBoolean(encryptionValues[encryptionSelection.indexOf(s)])).commit();
					if(encryptionSelection.indexOf(s) != 0)
						nextOffset += 2;
				}
			}
		} catch(Exception e) {
			sendLog();
		}
	}
	
	private void setDeviceId(Bitmap baseImage) throws IOException {
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(preferences.getString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, ""));
		
		// set up the device.
		dh.setTable(db, InformaConstants.Keys.Tables.KEYRING);
		
		ByteBuffer b = ByteBuffer.allocate(baseImage.getRowBytes() * baseImage.getHeight());
		baseImage.copyPixelsToBuffer(b);
		
		byte[] imageBytes = new byte[b.capacity()];
		try {
			b.get(imageBytes, 0, imageBytes.length);
		} catch(BufferUnderflowException e) {
			Log.d(InformaConstants.TAG, "buffer underflow!" + e.toString());
		}
		
		
		ContentValues cv = new ContentValues();
		cv.put(Device.BASE_IMAGE, imageBytes);
		cv.put(TrustedDestinations.DISPLAY_NAME, InformaConstants.Device.SELF);
		
		long insert = db.insert(dh.getTable(), null, cv);
		
		// set up the user.
		dh.setTable(db, InformaConstants.Keys.Tables.SETUP);
		long localTimestamp = System.currentTimeMillis();
		
		cv = new ContentValues();
		cv.put(InformaConstants.Keys.Owner.DEFAULT_SECURITY_LEVEL, InformaConstants.SecurityLevels.UNENCRYPTED_NOT_SHARABLE);
		cv.put(InformaConstants.Keys.Owner.OWNERSHIP_TYPE, InformaConstants.Owner.INDIVIDUAL);
		cv.put(InformaConstants.Keys.Device.LOCAL_TIMESTAMP, localTimestamp);
		cv.put(InformaConstants.Keys.Device.PUBLIC_TIMESTAMP, getPublicTimestamp(localTimestamp));
		insert = db.insert(dh.getTable(), null, cv);
		
		db.close();
		dh.close();
		
		if(insert > 0)
			passThrough();
	}
	
	@SuppressWarnings({ "deprecation", "unused" })
	private void registerDeviceKeys() {
		dh = new DatabaseHelper(this);
		db = dh.getWritableDatabase(preferences.getString(InformaConstants.Keys.Settings.HAS_DB_PASSWORD, ""));
		
		Security.addProvider(new BouncyCastleProvider());
		KeyPairGenerator kpg;
		
		dh.setTable(db, InformaConstants.Keys.Tables.KEYRING);
		Cursor c = dh.getValue(db, new String[] {Keys.Device.BASE_IMAGE}, BaseColumns._ID, 1);
		if(c != null && c.getCount() > 0) {
			c.moveToFirst();
			byte[] baseImage = c.getBlob(c.getColumnIndex(Keys.Device.BASE_IMAGE));
			c.close();
			
			// init keypair
			
			try {
				String pwd = generatePassword(baseImage);
				kpg = KeyPairGenerator.getInstance("RSA","BC");
				kpg.initialize(4096);
				KeyPair keyPair = kpg.generateKeyPair();
				
				PGPSignatureSubpacketGenerator hashedGen = new PGPSignatureSubpacketGenerator();
				hashedGen.setKeyFlags(true, KeyFlags.ENCRYPT_STORAGE);
				hashedGen.setPreferredCompressionAlgorithms(false, new int[] {
					CompressionAlgorithmTags.ZLIB,
					CompressionAlgorithmTags.ZIP
				});
				hashedGen.setPreferredHashAlgorithms(false, new int[] {
					HashAlgorithmTags.SHA256,
					HashAlgorithmTags.SHA384,
					HashAlgorithmTags.SHA512
				});
				hashedGen.setPreferredSymmetricAlgorithms(false, new int[] {
					SymmetricKeyAlgorithmTags.AES_256,
					SymmetricKeyAlgorithmTags.AES_192,
					SymmetricKeyAlgorithmTags.AES_128,
					SymmetricKeyAlgorithmTags.CAST5,
					SymmetricKeyAlgorithmTags.DES
				});
				
				PGPSecretKey secret = new PGPSecretKey(
						PGPSignature.DEFAULT_CERTIFICATION,
						PublicKeyAlgorithmTags.RSA_GENERAL,
						keyPair.getPublic(),
						keyPair.getPrivate(),
						new Date(),
						"InformaCam OpenPGP Key",
						SymmetricKeyAlgorithmTags.AES_256,
						pwd.toCharArray(),
						hashedGen.generate(),
						null,
						new SecureRandom(),
						"BC");
				
				ContentValues cv = new ContentValues();
				cv.put(Device.PRIVATE_KEY, secret.getEncoded());
				cv.put(Device.PASSPHRASE, pwd);
				cv.put(Device.PUBLIC_KEY, secret.getPublicKey().getEncoded());
				cv.put(Device.PUBLIC_KEY_HASH, MediaHasher.hash(secret.getPublicKey().getEncoded(), "SHA-1"));
				
				Log.d(InformaConstants.TAG, "key id: " + secret.getKeyID());				
				// update cv with new key
				db.update(dh.getTable(), cv, BaseColumns._ID + " = ?", new String[] {Integer.toString(1)});
				_ed.putBoolean(Keys.Settings.WITH_ENCRYPTION, true).commit();
			} catch (NoSuchAlgorithmException e) {
				Log.e(InformaConstants.TAG, "key error: " + e.toString());
			} catch (NoSuchProviderException e) {
				Log.e(InformaConstants.TAG, "key error: " + e.toString());
			} catch (PGPException e) {
				Log.e(InformaConstants.TAG, "key error: " + e.toString());
			} catch (IOException e) {
				Log.e(InformaConstants.TAG, "key error: " + e.toString());
			}
			
		}
		
		db.close();
		dh.close();
		
		passThrough();
	}
	
	private String generatePassword(byte[] baseImage) throws NoSuchAlgorithmException {
		// initialize random bytes
		byte[] randomBytes = new byte[baseImage.length];
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
		sr.nextBytes(randomBytes);
		
		// xor by baseImage
		byte[] product = new byte[baseImage.length];
		for(int b = 0; b < baseImage.length; b++) {
			product[b] = (byte) (baseImage[b] ^ randomBytes[b]);
		}
		
		// digest to SHA1 string, voila password.
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		return Base64.encodeToString(md.digest(product), Base64.DEFAULT);
	}
	
	@SuppressWarnings("unused")
	private String[] getDefaultImageHandlingOptions() {
		return getResources().getStringArray(R.array.default_image_handling);
	}
	
	@SuppressWarnings("unused")
	private String[] getDBPWCacheValues() {
		return getResources().getStringArray(R.array.password_cache);
	}
	
	@SuppressWarnings("unused")
	private String[] getEncryptionValues() {
		return getResources().getStringArray(R.array.enable_encryption);
	}
	
	@SuppressWarnings("unused")
	private void getDeviceId() {
        Intent  intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 60000L);		
		startActivityForResult(intent, InformaConstants.FROM_REGISTRATION_IMAGE);
	}
	
	private class WizardForm extends JSONObject {
		Context _c;
		JSONArray frames, order;
		JSONObject currentFrame;
		ArrayList<Callback> callbacks;
		
		public final static String frameKey = "frameKey";
		public final static String frameTitle = "frameTitle";
		public final static String frameContent = "frameContent";
		public final static String frameOrder = "frameOrder";
		public final static String allFrames = "frames";
		
		public WizardForm(Context c, String orderFile) {
			_c = c;
			frames = new JSONArray();
			order = new JSONArray();
			callbacks = new ArrayList<Callback>();
			
			// get the list of files within assets/wizard
			try {
				String[] allFiles = _c.getAssets().list("wizard");
				for(String f : allFiles) {
					// get the file
					BufferedReader br = new BufferedReader(new InputStreamReader(_c.getAssets().open("wizard/" + f)));
					String line;
					StringBuilder sb = new StringBuilder();
					while((line = br.readLine()) != null)
						sb.append(line).append('\n');
					
					// if the file is not "order.json"
					//if(f.compareTo("order.wizard") == 0) {
					if(f.contains(".wizard")) {
						if(f.equals(orderFile)) {
							for(String s : sb.toString().split(","))
								order.put(s);
						}
					} else {
						JSONObject frame = new JSONObject();
						frame.put(frameKey, f);
						frame.put(frameTitle, parseAsTitle(f));
						frame.put(frameContent, sb.toString());
						frames.put(frame);	
					}
					
					br.close();
				}
				this.put(frameOrder, order);
				this.put(allFrames, frames);
			} catch (IOException e) {
				Log.e(InformaConstants.TAG, e.toString());
				sendLog();
			} catch (JSONException e) {
				Log.e(InformaConstants.TAG, e.toString());
			}			
		}
		
		private String parseAsTitle(String rawTitle) {
			String[] words = rawTitle.split("_");
			StringBuffer sb = new StringBuffer();
			for(String word : words) {
				sb.append(word.toUpperCase() + " ");
			}
			
			return sb.toString().substring(0, sb.length() - 1);
		}
		
		public void setFrame(int which) throws JSONException {
			for(int f=0; f<frames.length(); f++) {
				JSONObject frame = frames.getJSONObject(f);
				if(frame.getString(frameKey).compareTo(order.getString(which)) == 0)
					currentFrame = frame;
			}
		}
		
		public ArrayList<Callback> getCallbacks() {
			return callbacks;
		}
		
		private String findKey(String content, String key) {
			if(content.indexOf(key) != -1) {
				String keyTail = content.substring(content.indexOf(key + "="));
				String[] pair = keyTail.substring(0, keyTail.indexOf(";")).split("=");
				return pair[1];
			} else {
				return null;
			}
		}
		
		private String[] parseArguments(String args) {
			String[] a = null;
			if(args != null) {
				a = args.split(",");
			}
			return a;
		}
		
		public ArrayList<View> getContent() throws JSONException {
			ArrayList<View> views = new ArrayList<View>();
			String content = currentFrame.getString(frameContent);
			
			for(final String s : content.split("\n")) {
				if(s.contains("{$")) {
					final String type = findKey(s, "type");
					final String callback = findKey(s, "callback");
					final boolean isMandatory = Boolean.parseBoolean(findKey(s, "mandatory"));
					final String attachTo = findKey(s, "attachTo");
					
					if(isMandatory)
						Wizard.this.setMandatory(wizard_next);
					
					if(type.compareTo("button") == 0) {
						InformaButton button = new InformaButton(_c);
						button.setText(findKey(s, "text"));
						
						String[] args = parseArguments(findKey(s, "args"));
						final Callback buttonCall = new Callback(callback, args); 
						
						button.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								try {
									buttonCall.doCallback();
								} catch (IllegalAccessException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (NoSuchMethodException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (InvocationTargetException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								}
							}
							
						});
						views.add(button);
						
					} else if(type.compareTo("spinner") == 0) {
						InformaSpinner spinner = new InformaSpinner(_c);
						
						String[] args = parseArguments(findKey(s, "args"));
						final Callback spinnerCall = new Callback(callback, args);
						
						disableAction(wizard_next);
						disableAction(wizard_back);
						
						new Thread(new Runnable() {

							@Override
							public void run() {
								try {
									spinnerCall.doCallback();
								} catch (IllegalAccessException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (NoSuchMethodException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (InvocationTargetException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								}
								
							}
						}).start();
						
						
						views.add(spinner);
					} else if(type.compareTo("input") == 0) {
						InformaEditText edittext = new InformaEditText(_c);
						
						edittext.addOnLayoutChangeListener(new TextView.OnLayoutChangeListener() {

							@Override
							public void onLayoutChange(View v, int left,
									int top, int right, int bottom,
									int oldLeft, int oldTop, int oldRight,
									int oldBottom) {
								
							}
							
						});
						views.add(edittext);
					} else if(type.compareTo("password") == 0) {
						InformaEditText edittext = new InformaEditText(_c);
						
						edittext.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
						edittext.setTransformationMethod(new PasswordTransformationMethod());
						
						edittext.addTextChangedListener(new TextWatcher() {

							@Override
							public void afterTextChanged(Editable e) {
								String pw = e.toString();
								if(pw.length() == 0) {} 
								else if(isValidatedPassword(pw)){
									enableAction(wizard_next);
									if(callback != null) {
										if(attachTo == null)
											callbacks.add(new Callback(callback, new String[] {pw}));
										
									}
								} else if(!isValidatedPassword(pw)) {
									disableAction(wizard_next);
								}		
							}

							@Override
							public void beforeTextChanged(CharSequence s,
									int start, int count, int after) {}

							@Override
							public void onTextChanged(CharSequence s,
									int start, int before, int count) {}
							
						});
						
						views.add(edittext);
					} else if(type.compareTo("select_one") == 0 || type.compareTo("select_multi") == 0) {
						
						ArrayList<Selections> selections = new ArrayList<Selections>();
						ListView lv = new ListView(_c);
						
						for(String option : findKey(s, "values").split(",")) {
							Log.d(InformaConstants.TAG, "this option: " + option);
							if(Character.toString(option.charAt(0)).compareTo("#") == 0) {
								// populate from callback
								Callback populate = new Callback(option.substring(1), null);
								
								try {
									for(String res : (String[]) populate.doCallback())
										selections.add(new Selections(res, false));
									
								} catch (IllegalAccessException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (NoSuchMethodException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								} catch (InvocationTargetException e) {
									Log.d(InformaConstants.TAG, "wizard error", e);
								}
							} else 
								selections.add(new Selections(option, false));
						}
						
						callbacks.add(new Callback(callback, new Object[] {selections}));
						lv.setAdapter(new SelectionsAdapter(_c, selections, type));
						views.add(lv);
					}
					
				} else {
					InformaTextView tv = new InformaTextView(_c);
					tv.setText(s);
					views.add(tv);
				}
			}
			
			return views;
		}
		
		public String getTitle() throws JSONException {
			return currentFrame.getString(frameTitle);
		}
	}
	
	public class Callback {
		String _func;
		Object[] _args;
		
		public Callback(String func, Object[] args) {
			_func = func;
			_args = args;
		}
		
		public Object doCallback() throws  IllegalAccessException, NoSuchMethodException, InvocationTargetException {
			Method method;
			if(_args != null) {
				Class<?>[] paramTypes = new Class[_args.length];
				
				for(int p=0; p<paramTypes.length; p++)
					paramTypes[p] = _args[p].getClass();
				
				method = Wizard.this.getClass().getDeclaredMethod(_func, paramTypes);
			} else
				method = Wizard.this.getClass().getDeclaredMethod(_func, null);
			
			method.setAccessible(true);
			return method.invoke(Wizard.this, _args);
		}
	}
	
	private boolean isValidatedPassword (String password)
	{
		return password.length() >= 6;
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		if(db != null) {
			db.close();
			dh.close();
		}
			
	}
	
	private void passThrough() {
		Intent i = new Intent(this, Wizard.class);
		i.putExtra("current", current + 1);
		if(!orderFile.equals("order.wizard"))
			i.putExtra("wizardRoot", orderFile);
		
		startActivity(i);
		finish();
	}
	
	@Override
	public void onClick(View v) {
		if(v == wizard_back) {
			if(current > 0) {
				Intent i = new Intent(this,Wizard.class);
				i.putExtra("current", current - backOffset);
				if(!orderFile.equals("order.wizard"))
					i.putExtra("wizardRoot", orderFile);
					
				startActivity(i);
				finish();
			}
		} else if(v == wizard_next) {
			if(current < wizardForm.frames.length() - 1) {
				// do the callbacks...
				for(Callback c: wizardForm.getCallbacks()) {
					try {
						c.doCallback();
					} catch (IllegalAccessException e) {
						Log.d(InformaConstants.TAG, e.toString());
					} catch (NoSuchMethodException e) {
						Log.d(InformaConstants.TAG, e.toString());
					} catch (InvocationTargetException e) {
						Log.d(InformaConstants.TAG, e.toString());
					}
				}
				
				Intent i = new Intent(this,Wizard.class);
				i.putExtra("current", current + nextOffset);
				if(!orderFile.equals("order.wizard"))
					i.putExtra("wizardRoot", orderFile);
				
				startActivity(i);
				finish();
			}
		} else if(v == wizard_done) {
			_ed.putBoolean(InformaConstants.Keys.Settings.SETTINGS_VIEWED, true).commit();
			Intent i = new Intent(this, InformaApp.class);
			startActivity(i);
			finish();
		}
		
	}
	
	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if(result == SherlockActivity.RESULT_OK) {
			if(request != InformaConstants.FROM_REGISTRATION_IMAGE)
				apg.onActivityResult(this, request, result, data);
			
			switch(request) {
			case Apg.SELECT_SECRET_KEY:
				setUserPGP();
				break;
			case Apg.SELECT_PUBLIC_KEYS:
				setTrustedDestinations();
				break;
			case InformaConstants.FROM_REGISTRATION_IMAGE:
				try {
					setDeviceId((Bitmap) data.getExtras().get("data"));
				} catch (IOException e) {
					Log.d(InformaConstants.TAG, e.toString());
				}
				break;
			}
		}
			
	}
	
	@Override
	public void onBackPressed() {}

}
