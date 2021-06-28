/**
 * MIT License
 *
 * Copyright (c) 2021 nunoalmarques
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
*/
package net.nunoalmarques.squale.indicator;

import org.apache.commons.lang3.tuple.Pair;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

import static net.nunoalmarques.squale.indicator.Trend.DOWN;
import static net.nunoalmarques.squale.indicator.Trend.UP;

public abstract class AbstractSuperTrend<T> extends AbstractIndicator<T>
{
    private final int barCount;
    private final Num barCountNum;
    private final Num multiplier;
    private final Num two;
    private final Num zero;
    private final TRIndicator trIndicator;
    private final ClosePriceIndicator closePriceIndicator;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;

    private final BinaryOperator<Num> upper;
    private final BinaryOperator<Num> lower;

    private final Map<Integer, Pair<Num, Num> > prevLower;
    private final Map<Integer, Pair<Num, Num> > prevUpper;
    private final Map<Integer, Pair<Trend, Num>> prevTrend;

    protected AbstractSuperTrend(BarSeries series, int barCount, int multiplier)
    {
        super(series);
        this.barCount = barCount;
        this.barCountNum = this.numOf(barCount);
        this.multiplier = this.numOf(multiplier);
        this.two = this.numOf(2);
        this.zero = this.numOf(0);
        this.trIndicator = new TRIndicator(series);
        this.closePriceIndicator = new ClosePriceIndicator(series);
        this.highPriceIndicator = new HighPriceIndicator(series);
        this.lowPriceIndicator = new LowPriceIndicator(series);
        this.upper = (midpoint, atr) -> midpoint.plus((this.multiplier).multipliedBy(atr));
        this.lower = (midpoint, atr) -> midpoint.minus((this.multiplier).multipliedBy(atr));
        this.prevLower = new HashMap<>();
        this.prevUpper = new HashMap<>();
        this.prevTrend = new HashMap<>();
    }

    @SuppressWarnings("java:S3776") //Ignore Sonar Cognitive Complexity of methods warning
    protected Pair<Trend, Num> calculate(int index)
    {
        Num close = closePriceIndicator.getValue(index);
        Pair<Num, Num> finalUpperBand = calculateUpperBand(index, index);
        Pair<Num, Num> finalLowerBand = calculateLowerBand(index, index);

        Pair<Trend, Num> result = Pair.of(UP, finalLowerBand.getLeft());
        if(index < barCount)
        {
            return result;
        }

        Pair<Trend, Num> previousSuperTrend = prevTrend.get(index - 1);
        if(previousSuperTrend == null)
        {
            previousSuperTrend = calculate(index - 1);
            prevTrend.put(index - 1, previousSuperTrend);
        }

        if(previousSuperTrend.getRight().isEqual(finalUpperBand.getRight())
                && close.isLessThanOrEqual(finalUpperBand.getLeft()))
        {
            result = Pair.of(DOWN, finalUpperBand.getLeft());
        }
        else
        {
            if(previousSuperTrend.getRight().isEqual(finalUpperBand.getRight())
                    && close.isGreaterThan(finalUpperBand.getLeft()))
            {
                result = Pair.of(UP, finalLowerBand.getLeft());
            }
            else
            {
                if(previousSuperTrend.getRight().isEqual(finalLowerBand.getRight())
                        && close.isGreaterThanOrEqual(finalLowerBand.getLeft()))
                {
                    result = Pair.of(UP, finalLowerBand.getLeft());
                }
                else
                {
                    if(previousSuperTrend.getRight().isEqual(finalLowerBand.getRight())
                            && close.isLessThan(finalUpperBand.getLeft()))
                    {
                        result = Pair.of(DOWN, finalUpperBand.getLeft());
                    }
                }
            }
        }

        return result;
    }

    private Pair<Num, Num> calculateUpperBand(int index, int initialIndex)
    {
        Num currentBasicUpperBand = getBasicBandValue(index, upper);
        if(index < barCount)
        {
            return Pair.of(currentBasicUpperBand, zero);
        }

        Num previousClose = closePriceIndicator.getValue(index - 1);

        Pair<Num, Num> previous = prevUpper.get(index - 1);
        if(previous == null)
        {
            previous = calculateUpperBand(index - 1, initialIndex);
            prevUpper.put(index - 1, previous);
        }

        if(currentBasicUpperBand.isLessThan(previous.getLeft())
                || previousClose.isGreaterThan(previous.getLeft()))
        {
            return Pair.of(currentBasicUpperBand, previous.getLeft());
        }
        return Pair.of(previous.getLeft(), previous.getLeft());
    }

    private Pair<Num, Num> calculateLowerBand(int index, int initialIndex)
    {
        Num currentBasicLowerBand = getBasicBandValue(index, lower);

        if(index < barCount)
        {
            return Pair.of(currentBasicLowerBand, zero);
        }

        Num previousClose = closePriceIndicator.getValue(index - 1);

        Pair<Num, Num> previous = prevLower.get(index - 1);
        if(previous == null)
        {
            previous = calculateLowerBand(index - 1, initialIndex);
            prevLower.put(index - 1, previous);
        }

        if(currentBasicLowerBand.isGreaterThan(previous.getLeft())
                || previousClose.isLessThan(previous.getLeft()))
        {
            return Pair.of(currentBasicLowerBand, previous.getLeft());
        }
        return Pair.of(previous.getLeft(), previous.getLeft());
    }

    private Num getBasicBandValue(int index, BinaryOperator<Num> calculator)
    {
        Num high = highPriceIndicator.getValue(index);
        Num low = lowPriceIndicator.getValue(index);
        Num atr = calculateSimpleAverageTrueRange(index);
        Num midpoint = (high.plus(low)).dividedBy(two);
        return calculator.apply(midpoint, atr);
    }

    private Num calculateSimpleAverageTrueRange(int index)
    {
        Num sum = zero;
        for(int i = index - barCount + 1; i <= index; i++)
        {
            sum = sum.plus(trIndicator.getValue(i));
        }
        return sum.dividedBy(barCountNum);
    }

    @Override
    public String toString()
    {
        Pair<Trend, Num> result = calculate(getBarSeries().getEndIndex());
        return String.format("SuperTrend %d %d: %s\t%6f"
                , barCount
                , multiplier.intValue()
                , result.getLeft()
                , result.getRight().doubleValue());
    }
}
