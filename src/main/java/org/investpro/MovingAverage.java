package org.investpro;

public class MovingAverage {
    private final int[] window;
    private int n, insert;
    private double sum;

    public MovingAverage(int size) {
        window = new int[size];
        n = 0;
        insert = 0;
        sum = 0;
    }

    public double next(int val) {
        if (n < window.length) {
            n++;
        }
        sum = sum - window[insert] + val;
        window[insert] = val;
        insert = (insert + 1) % window.length;
        return sum / n;
    }


}
