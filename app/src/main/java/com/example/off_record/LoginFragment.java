package com.example.off_record;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginFragment extends Fragment {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private FirebaseAuth mAuth;
    private Button btnTestUser1, btnTestUser2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        mAuth = FirebaseAuth.getInstance();

        // 1. 기존 컴포넌트 연결
        etEmail = view.findViewById(R.id.etLoginEmail);
        etPassword = view.findViewById(R.id.etLoginPassword);
        btnLogin = view.findViewById(R.id.btnLogin);
        btnRegister = view.findViewById(R.id.btnRegister);

        // 💡 [추가] XML에 추가한 테스트 버튼 1, 2를 자바 객체와 연결합니다.
        btnTestUser1 = view.findViewById(R.id.btnTestUser1);
        btnTestUser2 = view.findViewById(R.id.btnTestUser2);

        // [이메일로 회원가입] 버튼 클릭
        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "이메일과 비밀번호를 모두 입력해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(getContext(), "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            registerUser(email, password);
        });

        // [로그인] 버튼 클릭
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "이메일과 비밀번호를 모두 입력해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            loginUser(email, password);
        });

        // 💡 테스트 유저 1 버튼 클릭 시
        if (btnTestUser1 != null) {
            btnTestUser1.setOnClickListener(v -> {
                // 🛠️ userA@test.com 대신 [진짜 확인한 1번 이메일]과 [어제 설정한 비번] 넣기!
                fastLogin("test1@naver.com", "123456");
            });
        }

// 💡 테스트 유저 2 버튼 클릭 시
        if (btnTestUser2 != null) {
            btnTestUser2.setOnClickListener(v -> {
                // 🛠️ userB@test.com 대신 [진짜 확인한 2번 이메일]과 [어제 설정한 비번] 넣기!
                fastLogin("test2@naver.com", "123456");
            });
        }
        return view;
    }

    // 회원가입 로직
    private void registerUser(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(getContext(), "회원가입 성공! 로그인 버튼을 눌러주세요.", Toast.LENGTH_SHORT).show();
                        Log.d("LOGIN_DEBUG", "회원가입 성공 UID: " + (user != null ? user.getUid() : "null"));
                    } else {
                        String errorMsg = (task.getException() != null) ? task.getException().getMessage() : "알 수 없는 오류";
                        Toast.makeText(getContext(), "회원가입 실패: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 로그인 로직
    private void loginUser(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(getContext(), "로그인 성공! 환영합니다.", Toast.LENGTH_SHORT).show();
                        Log.d("LOGIN_DEBUG", "로그인 성공 UID: " + (user != null ? user.getUid() : "null"));

                        // 로그인 완료 시 기쁨님이 만든 세팅(설정) 화면으로 이동
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.frameLayout, new SettingsFragment())
                                .commit();
                    } else {
                        Toast.makeText(getContext(), "로그인 실패: 이메일 또는 비밀번호를 확인하세요.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 💡 [추가] 테스트용 원터치 빠른 로그인 헬퍼 메서드
    // 💡 [수정 완료] 테스트용 원터치 빠른 로그인 헬퍼 메서드
    private void fastLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        // 💡 [여기서 토스트 문구를 닉네임 맞춤형으로 변경합니다!]
                        String toastMessage = "로그인 성공! 환영합니다.";
                        if (email != null) {
                            if (email.contains("test1")) {
                                toastMessage = "사용자 1 로그인 성공! ";
                            } else if (email.contains("test2")) {
                                toastMessage = "사용자 2 로그인 성공! ";
                            }
                        }
                        Toast.makeText(getContext(), toastMessage, Toast.LENGTH_SHORT).show();

                        Log.d("LOGIN_DEBUG", "테스트 로그인 성공 UID: " + (user != null ? user.getUid() : "null"));

                        // 로그인 성공 시 메인 세팅 화면으로 전환
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.frameLayout, new SettingsFragment())
                                .commit();
                    } else {
                        Toast.makeText(getContext(), "테스트 계정 로그인 실패. 파이어베이스 계정이나 비밀번호를 확인해 주세요.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}