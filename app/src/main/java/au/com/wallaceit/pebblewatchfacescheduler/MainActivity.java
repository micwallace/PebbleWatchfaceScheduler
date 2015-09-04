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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.view.ViewPager;
import android.text.ClipboardManager;
import android.util.Log;
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

import com.getpebble.android.kit.PebbleKit;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private Manager manager;
    private SharedPreferences prefs;
    private WatchfacesAdapter watchfacesAdapter;
    private ScheduleAdapter scheduleAdapter;
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
        manager = new Manager(MainActivity.this);

        // setup views
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        SimpleTabsAdapter pageAdapter = new SimpleTabsAdapter(new String[]{"Watchfaces","Schedule"}, new int[]{R.id.watchface_view, R.id.schedule_listview}, MainActivity.this, null);
        viewPager.setAdapter(pageAdapter);
        LinearLayout tabLayout = (LinearLayout) findViewById(R.id.tab_widget);
        SimpleTabsWidget tabsIndicator = new SimpleTabsWidget(MainActivity.this, tabLayout);
        tabsIndicator.setViewPager(viewPager);
        tabsIndicator.setTextColor(Color.BLACK);
        tabsIndicator.setBackgroundColor(Color.parseColor("#42BAD8"));
        tabsIndicator.setInidicatorColor(Color.parseColor("#FF815D"));

        final ListView watchfaceList = (ListView) findViewById(R.id.watchface_listview);
        watchfacesAdapter = new WatchfacesAdapter(MainActivity.this);
        watchfaceList.setAdapter(watchfacesAdapter);
        watchfaceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (position == 0) {
                    LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_watchface, parent, false);
                    final EditText uuid = (EditText) layout.findViewById(R.id.watchface_uuid);
                    uuid.setVisibility(View.VISIBLE);
                    final EditText name = (EditText) layout.findViewById(R.id.watchface_name);

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Add a Watchface").setView(layout)
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (name.getText().toString().equals("")) {
                                Toast.makeText(MainActivity.this, "Please enter a name for the watchface", Toast.LENGTH_LONG).show();
                                return;
                            }
                            if (!Pattern.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", uuid.getText())) {
                                Toast.makeText(MainActivity.this, "Please enter a valid UUID", Toast.LENGTH_LONG).show();
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
                        PebbleKit.startAppOnPebble(MainActivity.this, UUID.fromString(uuid));
                        Toast.makeText(MainActivity.this, "Watchface selected", Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        watchfaceList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position==0){
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
                Toast.makeText(MainActivity.this, "Copied UUID to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        ListView scheduleList = (ListView) findViewById(R.id.schedule_listview);
        scheduleAdapter = new ScheduleAdapter(MainActivity.this);
        scheduleList.setAdapter(scheduleAdapter);
        scheduleList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (position == 0) {
                    LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_schedule, parent, false);
                    final Spinner watchselect = (Spinner) layout.findViewById(R.id.watchface_selector);
                    watchselect.setVisibility(View.VISIBLE);
                    watchselect.setAdapter(new WatchfaceSpinnerAdapter());
                    final TimePicker timeselect = (TimePicker) layout.findViewById(R.id.time_selector);

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Add schedule").setView(layout)
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Date date = new Date();
                            date.setHours(timeselect.getCurrentHour());
                            date.setMinutes(timeselect.getCurrentMinute());
                            date.setSeconds(0);
                            JSONObject watchface = (JSONObject) watchselect.getSelectedItem();
                            try {
                                manager.setScheduleItem(String.valueOf(System.currentTimeMillis()), watchface.getString("uuid"), date.getTime());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            dialogInterface.dismiss();
                            scheduleAdapter.refreshSchedule();
                        }
                    }).show();
                } else {
                    JSONObject scheduleObj = scheduleAdapter.getItem(position);
                    openScheduleEditDialog(scheduleObj);
                }
            }
        });

        // setup auto rotation UI
        CheckBox cb = (CheckBox) findViewById(R.id.auto_enabled);
        try {
            cb.setChecked(manager.getAutoSchedule().getBoolean("enabled"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                manager.setAutoScheduleEnabled(isChecked);
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
        autoSpinner.setSelection(Arrays.asList(getResources().getStringArray(R.array.time_units)).indexOf(prefs.getString("autoScheduleUnits", "Hours")), false);
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
        if (getIntent().getAction().equals("android.intent.action.VIEW") && getIntent().getData()!=null){
            doUUIDImport();
        }
    }

    private void saveAutoInterval(){
        SharedPreferences.Editor editor = prefs.edit();
        int number = autoSelect.getValue();
        String unit = (String) autoSpinner.getSelectedItem();
        // save prefs
        editor.putInt("autoScheduleQty", number);
        editor.putString("autoScheduleUnits", unit);
        editor.apply();
        // works out millis and set timer
        int unitmillis = 60000;
        switch (unit){
            case "Day":
                unitmillis = 86400000;
                break;
            case "Hours":
                unitmillis = 3600000;
                break;
            case "Minutes":
                unitmillis = 60000;
                break;
            case "Seconds":
                unitmillis = 1000;
                break;
        }
        long interval = number*unitmillis;
        manager.setAutoScheduleInterval(interval);
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
        public void registerDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {

        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
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
        final TimePicker timeselect = (TimePicker) layout.findViewById(R.id.time_selector);
        final Date date;
        final String key, uuid;
        try {
            date = new Date(scheduleObj.getLong("time"));
            date.setDate(new Date().getDate());
            date.setSeconds(0);
            key = scheduleObj.getString("key");
            uuid = scheduleObj.getString("uuid");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        timeselect.setCurrentHour(date.getHours());
        timeselect.setCurrentMinute(date.getMinutes());
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Edit schedule").setView(layout)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                date.setHours(timeselect.getCurrentHour());
                date.setMinutes(timeselect.getCurrentMinute());
                manager.setScheduleItem(key, uuid, date.getTime());
                dialogInterface.dismiss();
                scheduleAdapter.refreshSchedule();
            }
        }).show();
    }

    private void doUUIDImport() {

        try {
            // validate name
            String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
            Cursor metaCursor = this.getContentResolver().query(getIntent().getData(), projection, null, null, null);
            if (metaCursor != null) {
                try {
                    if (metaCursor.moveToFirst()) {
                        if (!metaCursor.getString(0).equals("pebble.log.gz")){
                            Toast.makeText(MainActivity.this, "Wrong file, please open pebble.log.gz to import app info", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                } finally {
                    metaCursor.close();
                }
            }
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
            InputStream is = this.getContentResolver().openInputStream(getIntent().getData());
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
                if (matcher.group(2).equals("watchface"))
                    watchfaces.put(matcher.group(3), matcher.group(1));
                applist+= matcher.group(1)+"\n"+matcher.group(3)+"\n\n";
            }
            manager.setUuids(watchfaces);
            //Log.w("au.com.wallaceit", manager.getUuids().toString());
            // refresh list view and offer to display all apps with their uuid
            watchfacesAdapter.refreshWatchfaces();
            doShowImportDialog(applist);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doShowImportDialog(final String applist){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Import Complete");
        builder.setMessage("Would you like to see a list of all watchfaces & apps?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
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
                        Toast.makeText(v.getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setView(showText);
                builder.setTitle("Locker Applications");
                builder.setCancelable(true);
                builder.create().show();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
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
        private ArrayList<JSONObject> watchfaceList;
        private ArrayList<String> autoUuids;

        public WatchfacesAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            refreshWatchfaces();
            // get watchfaces enabled for auto rotation
            try {
                JSONArray uuids = manager.getAutoSchedule().getJSONArray("uuids");
                autoUuids = new ArrayList<>();
                for (int i=0; i<uuids.length(); i++) {
                    autoUuids.add(uuids.getString(i));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void saveAutoUuids(){
            JSONArray json = new JSONArray();
            for (int i=0; i<autoUuids.size(); i++) {
                json.put(autoUuids.get(i));
            }
            manager.setAutoScheduleUuids(json);
        }

        public void refreshWatchfaces() {
            watchfaceList = manager.getUuidList();
            Collections.sort(watchfaceList, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject s, JSONObject t1) {
                    try {
                        return s.getString("name").compareToIgnoreCase(t1.getString("name"));
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
                if (position == 0) {
                    convertView = inflater.inflate(R.layout.listitem_add, parent, false);
                    ((TextView) convertView.findViewById(R.id.add_text)).setText("Add Watchface");
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
            if (position > 0) {
                JSONObject watchfaceObj = getItem(position);
                final String displayName, uuid;
                try {
                    displayName = watchfaceObj.getString("name");
                    uuid = watchfaceObj.getString("uuid");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
                // setup the row
                viewHolder.name.setText(displayName);
                viewHolder.deleteIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        manager.removeUuid(uuid);
                        refreshWatchfaces();
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
                        builder.setTitle("Edit Watchface").setView(layout)
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (name.getText().toString().equals("")) {
                                    Toast.makeText(MainActivity.this, "Please enter a name for the watchface", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                manager.setUuid(uuid, name.getText().toString());
                                dialogInterface.dismiss();
                                watchfacesAdapter.refreshWatchfaces();
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
                            Log.w(getPackageName(), "Adding "+uuid+" from auto rotate");
                        } else {
                            autoUuids.remove(uuid);
                            Log.w(getPackageName(), "Removing " + uuid + " from auto rotate");
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
            return watchfaceList.size()+1;
        }

        @Override
        public int getViewTypeCount(){
            return 2;
        }

        @Override
        public int getItemViewType(int position){
            if (position == 0)
                return 1;
            return 0;
        }

        public JSONObject getItem(int position){
            return watchfaceList.get(position-1);
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
                        return (int) (s.getLong("time")-t1.getLong("time"));
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
                if (position == 0) {
                    convertView = inflater.inflate(R.layout.listitem_add, parent, false);
                    ((TextView) convertView.findViewById(R.id.add_text)).setText("Add To Schedule");
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
            if (position > 0) {
                final JSONObject scheduleObj = getItem(position);
                final String key, uuid, displayTime, displayName;
                final Long time;
                try {
                    key = scheduleObj.getString("key");
                    uuid = scheduleObj.getString("uuid");
                    time = scheduleObj.getLong("time");
                    Date date = new Date(time);
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mma", Locale.ENGLISH);
                    displayTime = sdf.format(date);
                    displayName = manager.getUuids().getJSONObject(uuid).getString("name");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
                // setup the row
                viewHolder.name.setText(displayName);
                viewHolder.time.setText(displayTime);
                viewHolder.deleteIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        manager.removeScheduleItem(key);
                        refreshSchedule();
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
            if (position == 0)
                return 1;
            return 0;
        }

        public JSONObject getItem(int position){
            return scheduleList.get(position-1);
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
