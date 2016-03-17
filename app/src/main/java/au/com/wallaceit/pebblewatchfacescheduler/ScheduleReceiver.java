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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ScheduleReceiver extends BroadcastReceiver {
    public ScheduleReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String alarmKey = intent.getStringExtra("key");
        Manager manager = new Manager(context);
        String uuid = null;
        if (alarmKey.equals("0")){ // auto rotation schedule
            Log.w("au.com.wallaceit", "Intent received for auto change, switching watchface");
            JSONObject autoSchedule = manager.getAutoSchedule();
            try {
                int index;
                if (manager.isAutoScheduleRandom()) {
                    uuid = manager.getRandomUuidFromSelection();
                    index = manager.getAutoScheduleUuidIndex(uuid);
                } else {
                    // move to the next watchface or go back to the first if at the end
                    index = autoSchedule.getInt("curindex") + 1;
                    JSONArray uuids = autoSchedule.getJSONArray("uuids");
                    if (index >= uuids.length())
                        index = 0;
                    if (uuids.length() > 0) { // if no selected watchfaces, skip
                        uuid = uuids.getString(index);
                    }
                }
                manager.setAutoScheduleCurrentIndex(index);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // reschedule for the next interval
            manager.scheduleAutoAlarmIntent();
        } else {
            Log.w("au.com.wallaceit", "Intent received for schedule change, switching watchface");
            uuid = intent.getStringExtra("uuid");
            // uuid of zero indicates random watchface
            if (uuid.equals("0"))
                uuid = manager.getRandomUuidFromSelection();
            // reschedule
            manager.rescheduleAlarm(intent.getStringExtra("key"));
        }
        // open watchface
        if (uuid!=null)
            manager.setPebbleWatchface(uuid);

        manager.setLastChangeInfo(alarmKey.equals("0")?"auto":"scheduled", uuid==null?"null":uuid);
    }
}
