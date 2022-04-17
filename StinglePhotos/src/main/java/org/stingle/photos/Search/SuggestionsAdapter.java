package org.stingle.photos.Search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import org.stingle.photos.R;

import java.util.ArrayList;
import java.util.List;

public class SuggestionsAdapter extends ArrayAdapter<Suggestion> {

    Context context;
    int resource, textViewResourceId;
    List<Suggestion> items, tempItems, suggestions;

    public SuggestionsAdapter(Context context, int resource, int textViewResourceId, List<Suggestion> items) {
        super(context, resource, textViewResourceId, items);
        this.context = context;
        this.resource = resource;
        this.textViewResourceId = textViewResourceId;
        this.items = items;
        tempItems = new ArrayList<>(items); // this makes the difference.
        suggestions = new ArrayList<>();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.item_suggestion, parent, false);
        }
        Suggestion people = items.get(position);
        if (people != null) {
            TextView lblName = (TextView) view.findViewById(R.id.lbl_name);
            if (lblName != null)
                lblName.setText(people.getText());
            String type = people.getType();
            ImageView imageView = view.findViewById(R.id.image);
            if (type.equals("obj")) {
                imageView.setVisibility(View.INVISIBLE);
            } else if (type.equals("loc")) {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(R.drawable.ic_location);
            } else {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(R.drawable.ic_person);
            }
        }
        return view;
    }

    @Override
    public Filter getFilter() {
        return nameFilter;
    }

    /**
     * Custom Filter implementation for custom suggestions we provide.
     */
    Filter nameFilter = new Filter() {
        @Override
        public CharSequence convertResultToString(Object resultValue) {
            return ((Suggestion) resultValue).getText();
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint != null) {
                suggestions.clear();
                for (Suggestion people : tempItems) {
                    if (people.getText().toLowerCase().contains(constraint.toString().toLowerCase())) {
                        suggestions.add(people);
                    }
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = suggestions;
                filterResults.count = suggestions.size();
                return filterResults;
            } else {
                return new FilterResults();
            }
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            List<Suggestion> filterList = (ArrayList<Suggestion>) results.values;
            if (results != null && results.count > 0) {
                clear();
                for (Suggestion people : filterList) {
                    add(people);
                    notifyDataSetChanged();
                }
            }
        }
    };
}
