package exotic.app.planta.service.bi;

public record BiExcelExportOptions(ExcelDecimalSeparator decimalSeparator) {

    public static BiExcelExportOptions standard() {
        return new BiExcelExportOptions(null);
    }

    public static BiExcelExportOptions of(ExcelDecimalSeparator decimalSeparator) {
        return new BiExcelExportOptions(decimalSeparator);
    }

    public boolean hasDecimalSeparator() {
        return decimalSeparator != null;
    }
}
