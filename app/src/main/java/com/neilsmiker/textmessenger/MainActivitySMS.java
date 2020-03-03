package com.neilsmiker.textmessenger;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

public class MainActivitySMS extends AppCompatActivity implements MyContentObserver.ContentObserverCallbacks {

    //Context/Lifecycle
    private Context mContext;
    private Activity mActivity;

    //Final variables
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 160;
    private static final int RC_PHOTO_PICKER = 2;

    //Menus:
    Menu optionsMenu;
    Menu actionMenu;

    //Contexual Action Mode:
    private ActionMode actionMode;

    //UI
    private Toolbar myToolbar;
    private EditText mMessageEditText;
    private Button mSendButton, btnScrollToBottom;
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
    private String forwardedMessage;

    //Create new message
    private ConstraintLayout recipientLayout;
    private EditText recipientEditText;
    private TextView txtRecipients;
    private Button addRecipientButton;
    private List<LabelData> recipientsList = new ArrayList<>();
    private String recipientsTitleText;

    //Recipient List RecyclerView
    private RecyclerView recipientRecyclerView;
    private RecipientsRecyclerAdapter recipientsRecyclerAdapter;
    private FlexboxLayoutManager flexboxLayoutManager;

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
    private int currentScrollPosition = 0;
    private int currentScrollOffset = 0;
    private boolean scrollingToBottom = false;

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
    private boolean contentObserverRegistered = false;

