package com.cwpdf.saver;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnGithub = findViewById(R.id.btn_github);
        Button btnTelegram = findViewById(R.id.btn_telegram);
        Button btnTelegramGroup = findViewById(R.id.btn_telegram_group);

        btnGithub.setOnClickListener(v -> openUrl("https://github.com/myst-25"));
        btnTelegram.setOnClickListener(v -> openUrl("https://t.me/Myst_25"));
        btnTelegramGroup.setOnClickListener(v -> openUrl("https://t.me/myst2123"));
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}
