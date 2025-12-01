package me.rapierxbox.shellyelevatev2;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class KioskService extends Service {
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i("KioskService", "Foreground service created");
		startForeground(1, buildNotification());

		// Kick off watchdog loop
		startWatchdog();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY; // restart if killed
	}

	@Override
	public IBinder onBind(Intent intent) { return null; }

	private Notification buildNotification() {
		// On Android 8+, you must create a channel first
		return new NotificationCompat.Builder(this, "kiosk_channel")
				.setContentTitle("Kiosk running")
				.setContentText("Foreground anchor active")
				.setSmallIcon(R.drawable.ic_launcher_foreground)
				.build();
	}

	private void startWatchdog() {
		Handler handler = new Handler(Looper.getMainLooper());
		Runnable checkTask = new Runnable() {
			@Override
			public void run() {
				if (!isActivityRunning(MainActivity.class)) {
					Log.w("KioskService", "MainActivity not running, relaunching...");
					Intent activityIntent = new Intent(KioskService.this, MainActivity.class);
					activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(activityIntent);
				}
				handler.postDelayed(this, 10000); // check every 10s
			}
		};
		handler.postDelayed(checkTask, 10000);
	}

	private boolean isActivityRunning(Class<?> activityClass) {
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		if (am != null) {
			List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(Integer.MAX_VALUE);
			for (ActivityManager.RunningTaskInfo task : tasks) {
				if (task.topActivity != null &&
						task.topActivity.getClassName().equals(activityClass.getName())) {
					return true;
				}
			}
		}
		return false;
	}
}
