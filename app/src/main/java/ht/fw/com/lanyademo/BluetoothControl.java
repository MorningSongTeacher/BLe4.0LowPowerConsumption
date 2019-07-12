package ht.fw.com.lanyademo;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


public class BluetoothControl extends FragmentActivity implements View.OnClickListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SELECT_DEVICE = 2;

    private boolean link;
    private BluetoothGatt mBtGatt;  //中央使用和处理数据；
    private BluetoothGattCallback mGattCallback;    //中央的回调
    private BluetoothDevice mDevice = null;
    public BluetoothAdapter mBtAdapter = null;
    private String bleConnectName, deviceAddress = "E5:12:86:45:93:3F";     //默认一个蓝牙地址
    private HashMap<String, BluetoothGatt> hashBluetoothGatt = new HashMap<String, BluetoothGatt>();

    public static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID WT_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RD_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    private SharedPreferencesHelper sp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_bluetooth_control);
        sp = new SharedPreferencesHelper(getApplicationContext());
        findViewById(R.id.ib_list).setOnClickListener(this);
        //蓝牙连接
        initBle();
    }

    private void needConnectName() {
        AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothControl.this);
        builder.setTitle(R.string.prompt);
        builder.setMessage(R.string.please_open_bluetooth_note)
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (mBtAdapter.isEnabled()) {
                            Log.e("aaa", "蓝牙已经开始启用了");
                            if (sp.getSharedPreference("BleAddress", "") != null && sp.getSharedPreference("BleAddress", "").toString().length() > 0) {
                                deviceAddress = (String) sp.getSharedPreference("BleAddress", "");
                                mDevice = mBtAdapter.getRemoteDevice(deviceAddress);
                                mBtGatt = mDevice.connectGatt(BluetoothControl.this, false, mGattCallback);
                                hashBluetoothGatt.put(deviceAddress, mBtGatt);
                                Log.e("aaa", "正在连接范围内的该蓝牙");
                            }
                        }
                        dialog.dismiss();
                    }
                });
        builder.create();
        builder.show();
    }

    private void useAppNote() {
        AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothControl.this);
        builder.setTitle(R.string.prompt);
        builder.setMessage(R.string.useApp_note)
                .setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        sp.put("useAppNote", true);
                        dialog.dismiss();
                    }
                });
        builder.create();
        builder.show();
    }


    @Override
    protected void onResume() {
        super.onResume();
        hideInput();
        if (!(boolean) sp.getSharedPreference("useAppNote", false)) {
            useAppNote();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_list:    //蓝牙列表
                mBtAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBtAdapter == null) {
                    Toast.makeText(this, getResources().getString(R.string.ble_not_supported), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (!mBtAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    Intent newIntent = new Intent(BluetoothControl.this, BleList.class);
                    startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_DEVICE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    try {
                        bleConnectName = data.getStringExtra("bleConnectName");
                        deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                        reconnectHandler.sendEmptyMessage(0);
                        Log.e("aaa", "状态code:" + resultCode + "; 地址是:" + deviceAddress);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    Intent newIntent = new Intent(BluetoothControl
                            .this, BleList.class);
                    startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                }
                break;
            default:
                Log.e("aaa", "错误code:" + resultCode);
                break;
        }
    }

    private Handler reconnectHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            closeBleGatt();
            startProgressDialog(BluetoothControl.this.getResources().getString(R.string.connecting_bluetooth_device));// 显示进度框
            mDevice = mBtAdapter.getRemoteDevice(deviceAddress);
            if (hashBluetoothGatt.containsKey(deviceAddress)) {     //重新连接蓝牙
                mBtGatt = hashBluetoothGatt.get(deviceAddress);
                boolean isConnect = mBtGatt.connect();
                Log.e("aaa", "重新连接蓝牙的状态为:" + isConnect + ",地址是:" + deviceAddress);
            } else {        //发起蓝牙连接
                mBtGatt = mDevice.connectGatt(BluetoothControl.this, false, mGattCallback);
                hashBluetoothGatt.put(deviceAddress, mBtGatt);
            }
        }
    };


    private Handler linkedHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                super.handleMessage(msg);
                if (msg.what == 1) {
                    if (BluetoothControl.this.link)
                        ((ImageView) BluetoothControl.this.findViewById(R.id.imageView_online)).setImageResource(R.drawable.ic_bluetooth_normal);
                } else {
                    ((ImageView) BluetoothControl.this.findViewById(R.id.imageView_online)).setImageResource(R.drawable.ic_bluetooth_gray);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private Handler receiveHander = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String receiveData = (String) msg.obj;
            int flag = msg.what;
            switch (flag) {
                case 4:     //蓝牙断开
                    Toast.makeText(BluetoothControl.this, getResources().getString(R.string.bluetooth_disconnected), Toast.LENGTH_SHORT).show();
                    break;
                case 5:     //蓝牙连接
                    Toast.makeText(BluetoothControl.this, getResources().getString(R.string.connect_to_bluetooth), Toast.LENGTH_SHORT).show();
                    break;
                case 6:
                    //蓝牙4.0低功耗蓝牙, 主要是应用于数据频率高,传输量小的传输,故只能传输文本消息
                    //蓝牙4.0传统蓝牙, 主要是用于传输数据量大的传输,可传输音频视频,文件等
                    String text = "我想给蓝牙设备发送的文本消息";
                    writeRXCharacteristic(text);
                    break;
            }
        }
    };

    private void setLinked(boolean link) {
        this.link = link;
        if (link) {
            linkedHandler.sendEmptyMessage(1);
        } else {
            linkedHandler.sendEmptyMessage(0);
        }
    }

    /**
     * 隐藏键盘
     */
    protected void hideInput() {
        InputMethodManager imm = (InputMethodManager) BluetoothControl.this.getSystemService(Context.INPUT_METHOD_SERVICE);
        // 隐藏软键盘
        imm.hideSoftInputFromWindow(BluetoothControl.this.getWindow().getDecorView().getWindowToken(), 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeBleGatt();
        unregisterReceiver(mReceiver);
    }

    private void initBle() {
        this.setLinked(false);
        mGattCallback = new BluetoothGattCallback() {
            //获取连接状态方法，BLE设备连接上或断开时，会调用到此方
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Log.e("aaa", "进入onConnectionStateChange方法" + ",状态码是:" + status + ",新状态是:" + newState);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {     //蓝牙连接状态
                        gatt.discoverServices();    //连接成功调用服务
                        Log.e("aaa", "连接到蓝牙");
                        Message msg = receiveHander.obtainMessage();
                        msg.what = 5;
                        receiveHander.sendMessage(msg);
                        setLinked(true);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {   //断开蓝牙连接状态
                        Log.e("aaa", "蓝牙已断开");
                        closeBleGatt();
                        Message msg = receiveHander.obtainMessage();
                        msg.what = 4;
                        receiveHander.sendMessage(msg);
                        setLinked(false);
                    }
                } else {
                    Log.e("aaa", "蓝牙已断开");
                    closeBleGatt();
                    Message msg = receiveHander.obtainMessage();
                    msg.what = 4;
                    receiveHander.sendMessage(msg);
                    setLinked(false);
                }
                stopProgressDialog();
            }

            //成功发现设备的services时，调用此方法
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Log.e("aaa", "进入蓝牙服务:" + gatt.getServices().toString() + ", 状态:" + status);

                List<BluetoothGattService> services = gatt.getServices();
                for (int i = 0; i < services.size(); i++) {
                    Log.e("aaa", "所有的服务里UUID:" + services.get(i).getUuid());
                    for (int j = 0; j < services.get(i).getCharacteristics().size(); j++) {
                        Log.e("aaa", "服务里Characteristics的UUID和值:" +
                                services.get(i).getCharacteristics().get(j).getUuid()
                                + ",值是:" + services.get(i).getCharacteristics().get(j).getValue()
                        );
                        for (int k = 0; k < services.get(i).getCharacteristics().get(j).getDescriptors().size(); k++) {
                            Log.e("aaa", "Characteristicsz里最坑的UUID:" + services.get(i).getCharacteristics().get(j).getDescriptors().get(k).getUuid());
                        }
                    }
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService RxService = gatt.getService(SERVICE_UUID);
                    Log.e("aaa", "服务:" + RxService.getUuid());
                    if (RxService == null) {
                        Log.e("aaa", "Rx服务未找到!");
                        return;
                    }
                    BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(RD_UUID);
                    Log.e("aaa", "特征:" + TxChar.getUuid());
                    if (TxChar == null) {
                        Log.e("aaa", "Rx charateristic 没有找到!");
                        return;
                    }
                    //设置characteristic的通知，触发bluetoothGatt.onCharacteristicWrite()事件。
                    gatt.setCharacteristicNotification(TxChar, true);

                    BluetoothGattDescriptor descriptor = TxChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);

                    Log.e("aaa", "设备连上了");
                    setLinked(true);

                    final Timer timer = new Timer();
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            //                            writeRXCharacteristic("*HQ,S2,hand#");
                            timer.cancel();
                        }
                    };
                    timer.schedule(task, 100);
                } else {
                    Log.w("aaa", "服务发现收到: " + status);
                }
            }

            //读写characteristic时会调用到以下方法
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.e("aaa", gatt.getDevice().getName() + "进入读取蓝牙 " + Utils.byteToHex(characteristic.getValue()));
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (RD_UUID.equals(characteristic.getUuid())) {
                        String text = Utils.byteToHex(characteristic.getValue());
                        Log.e("aaa", "接收Read:" + text);
                        Message msg = receiveHander.obtainMessage();
                        msg.obj = text;
                        msg.what = 3;
                        receiveHander.sendMessage(msg);
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.e("aaa", "进入onCharacteristicChanged方法");
                if (RD_UUID.equals(characteristic.getUuid())) {
                    String text = Utils.byteToHex(characteristic.getValue());
                    Log.e("aaa", "接收的蓝牙状态数据text转换为:" + text);
                    Message msg = receiveHander.obtainMessage();
                    msg.obj = text;
                    String a = text.substring(4, 6);
                    Log.e("aaa", "接收的指令是:" + a);
                    if ("10".equals(a)) {
                        msg.what = 10;
                    } else if ("11".equals(a)) {
                        msg.what = 11;
                    } else if ("12".equals(a)) {
                        msg.what = 12;
                    } else if ("13".equals(a)) {
                        msg.what = 13;
                    } else if ("14".equals(a)) {
                        msg.what = 14;
                    } else {
                        msg.what = -1;
                    }
                    receiveHander.sendMessage(msg);
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.e("aaa", "进入onCharacteristicWrite方法");
                if (status == BluetoothGatt.GATT_SUCCESS) {//写入成功
                    Log.e("aaa", "写入蓝牙状态:写入成功");
                } else if (status == BluetoothGatt.GATT_FAILURE) {//写入失败
                    Log.e("aaa", "写入蓝牙状态:写入失败");
                } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {//没权限
                    Log.e("aaa", "写入蓝牙状态:没权限");
                }
                if (WT_UUID.equals(characteristic.getUuid())) {
                    String text = Utils.byteToHex(characteristic.getValue());
                    Log.e("aaa", "WriteChanged:" + text);
                    Message msg = receiveHander.obtainMessage();
                    msg.obj = text;
                    msg.what = 2;
                    receiveHander.sendMessage(msg);
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                Log.e("aaa", "进入了onDescriptorWrite方法, 状态码是:" + status);
            }

        };
        //获取本地蓝牙设备
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceAddress = (String) sp.getSharedPreference("BleAddress", "");
        if (mBtAdapter == null) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (mBtAdapter.isEnabled()) {
            Log.e("aaa", "蓝牙已经启用了");
            if (deviceAddress != null && deviceAddress.length() > 0) {
                startProgressDialog(BluetoothControl.this.getResources().getString(R.string.connecting_bluetooth_device));
                mDevice = mBtAdapter.getRemoteDevice(deviceAddress);
                //如果设置自动连接，则安卓底层会不停的跟对应Mac地址的设备反复连接，连接效率会变得很慢，而且容易发送阻塞，
                // 导致后边的设备一直在等前一个设备连接成功的回调，蓝牙设备的连接一定要分开逐个连接，尽量不要形成线程阻碍。
                //第二个参数为false速度会很快,为true是自动连接, 比较慢,都是创建一个后台服务,false会找到设备直接连接,true是所有设备查找完后再连接
                mBtGatt = mDevice.connectGatt(this, false, mGattCallback);
                //                mBtGatt = mDevice.connectGatt(this, true, mGattCallback);
                if (mBtGatt.connect()) {
                    hashBluetoothGatt.put(deviceAddress, mBtGatt);
                }
                //                                mBtGatt.requestConnectionPriority();  //加快蓝牙的连接过程（connect 到 discoverServices）,但是在比较旧的机型(类似华为)发现，在连续调用几次后，后面的蓝牙变得连接非常不稳定.
                Log.e("aaa", "正在连接该蓝牙:" + (String) deviceAddress + "状态是:" + mBtGatt.connect());

            }
        }
        registerReceiver(mReceiver, makeFilter());
    }

    private IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return filter;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    switch (blueState) {
                        case BluetoothAdapter.STATE_TURNING_ON: //在打开蓝牙之前先调用
                            break;
                        case BluetoothAdapter.STATE_ON:     //打开蓝牙
                            Log.e("aaa", "蓝牙重新启用了");
                            closeBleGatt();
                            startProgressDialog(BluetoothControl.this.getResources().getString(R.string.connecting_bluetooth_device));// 显示进度框
                            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
                            //这里是个深坑,在华为部分手机上,关闭蓝牙,在打开蓝牙会无法连接,出现133状态错误,这个是因为华为的蓝牙底层需要先扫描设备才能找到设备
                            //(华为的底层可能每次扫描会改变蓝牙状态中的某个值,可以将下面的延迟10秒连接蓝牙的操作放在扫描完成里)
                            mBtAdapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
                                @Override
                                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

                                }
                            });

                            mDevice = mBtAdapter.getRemoteDevice(deviceAddress);
                            if (hashBluetoothGatt.containsKey(deviceAddress)) {     //重新连接蓝牙
                                mBtGatt = hashBluetoothGatt.get(deviceAddress);
                                boolean isConnect = mBtGatt.connect();
                                Log.e("aaa", "蓝牙重新连接蓝牙的状态为:" + isConnect + ",地址是:" + deviceAddress);
                            } else {        //发起蓝牙连接
                                mBtGatt = mDevice.connectGatt(BluetoothControl.this, false, mGattCallback);
                                hashBluetoothGatt.put(deviceAddress, mBtGatt);
                            }
                            if (mBtAdapter.isEnabled()) {
                                if (deviceAddress != null && deviceAddress.length() > 0) {
                                    new Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            mDevice = mBtAdapter.getRemoteDevice(deviceAddress);
                                            mBtGatt = hashBluetoothGatt.get(deviceAddress);
                                            if (mBtGatt.connect()) {
                                                hashBluetoothGatt.put(deviceAddress, mBtGatt);
                                            }
                                            mBtGatt = mDevice.connectGatt(BluetoothControl.this, false, mGattCallback);
                                            Log.e("aaa", "连接蓝牙的地址是:" + deviceAddress + "状态是" + mBtGatt.connect());
                                        }
                                    }, 10 * 1000);
                                }
                            }
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:    //在关闭蓝牙之前先调用
                            break;
                        case BluetoothAdapter.STATE_OFF:    //关闭蓝牙
                            Log.e("aaa", "蓝牙被关闭了");
                            //同时断开将蓝牙相关都重置为null
                            closeBleGatt();
                            mDevice = null;
                            mBtAdapter = null;
                            setLinked(false);
                            break;
                    }
                    break;
            }
        }
    };

    private MProgressDialog mProgressDialog = null;

    private void startProgressDialog(String dialog) {
        try {
            if (mProgressDialog == null) {
                mProgressDialog = MProgressDialog.createDialog(BluetoothControl.this);
                mProgressDialog.setMessage(dialog);
                mProgressDialog.setCancelable(true);    //允许点击屏幕之外关闭进度框
            }
            mProgressDialog.show();
        } catch (WindowManager.BadTokenException e) {
        }
    }

    private void stopProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private boolean writeRXCharacteristic(String text) {
        BluetoothGattService service = mBtGatt.getService(SERVICE_UUID);
        if (service == null) {
            Log.e("aaa", "Rx service not found!");
            return false;
        }
        BluetoothGattCharacteristic RxChar = service.getCharacteristic(WT_UUID);
        if (RxChar == null) {
            Log.e("aaa", "Tx charateristic not found!");
            return false;
        }
        Log.e("aaa", "下发给蓝牙设备的文本字符是:" + text);
        RxChar.setValue(Utils.hexToByte(text));     //一般会根据双方的协议的比如16进制的格式来发送指令消息,
        //        RxChar.setValue(text.getBytes());    //如果没有相关协议要求不同的进制格式,就直接发送byte[]格式, 两边都能对格式进行定义,只要另一半来做转换就行
        boolean status = mBtGatt.writeCharacteristic(RxChar);
        Log.e("aaa", "蓝牙写入状态=" + status);
        return status;
    }

    /**
     * 关闭GATT
     */
    public void closeBleGatt() {
        if (mBtGatt != null) {
            refreshDeviceCache();   //此方法需要在BluetoothGatt的close方法之前调用
            mBtGatt.disconnect();
            mBtGatt.close();
            mBtGatt = null;
        }
    }

    /**
     * 清理蓝牙缓存
     */
    public boolean refreshDeviceCache() {
        if (mBtGatt != null) {
            try {
                Method localMethod = mBtGatt.getClass().getMethod("refresh", new Class[0]);
                if (localMethod != null) {
                    boolean bool = ((Boolean) localMethod.invoke(
                            mBtGatt, new Object[0])).booleanValue();
                    return bool;
                }
            } catch (Exception localException) {
                Log.e("aaa", "刷新蓝牙缓存出现异常");
            }
        }
        return false;
    }

}
