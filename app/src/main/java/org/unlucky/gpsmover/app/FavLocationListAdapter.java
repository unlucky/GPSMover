package org.unlucky.gpsmover.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

public class FavLocationListAdapter extends BaseAdapter {
    private Context context;
    private LayoutInflater inflater;
    private List<? extends Map<String, ?>> dataList;
    private int resId;

    public FavLocationListAdapter(Context context, int resId,
                            List<? extends Map<String, ?>> data) {
        this.context = context;
        this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.dataList = data;
        this.resId = resId;
    }

    @Override
    public int getCount() {
        return dataList.size();
    }

    @Override
    public Object getItem(int position) {
        return dataList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = inflater.inflate(R.layout.list_item_fav_location, null);
            holder.title = (TextView)convertView.findViewById(R.id.list_item_fav_location_title);
            holder.content = (TextView)convertView.findViewById(R.id.list_item_fav_location_content);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }

        Map<String, Object> item = (Map<String, Object>)dataList.get(position);
        holder.title.setText(item.get("title").toString());
        holder.content.setText(String.format(context.getString(R.string.list_item_content),
                Double.valueOf(item.get("lat").toString()), Double.valueOf(item.get("lng").toString())));

        return convertView;
    }

    static class ViewHolder {
        public TextView title;
        public TextView content;
    }
}
