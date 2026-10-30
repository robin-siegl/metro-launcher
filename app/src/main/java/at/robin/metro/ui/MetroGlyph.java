package at.robin.metro.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Original vector drawings of common symbols, in a 64-unit coordinate space. */
public final class MetroGlyph {
    private MetroGlyph() { }
    public static void draw(Canvas canvas, String name, float x, float y, float size, int color) {
        canvas.save(); canvas.translate(x, y); canvas.scale(size / 64, size / 64);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setStrokeWidth(3);
        p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND); p.setStyle(Paint.Style.STROKE);
        Path path = new Path();
        switch (name) {
            case "phone":
                path.moveTo(16, 7); path.lineTo(8, 13); path.cubicTo(3, 27, 35, 59, 49, 55);
                path.lineTo(57, 46); path.lineTo(43, 34); path.lineTo(36, 41);
                path.cubicTo(30, 39, 24, 33, 22, 27); path.lineTo(29, 20); path.close();
                p.setStyle(Paint.Style.FILL); canvas.drawPath(path, p); break;
            case "message":
                path.moveTo(7, 10); path.lineTo(57, 10); path.lineTo(57, 45); path.lineTo(31, 45);
                path.lineTo(19, 55); path.lineTo(19, 45); path.lineTo(7, 45); path.close();
                canvas.drawPath(path, p); canvas.drawLine(17, 22, 47, 22, p); canvas.drawLine(17, 31, 39, 31, p); break;
            case "mail":
                canvas.drawRect(5, 12, 59, 51, p); canvas.drawLine(6, 14, 32, 34, p);
                canvas.drawLine(58, 14, 32, 34, p); canvas.drawLine(6, 50, 23, 32, p);
                canvas.drawLine(58, 50, 41, 32, p); break;
            case "browser":
                canvas.drawCircle(32, 32, 25, p); canvas.drawOval(new RectF(20, 7, 44, 57), p);
                canvas.drawLine(8, 24, 56, 24, p); canvas.drawLine(8, 40, 56, 40, p); break;
            case "camera":
                path.moveTo(6, 18); path.lineTo(20, 18); path.lineTo(24, 11); path.lineTo(41, 11);
                path.lineTo(45, 18); path.lineTo(58, 18); path.lineTo(58, 52); path.lineTo(6, 52); path.close();
                canvas.drawPath(path, p); canvas.drawCircle(32, 35, 12, p); canvas.drawCircle(49, 24, 1, p); break;
            case "photos":
                canvas.drawRect(5, 9, 59, 54, p); canvas.drawCircle(19, 22, 5, p);
                path.moveTo(7, 49); path.lineTo(25, 31); path.lineTo(34, 40); path.lineTo(44, 28);
                path.lineTo(58, 48); canvas.drawPath(path, p); break;
            case "calendar":
                canvas.drawRect(8, 12, 56, 56, p); canvas.drawLine(8, 24, 56, 24, p);
                canvas.drawLine(21, 6, 21, 18, p); canvas.drawLine(43, 6, 43, 18, p);
                p.setStyle(Paint.Style.FILL);
                for (int row = 0; row < 2; row++) for (int col = 0; col < 3; col++)
                    canvas.drawRect(18 + col * 11, 32 + row * 11, 23 + col * 11, 37 + row * 11, p); break;
            case "clock":
                canvas.drawCircle(32, 32, 25, p); canvas.drawLine(32, 13, 32, 32, p); canvas.drawLine(32, 32, 45, 40, p); break;
            case "people":
                canvas.drawCircle(23, 21, 9, p); canvas.drawCircle(45, 23, 7, p);
                canvas.drawArc(new RectF(6, 34, 40, 68), 180, 180, false, p);
                canvas.drawArc(new RectF(34, 35, 60, 61), 190, 170, false, p); break;
            case "store":
                canvas.drawRect(10, 19, 54, 57, p); canvas.drawArc(new RectF(22, 4, 42, 32), 180, 180, false, p);
                canvas.drawLine(24, 35, 30, 41, p); canvas.drawLine(30, 41, 41, 29, p); break;
            case "music":
                canvas.drawLine(27, 13, 27, 45, p); canvas.drawLine(50, 8, 50, 40, p);
                canvas.drawLine(27, 13, 50, 8, p); p.setStyle(Paint.Style.FILL);
                canvas.drawOval(new RectF(11, 39, 28, 52), p); canvas.drawOval(new RectF(34, 34, 51, 47), p); break;
            case "settings":
                for (int i = 0; i < 8; i++) {
                    canvas.save(); canvas.rotate(i * 45, 32, 32); canvas.drawRect(27, 5, 37, 14, p); canvas.restore();
                }
                canvas.drawCircle(32, 32, 20, p); canvas.drawCircle(32, 32, 8, p); break;
            case "search":
                canvas.drawCircle(27, 27, 16, p); canvas.drawLine(39, 39, 55, 55, p); break;
            case "arrow":
                canvas.drawLine(9, 32, 53, 32, p); canvas.drawLine(37, 15, 54, 32, p); canvas.drawLine(54, 32, 37, 49, p); break;
            case "pin":
                canvas.drawLine(32, 30, 32, 54, p); canvas.drawLine(16, 30, 48, 30, p);
                path.moveTo(22, 29); path.lineTo(23, 10); path.lineTo(41, 10); path.lineTo(42, 29); canvas.drawPath(path, p); break;
            default:
                canvas.drawRect(12, 12, 52, 52, p); canvas.drawLine(12, 31, 52, 31, p); canvas.drawLine(31, 12, 31, 52, p); break;
        }
        canvas.restore();
    }
}
