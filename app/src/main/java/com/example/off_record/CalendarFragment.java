package com.example.off_record;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class CalendarFragment extends Fragment {

    private TextView selectedDate, detailText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        CalendarView calendarView = view.findViewById(R.id.calendarView);
        selectedDate = view.findViewById(R.id.selectedDate);
        detailText = view.findViewById(R.id.detailText);

        // 달력 날짜 클릭 이벤트
        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                // 월은 0부터 시작하므로 +1 해줍니다.
                String date = dayOfMonth + "일";
                selectedDate.setText(date);

                // 임시 데이터 (나중에 데이터베이스와 연결하세요)
                detailText.setText(year + "년 " + (month + 1) + "월 " + dayOfMonth + "일에 쓴 일기 내용이 나옵니다.");
            }
        });

        return view;
    }
}