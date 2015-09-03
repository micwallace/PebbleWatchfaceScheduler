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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class Manager {
    Context context;
    SharedPreferences preferences;
    JSONObject uuids;
    JSONObject schedule;

    public Manager(Context context){
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            uuids = new JSONObject(preferences.getString("uuids", "{}"));
            schedule = new JSONObject(preferences.getString("schedule", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public JSONObject getUuids(){
        return uuids;
    }

    public ArrayList<JSONObject> getUuidList(){
        ArrayList<JSONObject> uuidList = new ArrayList<>();
        Iterator i = uuids.keys();
        while (i.hasNext()){
            String key = (String) i.next();
            try {
                uuidList.add(uuids.getJSONObject(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return uuidList;
    }

    public void setUuids(JSONObject nuuids){
        Iterator it = nuuids.keys();
        while (it.hasNext()){
            String uuid = (String) it.next();
            try {
                String name = nuuids.getString(uuid);
                JSONObject uuidObj = new JSONObject();
                uuidObj.put("uuid", uuid);
                uuidObj.put("name", name);
                uuids.put(uuid, uuidObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        saveUuids();
    }

    public void setUuid(String uuid, String name){
        try {
            JSONObject uuidObj = new JSONObject();
            uuidObj.put("uuid", uuid);
            uuidObj.put("name", name);
            uuids.put(uuid, uuidObj);
            saveUuids();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        saveUuids();
    }

    public void removeUuid(String uuid){
        uuids.remove(uuid);
        saveUuids();
    }

    public JSONObject getSchedule(){
        return schedule;
    }

    public ArrayList<JSONObject> getScheduleList(){
        ArrayList<JSONObject> scheduleList = new ArrayList<>();
        Iterator i = schedule.keys();
        while (i.hasNext()){
            String key = (String) i.next();
            try {
                JSONObject scheduleObj = schedule.getJSONObject(key);
                scheduleObj.put("key", key);
                scheduleList.add(scheduleObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return scheduleList;
    }

    public void setScheduleItem(String key, String uuid, Long millis){
        JSONObject scheduleObj = new JSONObject();
        try {
            scheduleObj.put("uuid", uuid);
            scheduleObj.put("time", millis);
            schedule.put(key, scheduleObj);
            saveSchedule();
            scheduleAlarmIntent(key, uuid, millis);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void removeScheduleItem(String key){
        schedule.remove(key);
        saveSchedule();
        cancelAlarmIntent(key);
    }

    public void rescheduleAlarm(String key){
        // reset the alarm for the next day
        try {
            JSONObject scheduleObj = schedule.getJSONObject(key);
            String uuid = scheduleObj.getString("uuid");
            Long time = scheduleObj.getLong("time");
            time += 86400000; // add a day
            scheduleObj.put("time", time);
            schedule.put(key, scheduleObj);

            scheduleAlarmIntent(key, uuid, time);
            saveSchedule();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void cancelAlarmIntent(String key){
        Intent alarmIntent = new Intent(context, ScheduleReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, Long.valueOf(key).intValue(), alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    private void scheduleAlarmIntent(String key, String uuid, Long millis){
        Log.w("au.com.wallaceit", "Scheduling alarm");
        Intent alarmIntent = new Intent(context, ScheduleReceiver.class);
        alarmIntent.putExtra("uuid", uuid);
        alarmIntent.putExtra("key", key);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, Long.valueOf(key).intValue(), alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingIntent);
    }

    private void saveUuids(){
        preferences.edit().putString("uuids", uuids.toString()).apply();
    }

    private void saveSchedule(){
        preferences.edit().putString("schedule", schedule.toString()).apply();
    }
}
