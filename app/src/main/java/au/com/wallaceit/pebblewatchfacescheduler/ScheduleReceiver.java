/*
 * Copyright 2015 Michael Boyde Wallace (http://wallaceit.com.au)
 * This file is part of Pebble Watch Face Scheduler.
 *
 * Pebble Watch Face Scheduler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Pebble Watch Face Scheduler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pebble Watch Face Scheduler (COPYING). If not, see <http://www.gnu.org/licenses/>.
 */
package au.com.wallaceit.pebblewatchfacescheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ScheduleReceiver extends BroadcastReceiver {
    public ScheduleReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String alarmKey = intent.getStringExtra("key");
        Manager manager = new Manager(context);
        String uuid = null;
        if (alarmKey.equals("0")){
            Log.w("au.com.wallaceit", "Intent received for autorotate, switching watchface");
            JSONObject autoSchedule = manager.getAutoSchedule();
            try {
                // move to the next watchface or go back to the first if at the end
                int index = autoSchedule.getInt("curindex")+1;
                JSONArray uuids = autoSchedule.getJSONArray("uuids");
                if (index>=uuids.length())
                    index = 0;
                uuid = uuids.getString(index);
                manager.setAutoScheduleCurrentIndex(index);
                // reschedule for the next interval
                manager.scheduleAutoAlarmIntent();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            Log.w("au.com.wallaceit", "Intent received for schedule change, switching watchface");
            uuid = intent.getStringExtra("uuid");
            // reschedule
            manager.rescheduleAlarm(intent.getStringExtra("key"));
        }
        // open watchface
        if (uuid!=null)
            PebbleKit.startAppOnPebble(context, UUID.fromString(uuid));
    }
}
