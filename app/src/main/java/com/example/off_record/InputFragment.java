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

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.ai.java.GenerativeModelFutures;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;


public class InputFragment extends Fragment {

    private String selectedEmotion = "";
    private RadioGroup rgInfluence, rgStress, rgFatigue, rgSleep, rgNeed, rgFeedback;
    private FirebaseFirestore db;

    private GenerativeModelFutures model;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input, container, false);

        db = FirebaseFirestore.getInstance();

        try {
            // GenerativeModel 초기화
            GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.1-flash-lite"); // 사용할 모델명

            // 여기에 model 변수를 초기화하는 코드가 반드시 있어야 합니다!
            model = GenerativeModelFutures.from(ai);
        } catch (Exception e) {
            android.util.Log.e("GeminiError", "모델 초기화 중 에러 발생: " + e.getMessage());
        }

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
                btnComplete.setEnabled(false); // 중복 클릭 방지

                // 1. 현재 입력된 데이터 수집
                int score = seekBar.getProgress();
                String diaryEntry = etDiary.getText().toString(); // 사용자가 쓴 짧은 메모

                // 체크박스 정보
                String meals = (cbBreakfast.isChecked() ? "아침 " : "")
                        + (cbLunch.isChecked() ? "점심 " : "")
                        + (cbDinner.isChecked() ? "저녁 " : "")
                        + (cbLateNight.isChecked() ? "야식" : "");

                // 2. 수집된 데이터를 바탕으로 프롬프트 문자열 생성
                String customPrompt = String.format(
                        "지금 보내주는 정보는 나의 오늘 하루 데이터야. 너는 이 데이터들을 면밀히 분석해서 " +
                                "나의 마음을 위로해주고 공감해주는 따뜻한 일기를 만들어줘야해. " +
                                "글은 약 400자 미민아었으면 좋겠어. 그렇다고 꼭 400자를 꽉 채울 필요는 없어. 글의 길이는 너의 필요에 따라 변경해도 괜찮아" +
                                "\n\n" +
                                "[오늘의 데이터]\n" +
                                "- 선택한 감정: %s (참고: emo1은 가장 슬픔, emo5는 가장 행복)\n" +
                                "- 컨디션 점수: %d점\n" +
                                "- 먹은 식사: %s\n" +
                                "- 오늘 감정에 가장 큰 영향을준 것: %s\n" +
                                "- 스트레스 정도: %s\n" +
                                "- 피로도: %s\n" +
                                "- 수면 상태: %s\n" +
                                "- 지금 나에게 필요한 것: %s\n" +
                                "- 내가 받고 싶은 피드백 유형: %s\n" +
                                "- 오늘 나의 이야기: %s\n\n" +
                                "이 모든 정보를 종합해서 분석해줘. 특히 \"내가 받고 싶은 피드백\"은 꼭 맞춰줘야해.",
                        selectedEmotion,
                        score,
                        meals.isEmpty() ? "모두 거름" : meals,
                        getSelectedText(rgInfluence),
                        getSelectedText(rgStress),
                        getSelectedText(rgFatigue),
                        getSelectedText(rgSleep),
                        getSelectedText(rgNeed),
                        getSelectedText(rgFeedback),
                        diaryEntry.isEmpty() ? "없음" : diaryEntry
                );

                // 3. 생성된 맞춤형 프롬프트를 Gemini에게 전달
                askGemini(customPrompt);
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

        // 날짜를 문서 ID로 사용하여 하루에 하나의 기록만 저장 (또는 덮어쓰기)
        String dateId = fullTime.split(" ")[0]; 

        db.collection("daily_records")
                .document(dateId)
                .set(record)
                .addOnSuccessListener(aVoid -> {
                    if (getContext() != null) {
                        android.util.Log.d("Firestore", "기록이 성공적으로 저장되었습니다.");
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
    private void askGemini(String userPrompt) {

        // 4. 프롬프트 구성
        Content prompt = new Content.Builder()
                .addText(userPrompt)
                .build();

        // 5. 실행을 위한 Executor 설정 (UI 스레드에서 결과를 받기 위함)
        Executor executor = ContextCompat.getMainExecutor(requireContext());

        // 6. 콘텐츠 생성 요청
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        // 7. 결과 처리 콜백 등록
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                // 성공 시 텍스트뷰에 답변 세팅
                String resultText = result.getText();
                saveAndNavigate(resultText);
            }

            @Override
            public void onFailure(Throwable t) {
                // 실패 시 에러 메시지 표시
                t.printStackTrace();
                saveAndNavigate("에러 발생: " + t.getMessage());
            }
        }, executor);
    }

    // 3. 저장 및 화면 전환 로직을 별도 메소드로 분리 (resultText를 일기 내용으로 사용)
    private void saveAndNavigate(String story) {
        if (getContext() == null) return;

        SharedPreferences pref = requireActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        String fullTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String today = fullTime.split(" ")[0];

        CheckBox cbBreakfast = getView().findViewById(R.id.cbBreakfast);
        CheckBox cbLunch = getView().findViewById(R.id.cbLunch);
        CheckBox cbDinner = getView().findViewById(R.id.cbDinner);
        CheckBox cbLateNight = getView().findViewById(R.id.cbLateNight);
        SeekBar seekBar = getView().findViewById(R.id.scoreSeekBar);

        String mealInfo = (cbBreakfast.isChecked() ? "아침 " : "")
                + (cbLunch.isChecked() ? "점심 " : "")
                + (cbDinner.isChecked() ? "저녁 " : "")
                + (cbLateNight.isChecked() ? "야식" : "");

        String newRecord = String.format("%s|%s|%d|%s|%s|%s|%s|%s|%s|%s|%s",
                fullTime,
                selectedEmotion,
                seekBar.getProgress(),
                story,  // gemini 결과?
                mealInfo,
                getSelectedText(rgInfluence),
                getSelectedText(rgStress),
                getSelectedText(rgFatigue),
                getSelectedText(rgSleep),
                getSelectedText(rgNeed),
                getSelectedText(rgFeedback));

        // SharedPreferences 저장
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

        // Firestore 저장
        saveToFirestore(fullTime, mealInfo, seekBar.getProgress(), story);

        // 화면 이동
        if (getActivity() != null) {
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottomNav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.extra); // 혹은 상세 화면으로 바로 이동하도록 구현
            }
        }
    }
}
