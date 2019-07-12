package ht.fw.com.lanyademo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@TargetApi(21)
public class BleList extends Activity {

	private BluetoothAdapter mBluetoothAdapter;

	// private BluetoothAdapter mBtAdapter;
	private TextView mEmptyList;
	public static final String TAG = "BleList";

	List<BluetoothDevice> deviceList;
	private DeviceAdapter deviceAdapter;
	private ServiceConnection onService = null;
	Map<String, Integer> devRssiValues;
	private static final long SCAN_PERIOD = 10000; // 10 seconds
	private Handler mHandler;
	private boolean mScanning;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.bledevice_list);
		this.findViewById(R.id.button_back).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				BleList.this.finish();
			}

		});
		// android.view.WindowManager.LayoutParams layoutParams =
		// this.getWindow()
		// .getAttributes();
		// layoutParams.gravity = Gravity.TOP;
		// layoutParams.y = 200;
		mHandler = new Handler();
		// Use this check to determine whether BLE is supported on the device.
		// Then you can
		// selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		populateList();
		mEmptyList = (TextView) findViewById(R.id.empty);
		Button cancelButton = (Button) findViewById(R.id.btn_cancel);
		cancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mScanning == false) {
					scanLeDevice(true);
				} else {
					BleList.this.finish();
				}
			}
		});

	}

	private void populateList() {
		deviceList = new ArrayList<BluetoothDevice>();
		deviceAdapter = new DeviceAdapter(this, deviceList);
		devRssiValues = new HashMap<String, Integer>();

		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(deviceAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);

		scanLeDevice(true);
	}

	private void scanLeDevice(final boolean enable) {
		final Button cancelButton = (Button) findViewById(R.id.btn_cancel);
		if (enable) {
//			mHandler.postDelayed(new Runnable() {
//				@Override
//				public void run() {
//					mScanning = false;
//					stopScan();
//					cancelButton.setText(R.string.scan);
//
//				}
//			}, SCAN_PERIOD);

			mScanning = true;
			startScan();
			cancelButton.setText(R.string.cancel);
		} else {
			mScanning = false;
			stopScan();
			cancelButton.setText(R.string.scan);
		}

	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							addDevice(device, rssi);
						}
					});

				}
			});
		}
	};
//	private ScanCallback mLeScanCallback2 = new ScanCallback() {
//
//		@Override
//		public void onScanResult(int callbackType, ScanResult result) {
//			super.onScanResult(callbackType, result);
//			addDevice(result.getDevice(), result.getRssi());
//		}
//
//		@Override
//		public void onScanFailed(int errorCode) {
//			Log.i(TAG, "onScanFailed" + errorCode);
//
//		}
//	};

	private void startScan() {
//		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			mBluetoothAdapter.startLeScan(mLeScanCallback);
//		} else {
//			mBluetoothAdapter.getBluetoothLeScanner().startScan(mLeScanCallback2);
//		}

	}

	private void stopScan() {
//		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
		if (mBluetoothAdapter != null || mLeScanCallback != null) {
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}
//		} else {
//			mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback2);
//		}
	}

	private void addDevice(BluetoothDevice device, int rssi) {
		boolean deviceFound = false;

		for (BluetoothDevice listDev : deviceList) {
			if (listDev.getAddress().equals(device.getAddress())) {
				deviceFound = true;
				break;
			}
		}

		devRssiValues.put(device.getAddress(), rssi);
		if (!deviceFound) {
			deviceList.add(device);
			mEmptyList.setVisibility(View.GONE);

			deviceAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
	}

	@Override
	public void onStop() {
		super.onStop();
		stopScan();

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopScan();

	}

	LinearLayout inearLayout;
	EditText input1;
	AlertDialog.Builder builder;

	private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
			inearLayout = new LinearLayout(BleList.this);
			inearLayout.setOrientation(LinearLayout.VERTICAL);
			input1 = new EditText(BleList.this);
			input1.setFocusable(true);
			input1.setInputType(EditorInfo.TYPE_CLASS_NUMBER);
			input1.setHint(getResources().getString(R.string.please_enter_device_ID));      //用于蓝牙数据传输的协议凭证, 如果一开始没有, 可以直接取消弹框,写回调中内容即可
			input1.setSingleLine();
			input1.setMaxLines(10);
			inearLayout.addView(input1);
			builder = new AlertDialog.Builder(BleList.this);
			builder.setTitle(R.string.bluetooth_naming).setView(inearLayout).setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int i) {
					dialog.dismiss();
				}
			}).setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
