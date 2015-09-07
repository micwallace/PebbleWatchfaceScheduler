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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;

public class Manager {
    Context context;
    SharedPreferences preferences;
    JSONObject uuids;
    JSONObject schedule;
    JSONObject autoSchedule;

    public Manager(Context context){
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            uuids = new JSONObject(preferences.getString("uuids", "{}"));
            schedule = new JSONObject(preferences.getString("schedule", "{}"));
            autoSchedule = new JSONObject(preferences.getString("autoSchedule", "{\"enabled\":false,\"interval\":86400000,\"uuids\":[],\"curindex\":0}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // APP/UUID list functions
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

    // Scheduling functions
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

    public void setScheduleItem(String key, String uuid, Calendar time, int dayOfWeek){
        // if day of week selected, adjust to that day
        long millis;
        if (dayOfWeek==0) {
            millis = time.getTimeInMillis();
            if (millis<Calendar.getInstance().getTimeInMillis()){ // if time has past, schedule for the next day
                time.add(Calendar.DAY_OF_YEAR, 1);
                millis = time.getTimeInMillis(); // add a day
            }
        } else {
            millis = Manager.nextDayOfWeek(dayOfWeek, time).getTimeInMillis();
        }

        JSONObject scheduleObj = new JSONObject();
        try {
            scheduleObj.put("uuid", uuid);
            scheduleObj.put("time", millis);
            scheduleObj.put("day", dayOfWeek);
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

    public void rescheduleAlarm(String key) {
        // reset the alarm for the next day
        try {
            JSONObject scheduleObj = schedule.getJSONObject(key);
            String uuid = scheduleObj.getString("uuid");
            Long time = scheduleObj.getLong("time");
            int dayOfWeek = scheduleObj.has("day")?scheduleObj.getInt("day"):0;
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(time);
            if (dayOfWeek==0) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                time = calendar.getTimeInMillis(); // add a day
            } else {
                // set for the next week on the specified day
                time = Manager.nextDayOfWeek(dayOfWeek, calendar).getTimeInMillis();
            }
            scheduleObj.put("time", time);
            schedule.put(key, scheduleObj);
            scheduleAlarmIntent(key, uuid, time);
            saveSchedule();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // auto scheduling functions
    public JSONObject getAutoSchedule(){
        return autoSchedule;
    }

    public void setAutoScheduleEnabled(boolean enabled){
        try {
            autoSchedule.put("enabled", enabled);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (enabled){
            scheduleAutoAlarmIntent();
        } else {
            cancelAlarmIntent("0");
        }
        saveAutoSchedule();
    }

    public void setAutoScheduleInterval(Long interval){
        try {
            autoSchedule.put("interval", interval);
            if (autoSchedule.getBoolean("enabled"))
                scheduleAutoAlarmIntent();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        saveAutoSchedule();
    }

    public void setAutoScheduleUuids(JSONArray array){
        try {
            autoSchedule.put("uuids", array);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        saveAutoSchedule();
    }

    public void setAutoScheduleCurrentIndex(int index){
        try {
            autoSchedule.put("curindex", index);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        saveAutoSchedule();
    }

    public int getAutoScheduleUuidIndex(String uuid){
        ArrayList<String> index = new ArrayList<>();
        try {
            JSONArray uuids = autoSchedule.getJSONArray("uuids");
            for (int i = 0; i<uuids.length(); i++){
                index.add(uuids.getString(i));
            }
            return index.indexOf(uuid);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void scheduleAutoAlarmIntent(){
        Long time = System.currentTimeMillis();
        try {
            time += autoSchedule.getLong("interval");
            scheduleAlarmIntent("0", "0", time);
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

    public void scheduleAlarmIntent(String key, String uuid, Long millis){
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        Log.w("au.com.wallaceit", "Scheduling alarm "+key+" for "+cal.getTime().toString());
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

    private void saveAutoSchedule(){
        preferences.edit().putString("autoSchedule", autoSchedule.toString()).apply();
    }

    public static Calendar nextDayOfWeek(int dow, Calendar alarmDate) {
        Calendar date = Calendar.getInstance();
        int diff = dow - alarmDate.get(Calendar.DAY_OF_WEEK);
        if (diff<0 || (diff==0 && alarmDate.getTimeInMillis()<date.getTimeInMillis())) { // add 7 days if the difference is minus, or if it's today & the alarm time has passed.
            diff += 7;
        }
        alarmDate.add(Calendar.DAY_OF_MONTH, diff);
        return alarmDate;
    }

    public static int getDayOfWeekNumber(String day){
        int daynum = 0;
        switch (day.toLowerCase()){
            case "sunday": daynum = 1; break;
            case "monday": daynum = 2; break;
            case "tuesday": daynum = 3; break;
            case "wednesday": daynum = 4; break;
            case "thursday": daynum = 5; break;
            case "friday": daynum = 6; break;
            case "saturday": daynum = 7; break;
        }
        return daynum;
    }

    public static String getDayOfWeekLabel(int daynum){
        String day = "";
        switch (daynum){
            case 0: day = "Every Day"; break;
            case 1: day = "Sunday"; break;
            case 2: day = "Monday"; break;
            case 3: day = "Tuesday"; break;
            case 4: day = "Wednesday"; break;
            case 5: day = "Thursday"; break;
            case 6: day = "Friday"; break;
            case 7: day = "Saturday"; break;
        }
        return day;
    }
}
