package com.neilsmiker.textmessenger;

import java.util.List;

public class Contact {
    private String _id;
    private String _name;
    private List<LabelData> _phone;
    private List<LabelData> _email;

    public String getId(){
        return _id;
    }
    public String getName(){
        return _name;
    }
    List<LabelData> getPhone(){
        return _phone;
    }
    List<LabelData> getEmail(){
        return _email;
    }

    public void setId(String id){
        _id = id;
    }
    public void setName(String name){
        _name = name;
    }
    void setPhone(List<LabelData> phone){
        _phone = phone;
    }
    void setEmail(List<LabelData> email){
        _email = email;
    }

    /*static Comparator<Contact> ContactComparator = new Comparator<Contact>() {

        public int compare(Contact c1, Contact c2) {
            String Contact1 = c1.getName().toUpperCase();
            String Contact2 = c2.getName().toUpperCase();

            //ascending order
            return Contact1.compareTo(Contact2);

            //descending order
            //return Contact2.compareTo(Contact1);
        }};*/
}
