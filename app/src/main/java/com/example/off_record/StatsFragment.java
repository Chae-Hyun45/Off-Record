package com.example.off_record;

import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class StatsFragment extends Fragment {

    private TextView tvStatsTest;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        tvStatsTest = view.findViewById(R.id.tvStatsTest);
        db = FirebaseFirestore.getInstance();

        loadTestData();

        return view;
    }

    private void loadTestData() {
        db.collection("test_collection")
                .document("test_doc")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            String message = document.getString("message");
                            tvStatsTest.setText("Firestore 데이터: " + message);
                        } else {
                            tvStatsTest.setText("문서가 존재하지 않습니다.");
                        }
                    } else {
                        tvStatsTest.setText("데이터 불러오기 실패: " + task.getException().getMessage());
                    }
                });
    }
}