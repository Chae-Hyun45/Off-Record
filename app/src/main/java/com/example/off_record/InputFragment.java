package com.example.off_record;

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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class InputFragment extends Fragment {

    private String selectedEmotion = "";
    private RadioGroup rgInfluence, rgStress, rgFatigue, rgSleep, rgNeed, rgFeedback;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input, container, false);

        db = FirebaseFirestore.getInstance();

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

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // 💡 [5단계 격리 반영] 현재 로그인한 유저의 고유 UID를 가져옵니다.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";

        // 💡 [5단계 격리 반영] 공용 보관함이 아닌, users/{uid}/daily_records 경로에서 오늘의 기록을 로드합니다.
        db.collection("users").document(uid).collection("daily_records").document(today).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        if (selectedEmotion.isEmpty()) selectedEmotion = doc.getString("emotion");
                        updateEmotionHighlight(view);

                        Long scoreLong = doc.getLong("score");
                        int score = (scoreLong != null) ? scoreLong.intValue() : 0;
                        seekBar.setProgress(score);
                        tvScoreValue.setText(score + "점");
                        etDiary.setText(doc.getString("diary"));

                        String meals = doc.getString("meals");
                        if (meals != null) {
                            cbBreakfast.setChecked(meals.contains("아침"));
                            cbLunch.setChecked(meals.contains("점심"));
                            cbDinner.setChecked(meals.contains("저녁"));
                            cbLateNight.setChecked(meals.contains("야식"));
                        }

                        setRadioCheckedByText(rgInfluence, doc.getString("influence"));
                        setRadioCheckedByText(rgStress, doc.getString("stress"));
                        setRadioCheckedByText(rgFatigue, doc.getString("fatigue"));
                        setRadioCheckedByText(rgSleep, doc.getString("sleep"));
                        setRadioCheckedByText(rgNeed, doc.getString("need"));
                        setRadioCheckedByText(rgFeedback, doc.getString("feedback"));
                    } else {
                        updateEmotionHighlight(view);
                    }
                });

        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> {
                String fullTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                String mealInfo = (cbBreakfast.isChecked() ? "아침 " : "")
                        + (cbLunch.isChecked() ? "점심 " : "")
                        + (cbDinner.isChecked() ? "저녁 " : "")
                        + (cbLateNight.isChecked() ? "야식" : "");

                // Firestore에 데이터 저장
                saveToFirestore(fullTime, mealInfo, seekBar.getProgress(), etDiary.getText().toString());

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

    private void saveToFirestore(String fullTime, String mealInfo, int score, String diary) {
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", fullTime);
        record.put("emotion", selectedEmotion);
        record.put("score", score);
        record.put("diary", diary);
        record.put("meals", mealInfo);
        record.put("influence", getSelectedText(rgInfluence));
        record.put("stress", getSelectedText(rgStress));
        record.put("fatigue", getSelectedText(rgFatigue));
        record.put("sleep", getSelectedText(rgSleep));
        record.put("need", getSelectedText(rgNeed));
        record.put("feedback", getSelectedText(rgFeedback));

        String dateId = fullTime.split(" ")[0];

        // 💡 [5단계 격리 반영] 저장할 때도 현재 로그인한 유저의 고유 UID를 가져옵니다.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";

        // 💡 [5단계 격리 반영] users/{uid}/daily_records/{dateId} 구조로 완벽히 격리하여 저장합니다.
        db.collection("users")
                .document(uid)
                .collection("daily_records")
                .document(dateId)
                .set(record)
                .addOnSuccessListener(aVoid -> {
                    if (getContext() != null) {
                        android.util.Log.d("Firestore", "유저별 개인 방에 기록이 성공적으로 저장되었습니다.");
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("Firestore", "기록 저장 실패", e);
                });
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
                boolean isSelected = selectedEmotion.equals(codes[i]);

                // 선택된 이모지는 은은한 연두 배경 + 얇은 테두리
                button.setBackgroundResource(isSelected ? R.drawable.emoji_selected_bg : android.R.color.transparent);

                // 선택된 이모지는 조금만 더 선명하고 크게
                button.setAlpha(isSelected ? 1.0f : 0.68f);
                button.setScaleX(isSelected ? 1.06f : 1.0f);
                button.setScaleY(isSelected ? 1.06f : 1.0f);
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