//								BluetoothDevice device = deviceList.get(position);
					stopScan();
					hideInput();
					Bundle b = new Bundle();
					b.putString("bleConnectName", deviceList.get(position).getName());
					b.putString(BluetoothDevice.EXTRA_DEVICE, deviceList.get(position).getAddress());

					SharedPreferencesHelper sp = new SharedPreferencesHelper(getApplicationContext());
					sp.put("DeviceID",input1.getText().toString().trim());      //这个设备ID一般是由蓝牙设备(如手表)厂家或商家提供, 用作数据传输的协议凭证
					sp.put("BleName",deviceList.get(position).getName());       //连接蓝牙设备的名称
					sp.put("BleAddress",deviceList.get(position).getAddress());       //格式如: "E5:12:86:45:93:3F"

					Intent result = new Intent();
					result.putExtras(b);
					setResult(Activity.RESULT_OK, result);
					finish();
				}
			});
			builder.create();
			builder.show();

		}
	};

	protected void onPause() {
		super.onPause();
		scanLeDevice(false);
	}

	class DeviceAdapter extends BaseAdapter {
		Context context;
		List<BluetoothDevice> devices;
		LayoutInflater inflater;

		public DeviceAdapter(Context context, List<BluetoothDevice> devices) {
			this.context = context;
			inflater = LayoutInflater.from(context);
			this.devices = devices;
		}

		@Override
		public int getCount() {
			return devices.size();
		}

		@Override
		public Object getItem(int position) {
			return devices.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewGroup vg;

			if (convertView != null) {
				vg = (ViewGroup) convertView;
			} else {
				vg = (ViewGroup) inflater.inflate(R.layout.bledevice_list_item, null);
			}

			BluetoothDevice device = devices.get(position);
			final TextView tvadd = ((TextView) vg.findViewById(R.id.address));
			final TextView tvname = ((TextView) vg.findViewById(R.id.name));
			final TextView tvpaired = (TextView) vg.findViewById(R.id.paired);
			final TextView tvrssi = (TextView) vg.findViewById(R.id.rssi);

			tvrssi.setVisibility(View.VISIBLE);
			byte rssival = (byte) devRssiValues.get(device.getAddress()).intValue();
			if (rssival != 0) {
				tvrssi.setText("Rssi = " + String.valueOf(rssival));
			}

			tvname.setText(device.getName());
			tvadd.setText(device.getAddress());
			if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
				tvname.setTextColor(Color.BLACK);
				tvadd.setTextColor(Color.BLACK);
				tvpaired.setTextColor(Color.GRAY);
				tvpaired.setVisibility(View.VISIBLE);
				tvpaired.setText(R.string.paired);
				tvrssi.setVisibility(View.VISIBLE);
				tvrssi.setTextColor(Color.BLACK);

			} else {
				tvname.setTextColor(Color.BLACK);
				tvadd.setTextColor(Color.BLACK);
				tvpaired.setVisibility(View.GONE);
				tvrssi.setVisibility(View.VISIBLE);
				tvrssi.setTextColor(Color.BLACK);
			}
			return vg;
		}
	}

	private void showMessage(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}

	/**
	 * 隐藏键盘
	 */
	protected void hideInput() {
		InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		View v = getWindow().peekDecorView();
		if (null != v) {
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
		}
	}

}
