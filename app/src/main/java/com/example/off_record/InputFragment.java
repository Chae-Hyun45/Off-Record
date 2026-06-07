package com.example.off_record;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private TextView tvResultView;

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input, container, false);

        db = FirebaseFirestore.getInstance();

        try {
            // GenerativeModel 초기화
            GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.1-flash-lite"); // 사용할 모델명

            // model 변수 초기화
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

        tvResultView = view.findViewById(R.id.tvResultView);

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

                String feedbackValue = getSelectedText(rgFeedback);
                String aiRoll = "";
                if(feedbackValue.equals("공감형 피드백")){
                    aiRoll = "너는 내담자의 상처를 따뜻하게 치유해 주는 '감정 코칭 전문 심리상담사'야. 친구처럼 든든하고 다정하게, 사용자의 마음에 깊이 공감하고 위로해 줘.";
                }else if(feedbackValue.equals("현실적인 조언")){
                    aiRoll = "너는 객관적인 팩트를 바탕으로 해결책을 제시하는 '인지행동치료(CBT) 전문가'야. 감정적인 위로보다는 사용자가 처한 상황을 이성적으로 파악하고, 현실적이고 날카로운 조언을 해줘.";
                }else if(feedbackValue.equals("스트레스 원인 분석")){
                    aiRoll = "너는 사용자의 일상 데이터를 기반으로 심리 상태를 추적하는 '데이터 기반 정신건강 분석가'야. 오늘 기록된 데이터를 논리적이고 과학적으로 분석해서 스트레스의 핵심 원인을 정밀하게 짚어줘.";
                }else if(feedbackValue.equals("내일 행동 추천")){
                    aiRoll = "너는 삶의 긍정적인 변화를 이끄는 '라이프 코치(Life Coach)'야. 사용자가 내일 바로 실천할 수 있는 가장 효과적이고 구체적인 행동 몇가지를 명확하게 미션으로 추천해줘.";
                }else if(feedbackValue.equals("짧은 한마디")){
                    aiRoll = "너는 복잡한 생각을 한 번에 정리해 주는 '통찰력 있는 인생 멘토'야. 군더더기 없이 오늘 하루를 관통하는 짧고 강렬한 응원과 뼈 때리는 문장 한 마디만 남겨줘.";
                }else{
                    aiRoll = "너는 내담자의 상처를 따뜻하게 치유해 주는 '감정 코칭 전문 심리상담사'야. 친구처럼 든든하고 다정하게, 사용자의 마음에 깊이 공감하고 위로해 줘.";
                }

                String customPrompt = String.format(
                        "%s" +
                                "지금 보내주는 정보는 나의 오늘 하루 데이터야. 너는 이 데이터들을 면밀히 분석해서 말해줘"+
                                "글은 약 400자 미민아었으면 좋겠어. 그렇다고 꼭 400자를 꽉 채울 필요는 없어. 글의 길이는 너의 필요에 따라 변경해도 괜찮아" +
                                "\n\n" +
                                "[오늘의 데이터]\n" +
                                "- 선택한 감정: %s (참고: emo1은 가장 슬픔, emo5는 가장 행복)\n" +
                                "- 컨디션 점수: %s점\n" +
                                "- 먹은 식사: %s\n" +
                                "- 오늘 감정에 가장 큰 영향을준 것: %s\n" +
                                "- 스트레스 정도: %s\n" +
                                "- 피로도: %s\n" +
                                "- 수면 상태: %s\n" +
                                "- 지금 나에게 필요한 것: %s\n" +
                                "- 오늘 나의 이야기: %s\n\n" +
                                "이 모든 정보를 종합해서 분석해줘. 특히 \"내가 받고 싶은 피드백\"은 꼭 맞춰줘야해.",
                        aiRoll,
                        selectedEmotion,
                        tvScoreValue.getText().toString(),
                        mealInfo.isEmpty() ? "모두 거름" : mealInfo,
                        getSelectedText(rgInfluence),
                        getSelectedText(rgStress),
                        getSelectedText(rgFatigue),
                        getSelectedText(rgSleep),
                        getSelectedText(rgNeed),
                        etDiary.getText().toString()
                );
                // 3. 생성된 맞춤형 프롬프트를 Gemini에게 전달
                askGemini(customPrompt, fullTime, mealInfo, seekBar.getProgress(), etDiary.getText().toString());
                // Firestore에 데이터 저장
            });
        }

        return view;
    }

    private void saveToFirestore(String fullTime, String mealInfo, int score, String diary, String resultText) {
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
        record.put("resultText", resultText);

        String dateId = fullTime.split("")[0];

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

    private void askGemini(String userPrompt, String fullTime, String mealInfo, int score, String diary) {
        if (model == null) return;

        Content prompt = new Content.Builder().addText(userPrompt).build();
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String resultText = result.getText(); // AI 답변 추출


                // 3. AI 답변을 포함하여 Firestore에 최종 저장
                saveToFirestore(fullTime, mealInfo, score, diary, resultText);

                // 4. 하단 네비게이션을 통해 화면 이동
                if (getActivity() != null) {
                    BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottomNav);
                    if (bottomNav != null) {
                        bottomNav.setSelectedItemId(R.id.extra); // 기록 확인 화면으로 이동
                    }
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("Gemini", "에러 발생: " + t.getMessage());
            }
        }, executor);
    }

    private void navigateToExtra(){
        if (getActivity() != null) {
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottomNav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.extra);
            }
        }
    }
}