package com.neilsmiker.textmessenger;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;


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

    //ListView
    private ListView mMessageListView;
    private SmsMessageAdapter mMessageAdapter;
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

    //TODO find display limit from user settings?
    private int displayLimit = 50;

    //Content Observer
    private MyContentObserver myContentObserver;
    private String lastID = "null";

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

        //ListView Initialization
        mMessageListView = findViewById(R.id.messageListView);
        mMessageListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectListItem(position, view, false);
            }
        });
        mMessageListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                selectListItem(position, view, true);
                return true;
            }
        });
        mMessageListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

                //TODO This is a very ugly scroll update implementation. It works until we switch to a RecyclerView; no point trying to hack around
                // the limited ListView options in the meantime.

                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && mMessageListView.getFirstVisiblePosition() == 0) {
                    int position = listMessages.size();
                    Cursor c;
                    if (selectedThreadId == null) {
                        c = getContentResolver().query(Uri.parse("content://sms"), null, "address = ?", new String[]{selectedAddress}, null);
                    } else {
                        c = getContentResolver().query(Uri.parse("content://sms"), null, "thread_id = ?", new String[]{selectedThreadId}, null);
                    }

                    boolean allowUpdate = false;
                    if (c != null && c.moveToFirst()) {
                        int i = 0;
                        do {
                            i++;
                            if (i >= displayLimit) {
                                listMessages.add(0, createSmsObject(c));
                                allowUpdate = true;
                            }
                        } while (c.moveToNext() && i < displayLimit+50);
                    }

                    if (allowUpdate) {
                        displayLimit += 50;
                        mMessageAdapter.notifyDataSetChanged();
                        mMessageListView.smoothScrollToPositionFromTop(listMessages.size() - position,0,0);
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });

        // Find the display screen width in pixels to properly size the max text bubble widths in the Adapters
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = size.x;

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
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
            mMessageListView.setVisibility(View.GONE);
            myToolbar.setTitle("New Message");

            addRecipientButton = findViewById(R.id.addRecipientButton);
            addRecipientButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedAddress = recipientEditText.getText().toString();
                    selectedName = getContactName(selectedAddress, mContext);
                    myToolbar.setTitle(selectedName);
                    initializeSmsList();

                    mMessageListView.setVisibility(View.VISIBLE);
                    inputLayout.setVisibility(View.VISIBLE);
                    recipientLayout.setVisibility(View.GONE);

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

        //Get the new display width to adjust the max text bubble width in the SmsMessageAdapter
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = (size.x);

        mMessageAdapter = new SmsMessageAdapter(this, R.layout.item_message_user, listMessages, width);
        mMessageListView.setAdapter(mMessageAdapter);
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

        int lastViewedPosition = mMessageListView.getFirstVisiblePosition();

        //TODO add error handling

        for(int i : selectionList) {
            getContentResolver().delete(Uri.parse("content://sms/" + listMessages.get(i).getId()), null, null);
            listMessages.remove(i);
        }

        selectionList.clear();
        optionsMenu.findItem(R.id.delete_item).setVisible(false);

        mMessageAdapter.notifyDataSetChanged();
        mMessageListView.smoothScrollToPosition(lastViewedPosition);
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

    public List<Sms> getAllSms() {
        List<Sms> lstSms = new ArrayList<>();

        Cursor c;
        if (selectedThreadId == null) {
            c = getContentResolver().query(Uri.parse("content://sms"), null, "address = ?", new String[]{selectedAddress}, null);
        } else {
            c = getContentResolver().query(Uri.parse("content://sms"), null, "thread_id = ?", new String[]{selectedThreadId}, null);
        }

        if (c != null && c.moveToFirst()) {

            /*Log.i(TAG, Arrays.toString(c.getColumnNames()));*/
            /*for (String column:c.getColumnNames()){
                String item = c.getString(c.getColumnIndexOrThrow(column));
                Log.i(TAG, column + ": " + item);
            }*/

            int i = 0;
            do {
                i++;
                lstSms.add(0,createSmsObject(c));
            } while (c.moveToNext() && i < displayLimit);
        } else {
            // Inbox Empty
            //TODO Update UI to read Empty/nothing to see here etc.
        }

        if (c != null) {
            c.close();
        }

        return lstSms;
    }

    private void initializeSmsList() {
        //Log.i(TAG,"Permissions Granted");
        listMessages = getAllSms();
        mMessageAdapter = new SmsMessageAdapter(this, R.layout.item_message_user, listMessages, width);
        mMessageListView.setAdapter(mMessageAdapter);
    }

    //Called from Content Observer
    @Override
    public void updateMessageFeed() {
        Uri uriSMS = Uri.parse("content://sms");

        Cursor c = getContentResolver().query(uriSMS, null, null,
                null, null);
        if (c != null) {
            c.moveToNext();

            String id = c.getString(c.getColumnIndexOrThrow("_id"));
            String address = c.getString(c.getColumnIndexOrThrow("address"));

            if (!id.equals(lastID) && address.equals(selectedAddress)) {
                lastID = id;
                listMessages.add(createSmsObject(c));
                mMessageAdapter.notifyDataSetChanged();
            }
            c.close();
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

    //--------------------------------------------------------------------------------
    //End Contacts
    //--------------------------------------------------------------------------------


    //--------------------------------------------------------------------------------
    //MMS: Add MMS methods below
    //--------------------------------------------------------------------------------

    private void sendMMS(final String recipients, final String subject, final String text)
    {
        //Log.d(TAG, "Sending");
        mSendStatusView.setText(getResources().getString(R.string.mms_status_sending));
        mSendButton.setEnabled(false);
        final String fileName = "send." + String.valueOf(Math.abs(mRandom.nextLong())) + ".dat";
        mSendFile = new File(getCacheDir(), fileName);

        // Making RPC call in non-UI thread
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final byte[] pdu = buildPdu(MmsMessagingDemo.this, recipients, subject, text);
                Uri writerUri = (new Uri.Builder())
                        .authority("com.example.android.apis.os.MmsFileProvider")
                        .path(fileName)
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .build();
                final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        MmsMessagingDemo.this, 0, new Intent(ACTION_MMS_SENT), 0);
                FileOutputStream writer = null;
                Uri contentUri = null;
                try {
                    writer = new FileOutputStream(mSendFile);
                    writer.write(pdu);
                    contentUri = writerUri;
                } catch (final IOException e) {
                    Log.e(TAG, "Error writing send file", e);
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                        }
                    }
                }

                if (contentUri != null) {
                    SmsManager.getDefault().sendMultimediaMessage(getApplicationContext(),
                            contentUri, null/*locationUrl*/, null/*configOverrides*/,
                            pendingIntent);
                } else {
                    Log.e(TAG, "Error writing sending Mms");
                    try {
                        pendingIntent.send(SmsManager.MMS_ERROR_IO_ERROR);
                    } catch (PendingIntent.CanceledException ex) {
                        Log.e(TAG, "Mms pending intent cancelled?", ex);
                    }
                }
            }
        });
    }

    //--------------------------------------------------------------------------------
    //End MMS
    //--------------------------------------------------------------------------------

}
