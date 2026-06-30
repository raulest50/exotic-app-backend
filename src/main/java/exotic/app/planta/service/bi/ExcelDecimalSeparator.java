package exotic.app.planta.service.bi;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public enum ExcelDecimalSeparator {
    COMMA(',', '.'),
    DOT('.', ',');

    private static final String DECIMAL_PATTERN = "#,##0.00";

    private final char decimalSeparator;
    private final char groupingSeparator;

    ExcelDecimalSeparator(char decimalSeparator, char groupingSeparator) {
        this.decimalSeparator = decimalSeparator;
        this.groupingSeparator = groupingSeparator;
    }

    public DecimalFormat createFormatter() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator(decimalSeparator);
        symbols.setGroupingSeparator(groupingSeparator);

        DecimalFormat formatter = new DecimalFormat(DECIMAL_PATTERN, symbols);
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter;
    }
}
