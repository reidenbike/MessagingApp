package com.neilsmiker.textmessenger;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivitySMS extends AppCompatActivity implements ContentObserverCallbacks {

    private static final String TAG = "TREX";

    private Context mContext;
    private Activity mActivity;

    //Menu:
    Menu optionsMenu;

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int RC_PHOTO_PICKER = 2;

    private ListView mMessageListView;
    private SmsMessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private List<Sms> listMessages = new ArrayList<>();
    int width;
    private List<Integer> selectionList = new ArrayList<>();

    //SMS
    private static final int PERMISSIONS_REQUEST_CODE = 2020;
    private int displayLimit = 50;

    //Broadcast Receiver
    private MyContentObserver myContentObserver;
    private String lastID = "null";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the application context
        mContext = getApplicationContext();
        mActivity = MainActivitySMS.this;

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        myToolbar.setLogo(R.drawable.icons8_dinosaur_96);
        myToolbar.setTitle(R.string.app_name);
        setSupportActionBar(myToolbar);

        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar);
        mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);

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

        // Initialize message ListView and its adapter
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = (int) (.8 * size.x);

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
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO change to actual target phone #
                sendSMS(mMessageEditText.getText().toString(), "2406045122");

                // Clear input box
                mMessageEditText.setText("");
            }
        });

        //SMS Initialization:
        Handler handler = new Handler();
        myContentObserver = new MyContentObserver(handler);

/*        if (checkPermission()){
            initializeSmsList();

            Handler handler = new Handler();
            myContentObserver = new MyContentObserver(handler);
            this.getApplicationContext()
                    .getContentResolver()
                    .registerContentObserver(
                            Uri.parse("content://sms/"), true,
                            myContentObserver);
        }*/
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
            case R.id.delete_item:
                deleteMessages();
                return true;
            case R.id.sign_out_menu:
                // Sign out
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //TODO Completely refresh listview and get all sms on resume

        //SMS Initialization:
        if (checkPermission()){

            initializeSmsList();

            if (myContentObserver != null) {
                getContentResolver().registerContentObserver(
                        Uri.parse("content://sms/"), true,
                        myContentObserver);
                myContentObserver.setCallbacks(MainActivitySMS.this);
            }
        }
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

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = (int) (.8 * size.x);

        mMessageAdapter = new SmsMessageAdapter(this, R.layout.item_message_user, listMessages, width);
        mMessageListView.setAdapter(mMessageAdapter);
    }

    private void selectListItem(int position, View view, boolean longClick) {
        TextView tv = view.findViewById(R.id.messageTextView);
        TextView txtTimestamp = view.findViewById(R.id.txtTimestamp);
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
            Log.i(TAG, String.valueOf(selectionList));
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
        } else {
            if (txtTimestamp.getVisibility() == View.GONE) {
                txtTimestamp.setVisibility(View.VISIBLE);
            } else {
                txtTimestamp.setVisibility(View.GONE);
            }
        }

        if (selectionList.size() > 0) {
            optionsMenu.findItem(R.id.delete_item).setVisible(true);
        } else {
            optionsMenu.findItem(R.id.delete_item).setVisible(false);
        }

        Collections.sort(selectionList, Collections.<Integer>reverseOrder());

        Log.i(TAG, String.valueOf(selectionList));
    }

    private void deleteMessages() {

        int lastViewedPosition = mMessageListView.getFirstVisiblePosition();

        //TODO Delete selected messages from folder
        /*for(int i : selectionList) {
            Log.i(TAG,"Removed int " + i + ", " + friendlyMessages.get(i).getText());
            mMessagesDatabaseReference.child(friendlyMessages.get(i).getKey()).removeValue();
            friendlyMessages.remove(i);
        }*/

        selectionList.clear();
        optionsMenu.findItem(R.id.delete_item).setVisible(false);

        mMessageAdapter.notifyDataSetChanged();
        mMessageListView.smoothScrollToPosition(lastViewedPosition);
    }


    //----------------------------------------------------------------------------------------
    //SMS
    private void disableSmsButton() {
        Toast.makeText(this, "SMS usage disabled", Toast.LENGTH_LONG).show();
        mSendButton.setVisibility(View.INVISIBLE);
        /*Button retryButton = findViewById(R.id.button_retry);
        retryButton.setVisibility(View.VISIBLE);*/
    }

    private void enableSmsButton() {
        mSendButton.setVisibility(View.VISIBLE);
    }

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

    private void sendSMS(String message, String cellNumber) {
        if (checkPermission()) {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(cellNumber, null, message, null, null);

            //TODO Instead, get from sent folder using the number of sent messages as the limit if that works better to get ID and other params.
            // Also add listener to check if message sends correctly and address errors. Display progress bar until callback triggers.
/*            Sms objSms = new Sms();
            objSms.setId(null);
            objSms.setAddress(mUsername);
            objSms.setMsg(message);
            objSms.setReadState("1");
            objSms.setTime(String.valueOf(System.currentTimeMillis()));
            objSms.setFolderName("sent");
            objSms.setDisplayName(null);

            //mMessageAdapter.add(objSms);
            listMessages.add(objSms);
            mMessageAdapter.notifyDataSetChanged();*/
        }
    }

    public List<Sms> getAllSms() {
        List<Sms> lstSms = new ArrayList<>();
        Sms objSms;
        /*Uri message = Uri.parse("content://sms/");
        ContentResolver cr = this.getContentResolver();*/

        Cursor c = getContentResolver().query(Uri.parse("content://sms"), null, null, null, null);

        //Cursor c = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);

        if (c != null && c.moveToFirst()) {
            int i = 0;
            do {
                i++;
                objSms = new Sms();
                objSms.setId(c.getString(c.getColumnIndexOrThrow("_id")));
                objSms.setAddress(c.getString(c
                        .getColumnIndexOrThrow("address")));
                objSms.setMsg(c.getString(c.getColumnIndexOrThrow("body")));
                objSms.setReadState(c.getString(c.getColumnIndex("read")));
                objSms.setTime(c.getString(c.getColumnIndexOrThrow("date")));
                if (c.getString(c.getColumnIndexOrThrow("type")).contains("1")) {
                    objSms.setFolderName("inbox");
                } else {
                    objSms.setFolderName("sent");
                }

                if (objSms.getFolderName().equals("inbox")){
                    objSms.setDisplayName(getContactName(objSms.getAddress(),mContext));
                }

                lstSms.add(0,objSms);
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
        Log.i(TAG,"Permissions Granted");
        listMessages = getAllSms();
        mMessageAdapter = new SmsMessageAdapter(this, R.layout.item_message_user, listMessages, width);
        mMessageListView.setAdapter(mMessageAdapter);
    }

    public String getContactName(final String phoneNumber, Context context)
    {
        if (checkPermission()) {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));

            String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

            String contactName = "";
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(0);
                }
                cursor.close();
            }

            return contactName;
        }
        return null;
    }

    //Broadcast Receiver
