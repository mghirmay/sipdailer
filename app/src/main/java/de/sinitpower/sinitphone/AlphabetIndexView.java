package de.sinitpower.sinitphone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class AlphabetIndexView extends View {

    public interface Listener {
        void onLetterPick(String letter);
        void onRelease();
    }

    private final List<String> letters = new ArrayList<>();
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF circleRect  = new RectF();

    private Listener listener;
    private int activeIndex = -1;

    public AlphabetIndexView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float dp = context.getResources().getDisplayMetrics().density;
        float sp10 = 10 * dp;

        textPaint.setTextSize(sp10);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFF1976D2);

        activePaint.setTextSize(sp10);
        activePaint.setTextAlign(Paint.Align.CENTER);
        activePaint.setColor(0xFFFFFFFF);
        activePaint.setFakeBoldText(true);

        circlePaint.setColor(0xFF1976D2);
    }

    public void setLetters(List<String> letters) {
        this.letters.clear();
        this.letters.addAll(letters);
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (letters.isEmpty()) return;
        float cellH = (float) getHeight() / letters.size();
        float cx    = getWidth() / 2f;

        for (int i = 0; i < letters.size(); i++) {
            float cy = i * cellH + cellH / 2f;
            if (i == activeIndex) {
                float r = cellH * 0.46f;
                circleRect.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawOval(circleRect, circlePaint);
                canvas.drawText(letters.get(i), cx,
                        cy - (activePaint.ascent() + activePaint.descent()) / 2f, activePaint);
            } else {
                canvas.drawText(letters.get(i), cx,
                        cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (letters.isEmpty()) return false;
        int idx = (int) (event.getY() / getHeight() * letters.size());
        idx = Math.max(0, Math.min(letters.size() - 1, idx));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                activeIndex = idx;
                invalidate();
                if (listener != null) listener.onLetterPick(letters.get(idx));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeIndex = -1;
                invalidate();
                if (listener != null) listener.onRelease();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