    //Contacts Fragment
    ContactsFragment fragment;
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
            forwardedMessage = extras.getString("fwd");
        }

        //Set up the toolbar
        myToolbar = findViewById(R.id.my_toolbar);
        myToolbar.setTitle((selectedName != null) ? selectedName : getString(R.string.app_name));
        setSupportActionBar(myToolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Initialize references to views
        ImageButton mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);
        btnScrollToBottom = findViewById(R.id.btnScrollToBottom);
        inputLayout = findViewById(R.id.inputLayout);
        recipientLayout = findViewById(R.id.recipientLayout);

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
                    if (currentScrollPosition >= 5){
                        btnScrollToBottom.setVisibility(View.VISIBLE);
                    } else if (currentScrollPosition == 0){
                        btnScrollToBottom.setVisibility(View.GONE);
                    }
                    View child = linearLayoutManager.getChildAt(0);
                    if (child != null) {
                        currentScrollOffset = recyclerView.getBottom() - recyclerView.getTop() - child.getBottom();
                    }
                    //Log.i(TAG,"Current position: " + currentScrollPosition + ", offset: " + currentScrollOffset);
                } else if (!scrollingToBottom) {
                    //Called when soft keyboard opens/closes. Set the scroll position via the Runnable
                    handler.post(r);
                } else {
                    scrollingToBottom = false;
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);

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

                if (newMessage){
                    myToolbar.setTitle((recipientsTitleText != null) ? recipientsTitleText : getString(R.string.new_message));
                    txtRecipients.setVisibility(View.GONE);
                }
            }
        });

        btnScrollToBottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrollingToBottom = true;
                linearLayoutManager.scrollToPosition(0);
                currentScrollPosition = 0;
                currentScrollOffset = 0;
                recyclerView.stopScroll();
                btnScrollToBottom.setVisibility(View.GONE);
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
        recipientRecyclerView = findViewById(R.id.recipientRecyclerView);

        recipientsRecyclerAdapter = new RecipientsRecyclerAdapter(recipientsList, this);
        recipientRecyclerView.setAdapter(recipientsRecyclerAdapter);

        flexboxLayoutManager = new FlexboxLayoutManager(this);
        flexboxLayoutManager.setFlexDirection(FlexDirection.ROW);
        flexboxLayoutManager.setJustifyContent(JustifyContent.FLEX_START);
        recipientRecyclerView.setLayoutManager(flexboxLayoutManager);

        if (newMessage) {
            recipientLayout.setVisibility(View.VISIBLE);
            inputLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            myToolbar.setTitle("New conversation");

            addRecipientButton = findViewById(R.id.addRecipientButton);
            addRecipientButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //selectedAddress = recipientEditText.getText().toString();
                    //selectedName = getContactName(selectedAddress, mContext);

                    String number = recipientEditText.getText().toString();
                    String name = getContactName(number, mContext);
                    insertRecipientNumber(number,name);
                    recipientEditText.setText("");


                    //myToolbar.setTitle(selectedName);
                    //initializeSmsList();

                    /*recyclerView.setVisibility(View.VISIBLE);
                    inputLayout.setVisibility(View.VISIBLE);
                    recipientLayout.setVisibility(View.GONE);
                    fragmentContainer.setVisibility(View.GONE);*/

                    //Set focus to Message EditText and show the keyboard if not already active
                    /*mMessageEditText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mMessageEditText, InputMethodManager.SHOW_IMPLICIT);*/
                }
            });

            txtRecipients = findViewById(R.id.txtRecipients);
            txtRecipients.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayRecipientView(true);
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

                    fragment.filterContacts(recipientEditText.getText().toString());
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });

            recipientEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    if (hasFocus) {
                        displayRecipientView(true);
                    }
                }
            });

            mMessageEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    if (hasFocus) {
                        displayRecipientView(false);
                    }
                }
            });

            //Initialize Contacts Fragment:
            fragmentContainer.setVisibility(View.VISIBLE);
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            fragment = new ContactsFragment();
            fragmentTransaction.add(R.id.fragment_container, fragment);
            fragmentTransaction.commit();
        }

        //Content Observer Initialization:
        Handler handler = new Handler();
        myContentObserver = new MyContentObserver(handler);

        if (!newMessage) {
            initializeSmsList();
            if (selectedName != null){
                recipientsTitleText = selectedName;
            }
        } else {
            if (forwardedMessage != null){
                mMessageEditText.setText(forwardedMessage);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //TODO display image attached to current message and send via MMS on send button
        /*if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
        }*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        optionsMenu = menu;

        if (selectedAddress != null) {
            optionsMenu.findItem(R.id.call_item).setVisible(true);
            optionsMenu.findItem(R.id.contact_item).setVisible(true);
            optionsMenu.findItem(R.id.delete_item).setVisible(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.call_item:
                if (selectedAddress != null && recipientsList.size() <= 1){
                    //No permissions required for ACTION_DIAL. Calling directly from app using ACTION_CALL requires permission grants (see Manifest)
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + selectedAddress));
                    startActivity(intent);
                }
                return true;
            case R.id.contact_item:
                if (selectedAddress != null && recipientsList.size() <= 1){
                    String contactID = getContactId(selectedAddress,mContext);
                    if (!contactID.equals("")) {
                        //No permissions required for ACTION_DIAL. Calling directly from app using ACTION_CALL requires permission grants (see Manifest)
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactID);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                }
                return true;
            case R.id.delete_item:
                showDeleteConfirmationDialog(true);
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
            registerContentObserver();
        }

        //TODO Temporarily removing call to request set default for testing purposes
        //requestSetDefault();
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterContentObserver();
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
                case R.id.forward_item:
                    Intent intent = new Intent(MainActivitySMS.this,MainActivitySMS.class);
                    intent.putExtra("newMessage",true);
                    intent.putExtra("fwd","Fwd:" + listMessages.get(selectionList.get(0)).getMsg());
                    //To remove transition animation:
                    //intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    /*if (actionMode != null) {
                        actionMode.finish();
                    }*/
                    return true;
                case R.id.copy_item:
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(getString(R.string.message_text), listMessages.get(selectionList.get(0)).getMsg());
                    clipboard.setPrimaryClip(clip);
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                    return true;
                case R.id.delete_item:
                    showDeleteConfirmationDialog(false);
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
                Sms message = listMessages.get(position);
                message.setSelected(false);
                recyclerAdapter.notifyItemChanged(position);
            }
            selectionList.clear();
        }
    }

    private void selectListItem(int position, View view, boolean longClick) {
        TextView tv = view.findViewById(R.id.messageTextView);
        LinearLayout textBubble = view.findViewById(R.id.textBubble);

        Sms message = listMessages.get(position);
        boolean isUser = message.getFolderName().equals("sent");
        if (selectionList.contains(position)) {
            selectionList.remove((Integer) position);
            message.setSelected(false);
            if (isUser) {
                textBubble.setBackground(getDrawable(R.drawable.text_bubble_user));
            } else {
                textBubble.setBackground(getDrawable(R.drawable.text_bubble_other));
            }
            tv.setTextColor(Color.parseColor("#000000"));
        } else if (selectionList.size() > 0 || longClick) {
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
            startActionMode();
            if (selectionList.size() > 1){
                actionMenu.findItem(R.id.copy_item).setVisible(false);
                actionMenu.findItem(R.id.forward_item).setVisible(false);
                actionMenu.findItem(R.id.delete_item).setIcon(R.drawable.ic_delete_sweep_white_24dp);
            } else {
                actionMenu.findItem(R.id.copy_item).setVisible(true);
                actionMenu.findItem(R.id.forward_item).setVisible(true);
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

    private void deleteMessageThread() {
        //TODO add error handling
        if (listMessages.size() > 0) {
            String threadId = listMessages.get(0).getThreadId();
            if (threadId != null) {
                getContentResolver().delete(Uri.parse("content://sms/conversations/" + threadId), null, null);
                Intent intent = new Intent(MainActivitySMS.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
    }

    private void showDeleteConfirmationDialog(final boolean deleteThread) {

        final Dialog dialog = new Dialog(mActivity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setContentView(R.layout.delete_message_dialog);

        TextView title = dialog.findViewById(R.id.txtDeleteHeader);

        if (deleteThread){
            title.setText(getString(R.string.delete_entire_thread));
        } else {
            int numberToDelete = selectionList.size();
            if (numberToDelete > 1) {
                title.setText(getString(R.string.delete_selected_messages, numberToDelete));
            } else {
                title.setText(getString(R.string.delete_selected_message));
            }
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
                if (deleteThread){
                    deleteMessageThread();
                } else {
                    deleteMessages();
                }
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
            enableSmsButton();
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
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
            // TODO Also add listener to check if message sends correctly and address errors. Display progressbar until callback triggers?
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

    //Content Observer
    private void registerContentObserver(){
        if (myContentObserver != null) {
            getContentResolver().registerContentObserver(
                    Uri.parse("content://sms/"), true,
                    myContentObserver);
            myContentObserver.setCallbacks(MainActivitySMS.this);
            contentObserverRegistered = true;
        }
    }

    private void unregisterContentObserver(){
        if (myContentObserver != null) {
            getContentResolver().unregisterContentObserver(myContentObserver);
            myContentObserver.setCallbacks(null);
            contentObserverRegistered = false;
        }
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

    public String getContactId(final String phoneNumber, Context context)
    {
        if (checkPermission()) {
            Uri uri;

            String[] projection;
            if (phoneNumber.contains("@")){
                uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
                projection = new String[]{ContactsContract.CommonDataKinds.Email.CONTACT_ID};
            } else {
                uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
                projection = new String[]{ContactsContract.PhoneLookup._ID};
            }

            String contactId = "";
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    contactId = cursor.getString(0);
                }
                cursor.close();
            }

            return contactId;
        }
        return null;
    }

    public void insertRecipientNumber(String number, String name){

        final LabelData newContact = new LabelData();
        newContact.setLabel(number);
        newContact.setValue(name);

        for (LabelData recipient : recipientsList){
            if (recipient.getLabel().equals(number)){
                Toast.makeText(this,getString(R.string.duplicate_recipient),Toast.LENGTH_SHORT).show();
                return;
            }
        }

        recipientsList.add(newContact);
        recipientsRecyclerAdapter.notifyDataSetChanged();
        flexboxLayoutManager.smoothScrollToPosition(recipientRecyclerView, null, recipientsRecyclerAdapter.getItemCount());

        StringBuilder recipientNames = new StringBuilder();
        int i = 0;
        for (LabelData contact : recipientsList){
            if (i == 0){
                recipientNames.append(contact.getLabel());
            } else {
                recipientNames.append(",").append(contact.getLabel());
            }
            i++;
        }
        selectedAddress = recipientNames.toString();
        initializeSmsList();
        recipientRecyclerView.setVisibility(View.VISIBLE);
        inputLayout.setVisibility(View.VISIBLE);

        if (!contentObserverRegistered){
            registerContentObserver();
        }

        if (recipientsList.size() == 1) {
            optionsMenu.findItem(R.id.call_item).setVisible(true);
            optionsMenu.findItem(R.id.contact_item).setVisible(true);
            optionsMenu.findItem(R.id.delete_item).setVisible(true);
        } else {
            optionsMenu.findItem(R.id.call_item).setVisible(false);
            optionsMenu.findItem(R.id.contact_item).setVisible(false);
            optionsMenu.findItem(R.id.delete_item).setVisible(true);
        }
    }

    public void hideMessageMenuItems(boolean hideItems){
        if (hideItems) {
            optionsMenu.findItem(R.id.call_item).setVisible(false);
            optionsMenu.findItem(R.id.contact_item).setVisible(false);
            optionsMenu.findItem(R.id.delete_item).setVisible(false);
        } else {
            optionsMenu.findItem(R.id.call_item).setVisible(true);
            optionsMenu.findItem(R.id.contact_item).setVisible(true);
            optionsMenu.findItem(R.id.delete_item).setVisible(true);
        }
    }

    public void openContactCard(String selectedAddress){
        if (selectedAddress != null){
            String contactID = getContactId(selectedAddress,mContext);
            if (!contactID.equals("")) {
                //No permissions required for ACTION_DIAL. Calling directly from app using ACTION_CALL requires permission grants (see Manifest)
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactID);
                intent.setData(uri);
                startActivity(intent);
            }
        }
    }

    private void displayRecipientView(boolean display){
        if (display){
            recipientLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            //txtRecipients.setText(recipientSpannableBuilder);
            txtRecipients.setVisibility(View.GONE);
            recipientRecyclerView.setVisibility(View.VISIBLE);

            View v = getCurrentFocus();
            if (v != null && v != recipientEditText) {
                recipientEditText.requestFocus();
                /*InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);*/
            }
        } else {
            recipientLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
            recipientRecyclerView.setVisibility(View.GONE);
            txtRecipients.setVisibility(View.VISIBLE);
            int recipientCount = recipientsList.size();

            if (recipientCount > 0){
                if (recipientCount > 2) {
                    recipientsTitleText = getString(R.string.recipients_title,recipientsList.get(0).getValue(),recipientCount - 1);
                } else if (recipientCount == 2){
                    recipientsTitleText = getString(R.string.recipient_title,recipientsList.get(0).getValue());
                } else {
                    recipientsTitleText = recipientsList.get(0).getValue();
                }

                txtRecipients.setText(recipientsTitleText);
            }

            if (recipientCount > 2) {
                txtRecipients.setText(getString(R.string.recipients_title,
                        recipientsList.get(0).getValue(),
                        recipientCount - 1));
            } else if (recipientCount == 2){
                txtRecipients.setText(getString(R.string.recipient_title,recipientsList.get(0).getValue()));
            } else if (recipientCount == 1){
                txtRecipients.setText(recipientsList.get(0).getValue());
            }
        }
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