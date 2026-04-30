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
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class InputFragment extends Fragment {

    private String selectedEmotion = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input, container, false);

        if (getArguments() != null) {
            selectedEmotion = getArguments().getString("selected_emotion", "");
        }

        // 사용할 모든 뷰들을 먼저 미리 찾아둡니다 (리스너 안팎에서 공통 사용)
        SeekBar seekBar = view.findViewById(R.id.scoreSeekBar);
        TextView tvScoreValue = view.findViewById(R.id.tvScoreValue);
        EditText etDiary = view.findViewById(R.id.etDiary);
        CheckBox cb1 = view.findViewById(R.id.cbBreakfast);
        CheckBox cb2 = view.findViewById(R.id.cbLunch);
        CheckBox cb3 = view.findViewById(R.id.cbDinner);
        CheckBox cb4 = view.findViewById(R.id.cbLateNight);
        View btnComplete = view.findViewById(R.id.btnComplete);

        setupEmotionHighlight(view, selectedEmotion);
        setupScoreInput(view);
        setupMealSelection(view);

        // 1. 기존 기록이 있다면 불러와서 미리 채워넣기
        SharedPreferences pref = getActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        String allRecords = pref.getString("all_records", "");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (allRecords.contains(today)) {
            String[] recordsArray = allRecords.split("##");
            for (String record : recordsArray) {
                if (record.startsWith(today)) {
                    String[] detail = record.split("\\|");
                    if (detail.length >= 5) {
                        // 점수 세팅
                        int savedScore = Integer.parseInt(detail[2]);
                        seekBar.setProgress(savedScore);
                        tvScoreValue.setText(savedScore + "점");
                        // 일기 세팅
                        etDiary.setText(detail[3]);
                        // 식사 정보 세팅
                        cb1.setChecked(detail[4].contains("아침"));
                        cb2.setChecked(detail[4].contains("점심"));
                        cb3.setChecked(detail[4].contains("저녁"));
                        cb4.setChecked(detail[4].contains("야식"));

                        // 체크박스 투명도 조절 (메서드에서 설정한 효과 적용)
                        cb1.setAlpha(cb1.isChecked() ? 1.0f : 0.4f);
                        cb2.setAlpha(cb2.isChecked() ? 1.0f : 0.4f);
                        cb3.setAlpha(cb3.isChecked() ? 1.0f : 0.4f);
                        cb4.setAlpha(cb4.isChecked() ? 1.0f : 0.4f);
                    }
                    break;
                }
            }
        }

        // 2. 완료 버튼 클릭 시 데이터 저장 및 화면 이동
        if (btnComplete != null) {
            btnComplete.setOnClickListener(v -> {
                // 데이터 수집
                int score = (seekBar != null) ? seekBar.getProgress() : 0;
                String diary = (etDiary != null) ? etDiary.getText().toString() : "";
                String mealInfo = (cb1.isChecked() ? "아침 " : "") + (cb2.isChecked() ? "점심 " : "")
                        + (cb3.isChecked() ? "저녁 " : "") + (cb4.isChecked() ? "야식" : "");

                String fullTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

                // SharedPreferences 저장
                SharedPreferences.Editor editor = pref.edit();
                String oldRecords = pref.getString("all_records", "");
                String[] recordsArray = oldRecords.split("##");
                StringBuilder updatedList = new StringBuilder();

                // 기존 오늘 기록 삭제 로직
                for (String record : recordsArray) {
                    if (!record.isEmpty() && !record.startsWith(today)) {
                        updatedList.append(record).append("##");
                    }
                }

                // 새 기록 추가
                String newRecord = fullTime + "|" + selectedEmotion + "|" + score + "|" + diary + "|" + mealInfo;
                String finalRecords = newRecord + "##" + updatedList.toString();

                editor.putString("all_records", finalRecords);
                editor.apply();

                // 화면 이동
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frameLayout, new ExtraFragment())
                            .commit();
                }
            });
        }

        return view;
    }

    // 감정 강조 함수
    private void setupEmotionHighlight(View view, String emotion) {
        ImageButton btnHappy = view.findViewById(R.id.btnHappy);
        ImageButton btnSmile = view.findViewById(R.id.btnSmile);
        ImageButton btnNeutral = view.findViewById(R.id.btnNeutral);
        ImageButton btnSad = view.findViewById(R.id.btnSad);
        ImageButton btnAngry = view.findViewById(R.id.btnAngry);

        if (btnHappy != null) btnHappy.setBackgroundResource(0);
        if (btnSmile != null) btnSmile.setBackgroundResource(0);
        if (btnNeutral != null) btnNeutral.setBackgroundResource(0);
        if (btnSad != null) btnSad.setBackgroundResource(0);
        if (btnAngry != null) btnAngry.setBackgroundResource(0);

        if (emotion.equals("emo1") && btnHappy != null) btnHappy.setBackgroundResource(R.drawable.circle_bg);
        else if (emotion.equals("emo2") && btnSmile != null) btnSmile.setBackgroundResource(R.drawable.circle_bg);
        else if (emotion.equals("emo3") && btnNeutral != null) btnNeutral.setBackgroundResource(R.drawable.circle_bg);
        else if (emotion.equals("emo4") && btnSad != null) btnSad.setBackgroundResource(R.drawable.circle_bg);
        else if (emotion.equals("emo5") && btnAngry != null) btnAngry.setBackgroundResource(R.drawable.circle_bg);
    }

    // 점수 입력 함수
    private void setupScoreInput(View view) {
        SeekBar seekBar = view.findViewById(R.id.scoreSeekBar);
        TextView tvScoreValue = view.findViewById(R.id.tvScoreValue);
        if (seekBar != null && tvScoreValue != null) {
            tvScoreValue.setOnClickListener(v -> {
                int current = seekBar.getProgress();
                int next = (current >= 100) ? 0 : ((current / 10) + 1) * 10;
                seekBar.setProgress(next);
                tvScoreValue.setText(next + "점");
            });
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int snapped = Math.round(progress / 10.0f) * 10;
                    tvScoreValue.setText(snapped + "점");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    int snapped = Math.round(seekBar.getProgress() / 10.0f) * 10;
                    seekBar.setProgress(snapped);
                }
            });
        }
    }

    // 식사 선택 함수
    private void setupMealSelection(View view) {
        int[] mealIds = {R.id.cbBreakfast, R.id.cbLunch, R.id.cbDinner, R.id.cbLateNight};
        for (int id : mealIds) {
            CheckBox cb = view.findViewById(id);
            if (cb != null) {
                cb.setAlpha(cb.isChecked() ? 1.0f : 0.4f);
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) buttonView.setAlpha(1.0f);
                    else buttonView.setAlpha(0.4f);
                });
            }
        }
    }
}