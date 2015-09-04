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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class BootReceiver extends BroadcastReceiver {
    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // reschedule on boot
        Manager manager = new Manager(context);
        JSONObject schedule = manager.getSchedule();
        Iterator i = schedule.keys();
        Long currentTime = System.currentTimeMillis();
        while (i.hasNext()){
            String key = (String) i.next();
            try {
                JSONObject scheduleObj = schedule.getJSONObject(key);
                Long time = scheduleObj.getLong("time");
                String uuid = scheduleObj.getString("uuid");
                if (time < currentTime) {
                    manager.rescheduleAlarm(key);
                } else {
                    manager.scheduleAlarmIntent(key, uuid, time);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        // restart auto rotate schedule
        try {
            if (manager.getAutoSchedule().getBoolean("enabled"))
                manager.scheduleAutoAlarmIntent();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
