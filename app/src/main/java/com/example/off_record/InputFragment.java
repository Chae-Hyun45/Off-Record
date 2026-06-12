package com.example.off_record;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
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
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Calendar;
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
    private TextView tvAiQuestion, tvVoiceStatus;
    private EditText etAiAnswer;

    private static String cachedAiQuestion = "";
    private static String cachedQuestionTargetDate = "";

    public static void resetCache() {
        cachedAiQuestion = "";
        cachedQuestionTargetDate = "";
    }

    private ImageButton btnVoiceInput;
    private boolean isRecording = false;
    private android.media.MediaRecorder recorder = null;
    private java.io.File audioFile = null;
    private byte[] recordedAudioBytes = null;
    private final String audioMimeType = "audio/wav";

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input, container, false);

        db = FirebaseFirestore.getInstance();
        btnVoiceInput = view.findViewById(R.id.btnVoiceInput);

        if (btnVoiceInput != null) {
            btnVoiceInput.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 200);
                    return;
                }

                if (!isRecording) {
                    startRecording();
                } else {
                    stopRecording();
                }
            });
        }

        try {
            GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.1-flash-lite");
            model = GenerativeModelFutures.from(ai);
        } catch (Exception e) {
            android.util.Log.e("GeminiError", "모델 초기화 중 에러 발생: " + e.getMessage());
        }

        if (getArguments() != null) {
            selectedEmotion = getArguments().getString("selected_emotion", "");
        }

        rgInfluence = view.findViewById(R.id.rgInfluence);
        rgStress = view.findViewById(R.id.rgStress);
        rgFatigue = view.findViewById(R.id.rgFatigue);
        rgSleep = view.findViewById(R.id.rgSleep);
        rgNeed = view.findViewById(R.id.rgNeed);
        rgFeedback = view.findViewById(R.id.rgFeedback);

        View btnComplete = view.findViewById(R.id.btnComplete);
        tvAiQuestion = view.findViewById(R.id.tvAiQuestion);
        etAiAnswer = view.findViewById(R.id.etAiAnswer);
        tvVoiceStatus = view.findViewById(R.id.tvVoiceStatus);

        setupEmotionSelection(view);
        setupScoreInput(view);

        setupToggleableRadioGroup(rgInfluence);
        setupToggleableRadioGroup(rgStress);
        setupToggleableRadioGroup(rgFatigue);
        setupToggleableRadioGroup(rgSleep);
        setupToggleableRadioGroup(rgNeed);
        setupToggleableRadioGroup(rgFeedback);

        // 🌟 [구조 변경] onCreateView 내부의 복잡한 로딩/초기화 코드를 실시간 추적이 가능한 리프레시 전담 함수로 통합 이관했습니다.
        refreshTodayData(view);

        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> {
                // 클릭 시점에 유저 세션을 다시 체크해 독립 샌드박스 장부를 안전하게 재생성
                FirebaseUser freshUser = FirebaseAuth.getInstance().getCurrentUser();
                String freshSuffix = (freshUser != null) ? freshUser.getUid() : "guest";
                SharedPreferences freshPref = requireActivity().getSharedPreferences("DailyRecords_" + freshSuffix, Context.MODE_PRIVATE);

                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String fullTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

                SeekBar seekBar = view.findViewById(R.id.scoreSeekBar);
                EditText etDiary = view.findViewById(R.id.etDiary);
                CheckBox cbBreakfast = view.findViewById(R.id.cbBreakfast);
                CheckBox cbLunch = view.findViewById(R.id.cbLunch);
                CheckBox cbDinner = view.findViewById(R.id.cbDinner);
                CheckBox cbLateNight = view.findViewById(R.id.cbLateNight);

                String mealInfo = (cbBreakfast.isChecked() ? "아침 " : "")
                        + (cbLunch.isChecked() ? "점심 " : "")
                        + (cbDinner.isChecked() ? "저녁 " : "")
                        + (cbLateNight.isChecked() ? "야식" : "");

                String influenceValue = getSelectedText(rgInfluence);
                String stressValue = getSelectedText(rgStress);
                String fatigueValue = getSelectedText(rgFatigue);
                String sleepValue = getSelectedText(rgSleep);
                String needValue = getSelectedText(rgNeed);
                String feedbackValue = getSelectedText(rgFeedback);
                String diaryValue = etDiary.getText().toString();
                selectedEmotion = normalizeEmotionValue(selectedEmotion);
                String emotionLabel = getEmotionLabel(selectedEmotion);

                String aiQuestionValue = (tvAiQuestion != null) ? tvAiQuestion.getText().toString() : "오늘 가장 임팩트 있었던 일을 한 문장으로 표현하면?";
                String aiAnswerValue = (etAiAnswer != null) ? etAiAnswer.getText().toString() : "답변 없음";

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
                    aiRoll = "너는 복잡한 생각을 한 번에 정리해 주는 '통찰력 있는 인생 멘토'야. 군더더기 없이 오늘 하루를 관통하는 짧고 강렬한 응원 및 뼈 때리는 문장 한 마디만 남겨줘.";
                }else{
                    aiRoll = "너는 내담자의 상처를 따뜻하게 치유해 주는 '감정 코칭 전문 심리상담사'야. 친구처럼 든든하고 다정하게, 사용자의 마음에 깊이 공감하고 위로해 줘.";
                }

                String diaryText = etDiary.getText().toString().trim();

                if (diaryText.isEmpty() && (recordedAudioBytes == null || recordedAudioBytes.length == 0)) {
                    android.widget.Toast.makeText(getContext(), "오늘의 이야기나 음성 녹음을 남겨주세요!", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                UsageStatsHelper.PhoneUsageSummary phoneSummary = UsageStatsHelper.getTodayUsageSummary(requireContext());
                String phoneUsagePrompt = phoneSummary.toPromptText();

                String customPrompt = String.format(
                        "%s\n\n" +
                                "지금 보내주는 정보는 나의 오늘 하루 데이터야. 너는 이 데이터들을 면밀히 분석해서 말해줘. " +
                                "글은 약 400자 미만이면 좋겠어. 꼭 400자를 꽉 채울 필요는 없고, 필요한 만큼 자연스럽게 작성해줘.\n\n" +
                                "[오늘의 데이터]\n" +
                                "- 선택한 감정: %s\n" +
                                "- 컨디션 점수: %d점\n" +
                                "- 먹은 식사: %s\n" +
                                "- 오늘 감정에 가장 큰 영향을 준 것: %s\n" +
                                "- 스트레스 정도: %s\n" +
                                "- 피로도: %s\n" +
                                "- 수면 상태: %s\n" +
                                "- 지금 나에게 필요한 것: %s\n" +
                                "- 내가 원하는 피드백 방식: %s\n" +
                                "🌟 [AI가 유저에게 던졌던 특별 맞춤 질문]: %s\n" +
                                "🌟 [이 질문에 대해 유저가 적은 다이렉트 답변]: %s\n" +
                                "- 오늘 나의 이야기: %s\n\n" +
                                "[오늘의 휴대폰 사용 기반 행동 데이터]\n" +
                                "%s\n\n" +
                                "위 데이터를 모두 반영해서 분석해줘. " +
                                "휴대폰 사용 데이터는 진단 목적이 아니라 생활 패턴 참고용이야. " +
                                "불안, 스트레스, 회피성 사용 가능성을 단정하지 말고 가능성 중심으로 조심스럽게 분석해줘. " +
                                "특히 내가 원하는 피드백 방식에 맞춰서 답변해줘."+
                                "휴대폰 사용 데이터는 참고용 보조 지표일 뿐이며, 심리 상태를 확정하거나 진단하지 마. " +
                                "앱 실행 횟수나 짧은 반복 사용이 많더라도 반드시 불안이라고 단정하지 말고, " +
                                "사용자가 피곤하거나 바빠서 자주 확인했을 가능성도 함께 고려해줘. " +
                                "말투는 '귀하' 같은 딱딱한 표현을 쓰지 말고, 사용자를 '당신' 또는 자연스러운 말투로 불러줘. " +
                                "너무 진단 보고서처럼 쓰지 말고, 따뜻하지만 과하게 단정하지 않는 말투로 작성해줘. ",
                        aiRoll, emotionLabel, seekBar.getProgress(),
                        mealInfo.trim().isEmpty() ? "모두 거름" : mealInfo.trim(),
                        influenceValue, stressValue, fatigueValue, sleepValue, needValue, feedbackValue,
                        aiQuestionValue, aiAnswerValue.trim().isEmpty() ? "답변 없음" : aiAnswerValue, diaryValue, phoneUsagePrompt
                );

                String newRecord = String.format("%s|%s|%d|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                        fullTime, selectedEmotion, seekBar.getProgress(), diaryValue, mealInfo,
                        influenceValue, stressValue, fatigueValue, sleepValue, needValue, feedbackValue,
                        aiQuestionValue, aiAnswerValue);

                SharedPreferences.Editor editor = freshPref.edit();
                String oldRecords = freshPref.getString("all_records", "");
                StringBuilder updatedList = new StringBuilder();

                for (String r : oldRecords.split("##")) {
                    if (!r.isEmpty() && !r.startsWith(today)) {
                        updatedList.append(r).append("##");
                    }
                }

                editor.putString("all_records", newRecord + "##" + updatedList.toString());
                editor.apply();

                saveRecord(fullTime, mealInfo, seekBar.getProgress(), diaryValue, "AI 분석 중...");
                cachedAiQuestion = "";
                cachedQuestionTargetDate = "";

                askGemini(customPrompt, recordedAudioBytes, audioMimeType, fullTime, mealInfo, seekBar.getProgress(), etDiary.getText().toString());
            });
        }

        return view;
    }

    // 🌟 [독립 신설 및 통합 완료] onCreateView 내부에 있던 데이터 바인딩 및 자정 판단 파이프라인
    private void refreshTodayData(View view) {
        if (view == null || !isAdded()) return;

        SeekBar seekBar = view.findViewById(R.id.scoreSeekBar);
        TextView tvScoreValue = view.findViewById(R.id.tvScoreValue);
        EditText etDiary = view.findViewById(R.id.etDiary);
        CheckBox cbBreakfast = view.findViewById(R.id.cbBreakfast);
        CheckBox cbLunch = view.findViewById(R.id.cbLunch);
        CheckBox cbDinner = view.findViewById(R.id.cbDinner);
        CheckBox cbLateNight = view.findViewById(R.id.cbLateNight);
        TextView inputDateText = view.findViewById(R.id.inputDateText);

        clearAllGroups();
        etDiary.setText("");
        etAiAnswer.setText("");
        if (tvVoiceStatus != null) {
            tvVoiceStatus.setText("마이크 버튼을 눌러 이야기를 들려주세요");
            tvVoiceStatus.setTextColor(android.graphics.Color.parseColor("#888888"));
        }
        cbBreakfast.setChecked(false);
        cbLunch.setChecked(false);
        cbDinner.setChecked(false);
        cbLateNight.setChecked(false);
        seekBar.setProgress(80);
        tvScoreValue.setText("80점");

        if (inputDateText != null) {
            String todayStr = new SimpleDateFormat("yyyy년 MM월 dd일 EEEE", Locale.KOREAN).format(new Date());
            inputDateText.setText(todayStr);
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userSuffix = (currentUser != null) ? currentUser.getUid() : "guest";

        SharedPreferences pref = requireActivity().getSharedPreferences("DailyRecords_" + userSuffix, Context.MODE_PRIVATE);

        // 🌟 [핵심 기획 이식: 게스트 24시 데이터 자동 파기 처리]
        if (currentUser == null) {
            // 1. 단일 데이터 보관용 GuestRecordStore 자정 만료 가동
            GuestRecordStore.clearIfNotToday(requireContext());

            String currentAllRecords = pref.getString("all_records", "");
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            if (!currentAllRecords.isEmpty()) {
                boolean isTodayRecordExist = false;
                for (String r : currentAllRecords.split("##")) {
                    if (r.startsWith(todayDate)) {
                        isTodayRecordExist = true;
                        break;
                    }
                }
                // 2. 만약 장부에 어제 이전의 기록만 있고 '오늘 생성된 기록'이 단 한 줄도 없다면 즉시 초기화!
                if (!isTodayRecordExist) {
                    pref.edit().putString("all_records", "").apply();
                    Log.d("GuestMode", "24시 자정이 지나 게스트 데이터가 안전하게 자동 파기 및 초기화되었습니다. 🧼");
                }
            }
        }

        String allRecords = pref.getString("all_records", "");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String latestDiaryText = "";
        String latestDiaryDate = "";

        if (!allRecords.isEmpty()) {
            String[] recordsArray = allRecords.split("##");
            for (String r : recordsArray) {
                if (r.isEmpty()) continue;
                if (!r.startsWith(today)) {
                    String[] detail = r.split("\\|");
                    if (detail.length >= 4) {
                        latestDiaryText = "최근 일기 내용: " + detail[3];
                        if (detail.length >= 13) {
                            latestDiaryText += "\n이전 AI 질문: " + detail[11] + "\n그 질문에 대한 답변: " + detail[12];
                        }
                        latestDiaryDate = r.split(" ")[0];
                    }
                    break;
                }
            }
        }

        loadCustomQuestion(latestDiaryText, latestDiaryDate);

        if (currentUser != null) {
            loadTodayRecordFromFirestore(view, currentUser.getUid(), today, seekBar, tvScoreValue, etDiary, cbBreakfast, cbLunch, cbDinner, cbLateNight);
        } else {
            restoreTodayRecordFromLocal(view, allRecords, today, seekBar, tvScoreValue, etDiary, cbBreakfast, cbLunch, cbDinner, cbLateNight);
        }
    }

    // 🌟 [생명주기 동기화 감시망 추가] 유저가 탭을 바꾸거나 홈 화면을 갔다 와서 화면을 다시 마주할 때 리프레시 실시간 가동
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            refreshTodayData(getView());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            refreshTodayData(getView());
        }
    }

    private void loadTodayRecordFromFirestore(
            View view, String uid, String today, SeekBar seekBar, TextView tvScoreValue,
            EditText etDiary, CheckBox cbBreakfast, CheckBox cbLunch, CheckBox cbDinner, CheckBox cbLateNight
    ) {
        if (db == null || uid == null || today == null) {
            updateEmotionHighlight(view);
            return;
        }

        db.collection("users").document(uid).collection("daily_records").document(today).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;
                    if (documentSnapshot == null || !documentSnapshot.exists()) {
                        updateEmotionHighlight(view);
                        return;
                    }
                    restoreInputFromFirestoreDocument(view, documentSnapshot, seekBar, tvScoreValue, etDiary, cbBreakfast, cbLunch, cbDinner, cbLateNight);
                })
                .addOnFailureListener(e -> {
                    Log.w("Firestore", "오늘 기록 불러오기 실패", e);
                    if (isAdded()) updateEmotionHighlight(view);
                });
    }

    private void restoreInputFromFirestoreDocument(
            View view, DocumentSnapshot document, SeekBar seekBar, TextView tvScoreValue,
            EditText etDiary, CheckBox cbBreakfast, CheckBox cbLunch, CheckBox cbDinner, CheckBox cbLateNight
    ) {
        String emotion = document.getString("emotion");
        if (emotion != null && !emotion.trim().isEmpty()) {
            selectedEmotion = normalizeEmotionValue(emotion);
        }
        updateEmotionHighlight(view);

        Long scoreLong = document.getLong("score");
        int score = (scoreLong != null) ? scoreLong.intValue() : 80;
        seekBar.setProgress(score);
        tvScoreValue.setText(score + "점");

        String diary = document.getString("diary");
        etDiary.setText(diary != null ? diary : "");

        String meals = document.getString("meals");
        if (meals == null) meals = "";
        cbBreakfast.setChecked(meals.contains("아침"));
        cbLunch.setChecked(meals.contains("점심"));
        cbDinner.setChecked(meals.contains("저녁"));
        cbLateNight.setChecked(meals.contains("야식"));

        setRadioCheckedByText(rgInfluence, document.getString("influence"));
        setRadioCheckedByText(rgStress, document.getString("stress"));
        setRadioCheckedByText(rgFatigue, document.getString("fatigue"));
        setRadioCheckedByText(rgSleep, document.getString("sleep"));
        setRadioCheckedByText(rgNeed, document.getString("need"));
        setRadioCheckedByText(rgFeedback, document.getString("feedback"));

        String aiQuestion = document.getString("aiQuestion");
        if (tvAiQuestion != null && aiQuestion != null && !aiQuestion.trim().isEmpty()) {
            tvAiQuestion.setText(aiQuestion);
        }

        String aiAnswer = document.getString("aiAnswer");
        if (etAiAnswer != null && aiAnswer != null && !aiAnswer.trim().isEmpty()) {
            etAiAnswer.setText(aiAnswer);
        }
    }

    private void restoreTodayRecordFromLocal(
            View view, String allRecords, String today, SeekBar seekBar, TextView tvScoreValue,
            EditText etDiary, CheckBox cbBreakfast, CheckBox cbLunch, CheckBox cbDinner, CheckBox cbLateNight
    ) {
        if (allRecords == null || !allRecords.contains(today)) {
            updateEmotionHighlight(view);
            return;
        }

        String[] recordsArray = allRecords.split("##");
        for (String record : recordsArray) {
            if (record.startsWith(today)) {
                String[] detail = record.split("\\|");

                if (detail.length >= 5) {
                    if (selectedEmotion.isEmpty()) selectedEmotion = detail[1];
                    updateEmotionHighlight(view);

                    try {
                        seekBar.setProgress(Integer.parseInt(detail[2]));
                        tvScoreValue.setText(detail[2] + "점");
                    } catch (NumberFormatException e) {
                        seekBar.setProgress(80);
                        tvScoreValue.setText("80점");
                    }
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

                if (detail.length > 11 && tvAiQuestion != null) tvAiQuestion.setText(detail[11]);
                if (detail.length > 12 && etAiAnswer != null) etAiAnswer.setText(detail[12]);
                break;
            }
        }
    }

    private void saveRecord(String fullTime, String mealInfo, int score, String diary, String resultText) {
        Context context = getContext();
        if (context == null) return;

        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", fullTime);

        String normalizedEmotion = normalizeEmotionValue(selectedEmotion);
        record.put("emotion", normalizedEmotion);
        record.put("emotionLabel", getEmotionLabel(normalizedEmotion));
        record.put("emotionScore", getEmotionScore(normalizedEmotion));

        record.put("score", score);
        record.put("diary", diary);
        record.put("meals", mealInfo);
        record.put("influence", getSelectedText(rgInfluence));
        record.put("stress", getSelectedText(rgStress));
        record.put("fatigue", getSelectedText(rgFatigue));
        record.put("sleep", getSelectedText(rgSleep));
        record.put("need", getSelectedText(rgNeed));
        record.put("feedback", getSelectedText(rgFeedback));

        UsageStatsHelper.PhoneUsageSummary phoneSummary = UsageStatsHelper.getTodayUsageSummary(context);
        record.put("phoneTotalMinutes", phoneSummary.totalUsageMinutes);
        record.put("phoneOpenCount", phoneSummary.totalOpenCount);
        record.put("phoneShortSessionCount", phoneSummary.shortSessionCount);
        record.put("phoneNightUsageMinutes", phoneSummary.nightUsageMinutes);
        record.put("digitalSignalScore", phoneSummary.digitalSignalScore);
        record.put("digitalPattern", phoneSummary.digitalPattern);

        record.put("aiQuestion", (tvAiQuestion != null) ? tvAiQuestion.getText().toString() : "");
        record.put("aiAnswer", (etAiAnswer != null) ? etAiAnswer.getText().toString() : "");
        record.put("resultText", resultText);

        String dateId = fullTime.split(" ")[0];
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            String uid = currentUser.getUid();
            db.collection("users").document(uid).collection("daily_records").document(dateId).set(record);
        } else {
            GuestRecordStore.saveTodayRecord(context, record, dateId);
        }
    }

    private void loadCustomQuestion(String latestDiary, final String latestDiaryDate) {
        if (latestDiary == null || latestDiary.trim().isEmpty() || model == null) {
            if (tvAiQuestion != null) tvAiQuestion.setText("오늘 가장 임팩트 있었던 일을 한 문장으로 표현하면?");
            return;
        }

        if (latestDiaryDate.equals(cachedQuestionTargetDate) && !cachedAiQuestion.isEmpty()) {
            if (tvAiQuestion != null) tvAiQuestion.setText(cachedAiQuestion);
            return;
        }

        String formattedDate = latestDiaryDate;
        try {
            String[] parts = latestDiaryDate.split("-");
            if (parts.length >= 3) {
                formattedDate = Integer.parseInt(parts[1]) + "월 " + Integer.parseInt(parts[2]) + "일";
            }
        } catch (Exception e) {
            formattedDate = latestDiaryDate;
        }

        String questionPrompt = String.format(
                "사용자가 가장 최근(%s)에 작성했던 과거 일기 내용이야: \"%s\"\n" +
                        "이 내용을 면밀히 분석해서, 사용자가 오늘 하루를 시작하거나 돌아보며 깊이 고찰하고 답할 수 있는 다정하고 사려 깊은 '오늘의 맞춤형 후속 질문'을 딱 한 문장으로만 만들어줘. " +
                        "만약 어제 일기가 아니라 며칠 전 혹은 오랜만에 쓴 일기라면, 오랜만에 기록하러 온 점을 문맥상 아주 자연스럽고 다정하게 언급하면서 후속 질문을 유기적으로 이어가줘. " +
                        "다른 부연 설명이나 인사말은 절대 하지 말고 오직 질문 문장 한 줄만 출력해줘.",
                formattedDate, latestDiary
        );

        Content prompt = new Content.Builder().addText(questionPrompt).build();
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                if (isAdded() && tvAiQuestion != null) {
                    String questionText = result.getText().trim();
                    cachedAiQuestion = questionText;
                    cachedQuestionTargetDate = latestDiaryDate;
                    tvAiQuestion.setText(questionText);
                }
            }
            @Override
            public void onFailure(Throwable t) {
                if (isAdded() && tvAiQuestion != null) {
                    tvAiQuestion.setText("오늘 가장 임팩트 있었던 일을 한 문장으로 표현하면?");
                }
            }
        }, executor);
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
        String[] codes = {"매우_안좋아요", "안좋아요", "보통이에요", "좋아요", "매우_좋아요"};

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
        String[] codes = {"매우_안좋아요", "안좋아요", "보통이에요", "좋아요", "매우_좋아요"};

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
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int roundedScore = Math.round(seekBar.getProgress() / 10.0f) * 10;
                    seekBar.setProgress(roundedScore);
                    tvScoreValue.setText(roundedScore + "점");
                }
            });
        }
    }

    private void askGemini(String userPrompt, byte[] audioBytes, String mimeType, String fullTime, String mealInfo, int score, String diary) {
        if (model == null) return;

        Content.Builder contentBuilder = new Content.Builder();
        if (diary.trim().isEmpty()) {
            String audioInstructions = "\n\n★[음성 녹음 처리 필수 규칙]★\n" +
                    "너는 함께 첨부된 [음성 데이터]를 귀로 듣고 사용자가 말한 받아쓰기(STT) 내용을 파악해야 해.\n" +
                    "그리고 반드시 답변의 '최상단 첫 줄'에 유저가 말한 내용 그대로를 받아적어줘.\n" +
                    "그 바로 아랫줄에 '===STT_END===' 라는 구분자를 정확히 적고, " +
                    "그 다음 줄부터 사용자를 향한 따뜻한 위로와 피드백 대사를 이어가 줘. 사용자의 음성을 듣고 '템포', '톤' 등등을 확인해서 종합적으로 분석해줘.\n\n" +
                    "응답 예시:\n" +
                    "오늘 팀플 과제 때문에 너무 스트레스 받았어\n" +
                    "===STT_END===\n" +
                    "목소리 톤에 스트레스와 피로가 묻어나네요... 오늘 정말 고생 많으셨어요.";
            contentBuilder.addText(audioInstructions + userPrompt);
        } else {
            contentBuilder.addText(userPrompt);
        }

        if (audioBytes != null && audioBytes.length > 0) {
            contentBuilder.addInlineData(audioBytes, mimeType);
            Log.d("Gemini", "오디오 데이터 빌더에 삽입 완료: " + audioBytes.length + " bytes");
        }

        Content prompt = contentBuilder.build();
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String resultText = (result != null) ? result.getText() : null;
                String finalDiary = diary;
                String cleanAiResponse = resultText;

                if (finalDiary.trim().isEmpty() && resultText != null && resultText.contains("===STT_END===")) {
                    try {
                        String[] parts = resultText.split("===STT_END===");
                        if (parts.length >= 2) {
                            finalDiary = parts[0].trim();
                            cleanAiResponse = parts[1].trim();
                        }
                    } catch (Exception e) {
                        Log.e("GeminiParse", "STT 문장 쪼개기 실패", e);
                    }
                }

                if (finalDiary.trim().isEmpty()) finalDiary = "음성 녹음 과정에서 문제가 발생했어요...😥";
                if (cleanAiResponse == null || cleanAiResponse.trim().isEmpty()) cleanAiResponse = "AI 피드백을 생성하지 못했어요.";

                saveRecord(fullTime, mealInfo, score, finalDiary, cleanAiResponse);
                clearTemporaryAudioData();

                if (isAdded() && getActivity() != null) {
                    BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottomNav);
                    if (bottomNav != null) bottomNav.setSelectedItemId(R.id.extra);
                }
            }
            @Override
            public void onFailure(Throwable t) {
                Log.e("Gemini", "에러 발생: " + t.getMessage());
            }
        }, executor);
    }

    private String normalizeEmotionValue(String emotionValue) {
        if (emotionValue == null) return "";
        String value = emotionValue.trim();
        if ("emo1".equals(value) || "one".equals(value) || "매우_안좋아요".equals(value) || "매우 안 좋아요".equals(value)) return "매우_안좋아요";
        if ("emo2".equals(value) || "two".equals(value) || "안좋아요".equals(value) || "안 좋아요".equals(value)) return "안좋아요";
        if ("emo3".equals(value) || "three".equals(value) || "보통이에요".equals(value)) return "보통이에요";
        if ("emo4".equals(value) || "four".equals(value) || "좋아요".equals(value)) return "좋아요";
        if ("emo5".equals(value) || "five".equals(value) || "매우_좋아요".equals(value) || "매우 좋아요".equals(value)) return "매우_좋아요";
        return value;
    }

    private String getEmotionLabel(String emotionValue) {
        String value = normalizeEmotionValue(emotionValue);
        if ("매우_안좋아요".equals(value)) return "매우 안 좋아요";
        if ("안좋아요".equals(value)) return "안 좋아요";
        if ("보통이에요".equals(value)) return "보통이에요";
        if ("좋아요".equals(value)) return "좋아요";
        if ("매우_좋아요".equals(value)) return "매우 좋아요";
        return "감정 미선택";
    }

    private int getEmotionScore(String emotionValue) {
        String value = normalizeEmotionValue(emotionValue);
        if ("raw_emo1".equals(value) || "매우_안좋아요".equals(value)) return 1;
        if ("raw_emo2".equals(value) || "안좋아요".equals(value)) return 2;
        if ("raw_emo3".equals(value) || "보통이에요".equals(value)) return 3;
        if ("raw_emo4".equals(value) || "좋아요".equals(value)) return 4;
        if ("raw_emo5".equals(value) || "매우_좋아요".equals(value)) return 5;
        return 3;
    }

    private void clearTemporaryAudioData() {
        try {
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
            }
            recordedAudioBytes = null;
            audioFile = null;
        } catch (Exception e) {
            Log.e("AudioClean", "임시 데이터 정리 중 에러", e);
        }
    }

    private void startRecording() {
        try {
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
            }
            recordedAudioBytes = null;
            audioFile = java.io.File.createTempFile("off_record_audio", ".wav", requireContext().getCacheDir());

            recorder = new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(audioFile.getAbsolutePath());

            recorder.prepare();
            recorder.start();

            isRecording = true;
            btnVoiceInput.setColorFilter(android.graphics.Color.RED);
            if (tvVoiceStatus != null) {
                tvVoiceStatus.setText("녹음 중입니다... 다시 누르면 중지됩니다.");
                tvVoiceStatus.setTextColor(android.graphics.Color.RED);
            }
        } catch (java.io.IOException e) {
            Log.e("AudioRecord", "녹음 시작 실패", e);
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
                isRecording = false;

                btnVoiceInput.setColorFilter(android.graphics.Color.parseColor("#4CAF50"));
                if (tvVoiceStatus != null) {
                    tvVoiceStatus.setText("음성 녹음 완료! (기록 완료 시 함께 분석됩니다)");
                    tvVoiceStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                }

                if (audioFile != null && audioFile.exists()) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(audioFile);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    fis.close();
                    recordedAudioBytes = baos.toByteArray();
                }
            } catch (Exception e) {
                Log.e("AudioRecord", "녹음 중지 및 파일 변환 실패", e);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }
}