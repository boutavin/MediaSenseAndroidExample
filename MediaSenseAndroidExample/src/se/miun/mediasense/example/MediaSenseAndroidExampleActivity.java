package se.miun.mediasense.example;


import se.miun.mediasense.addinlayer.AddInManager;
import se.miun.mediasense.addinlayer.extensions.publishsubscribe.PublishSubscribeExtension;
import se.miun.mediasense.addinlayer.extensions.publishsubscribe.SubscriptionEventListener;
import se.miun.mediasense.disseminationlayer.communication.CommunicationInterface;
import se.miun.mediasense.disseminationlayer.disseminationcore.DisseminationCore;
import se.miun.mediasense.disseminationlayer.disseminationcore.GetEventListener;
import se.miun.mediasense.disseminationlayer.disseminationcore.GetResponseListener;
import se.miun.mediasense.disseminationlayer.disseminationcore.ResolveResponseListener;
import se.miun.mediasense.disseminationlayer.lookupservice.LookupServiceInterface;
import se.miun.mediasense.interfacelayer.MediaSensePlatform;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MediaSenseAndroidExampleActivity extends Activity implements OnClickListener, GetEventListener, GetResponseListener, ResolveResponseListener, SubscriptionEventListener, OnItemSelectedListener{
    	
	// UI components
	private LinearLayout layoutBeforeRegistration, layoutAfterRegistration;
	private Button buttonRegister, buttonGet, buttonSub, buttonNotify;
	private Spinner spinnerLocalUci, spinnerRemoteUci, spinnerContext;
	private TextView textViewGetResponse, textViewSubEvent;
	
	//MediaSense Platform Application Interfaces
	private MediaSensePlatform platform;
	private DisseminationCore core;
	private AddInManager addInManager;
	private PublishSubscribeExtension pse;
	
	private Handler handler; // Handler to check internet connection
	private boolean isCurrentRequestAGetCall = true; // Set the nature of the request (GET or SUBSCRIPTION)
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Declare and set listeners for UI components
        layoutBeforeRegistration = (LinearLayout) findViewById(R.id.layoutBeforeRegistration);
        layoutAfterRegistration = (LinearLayout) findViewById(R.id.layoutAfterRegistration);
        
        buttonRegister = (Button) findViewById(R.id.buttonRegister);
        buttonRegister.setOnClickListener(this);
        buttonGet = (Button) findViewById(R.id.buttonGet);
        buttonGet.setOnClickListener(this);
        buttonSub = (Button) findViewById(R.id.buttonSubscribe);
        buttonSub.setOnClickListener(this);
        buttonNotify = (Button) findViewById(R.id.buttonNotify);
        buttonNotify.setOnClickListener(this);
        
        spinnerLocalUci = (Spinner) findViewById(R.id.spinnerLocalUci);
        spinnerLocalUci.setOnItemSelectedListener(this);
        spinnerRemoteUci = (Spinner) findViewById(R.id.spinnerRemoteUci);
        spinnerContext = (Spinner) findViewById(R.id.spinnerContext);
        
        textViewGetResponse = (TextView) findViewById(R.id.textViewGetReponse);
        textViewSubEvent = (TextView) findViewById(R.id.textViewSubscriptionEvent);
        /////////////////////////////////////////////////
        
        // Create the platform itself
        platform = new MediaSensePlatform();
        
        // Initialize the platform with chosen LookupService type and chosen Communication type.        
        platform.initalize(LookupServiceInterface.SERVER, CommunicationInterface.TCP); //For Server Lookup and TCP P2P communication    	
        	        
       	//Extract the core for accessing the primitive functions
        core = platform.getDisseminationCore();
        
        // Load Publication/Subscription extension 
        addInManager = platform.getAddInManager();
        pse = new PublishSubscribeExtension();
        addInManager.loadAddIn(pse);
                
        // Set the event listeners
        core.setGetEventListener(this);
        pse.setSubscriptionEventListener(this);
        
        // Set the response listeners
        core.setGetResponseListener(this);
        core.setResolveResponseListener(this);
        
        // Check internet connection
        handler = new Handler();
        handler.postAtTime(new ConnectionThread(), SystemClock.uptimeMillis());
    }

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.buttonRegister:
				// Register own UCI
		        final String uci = spinnerLocalUci.getSelectedItem().toString();
		    	// Create and start a Thread to register to MediaSense
		        Thread registerThread = new Thread(new Runnable() {
					@Override
					public void run() {
						core.register(uci);
					}
				});
		        registerThread.start();
		        layoutBeforeRegistration.setVisibility(View.GONE); // When registered, hide button and textview
		        layoutAfterRegistration.setVisibility(View.VISIBLE); // And show the rest of the layout
		        Log.i("REGISTER", uci+" registered");
		        ///////////////////////////////////////////////////
				break;
			case R.id.buttonGet:		
				//Start new Thread to resolve the UCI
				Thread t1 = new Thread(new ResolveOrSubscribeThread(true));
				t1.start();
				break;
			case R.id.buttonSubscribe:		
				//Start new Thread to run the tests, Android does not like if we run these stuff in the UI thread!
				Thread t2 = new Thread(new ResolveOrSubscribeThread(false));
				t2.start();
				break;
			case R.id.buttonNotify:
				pse.notifySubscribers(spinnerLocalUci.getSelectedItem().toString(), spinnerContext.getSelectedItem().toString());
				break;
		}
	}

	/*
	 * Event triggered when received a GET request 
	 * 
	 * --> Notify the source with the context information value
	 */
	@Override
	public void getEvent(String source, String uci) {	
		platform.getDisseminationCore().notify(source, uci, spinnerContext.getSelectedItem().toString());	
	}	

	/*
	 * Event triggered when received a response from a resolve call
	 * 
	 * --> Perform a get call or start subscription
	 */
	@Override
	public void resolveResponse(String uci, String ip) {
		if(!ip.equalsIgnoreCase("null")){
			if(isCurrentRequestAGetCall) core.get(uci, ip); // Perform GET call
			else pse.startSubscription(uci, ip); 			// Start subscription
		} else { // If UCI not registered
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					textViewGetResponse.setText("uci not registered...");
				}
			});
		}
	}

	/*
	 * Event triggered when received a response from a GET call
	 * @see the attributed TextView displays the value fetch from the response 
	 */
	@Override
	public void getResponse(String uci, String value) {
		final String theValue = value;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				textViewGetResponse.setText(theValue);
			}
		});
	}
	
	/*
	 * Event triggered when a notification of the subscription is received
	 * @see the attributed TextView displays the fetched value
	 */
	@Override
	public void subscriptionEvent(String uci, String value) {
		final String _value = value;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				textViewSubEvent.setText(_value);
			}
		});
	}
	
	/*
	 * Runnable performing a resolve call for the remote UCI
	 */
	class ResolveOrSubscribeThread implements Runnable{
		public ResolveOrSubscribeThread(boolean isGetRequest){
			isCurrentRequestAGetCall = isGetRequest;
		}
		
		@Override
		public void run() {
			try {
				core.resolve(spinnerRemoteUci.getSelectedItem().toString());
				Log.i("ResolveThread", "Perform UCI resolution");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}		
	}

	/*
	 * Triggered local UCI has changed
	 * @see Show Register button and hide the extended layout
	 */
	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
				layoutBeforeRegistration.setVisibility(View.VISIBLE); // When registered, show button and textview
		        layoutAfterRegistration.setVisibility(View.INVISIBLE); // And hide the rest of the layout
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {}
	
	/*
	 * Thread checking the internet connection each 10 seconds
	 * 
	 * @see	if no internet connection, display AlertDialog 
	 */
	private class ConnectionThread implements Runnable{
		@Override
		public void run() {
			if(!isOnline()){
				AlertDialog.Builder dialog = new AlertDialog.Builder(MediaSenseAndroidExampleActivity.this);
		        dialog.
		        setMessage("No Internet Connection!").
				setNeutralButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						handler.postDelayed(new ConnectionThread(), 10000); // Check internet connection in 10 seconds
					}
				}).show();
			}
		}
	}

	/*
	 * Check if phone has internet connection
	 * 
	 * @result	true if is online, false if not online
	 */
	public boolean isOnline() {
	    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

	    return cm.getActiveNetworkInfo() != null && 
	       cm.getActiveNetworkInfo().isConnectedOrConnecting();
	}

}