package com.example.off_record;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class InputFragment extends Fragment {

    private String selectedEmotion = "";
    private RadioGroup rgInfluence, rgStress, rgFatigue, rgSleep, rgNeed, rgFeedback;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input, container, false);

        if (getArguments() != null) {
            selectedEmotion = getArguments().getString("selected_emotion", "");
        }

        SeekBar seekBar = view.findViewById(R.id.scoreSeekBar);
        TextView tvScoreValue = view.findViewById(R.id.tvScoreValue);
        EditText etDiary = view.findViewById(R.id.etDiary);
        CheckBox cbBreakfast = view.findViewById(R.id.cbBreakfast);
        CheckBox cbLunch = view.findViewById(R.id.cbLunch);
        CheckBox cbDinner = view.findViewById(R.id.cbDinner);
        CheckBox cbLateNight = view.findViewById(R.id.cbLateNight);

        rgInfluence = view.findViewById(R.id.rgInfluence);
        rgStress = view.findViewById(R.id.rgStress);
        rgFatigue = view.findViewById(R.id.rgFatigue);
        rgSleep = view.findViewById(R.id.rgSleep);
        rgNeed = view.findViewById(R.id.rgNeed);
        rgFeedback = view.findViewById(R.id.rgFeedback);

        View btnComplete = view.findViewById(R.id.btnComplete);

        setupEmotionSelection(view);
        setupScoreInput(view);

        setupToggleableRadioGroup(rgInfluence);
        setupToggleableRadioGroup(rgStress);
        setupToggleableRadioGroup(rgFatigue);
        setupToggleableRadioGroup(rgSleep);
        setupToggleableRadioGroup(rgNeed);
        setupToggleableRadioGroup(rgFeedback);

        clearAllGroups();

        SharedPreferences pref = requireActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        String allRecords = pref.getString("all_records", "");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (allRecords.contains(today)) {
            String[] recordsArray = allRecords.split("##");
            for (String record : recordsArray) {
                if (record.startsWith(today)) {
                    String[] detail = record.split("\\|");

                    if (detail.length >= 5) {
                        if (selectedEmotion.isEmpty()) selectedEmotion = detail[1];
                        updateEmotionHighlight(view);

                        seekBar.setProgress(Integer.parseInt(detail[2]));
                        tvScoreValue.setText(detail[2] + "점");
                        etDiary.setText(detail[3]);

                        cbBreakfast.setChecked(detail[4].contains("아침"));
                        cbLunch.setChecked(detail[4].contains("점심"));
                        cbDinner.setChecked(detail[4].contains("저녁"));
                        cbLateNight.setChecked(detail[4].contains("야식"));
                    }

                    if (detail.length > 5) setRadioCheckedByText(rgInfluence, detail[5]);
                    if (detail.length > 6) setRadioCheckedByText(rgStress, detail[6]);
                    if (detail.length > 7) setRadioCheckedByText(rgFatigue, detail[7]);
                    if (detail.length > 8) setRadioCheckedByText(rgSleep, detail[8]);
                    if (detail.length > 9) setRadioCheckedByText(rgNeed, detail[9]);

                    if (detail.length > 10) setRadioCheckedByText(rgFeedback, detail[10]);
                    break;
                }
            }
        } else {
            updateEmotionHighlight(view);
        }

        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> {
                String fullTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                String mealInfo = (cbBreakfast.isChecked() ? "아침 " : "")
                        + (cbLunch.isChecked() ? "점심 " : "")
                        + (cbDinner.isChecked() ? "저녁 " : "")
                        + (cbLateNight.isChecked() ? "야식" : "");

                String newRecord = String.format("%s|%s|%d|%s|%s|%s|%s|%s|%s|%s|%s",
                        fullTime,
                        selectedEmotion,
                        seekBar.getProgress(),
                        etDiary.getText().toString(),
                        mealInfo,
                        getSelectedText(rgInfluence),
                        getSelectedText(rgStress),
                        getSelectedText(rgFatigue),
                        getSelectedText(rgSleep),
                        getSelectedText(rgNeed),
                        getSelectedText(rgFeedback));

                SharedPreferences.Editor editor = pref.edit();
                String oldRecords = pref.getString("all_records", "");
                StringBuilder updatedList = new StringBuilder();

                for (String r : oldRecords.split("##")) {
                    if (!r.isEmpty() && !r.startsWith(today)) {
                        updatedList.append(r).append("##");
                    }
                }

                editor.putString("all_records", newRecord + "##" + updatedList.toString());
                editor.apply();

                if (getActivity() != null) {
                    BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottomNav);
                    if (bottomNav != null) {
                        bottomNav.setSelectedItemId(R.id.extra);
                    }
                }
            });
        }

        return view;
    }

    private void setupToggleableRadioGroup(RadioGroup radioGroup) {
        if (radioGroup == null) return;
        applyToggleLogicRecursively(radioGroup, radioGroup);
    }

    private void applyToggleLogicRecursively(View view, ViewGroup rootGroup) {
        if (view instanceof RadioButton) {
            RadioButton radioButton = (RadioButton) view;

            radioButton.setTag(false);
            radioButton.setOnClickListener(v -> {
                boolean wasChecked = radioButton.getTag() != null && (boolean) radioButton.getTag();

                uncheckAllInGroup(rootGroup);

                if (!wasChecked) {
                    radioButton.setChecked(true);
                    radioButton.setTag(true);
                }
            });
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyToggleLogicRecursively(viewGroup.getChildAt(i), rootGroup);
            }
        }
    }

    private void clearAllGroups() {
        uncheckAllInGroup(rgInfluence);
        uncheckAllInGroup(rgStress);
        uncheckAllInGroup(rgFatigue);
        uncheckAllInGroup(rgSleep);
        uncheckAllInGroup(rgNeed);
        uncheckAllInGroup(rgFeedback);
    }

    private void uncheckAllInGroup(ViewGroup group) {
        if (group == null) return;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof RadioButton) {
                ((RadioButton) child).setChecked(false);
                child.setTag(false);
            } else if (child instanceof ViewGroup) {
                uncheckAllInGroup((ViewGroup) child);
            }
        }
    }

    private String getSelectedText(ViewGroup group) {
        return findCheckedText(group);
    }

    private String findCheckedText(ViewGroup group) {
        if (group == null) return "미선택";

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof RadioButton && ((RadioButton) child).isChecked()) {
                return ((RadioButton) child).getText().toString();
            } else if (child instanceof ViewGroup) {
                String text = findCheckedText((ViewGroup) child);
                if (!text.equals("미선택")) return text;
            }
        }

        return "미선택";
    }

    private void setRadioCheckedByText(ViewGroup group, String text) {
        if (group == null || text == null || text.equals("미선택") || text.isEmpty()) return;
        applyCheckedStateByText(group, text.trim());
    }

    private void applyCheckedStateByText(ViewGroup group, String text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof RadioButton) {
                RadioButton radioButton = (RadioButton) child;
                if (radioButton.getText().toString().trim().equals(text)) {
                    radioButton.setChecked(true);
                    radioButton.setTag(true);
                }
            } else if (child instanceof ViewGroup) {
                applyCheckedStateByText((ViewGroup) child, text);
            }
        }
    }

    private void setupEmotionSelection(View view) {
        int[] resIds = {R.id.btnHappy, R.id.btnSmile, R.id.btnNeutral, R.id.btnSad, R.id.btnAngry};
        String[] codes = {"emo1", "emo2", "emo3", "emo4", "emo5"};

        for (int i = 0; i < resIds.length; i++) {
            final String code = codes[i];
            ImageButton button = view.findViewById(resIds[i]);

            if (button != null) {
                button.setOnClickListener(v -> {
                    selectedEmotion = code;
                    updateEmotionHighlight(view);
                });
            }
        }
    }

    private void updateEmotionHighlight(View view) {
        int[] resIds = {R.id.btnHappy, R.id.btnSmile, R.id.btnNeutral, R.id.btnSad, R.id.btnAngry};
        String[] codes = {"emo1", "emo2", "emo3", "emo4", "emo5"};

        for (int i = 0; i < resIds.length; i++) {
            ImageButton button = view.findViewById(resIds[i]);

            if (button != null) {
                button.setBackgroundResource(selectedEmotion.equals(codes[i]) ? R.drawable.circle_bg : 0);
            }
        }
    }

    private void setupScoreInput(View view) {
        SeekBar seekBar = view.findViewById(R.id.scoreSeekBar);
        TextView tvScoreValue = view.findViewById(R.id.tvScoreValue);

        if (seekBar != null && tvScoreValue != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int roundedScore = Math.round(progress / 10.0f) * 10;
                    tvScoreValue.setText(roundedScore + "점");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int roundedScore = Math.round(seekBar.getProgress() / 10.0f) * 10;
                    seekBar.setProgress(roundedScore);
                    tvScoreValue.setText(roundedScore + "점");
                }
            });
        }
    }
}
