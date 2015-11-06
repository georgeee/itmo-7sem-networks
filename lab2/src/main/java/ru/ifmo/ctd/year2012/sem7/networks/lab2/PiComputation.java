package ru.ifmo.ctd.year2012.sem7.networks.lab2;

import lombok.Getter;
import org.apache.commons.math3.fraction.BigFraction;
import ru.ifmo.ctd.year2012.sem7.networks.lab2.jitterbug.Data;

import java.math.BigDecimal;

public class PiComputation implements Data<PiComputation> {
    private static final int DIGITS_PER_STEP = 20;

    @Getter
    private final int digitsComputed;
    @Getter
    private final BigFraction pi;

    private PiComputation(int digitsComputed, BigFraction pi) {
        this.digitsComputed = digitsComputed;
        this.pi = pi;
    }

    public PiComputation() {
        this(0, BigFraction.ZERO);
    }

    @Override
    public PiComputation next() {
        BigFraction newPi = pi;
        for (int i = 0; i < DIGITS_PER_STEP; i++) {
            newPi = newPi.add(computeDigit(digitsComputed + i));
        }
        return new PiComputation(digitsComputed + DIGITS_PER_STEP, newPi);
    }

    private BigFraction computeDigit(int n) {
        BigFraction two = new BigFraction(2, 1);
        BigFraction x =  new BigFraction(0, 1);
        x = x.subtract(two.pow(5).divide(new BigFraction(4 * n + 1, 1)));
        x = x.subtract(two.pow(0).divide(new BigFraction(4 * n + 3, 1)));
        x = x.add(two.pow(8).divide(new BigFraction(10 * n + 1, 1)));
        x = x.subtract(two.pow(6).divide(new BigFraction(10 * n + 3, 1)));
        x = x.subtract(two.pow(2).divide(new BigFraction(10 * n + 5, 1)));
        x = x.subtract(two.pow(2).divide(new BigFraction(10 * n + 7, 1)));
        x = x.add(two.pow(0).divide(new BigFraction(10 * n + 9, 1)));
        x = x.divide(two.pow(10 * n + 6));
        if (n % 2 != 0) x = x.negate();
        return x;
    }

    @Override
    public PiComputation mergeWith(PiComputation o) {
        return digitsComputed < o.digitsComputed ? o : this;
    }

    @Override
    public String toString() {
        return "PiComputation{" +
                "digitsComputed=" + digitsComputed +
                ", pi=" + pi.bigDecimalValue(digitsComputed/3, BigDecimal.ROUND_HALF_EVEN) +
                '}';
    }
}
