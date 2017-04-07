package ps.hacking.zxing.oned;

import java.util.Map;
import ps.hacking.zxing.BarcodeFormat;
import ps.hacking.zxing.DecodeHintType;
import ps.hacking.zxing.FormatException;
import ps.hacking.zxing.NotFoundException;
import ps.hacking.zxing.Result;
import ps.hacking.zxing.ResultPoint;
import ps.hacking.zxing.common.BitArray;

public final class ITFReader extends OneDReader {
    private static final int[] DEFAULT_ALLOWED_LENGTHS;
    private static final int[] END_PATTERN_REVERSED;
    private static final int MAX_AVG_VARIANCE = 107;
    private static final int MAX_INDIVIDUAL_VARIANCE = 204;
    private static final int f36N = 1;
    static final int[][] PATTERNS;
    private static final int[] START_PATTERN;
    private static final int f37W = 3;
    private int narrowLineWidth;

    public ITFReader() {
        this.narrowLineWidth = -1;
    }

    static {
        DEFAULT_ALLOWED_LENGTHS = new int[]{44, 24, 20, 18, 16, 14, 12, 10, 8, 6};
        START_PATTERN = new int[]{f36N, f36N, f36N, f36N};
        END_PATTERN_REVERSED = new int[]{f36N, f36N, f37W};
        PATTERNS = new int[][]{new int[]{f36N, f36N, f37W, f37W, f36N}, new int[]{f37W, f36N, f36N, f36N, f37W}, new int[]{f36N, f37W, f36N, f36N, f37W}, new int[]{f37W, f37W, f36N, f36N, f36N}, new int[]{f36N, f36N, f37W, f36N, f37W}, new int[]{f37W, f36N, f37W, f36N, f36N}, new int[]{f36N, f37W, f37W, f36N, f36N}, new int[]{f36N, f36N, f36N, f37W, f37W}, new int[]{f37W, f36N, f36N, f37W, f36N}, new int[]{f36N, f37W, f36N, f37W, f36N}};
    }

    public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType, ?> hints) throws FormatException, NotFoundException {
        int[] startRange = decodeStart(row);
        int[] endRange = decodeEnd(row);
        StringBuilder result = new StringBuilder(20);
        decodeMiddle(row, startRange[f36N], endRange[0], result);
        String resultString = result.toString();
        int[] allowedLengths = null;
        if (hints != null) {
            allowedLengths = (int[]) hints.get(DecodeHintType.ALLOWED_LENGTHS);
        }
        if (allowedLengths == null) {
            allowedLengths = DEFAULT_ALLOWED_LENGTHS;
        }
        int length = resultString.length();
        boolean lengthOK = false;
        int[] arr$ = allowedLengths;
        int len$ = arr$.length;
        for (int i$ = 0; i$ < len$; i$ += f36N) {
            if (length == arr$[i$]) {
                lengthOK = true;
                break;
            }
        }
        if (lengthOK) {
            ResultPoint[] resultPointArr = new ResultPoint[2];
            resultPointArr[0] = new ResultPoint((float) startRange[f36N], (float) rowNumber);
            resultPointArr[f36N] = new ResultPoint((float) endRange[0], (float) rowNumber);
            return new Result(resultString, null, resultPointArr, BarcodeFormat.ITF);
        }
        throw FormatException.getFormatInstance();
    }

    private static void decodeMiddle(BitArray row, int payloadStart, int payloadEnd, StringBuilder resultString) throws NotFoundException {
        int[] counterDigitPair = new int[10];
        int[] counterBlack = new int[5];
        int[] counterWhite = new int[5];
        while (payloadStart < payloadEnd) {
            OneDReader.recordPattern(row, payloadStart, counterDigitPair);
            for (int k = 0; k < 5; k += f36N) {
                int twoK = k << f36N;
                counterBlack[k] = counterDigitPair[twoK];
                counterWhite[k] = counterDigitPair[twoK + f36N];
            }
            resultString.append((char) (decodeDigit(counterBlack) + 48));
            resultString.append((char) (decodeDigit(counterWhite) + 48));
            int[] arr$ = counterDigitPair;
            for (int i$ = 0; i$ < arr$.length; i$ += f36N) {
                payloadStart += arr$[i$];
            }
        }
    }

    int[] decodeStart(BitArray row) throws NotFoundException {
        int[] startPattern = findGuardPattern(row, skipWhiteSpace(row), START_PATTERN);
        this.narrowLineWidth = (startPattern[f36N] - startPattern[0]) >> 2;
        validateQuietZone(row, startPattern[0]);
        return startPattern;
    }

    private void validateQuietZone(BitArray row, int startPattern) throws NotFoundException {
        int quietCount = this.narrowLineWidth * 10;
        int i = startPattern - 1;
        while (quietCount > 0 && i >= 0 && !row.get(i)) {
            quietCount--;
            i--;
        }
        if (quietCount != 0) {
            throw NotFoundException.getNotFoundInstance();
        }
    }

    private static int skipWhiteSpace(BitArray row) throws NotFoundException {
        int width = row.getSize();
        int endStart = row.getNextSet(0);
        if (endStart != width) {
            return endStart;
        }
        throw NotFoundException.getNotFoundInstance();
    }

    int[] decodeEnd(BitArray row) throws NotFoundException {
        row.reverse();
        try {
            int[] endPattern = findGuardPattern(row, skipWhiteSpace(row), END_PATTERN_REVERSED);
            validateQuietZone(row, endPattern[0]);
            int temp = endPattern[0];
            endPattern[0] = row.getSize() - endPattern[f36N];
            endPattern[f36N] = row.getSize() - temp;
            return endPattern;
        } finally {
            row.reverse();
        }
    }

    private static int[] findGuardPattern(BitArray row, int rowOffset, int[] pattern) throws NotFoundException {
        int patternLength = pattern.length;
        int[] counters = new int[patternLength];
        int width = row.getSize();
        boolean isWhite = false;
        int counterPosition = 0;
        int patternStart = rowOffset;
        for (int x = rowOffset; x < width; x += f36N) {
            if ((row.get(x) ^ isWhite) != 0) {
                counters[counterPosition] = counters[counterPosition] + f36N;
            } else {
                if (counterPosition != patternLength - 1) {
                    counterPosition += f36N;
                } else if (OneDReader.patternMatchVariance(counters, pattern, MAX_INDIVIDUAL_VARIANCE) < MAX_AVG_VARIANCE) {
                    return new int[]{patternStart, x};
                } else {
                    patternStart += counters[0] + counters[f36N];
                    System.arraycopy(counters, 2, counters, 0, patternLength - 2);
                    counters[patternLength - 2] = 0;
                    counters[patternLength - 1] = 0;
                    counterPosition--;
                }
                counters[counterPosition] = f36N;
                if (isWhite) {
                    isWhite = false;
                } else {
                    isWhite = true;
                }
            }
        }
        throw NotFoundException.getNotFoundInstance();
    }

    private static int decodeDigit(int[] counters) throws NotFoundException {
        int bestVariance = MAX_AVG_VARIANCE;
        int bestMatch = -1;
        int max = PATTERNS.length;
        for (int i = 0; i < max; i += f36N) {
            int variance = OneDReader.patternMatchVariance(counters, PATTERNS[i], MAX_INDIVIDUAL_VARIANCE);
            if (variance < bestVariance) {
                bestVariance = variance;
                bestMatch = i;
            }
        }
        if (bestMatch >= 0) {
            return bestMatch;
        }
        throw NotFoundException.getNotFoundInstance();
    }
}
