package com.mongoutils.sendgpsdata;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeServicePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
