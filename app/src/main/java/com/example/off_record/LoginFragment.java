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

        etEmail = view.findViewById(R.id.etLoginEmail);
        etPassword = view.findViewById(R.id.etLoginPassword);
        btnLogin = view.findViewById(R.id.btnLogin);
        btnRegister = view.findViewById(R.id.btnRegister);
        btnTestUser1 = view.findViewById(R.id.btnTestUser1);
        btnTestUser2 = view.findViewById(R.id.btnTestUser2);

        if (btnRegister != null) {
            btnRegister.setOnClickListener(v -> {
                String email = (etEmail != null) ? etEmail.getText().toString().trim() : "";
                String password = (etPassword != null) ? etPassword.getText().toString().trim() : "";

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
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                String email = (etEmail != null) ? etEmail.getText().toString().trim() : "";
                String password = (etPassword != null) ? etPassword.getText().toString().trim() : "";

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(getContext(), "이메일과 비밀번호를 모두 입력해 주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                loginUser(email, password);
            });
        }

        if (btnTestUser1 != null) {
            btnTestUser1.setOnClickListener(v -> fastLogin("test1@naver.com", "123456"));
        }

        if (btnTestUser2 != null) {
            btnTestUser2.setOnClickListener(v -> fastLogin("test2@naver.com", "123456"));
        }
        
        return view;
    }

    private void registerUser(String email, String password) {
        if (getActivity() == null) return;
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(getActivity(), task -> {
                    if (!isAdded() || getContext() == null) return;
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "회원가입 성공! 로그인 버튼을 눌러주세요.", Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMsg = (task.getException() != null) ? task.getException().getMessage() : "오류 발생";
                        Toast.makeText(getContext(), "회원가입 실패: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginUser(String email, String password) {
        if (getActivity() == null) return;
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(getActivity(), task -> {
                    if (!isAdded() || getContext() == null) return;
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "로그인 성공! 환영합니다.", Toast.LENGTH_SHORT).show();
                        navigateToSettings();
                    } else {
                        Toast.makeText(getContext(), "로그인 실패: 정보를 확인하세요.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fastLogin(String email, String password) {
        if (getActivity() == null) return;
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(getActivity(), task -> {
                    if (!isAdded() || getContext() == null) return;
                    if (task.isSuccessful()) {
                        String name = email.contains("test1") ? "사용자 1" : "사용자 2";
                        Toast.makeText(getContext(), name + "님 환영합니다!", Toast.LENGTH_SHORT).show();
                        navigateToSettings();
                    } else {
                        Toast.makeText(getContext(), "테스트 계정 로그인 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToSettings() {
        if (isAdded()) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, new SettingsFragment())
                    .commit();
        }
    }
}
