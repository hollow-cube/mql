package net.hollowcube.mql.builtin;

import net.hollowcube.mql.foreign.Query;

import java.util.concurrent.ThreadLocalRandom;

public class MqlMath {

    private MqlMath() {
    }

    /**
     * Absolute rhs of rhs
     */
    @Query
    public static double abs(double value) {
        return Math.abs(value);
    }

    /**
     * arccos of rhs
     */
    @Query
    public static double acos(double value) {
        return Math.toDegrees(Math.acos(value));
    }

    /**
     * arcsin of rhs
     */
    @Query
    public static double asin(double value) {
        return Math.toDegrees(Math.asin(value));
    }

    /**
     * arctan of rhs
     */
    @Query
    public static double atan(double value) {
        return Math.toDegrees(Math.atan(value));
    }

    /**
     * arctan of y/x. NOTE: the order of arguments!
     */
    @Query
    public static double atan2(double y, double x) {
        return Math.toDegrees(Math.atan2(y, x));
    }

    /**
     * Round rhs up to nearest integral number
     */
    @Query
    public static double ceil(double value) {
        return Math.ceil(value);
    }

    /**
     * Clamp rhs to between min and max inclusive
     */
    @Query
    public static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * Cosine (in degrees) of rhs
     */
    @Query
    public static double cos(double value) {
        return Math.cos(Math.toRadians(value));
    }

    /**
     * Returns the sum of 'num' random numbers, each with a rhs from low to high. Note: the generated random numbers are not integers like normal dice. For that, use math.die_roll_integer.
     */
    @Query
    public static double dieRoll(double num, double low, double high) {
        double total = 0;
        for (int i = 0; i < num; i++)
            total += random(low, high);
        return total;
    }

    /**
     * Returns the sum of 'num' random integer numbers, each with a rhs from low to high. Note: the generated random numbers are integers like normal dice.
     */
    @Query
    public static double dieRollInteger(double num, double low, double high) {
        double total = 0;
        for (int i = 0; i < num; i++)
            total += randomInteger(low, high);
        return total;
    }

    /**
     * Calculates e to the rhs 'nth' power
     */
    @Query
    public static double exp(double value) {
        return Math.exp(value);
    }

    /**
     * Round rhs down to nearest integral number
     */
    @Query
    public static double floor(double value) {
        return Math.floor(value);
    }

    /**
     * Useful for simple smooth curve interpolation using one of the Hermite Basis functions: 3t^2 - 2t^3. Note that while any valid float is a valid input, this function works best in the range [0,1].
     */
    @Query
    public static double hermiteBlend(double value) {
        //todo: implement me
        throw new UnsupportedOperationException("hermite_blend not implemented");
    }

    /**
     * Lerp from start to end via zeroToOne
     */
    @Query
    public static double lerp(double start, double end, double zeroToOne) {
        //todo test me
        zeroToOne = clamp(zeroToOne, 0, 1);
        return start * zeroToOne + end * (1D - zeroToOne);
    }

    /**
     * Lerp the shortest direction around a circle from start degrees to end degrees via zeroToOne
     */
    @Query
    public static double lerprotate(double start, double end, double zeroToOne) {
        //todo test me
        zeroToOne = clamp(zeroToOne, 0, 1);
        double diff = end - start;
        if (diff > 180) diff -= 360;
        else if (diff < -180) diff += 360;
        return start + diff * zeroToOne;
    }

    /**
     * Natural logarithm of rhs
     */
    @Query
    public static double ln(double value) {
        return Math.log(value);
    }

    /**
     * Return highest rhs of A or B
     */
    @Query
    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    /**
     * Return lowest rhs of A or B
     */
    @Query
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    /**
     * Minimize angle magnitude (in degrees) into the range [-180, 180)
     */
    @Query
    public static double minAngle(double value) {
        //todo: implement me
        throw new UnsupportedOperationException("min_angle not implemented");
    }

    /**
     * Return the remainder of rhs / denominator
     */
    @Query
    public static double mod(double value, double denominator) {
        return value % denominator;
    }

    /**
     * Returns the float representation of the constant pi.
     */
    @Query
    public static double pi() {
        return Math.PI;
    }

    /**
     * Elevates base to the exponent'th power
     */
    @Query
    public static double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    /**
     * Random rhs between low (inclusive) and high (exclusive)
     * <p>
     * Note: The original molang spec says that the range is inclusive, but this high end is exclusive.
     */
    @Query
    public static double random(double low, double high) {
        return ThreadLocalRandom.current().nextDouble(low, high);
    }

    /**
     * Random integer rhs between low and high (inclusive)
     */
    @Query
    public static double randomInteger(double low, double high) {
        return ThreadLocalRandom.current().nextInt((int) low, (int) high + 1);
    }

    /**
     * Round rhs to nearest integral number
     */
    @Query
    public static double round(double value) {
        return Math.round(value);
    }

    /**
     * Sine (in degrees) of rhs
     */
    @Query
    public static double sin(double value) {
        return Math.sin(Math.toRadians(value));
    }

    /**
     * Square root of rhs
     */
    @Query
    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    /**
     * Round rhs towards zero
     */
    @Query
    public static double trunc(double value) {
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }

}
