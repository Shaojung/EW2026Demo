package com.example.ew2026demo.protocol;

public enum Direction {
    STRAIGHT(0, "Straight ahead"),
    LEFT(1, "Turn left"),
    RIGHT(2, "Turn right"),
    LEFT_FWD(3, "Bear left"),
    RIGHT_FWD(4, "Bear right");

    private final int code;
    private final String chineseText;

    Direction(int code, String chineseText) {
        this.code = code;
        this.chineseText = chineseText;
    }

    public int getCode() {
        return code;
    }

    public String getChineseText() {
        return chineseText;
    }

    public String getEnglishText() {
        switch (this) {
            case STRAIGHT: return "Straight ahead";
            case LEFT: return "Turn left";
            case RIGHT: return "Turn right";
            case LEFT_FWD: return "Bear left";
            case RIGHT_FWD: return "Bear right";
            default: return "Straight ahead";
        }
    }
}
