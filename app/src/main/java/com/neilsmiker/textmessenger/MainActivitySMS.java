package com.neilsmiker.textmessenger;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NavUtils;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MainActivitySMS extends AppCompatActivity implements MyContentObserver.ContentObserverCallbacks {

    //Context/Lifecycle
    private Context mContext;
    private Activity mActivity;

    //Final variables
    private static final String TAG = "TREX";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 160;
    private static final int RC_PHOTO_PICKER = 2;

    //Menu:
    Menu optionsMenu;

    //UI
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private ConstraintLayout inputLayout;

    //Lists
    private List<Sms> listMessages = new ArrayList<>();
    int width;
    private List<Integer> selectionList = new ArrayList<>();

    //SMS
    private static final int PERMISSIONS_REQUEST_CODE = 2020;
    private String selectedAddress;
    private String selectedThreadId;
    private String selectedName;
    private boolean newMessage = false;

    //Create new message
    private ConstraintLayout recipientLayout;
    private EditText recipientEditText;
    private Button addRecipientButton;

    //RecyclerView
    private RecyclerView recyclerView;
    private LinearLayoutManager linearLayoutManager;
    private SmsRecyclerAdapter recyclerAdapter;
    private boolean allowContentObserver = true;
    private int totalToDelete = 0;
    private int deleted = 0;
    private int displayLimitInterval = 50;
    private int displayLimit;
    private boolean allowLazyLoad = true;
    private boolean allowRefocusScroll = false;
    int currentScrollPosition = 0;
    int currentScrollOffset = 0;

    //Set the RecyclerView scroll position when starting the activity (to 0) or when opening/closing the soft keyboard (to previous position)
    //TODO Still has issues returning to correct scroll position when large messages have offset
    Handler handler = new Handler();
    Runnable r = new Runnable() {
        public void run() {
            if (allowRefocusScroll) {
                linearLayoutManager.scrollToPositionWithOffset(currentScrollPosition, currentScrollOffset);
            } else {
                linearLayoutManager.scrollToPosition(0);
                allowRefocusScroll = true;
            }
        }
    };

    //Content Observer
    private MyContentObserver myContentObserver;
    private String lastID = "null";

    //Contacts Fragment
    LinearLayout fragmentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_sms);

        // Get the application context
        mContext = getApplicationContext();
        mActivity = MainActivitySMS.this;

        //Get intent extras
        Bundle extras = getIntent().getExtras();
        if(extras !=null)
        {
            selectedAddress = extras.getString("selectedAddress");
            selectedThreadId = extras.getString("selectedThreadId");
            selectedName = extras.getString("selectedName");
            newMessage = extras.getBoolean("newMessage");
        }

        //Set up the toolbar
        final Toolbar myToolbar = findViewById(R.id.my_toolbar);
        myToolbar.setTitle((selectedName != null) ? selectedName : getString(R.string.app_name));
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar);
        mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);
        inputLayout = findViewById(R.id.inputLayout);
        fragmentContainer = findViewById(R.id.fragment_container);

        // Find the display screen width in pixels to properly size the max text bubble widths in the Adapters
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = size.x;

        //RecyclerView initialization
        displayLimit = displayLimitInterval;

        recyclerView = findViewById(R.id.conversationRecyclerView);
        recyclerView.setHasFixedSize(true);

        recyclerAdapter = new SmsRecyclerAdapter(listMessages,mContext,width);
        recyclerView.setAdapter(recyclerAdapter);

        linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setReverseLayout(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        ItemClickSupport.addTo(recyclerView).setOnItemClickListener(new ItemClickSupport.OnItemClickListener() {
            @Override
            public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                selectListItem(position, v, false);
            }
        }).setOnItemLongClickListener(new ItemClickSupport.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClicked(RecyclerView recyclerView, int position, View v) {
                selectListItem(position, v, true);
                return true;
            }
        });

        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(linearLayoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                if (allowLazyLoad) {
                    getAllSms(displayLimit + 1, displayLimit + displayLimitInterval);
                    displayLimit += displayLimitInterval;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy != 0) {
                    //Get the current position and offset for the first visible item. Use these ints to set the scroll position
                    // when opening/closing the soft keyboard.
                    currentScrollPosition = linearLayoutManager.findFirstVisibleItemPosition();
                    View child = linearLayoutManager.getChildAt(0);
                    if (child != null) {
                        currentScrollOffset = recyclerView.getBottom() - recyclerView.getTop() - child.getBottom();
                    }
                    //Log.i(TAG,"Current position: " + currentScrollPosition + ", offset: " + currentScrollOffset);
                } else {
                    //Called when soft keyboard opens/closes. Set the scroll position via the Runnable
                    handler.post(r);
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        //TODO if over SMS char limit, switch to MMS or concatenate and segment.
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                sendSMS(mMessageEditText.getText().toString(), selectedAddress);

                // Clear input box
                mMessageEditText.setText("");
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        //Set up new message button and layouts
        if (newMessage) {
            recipientLayout = findViewById(R.id.recipientLayout);
            recipientLayout.setVisibility(View.VISIBLE);
            inputLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            myToolbar.setTitle("New Message");

            addRecipientButton = findViewById(R.id.addRecipientButton);
            addRecipientButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedAddress = recipientEditText.getText().toString();
                    selectedName = getContactName(selectedAddress, mContext);
                    myToolbar.setTitle(selectedName);
                    initializeSmsList();

                    recyclerView.setVisibility(View.VISIBLE);
                    inputLayout.setVisibility(View.VISIBLE);
                    recipientLayout.setVisibility(View.GONE);
                    fragmentContainer.setVisibility(View.GONE);

                    //Set focus to Message EditText and show the keyboard if not already active
                    mMessageEditText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mMessageEditText, InputMethodManager.SHOW_IMPLICIT);
                }
            });

            recipientEditText = findViewById(R.id.recipientEditText);
            // Enable Send button when there's text to send
            recipientEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    if (charSequence.toString().trim().length() > 0) {
                        addRecipientButton.setEnabled(true);
                    } else {
                        addRecipientButton.setEnabled(false);
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });

            //Initialize Contacts Fragment:
            fragmentContainer.setVisibility(View.VISIBLE);
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            ContactsFragment fragment = new ContactsFragment();
            fragmentTransaction.add(R.id.fragment_container, fragment);
            fragmentTransaction.commit();
        }

        //Content Observer Initialization:
        Handler handler = new Handler();
        myContentObserver = new MyContentObserver(handler);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            //TODO display attached to current message and send via MMS on send button
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        optionsMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.delete_item:
                deleteMessages();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //SMS Initialization:
        if (checkPermission() && selectedAddress != null){

            initializeSmsList();

            if (myContentObserver != null) {
                getContentResolver().registerContentObserver(
                        Uri.parse("content://sms/"), true,
                        myContentObserver);
                myContentObserver.setCallbacks(MainActivitySMS.this);
            }
        }

        requestSetDefault();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (myContentObserver != null) {
            getContentResolver().unregisterContentObserver(myContentObserver);
            myContentObserver.setCallbacks(null);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        //Get the new display width to adjust the max text bubble width in the SmsRecyclerAdapter
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = (size.x);

        recyclerAdapter.setMaxWidth(width);
    }

    private void selectListItem(int position, View view, boolean longClick) {
        TextView tv = view.findViewById(R.id.messageTextView);
        LinearLayout textBubble = view.findViewById(R.id.textBubble);

        Sms message = listMessages.get(position);
        boolean isUser = message.getFolderName().equals("sent");
        if (selectionList.contains(position)) {
            //TODO Pretty sure removal by index can't be replaced here, but test this later
            selectionList.remove(selectionList.indexOf(position));
            message.setSelected(false);
            if (isUser) {
                textBubble.setBackground(getDrawable(R.drawable.text_bubble_user));
            } else {
                textBubble.setBackground(getDrawable(R.drawable.text_bubble_other));
            }
            tv.setTextColor(Color.parseColor("#000000"));
            //Log.i(TAG, String.valueOf(selectionList));
        } else if (selectionList.size() > 0 || longClick) {
            //TODO Add on main back button pushed action to clear selection, else super.
            // Also enable back button on action bar with same action.
            selectionList.add(position);
            message.setSelected(true);
            if (isUser) {
                textBubble.setBackground(getDrawable(R.drawable.text_bubble_user_selected));
            } else {
                textBubble.setBackground(getDrawable(R.drawable.text_bubble_other_selected));
            }
            tv.setTextColor(getResources().getColor(R.color.colorTitle));
        }

        if (selectionList.size() > 0) {
            optionsMenu.findItem(R.id.delete_item).setVisible(true);
        } else {
            optionsMenu.findItem(R.id.delete_item).setVisible(false);
        }

        Collections.sort(selectionList, Collections.<Integer>reverseOrder());

        //Log.i(TAG, String.valueOf(selectionList));
    }

    private void deleteMessages() {

        //TODO add error handling
        allowContentObserver = false;
        totalToDelete = selectionList.size();
        deleted = 0;

        for(int i : selectionList) {
            getContentResolver().delete(Uri.parse("content://sms/" + listMessages.get(i).getId()), null, null);
            listMessages.remove(i);
            recyclerAdapter.notifyItemRemoved(i);
        }

        selectionList.clear();
        optionsMenu.findItem(R.id.delete_item).setVisible(false);
    }


    //----------------------------------------------------------------------------------------
    //Permissions and Set Default Requests
    //----------------------------------------------------------------------------------------

    protected boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                + ContextCompat.checkSelfPermission(
                mActivity, Manifest.permission.SEND_SMS)
                + ContextCompat.checkSelfPermission(
                mActivity, Manifest.permission.RECEIVE_SMS)
                + ContextCompat.checkSelfPermission(
                mActivity, Manifest.permission.READ_CONTACTS)
                + ContextCompat.checkSelfPermission(
                mActivity, Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            // Do something, when permissions not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.READ_SMS)
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.SEND_SMS)
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.RECEIVE_SMS)
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.READ_CONTACTS)
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.WRITE_CONTACTS)) {
                // If we should give explanation of requested permissions

                // Show an alert dialog here with request explanation
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setMessage("Read, Write, and Receive SMS permissions are required.");
                builder.setTitle("Please grant the following permissions");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(
                                mActivity,
                                new String[]{
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS
                                },
                                PERMISSIONS_REQUEST_CODE
                        );
                    }
                });
                builder.setNeutralButton("Cancel", null);
                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                // Directly request for required permissions, without explanation
                ActivityCompat.requestPermissions(
                        mActivity,
                        new String[]{
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS
                        },
                        PERMISSIONS_REQUEST_CODE
                );
            }
            return false;
        } else {
            // Permissions already granted
            enableSmsButton();
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                // When request is cancelled, the results array are empty
                if (
                        (grantResults.length > 0) &&
                                (grantResults[0]
                                        + grantResults[1]
                                        + grantResults[2]
                                        == PackageManager.PERMISSION_GRANTED
                                )
                ) {
                    // Permissions are granted
                    initializeSmsList();
                    enableSmsButton();
                } else {
                    // Permissions are denied
                    Toast.makeText(mContext, "Permissions denied.", Toast.LENGTH_SHORT).show();
                    disableSmsButton();
                }
            }
        }
    }

    private void requestSetDefault(){
        final String myPackageName = getPackageName();
        if (!Telephony.Sms.getDefaultSmsPackage(this).equals(myPackageName)) {
            // App is not default.
            // Show the "not currently set as the default SMS app" interface
            View viewGroup = findViewById(R.id.requestDefaultLayout);
            viewGroup.setVisibility(View.VISIBLE);

            //Get Package manager and service component names
            final PackageManager packageManager = mContext.getPackageManager();

            final ComponentName smsReceiver = new ComponentName(mContext,SMSreceiver.class);
            final ComponentName mmsReceiver = new ComponentName(mContext,MmsReceiver.class);
            final ComponentName headlessSmsSendService = new ComponentName(mContext,HeadlessSmsSendService.class);

            //Disable all services
            if (packageManager.getComponentEnabledSetting(smsReceiver) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(smsReceiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            if (packageManager.getComponentEnabledSetting(mmsReceiver) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(mmsReceiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            if (packageManager.getComponentEnabledSetting(headlessSmsSendService) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(headlessSmsSendService, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }

            // Set up a button that allows the user to change the default SMS app
            Button button = findViewById(R.id.btnSetDefault);
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (packageManager.getComponentEnabledSetting(smsReceiver) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                        packageManager.setComponentEnabledSetting(smsReceiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                    }
                    if (packageManager.getComponentEnabledSetting(mmsReceiver) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                        packageManager.setComponentEnabledSetting(mmsReceiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                    }
                    if (packageManager.getComponentEnabledSetting(headlessSmsSendService) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                        packageManager.setComponentEnabledSetting(headlessSmsSendService, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                    }

                    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, myPackageName);
                    startActivity(intent);
                }
            });
        } else {
            // App is the default.
            // Hide the "not currently set as the default SMS app" interface
            View viewGroup = findViewById(R.id.requestDefaultLayout);
            viewGroup.setVisibility(View.GONE);

            /*Log.i(TAG,"App is default, SMS receiver enabled?: " +
                    (mContext.getPackageManager().getComponentEnabledSetting(new ComponentName(mContext,SMSreceiver.class)) ==
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED));*/
        }
    }

    private void disableSmsButton() {
        Toast.makeText(this, "SMS usage disabled", Toast.LENGTH_LONG).show();
        mSendButton.setVisibility(View.INVISIBLE);
        /*Button retryButton = findViewById(R.id.button_retry);
        retryButton.setVisibility(View.VISIBLE);*/
    }

    private void enableSmsButton() {
        mSendButton.setVisibility(View.VISIBLE);
    }

    //----------------------------------------------------------------------------------------
    //SMS
    //----------------------------------------------------------------------------------------

    private void sendSMS(String message, String cellNumber) {
        if (checkPermission()) {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(cellNumber, null, message, null, null);

            if (Telephony.Sms.getDefaultSmsPackage(this).equals(getPackageName())) {
                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.ADDRESS, cellNumber);
                values.put(Telephony.Sms.BODY, message);
                getContentResolver().insert(Telephony.Sms.Sent.CONTENT_URI, values);
            }
            // TODO Also add listener to check if message sends correctly and address errors. Display spinner until callback triggers?
        }
    }

    public void getAllSms(int startDisplayLimit, int endDisplayLimit) {
        List<Sms> lstSms = new ArrayList<>();

        Cursor c;
        if (selectedThreadId == null) {
            c = getContentResolver().query(Uri.parse("content://sms"), null, "address = ?", new String[]{selectedAddress}, null);
        } else {
            c = getContentResolver().query(Uri.parse("content://sms"), null, "thread_id = ?", new String[]{selectedThreadId}, null);
        }

        if(c != null && c.moveToFirst()) {
            int i = 0;
            do {
                if (i >= startDisplayLimit) {
                    lstSms.add(createSmsObject(c));
                }
            } while (c.moveToNext() && i++ < endDisplayLimit);
            allowLazyLoad = c.moveToNext();
        }

        if (c != null) {
            c.close();
        }

        int positionStart = listMessages.size();
        listMessages.addAll(lstSms);
        recyclerAdapter.notifyItemRangeInserted(positionStart,lstSms.size());
    }

    private void initializeSmsList() {
        //Log.i(TAG,"Permissions Granted");
        listMessages.clear();
        getAllSms(0,displayLimit);

        recyclerAdapter.notifyDataSetChanged();
    }

    //Called from Content Observer
    @Override
    public void updateMessageFeed() {
        if (allowContentObserver){
            Uri uriSMS = Uri.parse("content://sms");

            Cursor c = getContentResolver().query(uriSMS, null, null,
                    null, null);
            if (c != null) {
                c.moveToNext();

                String id = c.getString(c.getColumnIndexOrThrow("_id"));
                String address = c.getString(c.getColumnIndexOrThrow("address"));

                if (!id.equals(lastID) && address.equals(selectedAddress)) {
                    lastID = id;
                    //TODO Move autoscroll to public. If false, display bottom notification that can be tapped to scroll to bottom
                    boolean autoscroll = linearLayoutManager.findFirstVisibleItemPosition() == 0;
                    listMessages.add(0,createSmsObject(c));
                    recyclerAdapter.notifyItemInserted(0);
                    if (autoscroll) {
                        recyclerView.smoothScrollToPosition(0);
                    }
                }
                c.close();
            }
        } else {
            deleted++;
            if (deleted >= totalToDelete){
                allowContentObserver = true;
            }
        }
    }

    public Sms createSmsObject(Cursor c){
        Sms objSms = new Sms();
        objSms.setId(c.getString(c.getColumnIndexOrThrow("_id")));
        objSms.setThreadId(c.getString(c.getColumnIndexOrThrow("thread_id")));
        objSms.setAddress(c.getString(c.getColumnIndexOrThrow("address")));
        objSms.setMsg(c.getString(c.getColumnIndexOrThrow("body")));
        objSms.setTime(c.getString(c.getColumnIndexOrThrow("date")));
        if (c.getString(c.getColumnIndexOrThrow("type")).contains("1")) {
            objSms.setFolderName("inbox");
        } else {
            objSms.setFolderName("sent");
        }

        if (objSms.getFolderName().equals("inbox")){
            objSms.setDisplayName(getContactName(objSms.getAddress(),mContext));
        }

        //Update if read == 0:
        if (c.getString(c.getColumnIndex("read")).equals("0")) {
            ContentValues values = new ContentValues();
            values.put(Telephony.Sms.READ, "1");
            getContentResolver().update(Telephony.Sms.Inbox.CONTENT_URI, values, Telephony.Sms.Inbox._ID + "=?",
                    new String[]{c.getString(c.getColumnIndexOrThrow("_id"))});
        }
        //Set SMS object read state to "1" since this is always true at this point
        objSms.setReadState("1");

        return objSms;
    }

    //--------------------------------------------------------------------------------
    //Contacts: Add Contacts methods below
    //--------------------------------------------------------------------------------

    public String getContactName(final String phoneNumber, Context context)
    {
        if (checkPermission()) {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));

            String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

            String contactName = phoneNumber;
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(0);
                    if (contactName.equals("")){
                        contactName = phoneNumber;
                    }
                }
                cursor.close();
            }

            return contactName;
        }
        return null;
    }

    public void insertRecipientNumber(String number){
        String currentText = recipientEditText.getText().toString();
        if (!currentText.equals("")){
            currentText = currentText + ",";
        }
        recipientEditText.setText(currentText + number);
    }

    //--------------------------------------------------------------------------------
    //End Contacts
    //--------------------------------------------------------------------------------


    //--------------------------------------------------------------------------------
    //MMS: Add MMS methods below
    //--------------------------------------------------------------------------------



    //--------------------------------------------------------------------------------
    //End MMS
    //--------------------------------------------------------------------------------

}