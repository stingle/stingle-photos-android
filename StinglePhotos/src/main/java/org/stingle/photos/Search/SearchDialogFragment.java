package org.stingle.photos.Search;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.stingle.photos.R;

import java.util.ArrayList;
import java.util.List;

public class SearchDialogFragment extends DialogFragment {

    private static final int INITIAL_CAPACITY = 15;

    private final List<Suggestion> suggestions = new ArrayList<>();
    private final List<String> objects = new ArrayList<>();
    private final List<String> faces = new ArrayList<>();
    private final List<String> locations = new ArrayList<>();

    private ChipGroup chipsObjectsGroup;
    private ChipGroup chipsPersonsGroup;
    private ChipGroup chipsLocationsGroup;
    private ScrollView scrollView;

    enum TYPE {
        OBJECT,
        FACE,
        LOCATION
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(AppCompatDialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_dialog, container, false);
        initViews(view);
        initDataSource();
        initSearchView(view);
        initChipGroup(view, TYPE.OBJECT, false);
        initChipGroup(view, TYPE.FACE, false);
        initChipGroup(view, TYPE.LOCATION, false);
        return view;
    }

    private void initViews(View view) {
        chipsObjectsGroup = (ChipGroup) view.findViewById(R.id.chipsObjects);
        chipsPersonsGroup = (ChipGroup) view.findViewById(R.id.chipsPersons);
        chipsLocationsGroup = (ChipGroup) view.findViewById(R.id.chipsLocations);
        scrollView = ((ScrollView) view.findViewById(R.id.scrollView));
    }

    // TODO - get from database, now it's TEST data
    private void initDataSource() {
        objects.add("iron");
        objects.add("bottle");
        objects.add("person");
        objects.add("dog");
        objects.add("cat");
        objects.add("laptop");
        objects.add("phone");
        objects.add("TV");
        objects.add("mouse");
        objects.add("cable");
        objects.add("book");
        objects.add("bread");
        objects.add("dish");
        objects.add("cup");
        objects.add("cap");
        objects.add("wine glass");
        objects.add("glass");


        faces.add("Alex Amiryan");
        faces.add("Alex");
        faces.add("Bob");
        faces.add("Jack");
        faces.add("John");
        faces.add("Garnik");
        faces.add("Garik");
        faces.add("Gevorg");
        faces.add("Aren");
        faces.add("Aram");
        faces.add("Artur");
        faces.add("James");
        faces.add("Elia");
        faces.add("Anna");
        faces.add("Maria");
        faces.add("Mary");
        faces.add("Margaret");
        faces.add("Kilian");
        faces.add("Sara");
        faces.add("Flora");
        faces.add("Johann");
        faces.add("Lisa");

        locations.add("Garni");
        locations.add("Yerevan");
        locations.add("Florida");
        locations.add("St. Janshtone");
        locations.add("Gyumri");
        locations.add("Gavar");
        locations.add("Amsterdam");
        locations.add("Moscow");
        locations.add("Los Angeles");
        locations.add("New York");
        locations.add("Johannesburg");
        locations.add("Madeira");
        locations.add("Lisbon");
        locations.add("Vienna");
        locations.add("Kiev");
        locations.add("Sarajevo");
        locations.add("Tbilisi");
        locations.add("Berlin");
        locations.add("Paris");
        locations.add("Modena");
        locations.add("Sienna");
        locations.add("Milan");
        locations.add("Rome");
        locations.add("Madrid");

        for (String el : objects) {
            suggestions.add(new Suggestion("obj", el));
        }
        for (String el : faces) {
            suggestions.add(new Suggestion("face", el));
        }
        for (String el : locations) {
            suggestions.add(new Suggestion("loc", el));
        }
    }

    private void initSearchView(View view) {
        SearchView searchView = (SearchView) view.findViewById(R.id.simpleSearchView);
        final SearchView.SearchAutoComplete searchAutoComplete =
                (SearchView.SearchAutoComplete)searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        SuggestionsAdapter newsAdapter = new SuggestionsAdapter(getActivity(),
                android.R.layout.simple_dropdown_item_1line, R.id.lbl_name, suggestions);
        searchAutoComplete.setAdapter(newsAdapter);
        // Listen to search view item on click event.
        searchAutoComplete.setOnItemClickListener((adapterView, view1, itemIndex, id) -> {
            Suggestion queryString=(Suggestion) adapterView.getItemAtPosition(itemIndex);
            searchAutoComplete.setText(queryString.getText());
            handleTestClick(queryString.getText());
        });
        // Below event is triggered when submit search query.
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                handleTestClick(query);
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void initChipGroup(View view, TYPE type, boolean more) {
        int count;
        if (type == TYPE.OBJECT) {
            count = objects.size();
        } else if (type == TYPE.FACE) {
            count = faces.size();
        } else {
            count = locations.size();
        }
        int initialCapacity = Math.min(count, INITIAL_CAPACITY);
        int startIndex = 0;
        if (more) {
            startIndex = INITIAL_CAPACITY;
            initialCapacity = count;
        }
        for (int i = startIndex; i < initialCapacity; ++i) {
            final String text;
            if (type == TYPE.OBJECT) {
                text = objects.get(i);
            } else if (type == TYPE.FACE) {
                text = faces.get(i);
            } else {
                text = locations.get(i);
            }
            Chip chip =
                    (Chip) this.getLayoutInflater().inflate(R.layout.item_chip_search, null, false);
            chip.setText(text);
            int paddingDp = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10,
                    getResources().getDisplayMetrics()
            );
            chip.setPadding(paddingDp, 0, paddingDp, 0);
            chip.setOnClickListener((v) -> handleTestClick(text));
            if (type == TYPE.OBJECT) {
                chipsObjectsGroup.addView(chip);
            } else if (type == TYPE.FACE) {
                chipsPersonsGroup.addView(chip);
            } else {
               chipsLocationsGroup.addView(chip);
            }
        }
        if (!more) {
            if (type == TYPE.OBJECT) {
                setMoreChips(view, TYPE.OBJECT);
            } else if (type == TYPE.FACE) {
                setMoreChips(view, TYPE.FACE);
            } else {
                setMoreChips(view, TYPE.LOCATION);
            }
        }
    }

    private void setMoreChips(View view, TYPE type) {
        Chip chip =
                (Chip) this.getLayoutInflater().inflate(R.layout.item_chip_search, null, false);
        chip.setText("More"); // TODO - move to strings.xml
        chip.setChipBackgroundColorResource(R.color.primaryLightColor);
        chip.setTextColor(ContextCompat.getColor(getContext(), R.color.buttonTextColor));
        int paddingDp = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10,
                getResources().getDisplayMetrics()
        );
        chip.setPadding(paddingDp, 0, paddingDp, 0);
        chip.setOnClickListener((v) -> handleMoreClick(v, view, type));
        if (type == TYPE.OBJECT) {
           chipsObjectsGroup.addView(chip);
        } else if (type == TYPE.FACE) {
            chipsPersonsGroup.addView(chip);
        } else {
            chipsLocationsGroup.addView(chip);
        }
    }

    private void handleMoreClick(View v, View view, TYPE type) {
        if (type == TYPE.OBJECT) {
           chipsObjectsGroup.removeView(v);
        } else if (type == TYPE.FACE) {
            chipsPersonsGroup.removeView(v);
        } else {
            chipsLocationsGroup.removeView(v);
        }
        initChipGroup(view, type, true);
        if (type == TYPE.LOCATION) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void handleTestClick(String query) {
        // TODO - handle correct click
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
        alertDialog.setMessage("Search keyword is " + query);
        alertDialog.show();
    }
}
