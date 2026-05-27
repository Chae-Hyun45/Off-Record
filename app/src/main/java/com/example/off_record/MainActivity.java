package com.example.off_record;

import androidx.fragment.app.Fragment;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 0. Firestore 초기화 및 테스트 데이터 전송
        db = FirebaseFirestore.getInstance();
        sendTestData();

        bottomNav = findViewById(R.id.bottomNav);

        // 1. 앱 실행 시 첫 화면은 캘린더로 설정
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, new CalendarFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.calendar);
        }

        // 2. 바텀 네비게이션 클릭
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.record) {
                showEmotionDialog();
                return false;
            }

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

    // Firestore 테스트 데이터 전송 함수
    private void sendTestData() {
        Map<String, Object> testData = new HashMap<>();
        testData.put("message", "Firestore 연결 성공!");
        testData.put("timestamp", System.currentTimeMillis());

        db.collection("test_collection")
                .document("test_doc")
                .set(testData)
                .addOnSuccessListener(aVoid -> android.util.Log.d("FirestoreTest", "데이터 쓰기 성공!"))
                .addOnFailureListener(e -> android.util.Log.w("FirestoreTest", "데이터 쓰기 실패", e));
    }

    // 3. 감정 선택 팝업창 띄우는 함수
    private void showEmotionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_emotion_select);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int[] emojiIds = {R.id.emo1, R.id.emo2, R.id.emo3, R.id.emo4, R.id.emo5};

        for (int id : emojiIds) {
            ImageButton btn = dialog.findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    dialog.dismiss();

                    // 4. 선택한 감정의 ID 정보를 Bundle에 담아서 InputFragment로 전달
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

                    if (bottomNav != null) {
                        bottomNav.getMenu().findItem(R.id.record).setChecked(true);
                    }
                });
            }
        }

        dialog.show();
    }
}