/*    public static MainActivitySMS getInstance(){
        return mainActivitySMS;
    }*/

    @Override
    public void updateMessageFeed() {
        Uri uriSMSURI = Uri.parse("content://sms");

        Cursor c = getContentResolver().query(uriSMSURI, null, null,
                null, null);
        if (c != null) {
            c.moveToNext();

            String id = c.getString(c.getColumnIndexOrThrow("_id"));

            if (!id.equals(lastID)) {
                Sms objSms = new Sms();
                objSms.setId(c.getString(c.getColumnIndexOrThrow("_id")));
                objSms.setAddress(c.getString(c
                        .getColumnIndexOrThrow("address")));
                objSms.setMsg(c.getString(c.getColumnIndexOrThrow("body")));
                objSms.setReadState(c.getString(c.getColumnIndex("read")));
                objSms.setTime(c.getString(c.getColumnIndexOrThrow("date")));
                if (c.getString(c.getColumnIndexOrThrow("type")).contains("1")) {
                    objSms.setFolderName("inbox");
                } else {
                    objSms.setFolderName("sent");
                }

                if (objSms.getFolderName().equals("inbox")) {
                    objSms.setDisplayName(getContactName(objSms.getAddress(), mContext));
                }

                lastID = id;
                listMessages.add(objSms);
                mMessageAdapter.notifyDataSetChanged();
            }
            c.close();
        }
    }

/*    public void updateMessageFeed(final SmsMessage[] messages) {
        MainActivitySMS.this.runOnUiThread(new Runnable() {
            public void run() {
                //TODO Use commented out block below instead of getAllSms, but figure out what value to use for setID.
                // Or count the number of new messages, limit getAllSms to that number and add to listMessages if ID is
                // easiest to get after SMS is saved to folder.
                for(SmsMessage message : messages) {
                    Sms objSms = new Sms();
                    objSms.setId(String.valueOf(message.getProtocolIdentifier()));
                    objSms.setAddress(message.getOriginatingAddress());
                    objSms.setMsg(message.getMessageBody());
                    objSms.setReadState("1");
                    objSms.setTime(String.valueOf(message.getTimestampMillis()));
                    objSms.setFolderName("inbox");
                    objSms.setDisplayName(getContactName(objSms.getAddress(),mContext));

                    listMessages.add(objSms);
                }

                //listMessages = getAllSms();
                mMessageAdapter.notifyDataSetChanged();
            }
        });
    }*/
}

class MyContentObserver extends ContentObserver {
    private ContentObserverCallbacks contentObserverCallbacks;
    //String lastID = "null";

    public MyContentObserver(Handler h) {
        super(h);
    }

    @Override
    public boolean deliverSelfNotifications() {
        return true;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);

/*        Uri uriSMSURI = Uri.parse("content://sms");

        Cursor cur = MainActivitySMS.getInstance().getContentResolver().query(uriSMSURI, null, null,
                null, null);
        cur.moveToNext();

        String protocol = cur.getString(cur.getColumnIndex("protocol"));
        String id = cur.getString(cur.getColumnIndexOrThrow("_id"));

        if (!id.equals(lastID)) {
            if (protocol == null) {
                //the message is sent out just now
                Log.d("TREX", "Sent: " + cur.getString(cur.getColumnIndexOrThrow("body")) + ", Status: " +
                        cur.getString(cur.getColumnIndexOrThrow("address")));
            } else {
                //the message is received just now
                Log.d("TREX", "Received: " + cur.getString(cur.getColumnIndexOrThrow("body")) + ", Status: " +
                        cur.getString(cur.getColumnIndexOrThrow("address")));
            }
        }*/

        contentObserverCallbacks.updateMessageFeed();

        /*lastID = id;

        cur.close();*/
    }

    public void setCallbacks(ContentObserverCallbacks callbacks) {
        contentObserverCallbacks = callbacks;
    }
}
