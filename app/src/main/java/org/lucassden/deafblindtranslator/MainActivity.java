package org.lucassden.deafblindtranslator;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Color;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText nearInput, farInput;
    private TextToSpeech tts;
    private boolean syncing = false;
    private static final int SPEECH_REQUEST = 41;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(Color.rgb(20, 36, 55));
        getWindow().setNavigationBarColor(Color.rgb(20, 36, 55));
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru", "RU"));
        });
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("Переводчик общения");
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(20, 36, 55));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView hint = new TextView(this);
        hint.setText("Говорите или печатайте — собеседник увидит текст");
        hint.setTextSize(13); hint.setTextColor(Color.DKGRAY); hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, dp(32)));

        farInput = makeEditor("Текст для собеседника", true);
        root.addView(farInput, new LinearLayout.LayoutParams(-1, 0, 1f));

        View line = new View(this); line.setBackgroundColor(Color.rgb(190, 200, 210));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(2)));

        nearInput = makeEditor("Введите сообщение здесь…", false);
        root.addView(nearInput, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button voice = new Button(this);
        voice.setText("🎙 Говорить");
        voice.setOnClickListener(v -> startSpeech());
        actions.addView(voice, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button speak = new Button(this);
        speak.setText("🔊 Озвучить");
        speak.setOnClickListener(v -> speakText());
        actions.addView(speak, new LinearLayout.LayoutParams(0, dp(54), 1f));

        Button clear = new Button(this);
        clear.setText("Очистить");
        clear.setOnClickListener(v -> { nearInput.setText(""); nearInput.requestFocus(); });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(54), 1f));
        root.addView(actions);

        TextView footer = new TextView(this);
        footer.setText("Верхняя сторона повёрнута на 180° · Нижняя — 0°");
        footer.setGravity(Gravity.CENTER); footer.setTextSize(11); footer.setTextColor(Color.GRAY);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(24)));

        setContentView(root);
        nearInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if (!syncing) { syncing = true; farInput.setText(s); farInput.setSelection(farInput.length()); syncing = false; }
            }
            public void afterTextChanged(Editable e) {}
        });
        farInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if (!syncing) { syncing = true; nearInput.setText(s); nearInput.setSelection(nearInput.length()); syncing = false; }
            }
            public void afterTextChanged(Editable e) {}
        });
    }

    private EditText makeEditor(String hint, boolean upsideDown) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextSize(24); e.setTextColor(Color.rgb(15, 25, 35));
        e.setGravity(Gravity.CENTER); e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackgroundColor(Color.WHITE); e.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setSingleLine(false); e.setMinLines(3);
        if (upsideDown) e.setRotation(180f);
        return e;
    }

    private void startSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите по-русски");
        try { startActivityForResult(intent, SPEECH_REQUEST); }
        catch (Exception ex) { Toast.makeText(this, "Не найден сервис распознавания речи. Проверьте Google Speech Services.", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String old = nearInput.getText().toString();
                nearInput.setText(old.isEmpty() ? results.get(0) : old + " " + results.get(0));
                nearInput.setSelection(nearInput.length());
            }
        }
    }

    private void speakText() {
        String text = nearInput.getText().toString().trim();
        if (text.isEmpty()) { Toast.makeText(this, "Сначала введите или надиктуйте текст", Toast.LENGTH_SHORT).show(); return; }
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translated_message");
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
}
