package com.example.off_record;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AiChatFragment extends Fragment {

    private String recordContext = "";
    private String chatSessionId = "";

    private final List<Map<String, String>> chatHistory = new ArrayList<>();
    private GenerativeModelFutures chatModel;
    private FirebaseFirestore db;

    private LinearLayout chatContainer;
    private ScrollView chatScrollView;
    private EditText etChatInput;
    private ImageButton btnSend;

    private View typingRow = null;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private int typingDotCount = 0;
    private Runnable typingRunnable;

    public static AiChatFragment newInstance(String recordContext, String chatSessionId) {
        AiChatFragment f = new AiChatFragment();
        Bundle args = new Bundle();
        args.putString("recordContext", recordContext);
        args.putString("chatSessionId", chatSessionId);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ai_chat, container, false);

        if (getArguments() != null) {
            recordContext = getArguments().getString("recordContext", "");
            chatSessionId = getArguments().getString("chatSessionId", "");
        }

        db = FirebaseFirestore.getInstance();
        chatContainer  = view.findViewById(R.id.chatContainer);
        chatScrollView = view.findViewById(R.id.chatScrollView);
        etChatInput    = view.findViewById(R.id.etChatInput);
        btnSend        = view.findViewById(R.id.btnSend);
        View btnBack   = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        btnSend.setOnClickListener(v -> trySendMessage());
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { trySendMessage(); return true; }
            return false;
        });

        try {
            GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.1-flash-lite");
            chatModel = GenerativeModelFutures.from(ai);
        } catch (Exception e) {
            android.util.Log.e("ChatError", "AI 초기화 실패: " + e.getMessage());
        }

        loadOrStartChat();
        return view;
    }

    private void loadOrStartChat() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { startFreshChat(); return; }

        db.collection("users").document(user.getUid())
                .collection("chat_sessions").document(chatSessionId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    if (snapshot.exists()) {
                        List<Map<String, String>> saved =
                                (List<Map<String, String>>) snapshot.get("messages");
                        if (saved != null && !saved.isEmpty()) {
                            addDivider("이전 대화 이어서 할게요 💬");
                            for (Map<String, String> msg : saved) {
                                String role = msg.get("role");
                                String text = msg.get("text");
                                String time = msg.get("time");
                                if (role == null || text == null) continue;
                                chatHistory.add(msg);
                                addBubble(text, "user".equals(role), time, false);
                            }
                            return;
                        }
                    }
                    startFreshChat();
                })
                .addOnFailureListener(e -> { if (isAdded()) startFreshChat(); });
    }

    private void startFreshChat() {
        if (chatModel == null) { addDivider("⚠️ AI에 연결할 수 없어요"); return; }
        showTypingIndicator();

        String firstPrompt = recordContext +
                "\n\n위 기록을 읽고, 사용자에게 따뜻하게 첫 인사를 건네줘. 2~3문장, 짧게.";

        Futures.addCallback(
                chatModel.generateContent(new Content.Builder().addText(firstPrompt).build()),
                new FutureCallback<GenerateContentResponse>() {
                    @Override public void onSuccess(GenerateContentResponse result) {
                        if (!isAdded()) return;
                        String greeting = result.getText();
                        if (greeting == null || greeting.trim().isEmpty())
                            greeting = "안녕하세요! 오늘 기록을 봤어요. 더 이야기 나눠볼까요? 😊";
                        String now = getCurrentTime();
                        Map<String, String> msg = new HashMap<>();
                        msg.put("role", "ai"); msg.put("text", greeting); msg.put("time", now);
                        chatHistory.add(msg);
                        String finalText = greeting;
                        requireActivity().runOnUiThread(() -> {
                            hideTypingIndicator();
                            addBubble(finalText, false, now, true);
                        });
                    }
                    @Override public void onFailure(Throwable t) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            hideTypingIndicator();
                            addDivider("⚠️ AI 연결 실패. 잠시 후 다시 시도해주세요.");
                        });
                    }
                }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void trySendMessage() {
        if (etChatInput == null) return;
        String msg = etChatInput.getText().toString().trim();
        if (msg.isEmpty()) return;
        etChatInput.setText("");
        sendChatMessage(msg);
    }

    private void sendChatMessage(String userMessage) {
        if (chatModel == null) return;

        String now = getCurrentTime();
        addBubble(userMessage, true, now, true);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user"); userMsg.put("text", userMessage); userMsg.put("time", now);
        chatHistory.add(userMsg);

        showTypingIndicator();

        StringBuilder fullPrompt = new StringBuilder(recordContext);
        fullPrompt.append("\n\n--- 대화 내역 ---\n");
        for (Map<String, String> m : chatHistory) {
            fullPrompt.append("user".equals(m.get("role")) ? "사용자: " : "AI: ")
                    .append(m.get("text")).append("\n");
        }
        fullPrompt.append("---\n마지막 사용자 메시지에 이어서 AI 답변만 출력해. 'AI:' 접두사 없이.");

        Futures.addCallback(
                chatModel.generateContent(new Content.Builder().addText(fullPrompt.toString()).build()),
                new FutureCallback<GenerateContentResponse>() {
                    @Override public void onSuccess(GenerateContentResponse result) {
                        if (!isAdded()) return;
                        String reply = result.getText();
                        if (reply == null || reply.trim().isEmpty()) reply = "다시 말해줄 수 있어요?";
                        String replyTime = getCurrentTime();
                        Map<String, String> aiMsg = new HashMap<>();
                        aiMsg.put("role", "ai"); aiMsg.put("text", reply); aiMsg.put("time", replyTime);
                        chatHistory.add(aiMsg);
                        String finalReply = reply;
                        requireActivity().runOnUiThread(() -> {
                            hideTypingIndicator();
                            addBubble(finalReply, false, replyTime, true);
                            saveChatToFirestore();
                        });
                    }
                    @Override public void onFailure(Throwable t) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            hideTypingIndicator();
                            addDivider("⚠️ 오류가 발생했어요. 다시 시도해주세요.");
                        });
                    }
                }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void showTypingIndicator() {
        if (getContext() == null || chatContainer == null) return;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dpToPx(4), 0, dpToPx(4));
        row.setLayoutParams(rp);
        row.setTag("typing_row");

        TextView bubble = new TextView(getContext());
        bubble.setText("입력중");
        bubble.setTextSize(15f);
        bubble.setTextColor(Color.parseColor("#888888"));
        bubble.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(dpToPx(16));
        shape.setColor(Color.parseColor("#EFEFEF"));
        bubble.setBackground(shape);

        row.addView(bubble);
        chatContainer.addView(row);
        typingRow = row;
        scrollToBottom();

        typingDotCount = 0;
        typingRunnable = new Runnable() {
            @Override public void run() {
                if (typingRow == null || !isAdded()) return;
                typingDotCount = (typingDotCount + 1) % 4;
                StringBuilder dots = new StringBuilder("입력중");
                for (int i = 0; i < typingDotCount; i++) dots.append(".");
                bubble.setText(dots.toString());
                typingHandler.postDelayed(this, 400);
            }
        };
        typingHandler.postDelayed(typingRunnable, 400);
    }

    private void hideTypingIndicator() {
        typingHandler.removeCallbacks(typingRunnable);
        if (typingRow != null && chatContainer != null) {
            chatContainer.removeView(typingRow);
            typingRow = null;
        }
    }

    private void addBubble(String text, boolean isUser, String time, boolean animate) {
        if (getContext() == null || chatContainer == null) return;

        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isUser ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.setMargins(0, dpToPx(6), 0, dpToPx(2));
        wrapper.setLayoutParams(wp);

        TextView bubble = new TextView(getContext());
        bubble.setText(text);
        bubble.setTextSize(15f);
        bubble.setLineSpacing(dpToPx(2), 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(isUser ? dpToPx(64) : 0, 0, isUser ? 0 : dpToPx(64), 0);
        bubble.setLayoutParams(bp);
        bubble.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(dpToPx(18));
        if (isUser) {
            shape.setColor(Color.parseColor("#385543"));
            bubble.setTextColor(Color.WHITE);
        } else {
            shape.setColor(Color.WHITE);
            shape.setStroke(dpToPx(1), Color.parseColor("#E5E5E5"));
            bubble.setTextColor(Color.parseColor("#2F2F2F"));
        }
        bubble.setBackground(shape);
        wrapper.addView(bubble);

        if (time != null && !time.isEmpty()) {
            TextView tvTime = new TextView(getContext());
            tvTime.setText(time);
            tvTime.setTextSize(11f);
            tvTime.setTextColor(Color.parseColor("#BBBBBB"));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(isUser ? 0 : dpToPx(4), dpToPx(2), isUser ? dpToPx(4) : 0, dpToPx(4));
            tvTime.setLayoutParams(tp);
            wrapper.addView(tvTime);
        }

        if (animate) {
            wrapper.setAlpha(0f);
            wrapper.animate().alpha(1f).setDuration(200).start();
        }

        chatContainer.addView(wrapper);
        scrollToBottom();
    }

    private void addDivider(String text) {
        if (getContext() == null || chatContainer == null) return;
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(Color.parseColor("#AAAAAA"));
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dpToPx(10), 0, dpToPx(10));
        tv.setLayoutParams(p);
        chatContainer.addView(tv);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (chatScrollView != null)
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date());
    }

    private void saveChatToFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || chatSessionId.isEmpty()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("messages", new ArrayList<>(chatHistory));
        data.put("updatedAt", System.currentTimeMillis());
        data.put("sessionDate", chatSessionId);
        db.collection("users").document(user.getUid())
                .collection("chat_sessions").document(chatSessionId)
                .set(data)
                .addOnFailureListener(e ->
                        android.util.Log.e("ChatSave", "저장 실패: " + e.getMessage()));
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        typingHandler.removeCallbacksAndMessages(null);
    }
}