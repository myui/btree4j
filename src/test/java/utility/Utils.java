package utility;

public class Utils {
    public static final int MAXN = 5000 * 100;

    public static Range getRangeOfTen(int x) {
        if (x <= 0) throw new RuntimeException("x should be positive.");

        int MAXNLength = String.valueOf(x).length();
        int max = (int)Math.pow(10, MAXNLength) - 1;
        int min = (int)Math.pow(10, MAXNLength - 1);
        return new Range(min, max);
    }
}
