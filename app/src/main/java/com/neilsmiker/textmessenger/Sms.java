package com.neilsmiker.textmessenger;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public class Sms{
    private String _id;
    private String _address;
    private String _msg;
    private String _readState; //"0" for have not read sms and "1" for have read sms
    private String _time;
    private String _folderName;
    private String _threadId;

    //Package-private variables
    private boolean selected = false;
    private String displayName = null;

    public String getId(){
        return _id;
    }
    public String getAddress(){
        return _address;
    }
    public String getMsg(){
        return _msg;
    }
    public String getReadState(){
        return _readState;
    }
    public String getTime(){
        return _time;
    }
    public String getFolderName(){
        return _folderName;
    }
    public String getThreadId(){
        return _threadId;
    }


    public void setId(String id){
        _id = id;
    }
    public void setAddress(String address){
        _address = address;
    }
    public void setMsg(String msg){
        _msg = msg;
    }
    public void setReadState(String readState){
        _readState = readState;
    }
    public void setTime(String time){
        _time = time;
    }
    public void setFolderName(String folderName){
        _folderName = folderName;
    }
    public void setThreadId(String threadId){
        _threadId = threadId;
    }

    //Package-private
    void setSelected(boolean selected){
        this.selected = selected;
    }
    boolean isSelected(){
        return selected;
    }

    void setDisplayName(String displayName){
        this.displayName = displayName;
    }
    String getDisplayName(){
        if (displayName != null) {
            return displayName;
        } else {
            return _address;
        }
    }
}
