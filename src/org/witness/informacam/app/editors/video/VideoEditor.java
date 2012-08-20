/*
 * 
 * To Do:
 * 
 * Handles on SeekBar - Not quite right
 * Editing region, not quite right
 * RegionBarArea will be graphical display of region tracks, no editing, just selecting
 * 
 */

package org.witness.informacam.app.editors.video;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.witness.informacam.R;
import org.witness.informacam.app.AddressBookActivity;
import org.witness.informacam.app.AnnotationActivity;
import org.witness.informacam.app.editors.detect.GoogleFaceDetection;
import org.witness.informacam.app.editors.filters.PixelizeObscure;
import org.witness.informacam.app.editors.video.InOutPlayheadSeekBar.InOutPlayheadSeekBarChangeListener;
import org.witness.informacam.app.editors.video.ShellUtils.ShellCallback;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.InformaService.InformaServiceListener;
import org.witness.informacam.informa.LogPack;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Informa.Keys.Data.VideoRegion;
import org.witness.informacam.utils.Constants.Media;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

public class VideoEditor extends Activity implements
						OnCompletionListener, OnErrorListener, OnInfoListener,
						OnBufferingUpdateListener, OnPreparedListener, OnSeekCompleteListener,
						OnVideoSizeChangedListener, SurfaceHolder.Callback,
						MediaController.MediaPlayerControl, OnTouchListener, OnClickListener,
						InOutPlayheadSeekBarChangeListener, OnActionItemClickListener, InformaServiceListener {

	public static final String LOGTAG = App.LOG;

	public static final int SHARE = 1;

    private final static float REGION_CORNER_SIZE = 26;
    
	ProgressDialog progressDialog;
	int completeActionFlag = -1;
	
	Uri originalVideoUri;

	File fileExternDir;
	File redactSettingsFile;
	File saveFile;
	File recordingFile;
	Random rand = new Random();
	
	int fingerCount = 0;
	int regionCornerMode = 0;
	float downX = -1;
	float downY = -1;
	
	Display currentDisplay;

	VideoView videoView;
	SurfaceHolder surfaceHolder;
	MediaPlayer mediaPlayer;	
	
	MediaMetadataRetriever retriever = new MediaMetadataRetriever();
	PixelizeObscure po = new PixelizeObscure ();
	 
	ImageView regionsView;
	Bitmap obscuredBmp;
    Canvas obscuredCanvas;
	Paint obscuredPaint;
	Paint selectedPaint;
	
	Bitmap bitmapPixel;
	
	InOutPlayheadSeekBar progressBar;
	
	int videoWidth = 0;
	int videoHeight = 0;
	
	ImageButton playPauseButton;
	
	private ArrayList<RegionTrail> obscureTrails = new ArrayList<RegionTrail>();
	private RegionTrail activeRegionTrail;
	private ObscureRegion activeRegion;
	
	boolean mAutoDetectEnabled = false;
	
	VideoConstructor ffmpeg;
	
	int timeNudgeOffset = 2;
	
	float vRatio;
	
	int outFrameRate = -1;
	int outBitRate = -1;
	String outFormat = null;
	String outAcodec = null;
	String outVcodec = null;
	int outVWidth = -1;
	int outVHeight = -1;
	
	private final static String DEFAULT_OUT_FPS = "15";
	private final static String DEFAULT_OUT_RATE = "300";
	private final static String DEFAULT_OUT_FORMAT = "3gp";
	private final static String DEFAULT_OUT_VCODEC = "libx264";
	private final static String DEFAULT_OUT_ACODEC = "copy";
	private final static String DEFAULT_OUT_WIDTH = "480";
	private final static String DEFAULT_OUT_HEIGHT = "320";
	

	private Handler mHandler = new Handler()
	{
		 public void handleMessage(Message msg) {
	            switch (msg.what) {
		            case 0: //status
	
	                    progressDialog.dismiss();
	                    
	                 break;
	                case 1: //status

	                       progressDialog.setMessage(msg.getData().getString("status"));
	                       progressDialog.setProgress(msg.getData().getInt("progress"));
	                    break;
	               
	                case 2: //cancelled
	                	mCancelled = true;
	                	mAutoDetectEnabled = false;
	                		killVideoProcessor();
	                	
	                	break;
	                	
	                case 3: //completed
	                	progressDialog.dismiss();
	                	InformaService.getInstance().packageInforma();
	                	
	                	break;
	                
	                case 5:	                	
	                	updateRegionDisplay(mediaPlayer.getCurrentPosition());	        			
	                	break;
	                default:
	                    super.handleMessage(msg);
	            }
	        }
	};
	
	private boolean mCancelled = false;
	
	QuickAction popupMenu;
	ActionItem[] popupMenuItems;
	
	private int mDuration;
	
	// TODO: my additions to global vars
	private SharedPreferences sp;
	private boolean mediaPlayerIsPrepared = true;
	private boolean shouldStartPlaying = false;
	private int currentCue = 1;
	private boolean metadataScraped = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		long timestampNow = System.currentTimeMillis();
		LogPack logPack = new LogPack(CaptureEvent.Keys.TYPE, CaptureEvent.MEDIA_OPENED);
		
		InformaService.getInstance().onUpdate(timestampNow, logPack);

		setContentView(R.layout.videoeditor);

		if (getIntent() != null)
		{
			// Passed in from ObscuraApp
			originalVideoUri = getIntent().getData();
			
			if (originalVideoUri == null)
			{
				if (getIntent().hasExtra(Intent.EXTRA_STREAM)) 
				{
					originalVideoUri = (Uri) getIntent().getExtras().get(Intent.EXTRA_STREAM);
				}
			}
			
			if (originalVideoUri == null)
			{
				if (savedInstanceState.getString("path")!=null)
				{
					originalVideoUri = Uri.fromFile(new File(savedInstanceState.getString("path")));
					recordingFile = new File (savedInstanceState.getString("path"));
					
					
				}
				else
				{
					finish();
					return;
				}
			}
			else
			{
			
				recordingFile = new File(pullPathFromUri(originalVideoUri));
			}
			
			Log.d(App.LOG, "recording file: " + recordingFile.getAbsolutePath());
			Log.d(App.LOG, "original uri: " + originalVideoUri.toString());
			
			InformaService.getInstance().onInformaInit(VideoEditor.this, originalVideoUri);
		}
		
		fileExternDir = new File(Environment.getExternalStorageDirectory(),getString(R.string.app_name));
		if (!fileExternDir.exists())
			fileExternDir.mkdirs();

		regionsView = (ImageView) this.findViewById(R.id.VideoEditorImageView);
		regionsView.setOnTouchListener(this);
		
	
		mAutoDetectEnabled = true; //first time do autodetect
		
		setPrefs();
		
	    retriever.setDataSource(recordingFile.getAbsolutePath());

		bitmapPixel = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_context_pixelate);
		

		showAutoDetectDialog();
		
		// TODO: my additions to onCreate()
		sp = PreferenceManager.getDefaultSharedPreferences(this);
				
		/* TODO
		sendBroadcast(new Intent()
			.setAction(InformaConstants.Keys.Service.SET_CURRENT)
			.putExtra(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, System.currentTimeMillis())
			.putExtra(InformaConstants.Keys.CaptureEvent.TYPE, InformaConstants.CaptureEvents.MEDIA_CAPTURED));
		
		br = new ArrayList<Broadcaster>();
		br.add(new Broadcaster(new IntentFilter(Keys.Service.FINISH_ACTIVITY)));
		br.add(new Broadcaster(new IntentFilter(Keys.Service.IMAGES_GENERATED)));
		*/
		
	}
	
	private void loadMedia ()
	{

		mediaPlayer = new MediaPlayer();
		mediaPlayer.setOnCompletionListener(this);
		mediaPlayer.setOnErrorListener(this);
		mediaPlayer.setOnInfoListener(this);
		mediaPlayer.setOnPreparedListener(this);
		mediaPlayer.setOnSeekCompleteListener(this);
		mediaPlayer.setOnVideoSizeChangedListener(this);
		mediaPlayer.setOnBufferingUpdateListener(this);

		mediaPlayer.setLooping(false);
		mediaPlayer.setScreenOnWhilePlaying(true);
		
		Log.d(LOGTAG, "attempting to load: " + originalVideoUri.toString());
		
		try {
			mediaPlayer.setDataSource(originalVideoUri.toString());
			Log.d(LOGTAG, "setData done.");
		} catch (IllegalArgumentException e) {
			Log.v(LOGTAG, "setDataSource error: " + e.getMessage());
			finish();
		} catch (IllegalStateException e) {
			Log.v(LOGTAG, "setDataSource error: " + e.getMessage());
			finish();
		} catch (IOException e) {
			Log.v(LOGTAG, "setDataSource error: " + e.getMessage());
			finish();
		}
		
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
	  
	  savedInstanceState.putString("path",recordingFile.getAbsolutePath());
	  
	  super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		
		Log.v(LOGTAG, "surfaceCreated Called");
		if (mediaPlayer != null)
		{

			mediaPlayer.setDisplay(holder);
			try {
				mediaPlayer.prepare();
				mDuration = mediaPlayer.getDuration();
				
				if(!metadataScraped) {
					LogPack metadata = InformaService.getInstance().getMetadata();
					if(metadata != null) {
						// TODO: init values needed from this...
						metadataScraped = true;
					}
						
				}
				
				progressBar.setMax(mDuration);
	
			} catch (Exception e) {
				Log.v(LOGTAG, "IllegalStateException " + e.getMessage());
				finish();
			}
			 
			
			 updateVideoLayout ();
			 mediaPlayer.seekTo(currentCue);
			
		}
	
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}

	@Override
	public void onCompletion(MediaPlayer mp) {
		Log.i(LOGTAG, "onCompletion Called");
		
	
		playPauseButton.setImageDrawable(this.getResources().getDrawable(android.R.drawable.ic_media_play));
	}
	
	@Override
	public boolean onError(MediaPlayer mp, int whatError, int extra) {
		Log.e(LOGTAG, "onError Called");
		if (whatError == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
			Log.e(LOGTAG, "Media Error, Server Died " + extra);
		} else if (whatError == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
			Log.e(LOGTAG, "Media Error, Error Unknown " + extra);
		}
		return false;
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int whatInfo, int extra) {
		if (whatInfo == MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING) {
			Log.v(LOGTAG, "Media Info, Media Info Bad Interleaving " + extra);
		} else if (whatInfo == MediaPlayer.MEDIA_INFO_NOT_SEEKABLE) {
			Log.v(LOGTAG, "Media Info, Media Info Not Seekable " + extra);
		} else if (whatInfo == MediaPlayer.MEDIA_INFO_UNKNOWN) {
			Log.v(LOGTAG, "Media Info, Media Info Unknown " + extra);
		} else if (whatInfo == MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING) {
			Log.v(LOGTAG, "MediaInfo, Media Info Video Track Lagging " + extra);
		} else if (whatInfo == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) { 
			Log.v(LOGTAG, "MediaInfo, Media Info Metadata Update " + extra); 
		}
		
		return false;
	}

	public void onPrepared(MediaPlayer mp) {
		Log.v(LOGTAG, "onPrepared Called");

		updateVideoLayout ();
		mediaPlayer.seekTo(currentCue);
	}
	
	private void showAutoDetectDialog ()
	{
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        switch (which){
		        case DialogInterface.BUTTON_POSITIVE:
		        	beginAutoDetect();
		            break;

		        case DialogInterface.BUTTON_NEGATIVE:
		            //start();
		            seekTo(1);
		            break;
		        }
		    }
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Would you like to detect faces in this video?").setPositiveButton("Yes", dialogClickListener)
		    .setNegativeButton("No", dialogClickListener).show();
		
	}
	
	private void beginAutoDetect ()
	{
		mAutoDetectEnabled = true;
		
		progressDialog = new ProgressDialog(this);
		progressDialog = ProgressDialog.show(this, "", "Detecting faces...", true, true);
    	progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(true);
        Message msg = mHandler.obtainMessage(2);
        msg.getData().putString("status","cancelled");
        progressDialog.setCancelMessage(msg);
   	
         progressDialog.show();
		
         new Thread (doAutoDetect).start();
		
	}

	public void onSeekComplete(MediaPlayer mp) {
		
		if (!mediaPlayer.isPlaying()) {			
			mediaPlayer.start();
			mediaPlayer.pause();
			playPauseButton.setImageDrawable(this.getResources().getDrawable(android.R.drawable.ic_media_play));
		}
		
		//TODO: my additions to onSeekComplete
		currentCue = mediaPlayer.getCurrentPosition();
	}

	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		Log.v(LOGTAG, "onVideoSizeChanged Called");

		videoWidth = mp.getVideoWidth();
		videoHeight = mp.getVideoHeight();

		updateVideoLayout ();
		
	}
	
    /*
     * Handling screen configuration changes ourselves, we don't want the activity to restart on rotation
     */
    @Override
    public void onConfigurationChanged(Configuration conf) 
    {
        super.onConfigurationChanged(conf);
    
      
    }   
	
	private boolean updateVideoLayout ()
	{
		//Get the dimensions of the video
	    int videoWidth = mediaPlayer.getVideoWidth();
	    int videoHeight = mediaPlayer.getVideoHeight();
	  //  Log.v(LOGTAG, "video size: " + videoWidth + "x" + videoHeight);
	   
	    if (videoWidth > 0 && videoHeight > 0)
	    {
		    //Get the width of the screen
		    int screenWidth = getWindowManager().getDefaultDisplay().getWidth();
	
		    //Get the SurfaceView layout parameters
		    android.view.ViewGroup.LayoutParams lp = videoView.getLayoutParams();
	
		    //Set the width of the SurfaceView to the width of the screen
		    lp.width = screenWidth;
	
		    //Set the height of the SurfaceView to match the aspect ratio of the video 
		    //be sure to cast these as floats otherwise the calculation will likely be 0
		   
		    int videoScaledHeight = (int) (((float)videoHeight) / ((float)videoWidth) * (float)screenWidth);
	
		    lp.height = videoScaledHeight;
		   
		    //Commit the layout parameters
		    videoView.setLayoutParams(lp);    
		    regionsView.setLayoutParams(lp);    
		    
		   // Log.v(LOGTAG, "view size: " + screenWidth + "x" + videoScaledHeight);
		    
			vRatio = ((float)screenWidth) / ((float)videoWidth);
			
		//	Log.v(LOGTAG, "video/screen ration: " + vRatio);

			return true;
	    }
	    else
	    	return false;
	}

	public void onBufferingUpdate(MediaPlayer mp, int bufferedPercent) {
		Log.v(LOGTAG, "MediaPlayer Buffering: " + bufferedPercent + "%");
	}

	public boolean canPause() {
		return true;
	}

	public boolean canSeekBackward() {
		return true;
	}

	public boolean canSeekForward() {
		return true;
	}

	@Override
	public int getBufferPercentage() {
		return 0;
	}

	@Override
	public int getCurrentPosition() {
		return mediaPlayer.getCurrentPosition();
	}

	@Override
	public int getDuration() {
		Log.v(LOGTAG,"Calling our getDuration method");
		return mediaPlayer.getDuration();
	}

	@Override
	public boolean isPlaying() {
		Log.v(LOGTAG,"Calling our isPlaying method");
		return mediaPlayer.isPlaying();
	}

	@Override
	public void pause() {
		Log.v(LOGTAG,"Calling our pause method");
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
			playPauseButton.setImageDrawable(this.getResources().getDrawable(android.R.drawable.ic_media_play));
		}
	}

	@Override
	public void seekTo(int pos) {
		mediaPlayer.seekTo(pos);
		
	}

	@Override
	public void start() {
		Log.v(LOGTAG,"Calling our start method");
		mediaPlayer.start();
		
		playPauseButton.setImageDrawable(this.getResources().getDrawable(android.R.drawable.ic_media_pause));
		
		mHandler.post(updatePlayProgress);
		

	}
	
	private Runnable doAutoDetect = new Runnable() {
		   public void run() {
			   
			   try
			   {
				   int timeInc = 250;
				   
				   if (mediaPlayer != null && mAutoDetectEnabled) 
				   {						   
					   mediaPlayer.start();
					   
					   //turn volume off
					   mediaPlayer.setVolume(0f, 0f);
					        
					   for (int f = 0; f < mDuration && mAutoDetectEnabled; f += timeInc)
					   {
						   seekTo(f);
						   
						   progressBar.setProgress(mediaPlayer.getCurrentPosition());
						   
						   //Bitmap bmp = getVideoFrame(rPath,f*1000);
						   Bitmap bmp = retriever.getFrameAtTime(f*1000, MediaMetadataRetriever.OPTION_CLOSEST);
						   
						   if (bmp != null)
							   autoDetectFrame(bmp,f, App.VideoEditor.FACE_TIME_BUFFER, mDuration);
						   
					   }
					   
					   //turn volume on
					   mediaPlayer.setVolume(1f, 1f);
					   
					   mediaPlayer.seekTo(0);
					   progressBar.setProgress(mediaPlayer.getCurrentPosition());
					   mediaPlayer.pause();
					   
					   
				   }   
			   }
			   catch (Exception e)
			   { 
				   Log.e(LOGTAG,"autodetect errored out", e);
			   }
			   
			   finally
			   {
				   if (mAutoDetectEnabled)
					{
						mAutoDetectEnabled = false;
						Message msg = mHandler.obtainMessage(0);
						mHandler.sendMessage(msg);
					}
			   }
			   
		   }
		};
		
	private Runnable updatePlayProgress = new Runnable() {
	   public void run() {
		   
		   try
		   {
			   if (mediaPlayer != null && mediaPlayer.isPlaying())
			   {
				   int curr = mediaPlayer.getCurrentPosition();
				   progressBar.setProgress(curr);
				   updateRegionDisplay(curr);
				   mHandler.post(this);				   
			   }
			   
		   }
		   catch (Exception e)
		   {
			   Log.e(LOGTAG,"autoplay errored out", e);
		   }
	   }
	};		
	
	private void updateRegionDisplay(int currentTime) {

		
		validateRegionView();
		clearRects();
		
		for (RegionTrail regionTrail:obscureTrails)
		{
		;
			ObscureRegion region;
			
			if ((region = regionTrail.getCurrentRegion(currentTime,regionTrail.isDoTweening()))!=null)
			{
				int currentColor = Color.WHITE;
				boolean selected = regionTrail == activeRegionTrail;
				
				if (selected)
				{
					currentColor = Color.GREEN;
					displayRegionTrail(regionTrail, selected, currentColor, currentTime);
				}
				
				displayRegion(region, selected, currentColor, regionTrail.getObscureMode());
			}
		}
		
		
		regionsView.invalidate();
		//seekBar.invalidate();
	}
	
	private void validateRegionView() {
		if (obscuredBmp == null && regionsView.getWidth() > 0 && regionsView.getHeight() > 0) {
		//	Log.v(LOGTAG,"obscuredBmp is null, creating it now");
			obscuredBmp = Bitmap.createBitmap(regionsView.getWidth(), regionsView.getHeight(), Bitmap.Config.ARGB_8888);
			obscuredCanvas = new Canvas(obscuredBmp); 
		    regionsView.setImageBitmap(obscuredBmp);			
		}
	}

	private void displayRegionTrail(RegionTrail trail, boolean selected, int color, int currentTime) {

		
		RectF lastRect = null;
	    
		obscuredPaint.setStyle(Style.FILL);
		obscuredPaint.setColor(color);
		obscuredPaint.setStrokeWidth(10f);
	    
		for (Integer regionKey:trail.getRegionKeys())
		{

			ObscureRegion region = trail.getRegion(regionKey);
			
			if (region.timeStamp < currentTime)
			{
				int alpha = 150;//Math.min(255,Math.max(0, ((currentTime - region.timeStamp)/1000)));
				
				RectF nRect = new RectF();
				nRect.set(region.getBounds());    	
				nRect.left *= vRatio;
				nRect.right *= vRatio;
				nRect.top *= vRatio;
				nRect.bottom *= vRatio;
	
				obscuredPaint.setAlpha(alpha);
	
				if (lastRect != null)
				{
					obscuredCanvas.drawLine(lastRect.centerX(), lastRect.centerY(), nRect.centerX(), nRect.centerY(), obscuredPaint);
				}
				
				lastRect = nRect;
			}
		}
		
		
	}
	
	private void displayRegion(ObscureRegion region, boolean selected, int color, String mode) {

		RectF paintingRect = new RectF();
    	paintingRect.set(region.getBounds());    	
    	paintingRect.left *= vRatio;
    	paintingRect.right *= vRatio;
    	paintingRect.top *= vRatio;
    	paintingRect.bottom *= vRatio;
    	
    	/*
		*/
    	
		
	
		//obscuredPaint.setTextSize(30);
		//obscuredPaint.setFakeBoldText(false);
		
    	if (mode.equals(RegionTrail.OBSCURE_MODE_PIXELATE))
    	{
    		obscuredPaint.setAlpha(150);
    		
    		 obscuredCanvas.drawBitmap(bitmapPixel, null, paintingRect, obscuredPaint);
     		
    		
    	}
    	else if (mode.equals(RegionTrail.OBSCURE_MODE_REDACT))
    	{
    	
        	obscuredPaint.setStyle(Style.FILL);
    		obscuredPaint.setColor(Color.BLACK);
    		obscuredPaint.setAlpha(150);
    		
    		obscuredCanvas.drawRect(paintingRect, obscuredPaint);  
    	}
    	
    	obscuredPaint.setStyle(Style.STROKE);	
    	obscuredPaint.setStrokeWidth(10f);
		obscuredPaint.setColor(color);
    	
		obscuredCanvas.drawRect(paintingRect, obscuredPaint);
    	
		
    
	}
	
	private void clearRects() {
		Paint clearPaint = new Paint();
		clearPaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
		
		if (obscuredCanvas != null)
			obscuredCanvas.drawPaint(clearPaint);
	}

	public ObscureRegion findRegion(float x, float y, int currentTime) 
	{
		ObscureRegion region = null;
		
		if (activeRegion != null && activeRegion.getRectF().contains(x, y))
			return activeRegion;
		
		for (RegionTrail regionTrail:obscureTrails)
		{
			if (currentTime != -1)
			{
				region = regionTrail.getCurrentRegion(currentTime, false);
				if (region != null && region.getRectF().contains(x,y))
				{
					return region;
				}
			}
			else
			{
				for (Integer regionKey : regionTrail.getRegionKeys())
				{
					region = regionTrail.getRegion(regionKey);
					
					if (region.getRectF().contains(x,y))
					{
						return region;
					}
				}
			}
		}
		
		return null;
	}
		
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		
		boolean handled = false;

		if (v == progressBar) {
			
			// It's the progress bar/scrubber
			if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
			   start();
		    } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
		       pause();
		    	
		    }
			
			/*
			Log.v(LOGTAG,"" + event.getX() + " " + event.getX()/progressBar.getWidth());
			Log.v(LOGTAG,"Seeking To: " + (int)(mDuration*(float)(event.getX()/progressBar.getWidth())));
			Log.v(LOGTAG,"MediaPlayer Position: " + mediaPlayer.getCurrentPosition());
			*/
			//int newTime = (int)(mediaPlayer.getDuration()*(float)(event.getX()/progressBar.getWidth()));
			
			mediaPlayer.seekTo(progressBar.getProgress());
			updateRegionDisplay(mediaPlayer.getCurrentPosition());
			// Attempt to get the player to update it's view - NOT WORKING
			
			handled = false; // The progress bar doesn't get it if we have true here
		}
		else
		{
			float x = event.getX() / vRatio;
			float y = event.getY() / vRatio;

			fingerCount = event.getPointerCount();
			
			switch (event.getAction() & MotionEvent.ACTION_MASK) {
			
				case MotionEvent.ACTION_DOWN:
					
					downX = x;
					downY = y;
					
					ObscureRegion newActiveRegion = findRegion(x,y,mediaPlayer.getCurrentPosition());
					
					if (newActiveRegion != null)
					{
						activeRegionTrail = newActiveRegion.getRegionTrail();
						
						updateProgressBar(activeRegionTrail);
						
						if (fingerCount == 1 && (!mediaPlayer.isPlaying()))
							inflatePopup(false, (int)x, (int)y);
						
						activeRegion = newActiveRegion;
					}
					else 
					{

						activeRegion = makeNewRegion(fingerCount, x, y, event, App.VideoEditor.HUMAN_OFFSET_BUFFER);
						
						if (activeRegion != null)
						{
							activeRegionTrail = findIntersectTrail(activeRegion,mediaPlayer.getCurrentPosition());
	
							if (activeRegionTrail == null)
							{
								activeRegionTrail = new RegionTrail(0,mDuration,this);
								obscureTrails.add(activeRegionTrail);
							}
							
							activeRegionTrail.addRegion(activeRegion);						
							
							updateProgressBar(activeRegionTrail);
						}
					}
					

					handled = true;

					break;
					
				case MotionEvent.ACTION_UP:
					try {
						InformaService.getInstance().onVideoRegionChanged(activeRegionTrail);
					} catch(NullPointerException e) {
						Log.e(App.LOG, "activeRegion was null");
						e.printStackTrace();
					}
					
					break;
										
				case MotionEvent.ACTION_MOVE:
					// Calculate distance moved

					if (Math.abs(x-downX)> App.VideoEditor.MIN_MOVE
						||Math.abs(y-downY)> App.VideoEditor.MIN_MOVE)
						{
						activeRegion = makeNewRegion (fingerCount, x, y, event, App.VideoEditor.HUMAN_OFFSET_BUFFER);
						
						if (activeRegion != null)
							activeRegionTrail.addRegion(activeRegion);
						
						}
					handled = true;
					
					
					break;
				
			}
		}
		
		updateRegionDisplay(mediaPlayer.getCurrentPosition());
		
		return handled; // indicate event was handled	
	}
	
	private ObscureRegion makeNewRegion (int fingerCount, float x, float y, MotionEvent event, int timeOffset)
	{
		ObscureRegion result = null;
		
		int regionTime = mediaPlayer.getCurrentPosition()-timeOffset;
		
		if (fingerCount > 1 && event != null)
		{
        	float[] points = {event.getX(0)/vRatio, event.getY(0)/vRatio, event.getX(1)/vRatio, event.getY(1)/vRatio}; 
        	
        	float startX = Math.min(points[0], points[2]);
        	float endX = Math.max(points[0], points[2]);
        	float startY = Math.min(points[1], points[3]);
        	float endY = Math.max(points[1], points[3]);
        	
        	result = new ObscureRegion(regionTime,startX,startY,endX,endY);
	
		}
		else
		{
			result = new ObscureRegion(mediaPlayer.getCurrentPosition(),x,y);
			
			if (activeRegion != null && RectF.intersects(activeRegion.getBounds(), result.getBounds()))
			{
					//newActiveRegion.ex = newActiveRegion.sx + (activeRegion.ex-activeRegion.sx);
					//newActiveRegion.ey = newActiveRegion.sy + (activeRegion.ey-activeRegion.sy);
					float arWidth = activeRegion.ex-activeRegion.sx;
					float arHeight = activeRegion.ey-activeRegion.sy;
					
					float sx = x - arWidth/2;
					float ex = sx + arWidth;
					
					float sy = y - arHeight/2;
					float ey = sy + arHeight;

					result = new ObscureRegion(regionTime,sx,sy,ex,ey);
					
			}
				
			
		}
		
		return result;

	}
	
	private void updateProgressBar (RegionTrail rTrail)
	{
		progressBar.setThumbsActive((int)((double)rTrail.getStartTime()/(double)mDuration*100), (int)((double)rTrail.getEndTime()/(double)mDuration*100));

	}
	
	@Override
	public void onClick(View v) {
		if (v == playPauseButton) {
			if (mediaPlayer.isPlaying()) {
				mediaPlayer.pause();
				playPauseButton.setImageDrawable(this.getResources().getDrawable(android.R.drawable.ic_media_play));
				mAutoDetectEnabled = false;
			} else {
				start();
				

			}
		}
	}	

	public String pullPathFromUri(Uri originalUri) {
    	String originalVideoFilePath = null;
    	String[] columnsToSelect = { MediaStore.Video.Media.DATA };
    	Cursor videoCursor = getContentResolver().query(originalUri, columnsToSelect, null, null, null );
    	if ( videoCursor != null && videoCursor.getCount() == 1 ) {
	        videoCursor.moveToFirst();
	        originalVideoFilePath = videoCursor.getString(videoCursor.getColumnIndex(MediaStore.Images.Media.DATA));
    	}

    	return originalVideoFilePath;
    }
	
	private void createCleanSavePath(String format) {
		
		try {
			saveFile = File.createTempFile("output", '.' + format, fileExternDir);
			redactSettingsFile = new File(fileExternDir,saveFile.getName()+".txt");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.video_editor_menu, menu);

        return true;
	}
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

    	switch (item.getItemId()) {
    	
    		case R.id.menu_new_region:
    			
    			//beginAutoDetect();
    			ObscureRegion region = makeNewRegion(mediaPlayer.getCurrentPosition(),(float)videoWidth/2,(float)videoHeight/2,null,0);
    			activeRegionTrail = new RegionTrail(0,mDuration,this);
				obscureTrails.add(activeRegionTrail);
				activeRegionTrail.addRegion(region);
				updateRegionDisplay(mediaPlayer.getCurrentPosition());
				
    			return true;
    			
        	case R.id.menu_save:
        		//TODO: my additions to menu_save (saving happens here)
        		mediaPlayerIsPrepared = true;
        		
        		Intent keyChooser = new Intent(this, AddressBookActivity.class)
        			.putExtra(App.VideoEditor.Keys.CHOOSE_TRUSTED_DESTINATION, true);
        		
				startActivityForResult(keyChooser, App.VideoEditor.FROM_DESTINATION_CHOOSER);
        		
        		return true;   
        		
        	case R.id.menu_prefs:
        		// TODO: my additions to menu_prefs
        		mediaPlayerIsPrepared = true;
        		showPrefs();
        		
        		return true;  
        	
        	case R.id.menu_clear_regions:
        		for(RegionTrail rt : obscureTrails)
        			InformaService.getInstance().onVideoRegionDeleted(rt);
        		
        		obscureTrails.clear();
        		
        		updateRegionDisplay(mediaPlayer.getCurrentPosition());
        		
        		return true;
        	
        	case R.id.menu_preview:
        		mediaPlayerIsPrepared = false;
        		playVideo();
        		
        		return true;
        		
    		default:
    			return false;
    	}
    }
    
    private void processVideo() {
    	
    	createCleanSavePath(outFormat);
		
    	mCancelled = false;
    	
    	mediaPlayer.pause();
    	//mediaPlayer.release();
    	
    	progressDialog = new ProgressDialog(this);
    	progressDialog.setMessage("Processing. Please wait...");
    	progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	progressDialog.setMax(100);
        progressDialog.setCancelable(true);
       
    	 Message msg = mHandler.obtainMessage(2);
         msg.getData().putString("status","cancelled");
         progressDialog.setCancelMessage(msg);
    	
         progressDialog.show();
     	
		// Convert to video
		Thread thread = new Thread (runProcessVideo);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
    }
    
	Runnable runProcessVideo = new Runnable () {
		
		public void run ()
		{

			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
			wl.acquire();
	        
			try
			{
				if (ffmpeg == null)
					ffmpeg = new VideoConstructor(VideoEditor.this.getBaseContext());
	
					
				ShellUtils.ShellCallback sc = new ShellUtils.ShellCallback ()
				{
					int total = 0;
					int current = 0;
					
					@Override
					public void shellOut(char[] shellout) {
						
						String line = new String(shellout);
						
						//Log.d(LOGTAG, line);
						
						//progressDialog.setMessage(new String(msg));
						//Duration: 00:00:00.99,
						//time=00:00:00.00
						
						int idx1;
						String newStatus = null;
						int progress = 0;
						
						if ((idx1 = line.indexOf("Duration:"))!=-1)
						{
							int idx2 = line.indexOf(",", idx1);
							String time = line.substring(idx1+10,idx2);
							
							int hour = Integer.parseInt(time.substring(0,2));
							int min = Integer.parseInt(time.substring(3,5));
							int sec = Integer.parseInt(time.substring(6,8));
							
							total = (hour * 60 * 60) + (min * 60) + sec;
							
							newStatus = line;
							progress = 0;
						}
						else if ((idx1 = line.indexOf("time="))!=-1)
						{
							int idx2 = line.indexOf(" ", idx1);
							String time = line.substring(idx1+5,idx2);
							newStatus = line;
							
							int hour = Integer.parseInt(time.substring(0,2));
							int min = Integer.parseInt(time.substring(3,5));
							int sec = Integer.parseInt(time.substring(6,8));
							
							current = (hour * 60 * 60) + (min * 60) + sec;
							
							progress = (int)( ((float)current) / ((float)total) *100f );
						}
						
						if (newStatus != null)
						{
						 Message msg = mHandler.obtainMessage(1);
				         msg.getData().putInt("progress", progress);
				         msg.getData().putString("status", newStatus);
				         
				         mHandler.sendMessage(msg);
						}
					}
					
				};
				
				int processVWidth = videoWidth;
				int processVHeight = videoHeight;
				
				if (outVWidth != -1)
					processVWidth = outVWidth;
				
				if (outVHeight != -1)
					processVHeight = outVHeight;
				
				// Could make some high/low quality presets	
				ffmpeg.processVideo(redactSettingsFile, obscureTrails, recordingFile, saveFile, outFormat, 
						mDuration, videoWidth, videoHeight, processVWidth, processVHeight, outFrameRate, outBitRate, outVcodec, outAcodec, sc);
			}
			catch (Exception e)
			{
				Log.e(LOGTAG,"error with ffmpeg",e);
			}
			
			wl.release();
		     
			if (!mCancelled)
			{
				addVideoToGallery(saveFile);
				
				Message msg = mHandler.obtainMessage(completeActionFlag);
				msg.getData().putString("status","complete");
				mHandler.sendMessage(msg);
			}
	         
		}
		
		
	};
	
	private void addVideoToGallery (File videoToAdd)
	{
	
     // force mediascanner to update file
     		MediaScannerConnection.scanFile(
     				this,
     				new String[] {videoToAdd.getAbsolutePath()},
     				new String[] {Media.Type.MIME_TYPE_MP4},
     				null);

//        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
	}
	
	private void playVideo() {
		
    	Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
    	intent.setDataAndType(Uri.parse(saveFile.getPath()), Media.Type.MIME_TYPE_MP4);    	
   	 	startActivityForResult(intent,0);
   	 	
	}
	
	@Override
	public void inOutValuesChanged(int thumbInValue, int thumbOutValue) {
		/*
		if (activeRegionTrail != null) {
			
			activeRegionTrail.setStartTime(thumbInValue);
			activeRegionTrail.setEndTime(thumbOutValue);
		}*/
	}
	
	boolean isPopupShowing = false;
	
	public void inflatePopup(boolean showDelayed, int x, int y) {
		if (popupMenu == null)
			initPopup();

		popupMenu.show(regionsView, x, y);
		
		isPopupShowing = !isPopupShowing;
	}
	
	private void initPopup ()
	{
		popupMenu = new QuickAction(this);

		
		ActionItem menu = new ActionItem();
		menu.setTitle("Set In Point");
		menu.setActionId(0);
		menu.setIcon(getResources().getDrawable(R.drawable.ic_add));
		popupMenu.addActionItem(menu);

		//popupMenuItems[0].setIcon(getResources().getDrawable(R.drawable.icon));			

		menu = new ActionItem();
		menu.setActionId(1);
		menu.setTitle("Set Out Point");
		menu.setIcon(getResources().getDrawable(R.drawable.ic_add));
		popupMenu.addActionItem(menu);
		
		menu = new ActionItem();
		menu.setActionId(7);		
		menu.setTitle(getString(R.string.menu_region_identify));	
		menu.setIcon(getResources().getDrawable(R.drawable.ic_context_id));
		popupMenu.addActionItem(menu);
		
		menu = new ActionItem();
		menu.setActionId(4);		
		menu.setTitle("Set Redact");	
		menu.setIcon(getResources().getDrawable(R.drawable.ic_context_fill));
		popupMenu.addActionItem(menu);

		menu = new ActionItem();
		menu.setActionId(5);		
		menu.setTitle("Set Pixelate");	
		menu.setIcon(getResources().getDrawable(R.drawable.ic_context_pixelate));
		popupMenu.addActionItem(menu);

		menu = new ActionItem();
		menu.setActionId(3);		
		menu.setTitle("Remove Trail");	
		menu.setIcon(getResources().getDrawable(R.drawable.ic_context_delete));
		popupMenu.addActionItem(menu);

		menu = new ActionItem();
		menu.setActionId(6);		
		menu.setTitle("Do Tween");	
		menu.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_rotate));
		popupMenu.addActionItem(menu);

		menu = new ActionItem();
		menu.setActionId(2);		
		menu.setTitle("Remove Keyframe");	
		menu.setIcon(getResources().getDrawable(R.drawable.ic_context_delete));
		popupMenu.addActionItem(menu);

		
		popupMenu.setOnActionItemClickListener(this);
	}

	@Override
	protected void onPause() {

		super.onPause();
		mediaPlayer.reset();
		
	}

	@Override
	protected void onStop() {
		super.onStop();
		this.mAutoDetectEnabled = false;
	}	
	
	private void killVideoProcessor ()
	{
		int killDelayMs = 300;

		String ffmpegBin = new File(getDir("bin",0),"ffmpeg").getAbsolutePath();

		int procId = -1;
		
		while ((procId = ShellUtils.findProcessId(ffmpegBin)) != -1)
		{
			
			Log.d(LOGTAG, "Found PID=" + procId + " - killing now...");
			
			String[] cmd = { ShellUtils.SHELL_CMD_KILL + ' ' + procId + "" };
			
			try { 
			ShellUtils.doShellCommand(cmd,new ShellCallback ()
			{

				@Override
				public void shellOut(char[] msg) {
					
					
				}
				
			}, false, false);
			Thread.sleep(killDelayMs); }
			catch (Exception e){}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
		super.onResume();

		videoView = (VideoView) this.findViewById(R.id.SurfaceView);
		
		surfaceHolder = videoView.getHolder();

		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


		currentDisplay = getWindowManager().getDefaultDisplay();
		
			
		progressBar = (InOutPlayheadSeekBar) this.findViewById(R.id.InOutPlayheadSeekBar);

		progressBar.setIndeterminate(false);
		progressBar.setSecondaryProgress(0);
		progressBar.setProgress(0);
		progressBar.setInOutPlayheadSeekBarChangeListener(this);
		progressBar.setThumbsInactive();
		progressBar.setOnTouchListener(this);

		playPauseButton = (ImageButton) this.findViewById(R.id.PlayPauseImageButton);
		playPauseButton.setOnClickListener(this);
		
				
		//regionBarArea = (RegionBarArea) this.findViewById(R.id.RegionBarArea);
		//regionBarArea.obscureRegions = obscureRegions;
		
		obscuredPaint = new Paint();   
        obscuredPaint.setColor(Color.WHITE);
	    obscuredPaint.setStyle(Style.STROKE);
	    obscuredPaint.setStrokeWidth(10f);
	    
	    selectedPaint = new Paint();
	    selectedPaint.setColor(Color.GREEN);
	    selectedPaint.setStyle(Style.STROKE);
	    selectedPaint.setStrokeWidth(10f);
	    
		setPrefs();
		loadMedia();
		
		if(!mediaPlayerIsPrepared) {
			Log.d(LOGTAG, "media is NOT prepared!");
			try {
				mediaPlayer.setDisplay(surfaceHolder);
				mediaPlayer.prepare();
				mediaPlayer.seekTo(currentCue);
			} catch (IllegalStateException e) {
				Log.e(LOGTAG, "player prepare: " + e.getMessage());
				finish();
			} catch (IOException e) {
				Log.v(LOGTAG, "player prepare: " + e.getMessage());
				finish();
			}
		}
		
		if(shouldStartPlaying)
			start();
		
	}
	
	private void setPrefs ()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

		
		outFrameRate = Integer.parseInt(prefs.getString("pref_out_fps", DEFAULT_OUT_FPS).trim());
		outBitRate = Integer.parseInt(prefs.getString("pref_out_rate", DEFAULT_OUT_RATE).trim());
		outFormat = prefs.getString("pref_out_format", DEFAULT_OUT_FORMAT).trim();
		outAcodec =  prefs.getString("pref_out_acodec", DEFAULT_OUT_ACODEC).trim();
		outVcodec =  prefs.getString("pref_out_vcodec", DEFAULT_OUT_VCODEC).trim();

		outVWidth =   Integer.parseInt(prefs.getString("pref_out_vwidth", DEFAULT_OUT_WIDTH).trim());
		outVHeight =   Integer.parseInt(prefs.getString("pref_out_vheight", DEFAULT_OUT_HEIGHT).trim());
		
	}
	
	private int autoDetectFrame(Bitmap bmp, int cTime, int cBuffer, int cDuration) 
	{
		
		
		
		RectF[] autodetectedRects = runFaceDetection(bmp);
		for (RectF autodetectedRect : autodetectedRects)
		{

			//float faceBuffer = -1 * (autodetectedRect.right-autodetectedRect.left)/15;			
			//autodetectedRect.inset(faceBuffer, faceBuffer);
			
			ObscureRegion newRegion = new ObscureRegion(cTime,autodetectedRect.left,
					autodetectedRect.top,
					autodetectedRect.right,
					autodetectedRect.bottom);
			
			//if we have an existing/last region
			
			boolean foundTrail = false;
			RegionTrail iTrail = findIntersectTrail(newRegion,cTime);
			
			if (iTrail != null)
			{
				iTrail.addRegion(newRegion);
				activeRegionTrail = iTrail;
				foundTrail = true;
				break;
			}
		
			if (!foundTrail)
			{
				activeRegionTrail = new RegionTrail(cTime,mDuration,this);
				obscureTrails.add(activeRegionTrail);

				activeRegionTrail.addRegion(newRegion);
				
			}
			
			activeRegion = newRegion;
			foundTrail = false;
		}	

		Message msg = mHandler.obtainMessage(5);
		mHandler.sendMessage(msg);
		
		return autodetectedRects.length;
	}
	
	private RegionTrail findIntersectTrail (ObscureRegion region, int currentTime)
	{
		for (RegionTrail trail:obscureTrails)
		{
			if (trail.isWithinTime(currentTime))
			{
				float iLeft=-1,iTop=-1,iRight=-1,iBottom=-1;
				
				//intersects check points
				RectF aRectF = region.getRectF();
				float iBuffer = 15;
				iLeft = aRectF.left - iBuffer;
				iTop = aRectF.top - iBuffer;
				iRight = aRectF.right + iBuffer;
				iBottom = aRectF.bottom + iBuffer;
				
				Iterator<ObscureRegion> itRegions = trail.getRegionsIterator();
				
				while (itRegions.hasNext())
				{
					ObscureRegion testRegion = itRegions.next();
						
					if (testRegion.getRectF().intersects(iLeft,iTop,iRight,iBottom))
					{
						return trail;
					}
				}
			}
		}
		
		return null;
	}
	
	/*
	 * The actual face detection calling method
	 */
	private RectF[] runFaceDetection(Bitmap bmp) {
		RectF[] possibleFaceRects;
		
		try {
			//Bitmap bProc = toGrayscale(bmp);
			GoogleFaceDetection gfd = new GoogleFaceDetection(bmp);
			int numFaces = gfd.findFaces();
	       // Log.d(ObscuraApp.TAG,"Num Faces Found: " + numFaces); 
	        
	        possibleFaceRects = gfd.getFaces();
		} catch(NullPointerException e) {
			possibleFaceRects = null;
		}
		return possibleFaceRects;				
	}
	
	public Bitmap toGrayscale(Bitmap bmpOriginal)
	{        
	    int width, height;
	    height = bmpOriginal.getHeight();
	    width = bmpOriginal.getWidth();    

	    Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
	    Canvas c = new Canvas(bmpGrayscale);
	    Paint paint = new Paint();
	    ColorMatrix cm = new ColorMatrix();
	    cm.setSaturation(0);
	    
	    ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
	    
	    paint.setColorFilter(f);
	 
	    c.drawBitmap(bmpOriginal, 0, 0, paint);
	    
	    
	    
	    return bmpGrayscale;
	}
	
	public static Bitmap createContrast(Bitmap src, double value) {
		// image size
		int width = src.getWidth();
		int height = src.getHeight();
		// create output bitmap
		Bitmap bmOut = Bitmap.createBitmap(width, height, src.getConfig());
		// color information
		int A, R, G, B;
		int pixel;
		// get contrast value
		double contrast = Math.pow((100 + value) / 100, 2);

		// scan through all pixels
		for(int x = 0; x < width; ++x) {
			for(int y = 0; y < height; ++y) {
				// get pixel color
				pixel = src.getPixel(x, y);
				A = Color.alpha(pixel);
				// apply filter contrast for every channel R, G, B
				R = Color.red(pixel);
				R = (int)(((((R / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
				if(R < 0) { R = 0; }
				else if(R > 255) { R = 255; }

				G = Color.red(pixel);
				G = (int)(((((G / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
				if(G < 0) { G = 0; }
				else if(G > 255) { G = 255; }

				B = Color.red(pixel);
				B = (int)(((((B / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
				if(B < 0) { B = 0; }
				else if(B > 255) { B = 255; }

				// set new pixel color to output bitmap
				bmOut.setPixel(x, y, Color.argb(A, R, G, B));
			}
		}

		// return final image
		return bmOut;
	}

	public void showPrefs ()
	{
		Intent intent = new Intent(this, VideoPreferences.class);
		startActivityForResult(intent,0);
		
	}

	@Override
	public void onItemClick(QuickAction source, int pos, int actionId) {
		
		switch (actionId) {
		case 0:
			// set in point
			activeRegionTrail.setStartTime(mediaPlayer.getCurrentPosition());
			updateProgressBar(activeRegionTrail);
			
			
			break;
		case 1:
			// set out point
			activeRegionTrail.setEndTime(mediaPlayer.getCurrentPosition());
			updateProgressBar(activeRegionTrail);
			activeRegion = null;
			activeRegionTrail = null;
			
			
			break;
		case 2:
			// Remove region
			if (activeRegion != null)
			{
				activeRegionTrail.removeRegion(activeRegion);
				activeRegion = null;
			}
			break;
			
		case 3:
			// Remove region
			obscureTrails.remove(activeRegionTrail);
			activeRegionTrail = null;
			activeRegion = null;
			
			break;
		
		case 4:
			activeRegionTrail.setObscureMode(RegionTrail.OBSCURE_MODE_REDACT);
			
			break;

		case 5:
			activeRegionTrail.setObscureMode(RegionTrail.OBSCURE_MODE_PIXELATE);
			break;
			
		case 6:
			activeRegionTrail.setDoTweening(!activeRegionTrail.isDoTweening());
			break;
			
		case 7:
			activeRegionTrail.setObscureMode(RegionTrail.OBSCURE_MODE_IDENTIFY);
			launchTagger(activeRegionTrail);
			break;
			
	}
		
		updateRegionDisplay(mediaPlayer.getCurrentPosition());
		
	}
	
	public void launchTagger(RegionTrail rt) {
		rt.addIdentityTagger();
		
    	Intent informa = new Intent(this, AnnotationActivity.class);
    	informa.putExtra(App.VideoEditor.Keys.PROPERTIES, rt.getPrettyPrintedProperties());
    	informa.putExtra(VideoRegion.INDEX, obscureTrails.indexOf(rt));
    	
	    retriever.setDataSource(recordingFile.getAbsolutePath());
    	
    	Bitmap b = retriever.getFrameAtTime(mediaPlayer.getCurrentPosition(), MediaMetadataRetriever.OPTION_CLOSEST);
    	// TODO: crop to region bounds!
    	
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
		b.compress(Bitmap.CompressFormat.JPEG, 50, baos);
		informa.putExtra(VideoRegion.THUMBNAIL, baos.toByteArray());
    	
    	mediaPlayerIsPrepared = false;
    	startActivityForResult(informa, App.VideoEditor.FROM_ANNOTATION_ACTIVITY);
    }
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if(resultCode == Activity.RESULT_OK) {
			if(requestCode == App.VideoEditor.FROM_ANNOTATION_ACTIVITY) {
				@SuppressWarnings("unchecked")
				HashMap<String, Object> informaReturn = 
					(HashMap<String, Object>) data.getSerializableExtra(VideoRegion.TAGGER_RETURN);    			
    			Properties mProp = obscureTrails.get(data.getIntExtra(VideoRegion.INDEX, 0))
    					.getProperties();
    			
    			// iterate through returned hashmap and place these new properties in it.
    			for(Map.Entry<String, Object> entry : informaReturn.entrySet())
    				mProp.setProperty(entry.getKey(), entry.getValue().toString());
    			
    			obscureTrails.get(data.getIntExtra(VideoRegion.INDEX, 0))
    				.setProperties(mProp);
			} else if(requestCode == App.VideoEditor.FROM_DESTINATION_CHOOSER) {
				completeActionFlag = 3;
        		processVideo();
        		
        		if(data.hasExtra(Informa.Keys.Intent.ENCRYPT_LIST))
        			InformaService.getInstance().setEncryptionList(data.getLongArrayExtra(Informa.Keys.Intent.ENCRYPT_LIST));
			}
		}
	}

	@Override
	public void onInformaPackageGenerated() {
		getIntent().putExtra(App.VideoEditor.Keys.FINISH_ON, App.VideoEditor.PACKAGE_GENERATED);
		setResult(Activity.RESULT_OK, getIntent());
		finish();
	}
	
	
}
