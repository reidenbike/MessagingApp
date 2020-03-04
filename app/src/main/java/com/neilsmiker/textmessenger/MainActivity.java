package com.neilsmiker.textmessenger;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements MyContentObserver.ContentObserverCallbacks {

    //private static final String TAG = "TREX";

    //Context/Lifecycle
    private Context mContext;
    private Activity mActivity;

    //Menu:
    Menu optionsMenu;

    //Contexual Action Mode:
    Menu actionMenu;
    private ActionMode actionMode;

    //Lists
    private List<Sms> listConversations = new ArrayList<>();
    private List<Integer> selectionList = new ArrayList<>();

    //RecyclerView
    private ConversationRecyclerAdapter recyclerAdapter;
    private boolean allowContentObserver = true;
    private int totalToDelete = 0;
    private int deleted = 0;
    private int displayLimitInterval = 20;
    private int displayLimit;
    private boolean allowLazyLoad = true;

    //Permissions
    private static final int PERMISSIONS_REQUEST_CODE = 2020;

    //Content Observer
    private MyContentObserver myContentObserver;

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

        //RecyclerView initialization
        displayLimit = displayLimitInterval;

        RecyclerView recyclerView = findViewById(R.id.conversationRecyclerView);

        recyclerView.setHasFixedSize(true);

        recyclerAdapter = new ConversationRecyclerAdapter(listConversations,mContext);
        recyclerView.setAdapter(recyclerAdapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
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
                    getActiveContacts(displayLimit + 1, displayLimit + displayLimitInterval);
                    displayLimit += displayLimitInterval;
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);

        //Set up new message button
        FloatingActionButton btnNewMessage = findViewById(R.id.btnNewMessage);
        btnNewMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
            /*case R.id.delete_item:
                deleteMessages();
                return true;*/
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

    //Contexual Action Mode and item selection
    //Set up Contexual Action Mode
    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.contextual_action_menu, menu);

            actionMenu = menu;
            actionMenu.findItem(R.id.forward_item).setVisible(false);
            actionMenu.findItem(R.id.copy_item).setVisible(false);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete_item:
                    showDeleteConfirmationDialog();
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            endActionMode();
            actionMode = null;
        }
    };

    private void startActionMode(){
        if (actionMode == null) {
            // Start the CAB using the ActionMode.Callback defined above
            actionMode = mActivity.startActionMode(actionModeCallback);
        }
    }

    private void endActionMode(){
        if (actionMode != null){
            for (int position : selectionList){
                Sms message = listConversations.get(position);
                message.setSelected(false);
                recyclerAdapter.notifyItemChanged(position);
            }
            selectionList.clear();
        }
    }

    private void selectListItem(int position, View view, boolean longClick) {
        Sms message = listConversations.get(position);

        ConstraintLayout conversationLayout = view.findViewById(R.id.conversationLayout);
        TextView txtLastMessage = view.findViewById(R.id.txtLastMessage);
        TextView txtProfileName = view.findViewById(R.id.profileName);
        TextView txtTimestamp = view.findViewById(R.id.txtTimestamp);

        if (selectionList.contains(position)) {
            selectionList.remove((Integer) position);
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
            Intent intent = new Intent(MainActivity.this,MainActivitySMS.class);
            intent.putExtra("selectedAddress",message.getAddress());
            intent.putExtra("selectedThreadId",message.getThreadId());
            intent.putExtra("selectedName",message.getDisplayName());
            //To remove transition animation:
            //intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            this.startActivity(intent);
        }

        if (selectionList.size() > 0) {
            startActionMode();
            if (selectionList.size() > 1){
                actionMenu.findItem(R.id.delete_item).setIcon(R.drawable.ic_delete_sweep_white_24dp);
            } else {
                actionMenu.findItem(R.id.delete_item).setIcon(R.drawable.ic_delete_white_24dp);
            }
        } else {
            if (actionMode != null) {
                actionMode.finish();
            }
        }

        Collections.sort(selectionList, Collections.<Integer>reverseOrder());
    }

    private void deleteMessages() {
        allowContentObserver = false;
        ContentResolver contentResolver = getContentResolver();
        for(int i : selectionList) {
            Cursor c;
            c = getContentResolver().query(Uri.parse("content://sms"), null, "thread_id = ?", new String[]{listConversations.get(i).getThreadId()}, null);

            if (c != null && c.moveToFirst()) {
                totalToDelete = c.getCount();
                deleted = 0;
                do {
                    contentResolver.delete(Uri.parse("content://sms/" + c.getString(c.getColumnIndexOrThrow("_id"))), null, null);
                } while (c.moveToNext());
            }

            if (c != null) {
                c.close();
            }

            listConversations.remove(i);
            recyclerAdapter.notifyItemRemoved(i);
        }

        selectionList.clear();
        optionsMenu.findItem(R.id.delete_item).setVisible(false);
    }

    private void showDeleteConfirmationDialog() {

        final Dialog dialog = new Dialog(mActivity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setContentView(R.layout.delete_message_dialog);

        TextView title = dialog.findViewById(R.id.txtDeleteHeader);

        int numberToDelete = selectionList.size();
        if (numberToDelete > 1) {
            title.setText(getString(R.string.delete_selected_threads, numberToDelete));
        } else {
            title.setText(getString(R.string.delete_selected_thread));
        }

        final TextView cancelButton = dialog.findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        final TextView deleteButton = dialog.findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteMessages();
                dialog.dismiss();

                if (actionMode != null) {
                    actionMode.finish();
                }
            }
        });

        dialog.show();
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

        recyclerAdapter.notifyDataSetChanged();
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

        List<Sms> listNewConversations = new ArrayList<>();

        if(c != null && c.moveToFirst()) {
            int i = 0;
            do {
                if (i >= startDisplayLimit) {
                    listNewConversations.add(createSmsObject(c));
                }
            } while (c.moveToNext() && i++ < endDisplayLimit);
            allowLazyLoad = c.moveToNext();
        }

        //Update unread notifications icon
        int unreadCount;
        for (Sms sms:listNewConversations) {
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
        int positionStart = listConversations.size();
        listConversations.addAll(listNewConversations);
        recyclerAdapter.notifyItemRangeInserted(positionStart,listNewConversations.size());
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
        if (allowContentObserver){
            //Add RecyclerView Update to changed items
        } else {
            deleted++;
            if (deleted >= totalToDelete){
                allowContentObserver = true;
            }
        }
    }
}

