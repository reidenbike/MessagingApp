package com.neilsmiker.textmessenger;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MyContentObserver.ContentObserverCallbacks {

    private static final String TAG = "TREX";

    //Context/Lifecycle
    private Context mContext;
    private Activity mActivity;

    //Menu:
    Menu optionsMenu;

    //ListView
    private ListView mConversationListView;
    private ConversationsAdapter mConversationsAdapter;
    private List<Sms> listConversations = new ArrayList<>();
    private List<Integer> selectionList = new ArrayList<>();
    private int displayLimitInterval = 20; //TODO find display limit from user settings?
    private int displayLimit;
    private boolean allowLazyLoad = true;

    //Permissions
    private static final int PERMISSIONS_REQUEST_CODE = 2020;

    //Create new message
    private FloatingActionButton btnNewMessage;

    //Content Observer
    private MyContentObserver myContentObserver;
    private String lastID = "null";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_conversation);

        // Get the application context
        mContext = getApplicationContext();
        mActivity = MainActivity.this;

        //Set up the toolbar
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        myToolbar.setLogo(R.drawable.icons8_dinosaur_96);
        myToolbar.setTitle(R.string.app_name);
        setSupportActionBar(myToolbar);

        //ListView Initialization
        displayLimit = displayLimitInterval;

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

        mConversationListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                //TODO This is a very ugly scroll update implementation. It works until we switch to a RecyclerView; no point trying to hack around
                // the limited ListView options in the meantime.
                if (allowLazyLoad && scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                        && mConversationListView.getLastVisiblePosition() == listConversations.size()-1) {
                    int firstPosition = mConversationListView.getFirstVisiblePosition();
                    int lastPosition = mConversationListView.getLastVisiblePosition();
                    int position = lastPosition - (lastPosition - firstPosition) + 1;

                    getActiveContacts(displayLimit+1,displayLimit + displayLimitInterval);

                    displayLimit += displayLimitInterval;
                    mConversationsAdapter.notifyDataSetChanged();
                    mConversationListView.smoothScrollToPositionFromTop(position,0,0);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });

        //Set up new message button
        btnNewMessage = findViewById(R.id.btnNewMessage);
        btnNewMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayLimit = displayLimitInterval;
                Intent intent = new Intent(MainActivity.this,MainActivitySMS.class);
                intent.putExtra("newMessage",true);
                //To remove transition animation:
                //intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            }
        });

        //Content Observer Initialization:
        Handler handler = new Handler();
        myContentObserver = new MyContentObserver(handler);
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

            if (myContentObserver != null) {
                getContentResolver().registerContentObserver(
                        Uri.parse("content://sms/"), true,
                        myContentObserver);
                myContentObserver.setCallbacks(MainActivity.this);
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

    private void selectListItem(int position, View view, boolean longClick) {
        Sms message = listConversations.get(position);

        ConstraintLayout conversationLayout = view.findViewById(R.id.conversationLayout);
        TextView txtLastMessage = view.findViewById(R.id.txtLastMessage);
        TextView txtProfileName = view.findViewById(R.id.profileName);
        TextView txtTimestamp = view.findViewById(R.id.txtTimestamp);

        if (selectionList.contains(position)) {
            //TODO Pretty sure removal by index can't be replaced here, but test this later
            selectionList.remove(selectionList.indexOf(position));
            message.setSelected(false);

            //Set item to not selected
            conversationLayout.setBackground(getDrawable(R.drawable.conversation_bubble));
            txtProfileName.setTextColor(Color.parseColor("#000000"));
            txtLastMessage.setTextColor(Color.parseColor("#000000"));
            txtTimestamp.setTextColor(Color.parseColor("#808080"));
        } else if (selectionList.size() > 0 || longClick) {
            //TODO Add on main back button pushed action to clear selection, else super.
            // Also enable back button on action bar with same action.
            selectionList.add(position);
            message.setSelected(true);

            //Set item to selected
            conversationLayout.setBackground(getDrawable(R.drawable.text_bubble_user_selected));
            txtProfileName.setTextColor(getResources().getColor(R.color.colorTitle));
            txtLastMessage.setTextColor(getResources().getColor(R.color.colorTitle));
            txtTimestamp.setTextColor(getResources().getColor(R.color.colorTitle));
        } else {
            displayLimit = displayLimitInterval;
            Intent intent = new Intent(MainActivity.this,MainActivitySMS.class);
            intent.putExtra("selectedAddress",message.getAddress());
            intent.putExtra("selectedThreadId",message.getThreadId());
            intent.putExtra("selectedName",message.getDisplayName());
            //To remove transition animation:
            //intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            this.startActivity(intent);
        }

        if (selectionList.size() > 0) {
            optionsMenu.findItem(R.id.delete_item).setVisible(true);
        } else {
            optionsMenu.findItem(R.id.delete_item).setVisible(false);
        }

        Collections.sort(selectionList, Collections.<Integer>reverseOrder());
    }

    private void deleteMessages() {

        int lastViewedPosition = mConversationListView.getFirstVisiblePosition();

        ContentResolver contentResolver = getContentResolver();
        for(int i : selectionList) {
            Cursor c;
            c = getContentResolver().query(Uri.parse("content://sms"), null, "thread_id = ?", new String[]{listConversations.get(i).getThreadId()}, null);

            if (c != null && c.moveToFirst()) {
                do {
                    contentResolver.delete(Uri.parse("content://sms/" + c.getString(c.getColumnIndexOrThrow("_id"))), null, null);
                } while (c.moveToNext());
            }

            if (c != null) {
                c.close();
            }
            listConversations.remove(i);
        }

        selectionList.clear();
        optionsMenu.findItem(R.id.delete_item).setVisible(false);

        mConversationsAdapter.notifyDataSetChanged();
        mConversationListView.smoothScrollToPosition(lastViewedPosition);
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

    //--------------------------------------------------------------------------------
    //Conversations List and Contacts: Add conversation and contacts methods below
    //--------------------------------------------------------------------------------

    private void initializeConversationList() {
        listConversations.clear();
        getActiveContacts(0,displayLimit);
        mConversationsAdapter = new ConversationsAdapter(this, R.layout.item_message_user, listConversations);
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

    //Returns an ArrayList of unique addresses in the sms folder.
    private void getActiveContacts(int startDisplayLimit, int endDisplayLimit){
        Uri uri = Uri.parse("content://sms");
        String [] columns = new String[]{"DISTINCT thread_id","_id","address","body","read","date","type"};

        Cursor c = getContentResolver().query(uri, columns, "thread_id IS NOT NULL) GROUP BY (thread_id", null, "max(date) desc");

        if(c != null && c.moveToFirst()) {
            int i = 0;
            do {
                if (i >= startDisplayLimit) {
                    listConversations.add(createSmsObject(c));
                }
            } while (c.moveToNext() && i++ < endDisplayLimit);
            allowLazyLoad = c.moveToNext();
        }

        //Update unread notifications icon
        int unreadCount;
        for (Sms sms:listConversations) {
            unreadCount = 0;
            c = getContentResolver().query(Uri.parse("content://sms"), null, "thread_id = ? and read = ?", new String[]{sms.getThreadId(),"0"}, null);
            if (c != null && c.moveToFirst()){
                unreadCount = c.getCount();
            }
            sms.setNumberUnread(String.valueOf(unreadCount));
        }
        if (c != null) {
            c.close();
        }
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

        objSms.setDisplayName(getContactName(objSms.getAddress(),mContext));

        //Log.i(TAG,c.getString(c.getColumnIndexOrThrow("body")));

        return objSms;
    }

    //Called from Content Observer
    @Override
    public void updateMessageFeed() {
        listConversations.clear();
        getActiveContacts(0,displayLimit);
        mConversationsAdapter.notifyDataSetChanged();
        mConversationListView.smoothScrollToPositionFromTop(0,0,0);
    }
}

