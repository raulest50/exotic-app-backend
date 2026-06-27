package exotic.app.planta.service.bi;

public enum ExcelDecimalSeparator {
    COMMA("[$-240A]#,##0.00"),
    DOT("[$-409]#,##0.00");

    private final String excelNumberFormat;

    ExcelDecimalSeparator(String excelNumberFormat) {
        this.excelNumberFormat = excelNumberFormat;
    }

    public String excelNumberFormat() {
        return excelNumberFormat;
    }
}
