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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.view.ViewPager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.IconTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.mobeta.android.dslv.DragSortListView;

import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private Manager manager;
    private SharedPreferences prefs;
    private WatchfacesAdapter watchfacesAdapter;
    private ScheduleAdapter scheduleAdapter;
    private Resources resources;
    NumberPicker autoSelect;
    Spinner autoSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        resources = getResources();
        manager = new Manager(MainActivity.this);

        // setup views
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        SimpleTabsAdapter pageAdapter = new SimpleTabsAdapter(
                new String[]{resources.getString(R.string.watchfaces), resources.getString(R.string.schedule)},
                new int[]{R.id.watchface_view, R.id.schedule_listview},
                MainActivity.this,
                null
        );
        viewPager.setAdapter(pageAdapter);
        LinearLayout tabLayout = (LinearLayout) findViewById(R.id.tab_widget);
        SimpleTabsWidget tabsIndicator = new SimpleTabsWidget(MainActivity.this, tabLayout);
        tabsIndicator.setViewPager(viewPager);
        tabsIndicator.setTextColor(Color.BLACK);
        tabsIndicator.setBackgroundColor(Color.parseColor("#42BAD8"));
        tabsIndicator.setInidicatorColor(Color.parseColor("#FF815D"));

        // show import instructions if no watchface present
        if (manager.getUuids().length()==0){
            findViewById(R.id.help_view).setVisibility(View.VISIBLE);
        }

        final DragSortListView watchfaceList = (DragSortListView) findViewById(R.id.watchface_listview);
        watchfacesAdapter = new WatchfacesAdapter(MainActivity.this);
        watchfaceList.setAdapter(watchfacesAdapter);
        watchfaceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (position == watchfacesAdapter.getCount()-1) {
                    LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_watchface, parent, false);
                    final EditText uuid = (EditText) layout.findViewById(R.id.watchface_uuid);
                    uuid.setVisibility(View.VISIBLE);
                    final EditText name = (EditText) layout.findViewById(R.id.watchface_name);
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(resources.getString(R.string.add_a_watchface)).setView(layout)
                            .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (name.getText().toString().equals("")) {
                                Toast.makeText(MainActivity.this, resources.getString(R.string.enter_watchface_name), Toast.LENGTH_LONG).show();
                                return;
                            }
                            if (!Pattern.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", uuid.getText())) {
                                Toast.makeText(MainActivity.this, resources.getString(R.string.enter_valid_uuid), Toast.LENGTH_LONG).show();
                                return;
                            }
                            manager.setUuid(uuid.getText().toString(), name.getText().toString());
                            dialogInterface.dismiss();
                            watchfacesAdapter.refreshWatchfaces();
                        }
                    }).create().show();
                } else {
                    JSONObject watchface = watchfacesAdapter.getItem(position);
                    try {
                        String uuid = watchface.getString("uuid");
                        manager.setPebbleWatchface(uuid);
                        // set auto rotate index
                        int index = manager.getAutoScheduleUuidIndex(uuid);
                        if (index>-1)
                            manager.setAutoScheduleCurrentIndex(index);

                        Toast.makeText(MainActivity.this, resources.getString(R.string.watchface_selected), Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        watchfaceList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == watchfacesAdapter.getCount()-1) {
                    return true;
                }
                JSONObject jsonObject = watchfacesAdapter.getItem(position);
                final String uuid;
                try {
                    uuid = jsonObject.getString("uuid");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return true;
                }
                // Copy the Text to the clipboard
                ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                manager.setText(uuid);
                // Show a message:
                Toast.makeText(MainActivity.this, resources.getString(R.string.uuid_copied), Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        watchfaceList.setDropListener(new DragSortListView.DropListener() {
            @Override
            public void drop(int i, int i1) {
                watchfacesAdapter.moveWatchfaceOrder(i, i1);
            }
        });

        final ListView scheduleList = (ListView) findViewById(R.id.schedule_listview);
        scheduleAdapter = new ScheduleAdapter(MainActivity.this);
        scheduleList.setAdapter(scheduleAdapter);
        scheduleList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {

                if (position == scheduleList.getCount() - 1) {
                    if (manager.getUuids().length() == 0) {
                        Toast.makeText(MainActivity.this, resources.getString(R.string.add_watchfaces_first), Toast.LENGTH_LONG).show();
                        return;
                    }
                    LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_schedule, parent, false);
                    final Spinner watchselect = (Spinner) layout.findViewById(R.id.watchface_selector);
                    watchselect.setAdapter(new WatchfaceSpinnerAdapter());

                    final MultiSelectSpinner daySelect = (MultiSelectSpinner) layout.findViewById(R.id.day_selector);
                    daySelect.setItems(Arrays.asList(getResources().getStringArray(R.array.day_units)), "All days");
                    daySelect.selectAll(true);

                    final TimePicker timeselect = (TimePicker) layout.findViewById(R.id.time_selector);

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(resources.getString(R.string.add_schedule)).setView(layout)
                            .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int index) {
                            Calendar date = Calendar.getInstance();
                            date.set(Calendar.HOUR_OF_DAY, timeselect.getCurrentHour());
                            date.set(Calendar.MINUTE, timeselect.getCurrentMinute());
                            date.set(Calendar.SECOND, 0);
                            JSONObject watchface = (JSONObject) watchselect.getSelectedItem();
                            // get days of week array
                            JSONArray daysOfWeek = getCalendarDaysFromSelectedArray(daySelect.getSelected());

                            try {
                                Long millis = manager.setScheduleItem(String.valueOf(System.currentTimeMillis()), watchface.getString("uuid"), date, daysOfWeek);
                                showAlarmSetToast(millis);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            dialogInterface.dismiss();
                            scheduleAdapter.refreshSchedule();
                            if (watchselect.getSelectedItemPosition() == 0 && manager.getUuidList().size() == 0) {
                                Toast.makeText(MainActivity.this, resources.getString(R.string.no_watchfaces_selected_error), Toast.LENGTH_LONG).show();
                            }
                        }
                    }).show();
                } else {
                    JSONObject scheduleObj = scheduleAdapter.getItem(position);
                    openScheduleEditDialog(scheduleObj);
                }
            }
        });

        // setup auto rotation UI
        final CheckBox cb = (CheckBox) findViewById(R.id.auto_enabled);
        try {
            cb.setChecked(manager.getAutoSchedule().getBoolean("enabled"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // check if watchfaces are selected
                    try {
                        JSONArray uuids = manager.getAutoSchedule().getJSONArray("uuids");
                        if (uuids.length() == 0) {
                            cb.setChecked(false);
                            Toast.makeText(MainActivity.this, resources.getString(R.string.select_watchfaces_first), Toast.LENGTH_LONG).show();
                            return;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return;
                    }
                }
                Long next = manager.setAutoScheduleEnabled(isChecked);
                showAlarmSetToast(next);
            }
        });

        final CheckBox randomCb = (CheckBox) findViewById(R.id.auto_random);
        randomCb.setChecked(manager.isAutoScheduleRandom());
        randomCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                manager.setAutoScheduleRandom(isChecked);
            }
        });

        autoSelect = (NumberPicker) findViewById(R.id.auto_increment);
        autoSelect.setMinValue(1);
        autoSelect.setMaxValue(99);
        autoSelect.setValue(prefs.getInt("autoScheduleQty", 1));
        autoSelect.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            Timer timer = new Timer();
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                timer.cancel();
                timer.purge();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        saveAutoInterval();
                    }
                }, 1000);
            }
        });

        autoSpinner = (Spinner) findViewById(R.id.auto_unit);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.time_units, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        autoSpinner.setAdapter(adapter);
        int autoUnits;
        try {
            // update for compatability: catch class cast exception thrown here and update preference to index (intl. support)
            autoUnits = prefs.getInt("autoScheduleUnits", 1);
        } catch (Exception ex){
            SharedPreferences.Editor editor = prefs.edit();
            autoUnits = 0;
            switch (prefs.getString("autoScheduleUnits", "Hours")){
                case "Minutes": // Minutes
                    autoUnits = 0;
                    break;
                case "Hours": // Hours
                    autoUnits = 1;
                    break;
                case "Days": // Days
                    autoUnits = 2;
                    break;
            }
            editor.putInt("autoScheduleUnits", autoUnits);
            editor.apply();
        }
        autoSpinner.setSelection(autoUnits, false);
        autoSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveAutoInterval();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // check for file import
        if ((getIntent().getAction().equals(Intent.ACTION_VIEW) && getIntent().getData()!=null) || (getIntent().getAction().equals(Intent.ACTION_SEND_MULTIPLE))){
            doUUIDImport();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            Toast.makeText(MainActivity.this, "Last Change: "+manager.getLastChangeInfo(), Toast.LENGTH_LONG).show();
            return true;
        } else if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)){
            Toast.makeText(MainActivity.this, "Next Change: "+manager.getNextChangeInfo(), Toast.LENGTH_LONG).show();
            return true;
        } else
            return super.onKeyDown(keyCode, event);
    }

    private void showAlarmSetToast(Long millis){
        if (millis>0)
            Toast.makeText(MainActivity.this, "Next change set for: "+(new Date(millis).toString()), Toast.LENGTH_LONG).show();
    }

    private void saveAutoInterval(){
        SharedPreferences.Editor editor = prefs.edit();
        int number = autoSelect.getValue();
        // use position for internationalisation support (strings must be in same order accross intl. strings.xml)
        int unit = autoSpinner.getSelectedItemPosition();
        // save prefs
        editor.putInt("autoScheduleQty", number);
        editor.putInt("autoScheduleUnits", unit);
        editor.apply();
        // works out millis and set timer
        int unitmillis = 60000;
        switch (unit){
            case 0: // Minutes
                unitmillis = 60000;
                break;
            case 1: // Hours
                unitmillis = 3600000;
                break;
            case 2: // Days
                unitmillis = 86400000;
                break;
            case 3: // seconds; debugging only
                unitmillis = 1000;
                break;
        }
        long interval = number*unitmillis;
        Long next = manager.setAutoScheduleInterval(interval);
        showAlarmSetToast(next);
    }

    class WatchfaceSpinnerAdapter implements SpinnerAdapter {
        LayoutInflater inflater;
        ArrayList<JSONObject> list;

        public WatchfaceSpinnerAdapter(){
            list = manager.getUuidList();
            inflater = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(android.R.id.text1);
                viewHolder.name.setTextSize(20);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            if (position==0){
                viewHolder.name.setText(resources.getString(R.string.random));
                return convertView;
            }
            JSONObject item = (JSONObject) getItem(position);
            try {
                String name = item.getString("name");
                viewHolder.name.setText(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return convertView;
        }

        public int getUuidIndex(String uuid){
            if (uuid.equals("0"))
                return 0;

            for (int i = 0; i<list.size(); i++){
                try {
                    String itemuuid = list.get(i).getString("uuid");
                    if (uuid.equals(itemuuid)) return i+1;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return 0;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public int getCount() {
            return list.size()+1;
        }

        @Override
        public Object getItem(int position) {
            if (position==0){
                JSONObject json = new JSONObject();
                try {
                    json.put("name", resources.getString(R.string.random));
                    json.put("uuid", "0");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return json;
            }
            return list.get(position-1);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(android.R.id.text1);
                viewHolder.name.setTextSize(20);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            if (position==0){
                viewHolder.name.setText(resources.getString(R.string.random));
                return convertView;
            }
            JSONObject item = (JSONObject) getItem(position);
            try {
                String name = item.getString("name");
                viewHolder.name.setText(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return list.size()==0;
        }

        class ViewHolder {
            TextView name;
        }
    }

    private void openScheduleEditDialog(JSONObject scheduleObj){
        LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_schedule, null, false);
        final Spinner watchselect = (Spinner) layout.findViewById(R.id.watchface_selector);
        WatchfaceSpinnerAdapter spinnerAdapter = new WatchfaceSpinnerAdapter();
        watchselect.setAdapter(spinnerAdapter);

        final MultiSelectSpinner daySelect = (MultiSelectSpinner) layout.findViewById(R.id.day_selector);
        daySelect.setItems(Arrays.asList(getResources().getStringArray(R.array.day_units)), getString(R.string.all_days));

        final TimePicker timeselect = (TimePicker) layout.findViewById(R.id.time_selector);
        final String key, uuid;
        final Calendar date;
        try {
            date = Calendar.getInstance();
            date.setTimeInMillis(scheduleObj.getLong("time"));
            Calendar curdate = Calendar.getInstance();
            date.set(Calendar.DAY_OF_YEAR, curdate.get(Calendar.DAY_OF_YEAR));
            date.set(Calendar.SECOND, 0);
            key = scheduleObj.getString("key");
            uuid = scheduleObj.getString("uuid");
            watchselect.setSelection(spinnerAdapter.getUuidIndex(uuid));

            // put selected days into an array
            if (!scheduleObj.has("days")){
                int day = scheduleObj.getInt("day");
                if (day==0){
                    daySelect.selectAll(true);
                } else {
                    boolean[] selected = daySelect.getSelected();
                    selected[day-1] = true;
                    daySelect.setSelected(selected);
                }
            } else {
                JSONArray daysOfWeek = scheduleObj.getJSONArray("days");
                boolean[] selected = new boolean[7];
                // convert days JSON array into boolean array for adapter
                for (int i=0; i<daysOfWeek.length(); i++){
                    int day = daysOfWeek.getInt(i);
                    selected[day-1] = true;
                }
                daySelect.setSelected(selected);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        timeselect.setCurrentHour(date.get(Calendar.HOUR_OF_DAY));
        timeselect.setCurrentMinute(date.get(Calendar.MINUTE));
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(resources.getString(R.string.edit_schedule)).setView(layout)
            .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int index) {
                    date.set(Calendar.HOUR_OF_DAY, timeselect.getCurrentHour());
                    date.set(Calendar.MINUTE, timeselect.getCurrentMinute());

                    JSONObject watchObj = (JSONObject) watchselect.getSelectedItem();
                    String newuuid;
                    try {
                        newuuid = watchObj.getString("uuid");
                    } catch (JSONException e) {
                        e.printStackTrace();
                        newuuid = uuid;
                    }

                    JSONArray daysOfWeek = getCalendarDaysFromSelectedArray(daySelect.getSelected());

                    Long millis = manager.setScheduleItem(key, newuuid, date, daysOfWeek);
                    showAlarmSetToast(millis);

                    dialogInterface.dismiss();
                    scheduleAdapter.refreshSchedule();
                }
        }).show();
    }

    private JSONArray getCalendarDaysFromSelectedArray(boolean[] selected){
        JSONArray daysOfWeek = new JSONArray();
        for (int i=0; i<selected.length; i++){
            if (selected[i])
                daysOfWeek.put(i+1);
        }
        return daysOfWeek;
    }

    private boolean checkPebbleLogFile(Uri file){
        // validate name
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        Cursor metaCursor = this.getContentResolver().query(file, projection, null, null, null);
        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    if (!metaCursor.getString(0).equals("pebble.log.gz")){
                        Toast.makeText(MainActivity.this, resources.getString(R.string.wrong_import_file_error), Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
            } finally {
                metaCursor.close();
            }
        }
        return true;
    }

    private void doUUIDImport() {
        Log.w(getPackageName(), getIntent().getType()+" "+getIntent().getScheme());
        String action = getIntent().getAction();
        Uri file = null;
        switch (action) {
            case Intent.ACTION_SEND_MULTIPLE:
                ArrayList<Uri> imageUris = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                boolean valid = false;
                for (int i = 0; i < imageUris.size(); i++) {
                    if (imageUris.get(i).toString().contains("pebble.log.gz")) {
                        valid = true;
                        file = imageUris.get(i);
                        break;
                    }
                }
                // did we get a valid file?
                if (!valid) {
                    Toast.makeText(MainActivity.this, resources.getString(R.string.wrong_import_file_error), Toast.LENGTH_LONG).show();
                    return;
                }
                break;
            case Intent.ACTION_VIEW:
                // validate name
                file = getIntent().getData();
                if (!checkPebbleLogFile(file)) {
                    return;
                }
                break;
            default:
                return;
        }
        try {
            // prepare to unzip file from archive
            final ZipUnArchiver ua = new ZipUnArchiver(); // although .gz, it seems to be zip encoded
            ua.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG, "Logger"));
            ua.setFileSelectors(new FileSelector[]{
                    new FileSelector() {
                        @Override
                        public boolean isSelected(FileInfo fileInfo) throws IOException {
                            return fileInfo.getName().equals("locker.log");
                        }
                    }
            });
            // copy the archive to a temp location; the plexus-archive library cannot work with an output stream provided by content provider
            InputStream is = this.getContentResolver().openInputStream(file);
            if (is==null){
                Toast.makeText(MainActivity.this, resources.getString(R.string.import_read_file_failed), Toast.LENGTH_LONG).show();
                return;
            }
            File destarchive = new File(this.getCacheDir(), "pebble.log.gz");
            FileOutputStream f = new FileOutputStream(destarchive);
            byte[] buffer = new byte[1024];
            int len1;
            while ((len1 = is.read(buffer)) > 0) {
                f.write(buffer, 0, len1);
            }
            f.close();
            // extract locker.log
            ua.setSourceFile(destarchive);
            File destination = new File(this.getCacheDir(), "locker.log");
            ua.setDestDirectory(this.getCacheDir());
            ua.extract();
            // get file into string and use magical regex powers to extract name & uuid
            String content = getStringFromFile(destination);
            String match = "title=(.*)\n   type=(.*)\n   user_token=.*\n   uuid=(.*)";
            Pattern pattern = Pattern.compile(match, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(content);
            // put into json file & save the list
            JSONObject watchfaces = new JSONObject();
            String applist = "";
            while (matcher.find()){
                if (matcher.group(2).equals("watchface") && (!matcher.group(1).equals("NOT ON WATCH") && !matcher.group(1).equals("UNSUPPORTED WATCHFACES")))
                    watchfaces.put(matcher.group(3), matcher.group(1));
                applist+= matcher.group(1)+"\n"+matcher.group(3)+"\n\n";
            }
            manager.setUuids(watchfaces);
            //Log.w("au.com.wallaceit", manager.getUuids().toString());
            // refresh list view and offer to display all apps with their uuid
            watchfacesAdapter.refreshWatchfaces();
            findViewById(R.id.help_view).setVisibility(View.GONE); // remove import help
            doShowImportDialog(applist);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doShowImportDialog(final String applist){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(resources.getString(R.string.import_complete));
        builder.setMessage(resources.getString(R.string.view_import_message));
        builder.setPositiveButton(resources.getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                TextView showText = new TextView(MainActivity.this);
                showText.setText(applist);
                showText.setTextIsSelectable(true);
                showText.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        // Copy the Text to the clipboard
                        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        TextView showTextParam = (TextView) v;
                        manager.setText(showTextParam.getText());
                        // Show a message:
                        Toast.makeText(v.getContext(), resources.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setView(showText);
                builder.setTitle(resources.getString(R.string.locker_applications));
                builder.setCancelable(true);
                builder.create().show();
            }
        });
        builder.setNegativeButton(resources.getString(R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile (File file) throws Exception {
        FileInputStream fin = new FileInputStream(file);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    class WatchfacesAdapter extends BaseAdapter {
        private LayoutInflater inflater;
        private ArrayList<String> autoUuids;
        private ArrayList<String> sortIndex;

        public WatchfacesAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            try {
                // get watchfaces enabled for auto rotation
                JSONArray uuids = manager.getAutoSchedule().getJSONArray("uuids");
                autoUuids = new ArrayList<>();
                for (int i=0; i<uuids.length(); i++) {
                    autoUuids.add(uuids.getString(i));
                }
                // load saved sorting index
                sortIndex = new ArrayList<>();
                JSONArray watchfaceSort = new JSONArray(prefs.getString("watchfaceSort", "[]"));
                for (int i=0; i<watchfaceSort.length(); i++) {
                    sortIndex.add(watchfaceSort.getString(i));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            refreshWatchfaces();
        }

        private void saveAutoUuids(){
            JSONArray json = new JSONArray();
            for (int i=0; i<sortIndex.size(); i++) { // iterate through sortIndex
                String uuid = sortIndex.get(i);
                if (autoUuids.contains(sortIndex.get(i))) // only add if checked
                    json.put(uuid);
            }
            manager.setAutoScheduleUuids(json);
        }

        public void refreshWatchfaces() {
            JSONObject object = manager.getUuids();
            // check that all watchfaces are on the sort index, if not add them on the end, keep a list of uuids so we can remove stale entries from the sortIndex
            ArrayList<String> currentUuids = new ArrayList<>();
            Iterator it = object.keys();
            while (it.hasNext()){
                String uuid  = (String) it.next();
                currentUuids.add(uuid);
                if (!sortIndex.contains(uuid))
                    sortIndex.add(uuid);
            }
            // remove any stale/removed watchfaces from the index & save
            JSONArray sortJson = new JSONArray();
            for (int i=0; i<sortIndex.size(); i++){
                String uuid = sortIndex.get(i);
                if (currentUuids.contains(uuid)){
                    sortJson.put(uuid);
                } else {
                    sortIndex.remove(i);
                }
            }
            prefs.edit().putString("watchfaceSort", sortJson.toString()).apply();

            this.notifyDataSetChanged();
        }

        public void moveWatchfaceOrder(int from, int to){
            if (to>=sortIndex.size()) return;
            //Log.w(getPackageName(), "Sorting: moving "+from+" to "+to);
            String uuid = sortIndex.get(from);
            sortIndex.remove(from);
            sortIndex.add(to, uuid);
            saveAutoUuids();
            refreshWatchfaces();
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            //super.getView(position, convertView, parent);
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                viewHolder = new ViewHolder();
                if (position == sortIndex.size()) {
                    convertView = inflater.inflate(R.layout.listitem_add, parent, false);
                    ((TextView) convertView.findViewById(R.id.add_text)).setText(resources.getString(R.string.add_a_watchface));
                } else {
                    convertView = inflater.inflate(R.layout.watchface_list_row, parent, false);
                    viewHolder.name = (TextView) convertView.findViewById(R.id.watchface_name);
                    viewHolder.deleteIcon = (IconTextView) convertView.findViewById(R.id.watchface_delete_btn);
                    viewHolder.editIcon = (IconTextView) convertView.findViewById(R.id.watchface_edit_btn);
                    viewHolder.autoCb = (CheckBox) convertView.findViewById(R.id.auto_watchface_enabled);
                }
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            if (position < sortIndex.size()) {
                JSONObject watchfaceObj = getItem(position);
                final String displayName, uuid;
                String tempName, tempUuid;
                try {
                    tempName = watchfaceObj.getString("name");
                    tempUuid = watchfaceObj.getString("uuid");
                } catch (JSONException e) {
                    e.printStackTrace();
                    tempName = "";
                    tempUuid = "null";
                }
                uuid = tempUuid;
                displayName = tempName;
                // setup the row
                viewHolder.name.setText(displayName);
                viewHolder.deleteIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder bulder = new AlertDialog.Builder(MainActivity.this);
                        bulder.setTitle(resources.getString(R.string.delete)).setMessage(resources.getString(R.string.delete_watchface))
                        .setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                manager.removeUuid(uuid);
                                refreshWatchfaces();
                                saveAutoUuids();
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
                    }
                });
                viewHolder.editIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_watchface, parent, false);
                        final EditText name = (EditText) layout.findViewById(R.id.watchface_name);
                        name.setText(displayName);
                        name.selectAll();
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(resources.getString(R.string.edit_watchface)).setView(layout)
                                .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                }).setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (name.getText().toString().equals("")) {
                                    Toast.makeText(MainActivity.this, resources.getString(R.string.enter_watchface_name), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                manager.setUuid(uuid, name.getText().toString());
                                dialogInterface.dismiss();
                                refreshWatchfaces();
                            }
                        }).show();
                    }
                });
                viewHolder.autoCb.setOnCheckedChangeListener(null);
                viewHolder.autoCb.setChecked(autoUuids.contains(uuid));
                viewHolder.autoCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked){
                            if (!autoUuids.contains(uuid))
                                autoUuids.add(uuid);
                            //Log.w(getPackageName(), "Adding "+uuid+" to auto rotate");
                        } else {
                            autoUuids.remove(uuid);
                            //Log.w(getPackageName(), "Removing " + uuid + " from auto rotate");
                        }
                        saveAutoUuids();
                    }
                });
            }

            convertView.setTag(viewHolder);

            return convertView;
        }

        @Override
        public int getCount() {
            return sortIndex.size()+1;
        }

        @Override
        public int getViewTypeCount(){
            return 2;
        }

        @Override
        public int getItemViewType(int position){
            if (position == sortIndex.size())
                return 1;
            return 0;
        }

        public JSONObject getItem(int position){
            try {
                return manager.getUuids().getJSONObject(sortIndex.get(position));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        class ViewHolder {
            CheckBox autoCb;
            TextView name;
            IconTextView deleteIcon;
            IconTextView editIcon;
        }
    }

    class ScheduleAdapter extends BaseAdapter {
        private LayoutInflater inflater;
        private ArrayList<JSONObject> scheduleList;

        public ScheduleAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            refreshSchedule();
        }

        public void refreshSchedule() {
            scheduleList = manager.getScheduleList();
            Collections.sort(scheduleList, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject s, JSONObject t1) {
                    try {
                        if (s.getInt("day")==0)
                            if (t1.getInt("day")==0){
                                return (int) ((s.getLong("time") % (24*60*60*1000L)) - (t1.getLong("time") % (24*60*60*1000L)));
                            } else {
                                return Integer.MIN_VALUE;
                            }
                        Calendar date1 = Calendar.getInstance();
                        date1.setTimeInMillis(s.getLong("time"));
                        Calendar date2 = Calendar.getInstance();
                        date2.setTimeInMillis(t1.getLong("time"));
                        if (date1.get(Calendar.DAY_OF_WEEK)==date2.get(Calendar.DAY_OF_WEEK)){
                            return (int) ((s.getLong("time") % (24*60*60*1000L)) - (t1.getLong("time") % (24*60*60*1000L)));
                        } else {
                            return (date1.get(Calendar.DAY_OF_WEEK)-date2.get(Calendar.DAY_OF_WEEK));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            });
            this.notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            //super.getView(position, convertView, parent);
            ViewHolder viewHolder;
            if (convertView == null || convertView.getTag() == null) {
                // inflate new view
                viewHolder = new ViewHolder();
                if (position == scheduleList.size()) {
                    convertView = inflater.inflate(R.layout.listitem_add, parent, false);
                    ((TextView) convertView.findViewById(R.id.add_text)).setText(resources.getString(R.string.add_schedule));
                } else {
                    convertView = inflater.inflate(R.layout.schedule_list_row, parent, false);
                    viewHolder.name = (TextView) convertView.findViewById(R.id.schedule_watchface_name);
                    viewHolder.time = (TextView) convertView.findViewById(R.id.schedule_time);
                    viewHolder.deleteIcon = (IconTextView) convertView.findViewById(R.id.schedule_delete_btn);
                    viewHolder.editIcon = (IconTextView) convertView.findViewById(R.id.schedule_edit_btn);
                }
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            if (position < scheduleList.size()) {
                final JSONObject scheduleObj = getItem(position);
                final String key, uuid, displayTime, displayName;
                String displayTimeTemp, displayNameTemp, keyTemp;
                final Long time;
                try {
                    keyTemp = scheduleObj.getString("key");
                    uuid = scheduleObj.getString("uuid");
                    time = scheduleObj.getLong("time");
                    JSONArray daysOfWeek;
                    if (!scheduleObj.has("days")){
                        int day = scheduleObj.getInt("day");
                        daysOfWeek = new JSONArray();
                        daysOfWeek.put(day);
                    } else {
                        daysOfWeek = scheduleObj.getJSONArray("days");
                    }

                    Date date = new Date(time);
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mma", Locale.ENGLISH);
                    displayTimeTemp = sdf.format(date)+" "+Manager.getDaysOfWeekLabel(MainActivity.this, daysOfWeek);
                    if (uuid.equals("0")) {
                        displayNameTemp = resources.getString(R.string.random);
                    } else {
                        displayNameTemp = manager.getUuids().getJSONObject(uuid).getString("name");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    displayNameTemp = "";
                    displayTimeTemp = "";
                    keyTemp = "null";
                }
                // setup the row
                key = keyTemp;
                displayTime = displayTimeTemp;
                displayName = displayNameTemp;
                viewHolder.name.setText(displayName);
                viewHolder.time.setText(displayTime);
                viewHolder.deleteIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder bulder = new AlertDialog.Builder(MainActivity.this);
                        bulder.setTitle(resources.getString(R.string.delete)).setMessage(resources.getString(R.string.delete_schedule))
                                .setPositiveButton(resources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        manager.removeScheduleItem(key);
                                        refreshSchedule();
                                        dialog.dismiss();
                                    }
                                })
                                .setNegativeButton(resources.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                }).show();
                    }
                });
                viewHolder.editIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openScheduleEditDialog(scheduleObj);
                    }
                });
            }

            convertView.setTag(viewHolder);

            return convertView;
        }

        @Override
        public int getCount() {
            return scheduleList.size()+1;
        }

        @Override
        public int getViewTypeCount(){
            return 2;
        }

        @Override
        public int getItemViewType(int position){
            if (position < scheduleList.size())
                return 1;
            return 0;
        }

        public JSONObject getItem(int position){
            return scheduleList.get(position);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        class ViewHolder {
            TextView name;
            TextView time;
            IconTextView deleteIcon;
            IconTextView editIcon;
        }
    }
}
