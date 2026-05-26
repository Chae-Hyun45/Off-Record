package com.example.off_record;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FirebaseFirestore;

public class StatsFragment extends Fragment {

    private TextView tvStatsTest;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        tvStatsTest = view.findViewById(R.id.tvStatsTest);
        db = FirebaseFirestore.getInstance();

        loadRecordCount();

        return view;
    }

    private void loadRecordCount() {
        db.collection("daily_records")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    tvStatsTest.setText("누적 감정 기록: " + count + "개");
                })
                .addOnFailureListener(e -> {
                    tvStatsTest.setText("기록 통계를 불러오지 못했습니다.");
                    android.util.Log.e("StatsFragment", "통계 불러오기 실패", e);
                });
    }
}
