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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        mAuth = FirebaseAuth.getInstance();

        etEmail = view.findViewById(R.id.etLoginEmail);
        etPassword = view.findViewById(R.id.etLoginPassword);
        btnLogin = view.findViewById(R.id.btnLogin);
        btnRegister = view.findViewById(R.id.btnRegister);

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
}