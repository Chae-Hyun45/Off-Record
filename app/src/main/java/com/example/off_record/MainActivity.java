package com.example.off_record;

import androidx.fragment.app.Fragment;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);

        // 1. 앱 실행 시 첫 화면은 캘린더로 설정
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, new CalendarFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.calendar);
        }

        // 2. 바텀 네비게이션 클릭 리스너
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.record) {
                // 👉 가운데 기록 버튼: 팝업 띄우기 (화면 전환은 팝업 내부 클릭 시 발생)
                showEmotionDialog();
                return false; // 하단바 선택 표시가 바뀌지 않도록 false 리턴
            }

            // 나머지 버튼들에 대한 화면 전환 처리
            Fragment selected = null;
            if (id == R.id.calendar) {
                selected = new CalendarFragment();
            } else if (id == R.id.stats) {
                selected = new StatsFragment();
            } else if (id == R.id.extra) {
                selected = new ExtraFragment();
            } else if (id == R.id.settings) {
                selected = new SettingsFragment();
            }

            if (selected != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, selected)
                        .commit();
                return true;
            }

            return false;
        });
    }

    // 3. 감정 선택 팝업창을 띄우는 함수
    private void showEmotionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_emotion_select);

        // 팝업 배경을 투명하게 해서 커스텀 디자인(말풍선 등)이 잘 보이게 함
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // 팝업 내부의 이모지 버튼 ID 배열
        int[] emojiIds = {R.id.emo1, R.id.emo2, R.id.emo3, R.id.emo4, R.id.emo5};

        // 각 버튼에 클릭 이벤트 설정
        for (int id : emojiIds) {
            ImageButton btn = dialog.findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    dialog.dismiss(); // 팝업 닫기

                    // 👉 4. 선택한 감정의 ID 정보를 Bundle에 담아서 InputFragment로 전달
                    InputFragment fragment = new InputFragment();
                    Bundle bundle = new Bundle();

                    // 클릭된 버튼의 Resource ID 이름을 문자열로 보냄 (예: "emo1")
                    String emotionKey = getResources().getResourceEntryName(id);
                    bundle.putString("selected_emotion", emotionKey);
                    fragment.setArguments(bundle);

                    // 화면 전환
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frameLayout, fragment)
                            .commit();
                });
            }
        }

        dialog.show();
    }
}