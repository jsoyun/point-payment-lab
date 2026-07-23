package com.paymentlab.voucher.common;

public final class Money {

    private Money() {
    }

    public static long parse(String value) {
        return Long.parseLong(value);
    }

    public static String add(String left, long right) {
        return String.valueOf(parse(left) + right);
    }

    public static String subtract(String left, long right) {
        return String.valueOf(parse(left) - right);
    }
}
