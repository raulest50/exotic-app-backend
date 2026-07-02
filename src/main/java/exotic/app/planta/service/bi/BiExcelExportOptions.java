package exotic.app.planta.service.bi;

public record BiExcelExportOptions(BiExcelExportMode exportMode, ExcelDecimalSeparator decimalSeparator) {

    public BiExcelExportOptions {
        exportMode = exportMode != null
                ? exportMode
                : decimalSeparator != null ? BiExcelExportMode.TEXT_DETERMINISTIC : BiExcelExportMode.NUMERIC;
        decimalSeparator = exportMode == BiExcelExportMode.TEXT_DETERMINISTIC
                ? decimalSeparator != null ? decimalSeparator : ExcelDecimalSeparator.COMMA
                : null;
    }

    public static BiExcelExportOptions standard() {
        return new BiExcelExportOptions(BiExcelExportMode.NUMERIC, null);
    }

    public static BiExcelExportOptions of(ExcelDecimalSeparator decimalSeparator) {
        return of(null, decimalSeparator);
    }

    public static BiExcelExportOptions of(BiExcelExportMode exportMode, ExcelDecimalSeparator decimalSeparator) {
        return new BiExcelExportOptions(exportMode, decimalSeparator);
    }

    public boolean isTextDeterministic() {
        return exportMode == BiExcelExportMode.TEXT_DETERMINISTIC;
    }
}
