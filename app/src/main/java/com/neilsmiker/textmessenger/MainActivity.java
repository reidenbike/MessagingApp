package com.neilsmiker.textmessenger;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
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

public class MainActivity extends AppCompatActivity implements ContentObserverCallbacks {

    private static final String TAG = "TREX";

    private Context mContext;
    private Activity mActivity;

    //Menu:
    Menu optionsMenu;

    private ListView mConversationListView;
    //private SmsMessageAdapter mMessageAdapter;
    private ConversationsAdapter mConversationsAdapter;
    private ProgressBar mProgressBar;

    private List<Sms> listConversations = new ArrayList<>();
    int width;
    private List<Integer> selectionList = new ArrayList<>();

    //SMS
    private static final int PERMISSIONS_REQUEST_CODE = 2020;

    //TODO find display limit from user settings?
    private int displayLimit = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_conversation);

        // Get the application context
        mContext = getApplicationContext();
        mActivity = MainActivity.this;

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        myToolbar.setLogo(R.drawable.icons8_dinosaur_96);
        myToolbar.setTitle(R.string.app_name);
        setSupportActionBar(myToolbar);

        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar);

        //ListView Initialization
        mConversationListView = findViewById(R.id.conversationListView);
        mConversationListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectListItem(position, view, false);
            }
        });

        mConversationListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //SMS Initialization:
        if (checkPermission()){
            initializeConversationList();
        }

        requestSetDefault();
        //Log.i(TAG,"onResume Called");
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void selectListItem(int position, View view, boolean longClick) {
        Sms message = listConversations.get(position);
        boolean isUser = message.getFolderName().equals("sent");
        //TODO highlight selected conversations
        if (selectionList.contains(position)) {
            //TODO Pretty sure removal by index can't be replaced here, but test this later
            selectionList.remove(selectionList.indexOf(position));
            message.setSelected(false);
            //Log.i(TAG, String.valueOf(selectionList));
        } else if (selectionList.size() > 0 || longClick) {
            //TODO Add on main back button pushed action to clear selection, else super.
            // Also enable back button on action bar with same action.
            selectionList.add(position);
            message.setSelected(true);
        } else {
            Intent intent = new Intent(MainActivity.this,MainActivitySMS.class);
            intent.putExtra("selectedAddress",message.getAddress());
            intent.putExtra("selectedThreadId",message.getThreadId());
            intent.putExtra("selectedName",message.getDisplayName());
            this.startActivity(intent);
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

        int lastViewedPosition = mConversationListView.getFirstVisiblePosition();

        //TODO delete all messages in the selected conversation

        /*for(int i : selectionList) {
            getContentResolver().delete(Uri.parse("content://sms/" + listConversations.get(i).getId()), null, null);
            listConversations.remove(i);
        }

        selectionList.clear();
        optionsMenu.findItem(R.id.delete_item).setVisible(false);

        mConversationsAdapter.notifyDataSetChanged();
        mConversationListView.smoothScrollToPosition(lastViewedPosition);*/
    }


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
                    initializeConversationList();
                } else {
                    // Permissions are denied
                    Toast.makeText(mContext, "Permissions denied.", Toast.LENGTH_SHORT).show();
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
        }
    }

    private void initializeConversationList() {
        //Log.i(TAG,"Permissions Granted");
        listConversations = getActiveContacts();
        mConversationsAdapter = new ConversationsAdapter(this, R.layout.item_message_user, listConversations, width);
        mConversationListView.setAdapter(mConversationsAdapter);
    }

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

    //Called from Content Observer
    @Override
    public void updateMessageFeed() {
/*        Uri uriSMSURI = Uri.parse("content://sms");

        Cursor c = getContentResolver().query(uriSMSURI, null, null,
                null, null);
        if (c != null) {
            c.moveToNext();

            String id = c.getString(c.getColumnIndexOrThrow("_id"));

            if (!id.equals(lastID)) {

                lastID = id;
                listMessages.add(createSmsObject(c));
                mMessageAdapter.notifyDataSetChanged();
            }
            c.close();
        }*/
    }

    //Returns an ArrayList of unique addresses in the sms inbox.
    //TODO Add check for target addresses for cases where user sends message to recipient without any existing messages to display
    // that conversation thread as well.
    private List<Sms> getActiveContacts(){
        Uri uri = Uri.parse("content://sms");
        //Using Distinct messes up the order by most recent...
        //Cursor c = getContentResolver().query(uri, new String[]{"thread_id","address"}, null, null, null);
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        List <Sms> listContact;
        listContact = new ArrayList<>();
        listContact.clear();

        List <String> listThread;
        listThread = new ArrayList<>();
        listThread.clear();

        if(c != null && c.moveToFirst()) {
            do {
                String threadId = c.getString(c.getColumnIndexOrThrow("thread_id"));
                if (!listThread.contains(threadId)){
                    listThread.add(threadId);
                    listContact.add(createSmsObject(c));
                }
            } while (c.moveToNext());
        }

        //Update unread notifications icon
        //TODO Maybe narrow by read status == 0, or start by checking if there are any unread messages, then check for each thread_id.
        int unreadCount;
        for (Sms sms:listContact) {
            unreadCount = 0;
            c = getContentResolver().query(Uri.parse("content://sms"), null, "thread_id = ?", new String[]{sms.getThreadId()}, null);
            if (c != null && c.moveToFirst()){
                do {
                    if (c.getString(c.getColumnIndex("read")).equals("0")) {
                        unreadCount++;
                    }
                } while (c.moveToNext());
            }
            sms.setNumberUnread(String.valueOf(unreadCount));
        }
        if (c != null) {
            c.close();
        }

        return listContact;
    }

    public Sms createSmsObject(Cursor c){
        Sms objSms = new Sms();
        objSms.setId(c.getString(c.getColumnIndexOrThrow("_id")));
        objSms.setThreadId(c.getString(c.getColumnIndexOrThrow("thread_id")));
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

        //if (objSms.getFolderName().equals("inbox")){
            objSms.setDisplayName(getContactName(objSms.getAddress(),mContext));
        //}

        return objSms;
    }
}

