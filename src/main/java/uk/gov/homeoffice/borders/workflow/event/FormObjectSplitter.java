package uk.gov.homeoffice.borders.workflow.event;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FormObjectSplitter {

    public List<String> split(String rootJson) {
        List<String> forms = new ArrayList<>();
        JSONObject json = new JSONObject(rootJson);
        for (Object key : json.keySet()) {
            Object value = json.get(key.toString());
            if (value instanceof JSONObject) {
                JSONObject obj = (JSONObject) value;
                if (obj.has("form")) {
                    forms.add(obj.toString());
                }
            }
            if (value instanceof JSONArray) {
                JSONArray array = (JSONArray)value;
                array.forEach(item -> {
                    if (item instanceof JSONObject) {
                        forms.addAll(split(item.toString()));
                    }
                });
            }
        }

        if (json.keySet().contains("form")) {
            forms.add(json.toString());
        }
        return forms;
    }

}
