package com.neilsmiker.textmessenger;

class LabelData {
    private String _label;
    private String _value;

/*    public LabelData (String label, String value){
        _label = label;
        _value = value;
    }*/

    String getLabel(){
        return _label;
    }
    String getValue(){
        return _value;
    }

    void setLabel(String label){
        _label = label;
    }
    void setValue(String value){
        _value = value;
    }
}
