package com.example.off_record;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

public class InputFragment extends Fragment {

    private String selectedEmotion = "";
    private FirebaseFirestore db;
    private GenerativeModelFutures model;

    // XML에서 매칭되는 뷰 컴포넌트 선언
    private SeekBar energySeekBar, scoreSeekBar;
    private EditText etDiary;
    private TextView tvScoreValue, inputDateText;
    private View btnComplete;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input, container, false);

        db = FirebaseFirestore.getInstance();

        // Gemini AI 모델 안전 초기화
        try {
            GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.1-flash-lite");
            model = GenerativeModelFutures.from(ai);
        } catch (Exception e) {
            android.util.Log.e("GeminiError", "모델 초기화 실패: " + e.getMessage());
        }

        // Arguments로부터 전달받은 선택 감정 캐치
        if (getArguments() != null) {
            selectedEmotion = getArguments().getString("selected_emotion", "");
        }

        // 새로운 3D XML 컴포넌트 ID 매핑 (오류 요소 완벽 제거)
        inputDateText = view.findViewById(R.id.inputDateText);
        energySeekBar = view.findViewById(R.id.energySeekBar);
        scoreSeekBar = view.findViewById(R.id.scoreSeekBar);
        etDiary = view.findViewById(R.id.etDiary);
        btnComplete = view.findViewById(R.id.btnComplete);

        // 상단 날짜 가이드 세팅
        String todayDisplay = new SimpleDateFormat("MMMM d'th', yyyy", Locale.ENGLISH).format(new Date());
        if (inputDateText != null) {
            inputDateText.setText(todayDisplay);
        }

        setupEmotionSelection(view);
        loadTodayRecordFromServer(view);

        // Capture Reflection (입력 완료) 버튼 이벤트 리스너 등록
        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> {
                btnComplete.setEnabled(false); // 중복 클릭 차단

                int energyLevel = (energySeekBar != null) ? energySeekBar.getProgress() : 50;
                int moodFrequency = (scoreSeekBar != null) ? scoreSeekBar.getProgress() : 50;
                String diaryEntry = (etDiary != null) ? etDiary.getText().toString() : "";

                // 3D 수집 데이터를 기반으로 따뜻한 감성 일기용 AI 맞춤형 프롬프트 생성
                String customPrompt = String.format(
                        "지금 보내주는 정보는 나의 오늘 하루 데이터야. 이 데이터들을 면밀히 분석해서 " +
                                "나의 마음을 위로해주고 따뜻하게 감싸주는 공감형 일기를 정성스레 만들어줘. " +
                                "글의 길이는 너무 길지 않게 400자 미만으로 유기적이고 부드러운 에세이 형태로 구성해줘.\n\n" +
                                "[오늘의 기분 데이터]\n" +
                                "- 선택한 감정 코드: %s (참고: emo1은 가장 행복함, emo5는 슬픔/분노 등 격한 감정)\n" +
                                "- 에너지 레벨: %d%%\n" +
                                "- 기분 안정도 주파수: %d%%\n" +
                                "- 사용자의 짤막한 속마음 메모: %s\n\n" +
                                "이 정량적인 상태와 감정을 분석해서 위로의 말을 건네는 감성적인 오늘 하루의 성찰 일기를 완성해줘.",
                        selectedEmotion.isEmpty() ? "emo3" : selectedEmotion,
                        energyLevel,
                        moodFrequency,
                        diaryEntry.isEmpty() ? "작성된 메모 없음" : diaryEntry
                );

                // Gemini에게 인공지능 분석 및 합성 요청
                askGemini(customPrompt);
            });
        }

        return view;
    }

    /**
     * 파이어베이스 서버로부터 오늘의 기존 작성본 데이터를 로드하여 바인딩
     */
    private void loadTodayRecordFromServer(View view) {
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";

        db.collection("users").document(uid).collection("daily_records").document(todayKey).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        if (selectedEmotion.isEmpty()) {
                            selectedEmotion = doc.getString("emotion");
                            if (selectedEmotion == null) selectedEmotion = "";
                        }
                        updateEmotionHighlight(view);

                        Long energyLong = doc.getLong("energy_level");
                        if (energyLong != null && energySeekBar != null) {
                            energySeekBar.setProgress(energyLong.intValue());
                        }

                        Long scoreLong = doc.getLong("score");
                        if (scoreLong != null && scoreSeekBar != null) {
                            scoreSeekBar.setProgress(scoreLong.intValue());
                        }

                        if (etDiary != null) {
                            etDiary.setText(doc.getString("diary"));
                        }
                    } else {
                        updateEmotionHighlight(view);
                    }
                });
    }

    /**
     * 이모지 선택 이벤트 바인딩
     */
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

    /**
     * 선택된 이모지 컴포넌트의 3D 입체 하이라이트 효과 갱신
     */
    private void updateEmotionHighlight(View view) {
        int[] resIds = {R.id.btnHappy, R.id.btnSmile, R.id.btnNeutral, R.id.btnSad, R.id.btnAngry};
        String[] codes = {"emo1", "emo2", "emo3", "emo4", "emo5"};

        for (int i = 0; i < resIds.length; i++) {
            ImageButton button = view.findViewById(resIds[i]);
            if (button != null) {
                boolean isSelected = selectedEmotion.equals(codes[i]);
                button.setSelected(isSelected);
                button.setAlpha(isSelected ? 1.0f : 0.55f);
                button.setScaleX(isSelected ? 1.08f : 1.0f);
                button.setScaleY(isSelected ? 1.08f : 1.0f);
            }
        }
    }

    /**
     * 제미니 엔진 비동기 호출 인터페이스
     */
    private void askGemini(String userPrompt) {
        if (model == null) {
            saveAndNavigate("AI 성찰 일기가 정상적으로 생성되지 못해 기본 데이터만 아늑하게 기록되었습니다.");
            return;
        }

        Content prompt = new Content.Builder().addText(userPrompt).build();
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                saveAndNavigate(result.getText());
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                saveAndNavigate("오늘 하루 참 고생 많았어. 비록 작지만 소중한 너의 생각들이 내일은 더 아름다운 꽃을 피울 거야. (AI 로드 지연)");
            }
        }, executor);
    }

    /**
     * 로컬 스토리지 및 파이어스토어 서버 트랜잭션 동시 완료 후 화면 탭 전환
     */
    private void saveAndNavigate(String aiStory) {
        if (getContext() == null || getView() == null) return;

        SharedPreferences pref = requireActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        String fullTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String todayKey = fullTime.split(" ")[0];

        int energy = (energySeekBar != null) ? energySeekBar.getProgress() : 50;
        int score = (scoreSeekBar != null) ? scoreSeekBar.getProgress() : 50;

        // 로컬 SharedPreferences 연속 아카이빙 명세서 포맷
        String newRecordStr = String.format(Locale.getDefault(), "%s|%s|%d|%d|%s",
                fullTime, selectedEmotion, energy, score, aiStory);

        SharedPreferences.Editor editor = pref.edit();
        String oldRecords = pref.getString("all_records", "");
        StringBuilder updatedList = new StringBuilder();
        for (String r : oldRecords.split("##")) {
            if (!r.isEmpty() && !r.startsWith(todayKey)) {
                updatedList.append(r).append("##");
            }
        }
        editor.putString("all_records", newRecordStr + "##" + updatedList.toString());
        editor.apply();

        // 파이어스토어 데이터베이스 업로드 규격 정의
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", fullTime);
        record.put("emotion", selectedEmotion);
        record.put("energy_level", energy);
        record.put("score", score);
        record.put("diary", aiStory);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";

        db.collection("users").document(uid)
                .collection("daily_records").document(todayKey)
                .set(record)
                .addOnSuccessListener(aVoid -> {
                    if (getActivity() != null) {
                        BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottomNav);
                        if (bottomNav != null) {
                            bottomNav.setSelectedItemId(R.id.extra); // 대시보드 성찰 탭으로 안전하게 이동
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "네트워크 상태 불량으로 임시 보관함에 저장되었습니다.", Toast.LENGTH_SHORT).show();
                });
    }
}