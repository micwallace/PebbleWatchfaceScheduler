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

import android.app.Activity;
import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

class SimpleTabsAdapter extends PagerAdapter {

    private View layout = null;
    private String[] labels;
    private int[] layoutIds;
    private Activity context;

    public SimpleTabsAdapter(String[] labels, int[] layoutIds, Context context, View layout){
        this.context = (Activity) context;
        this.layout = layout;
        this.labels = labels;
        this.layoutIds = layoutIds;
    }

    public Object instantiateItem(ViewGroup collection, int position) {
        if (position>labels.length)
            return null;
        if (layout==null)
            return context.findViewById(layoutIds[position]);
        return layout.findViewById(layoutIds[position]);
    }

    @Override
    public int getCount() {
        return labels.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position>labels.length)
            return null;
        return labels[position];
    }

    public void destroyItem(ViewGroup container, int position, Object object) {

    }

    @Override
    public boolean isViewFromObject(View arg0, Object arg1) {
        return arg0 == arg1;
    }
}
