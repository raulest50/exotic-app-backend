package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.produccion.dto.FilaInfVentasDTO;
import exotic.app.planta.model.produccion.dto.PlaneacionExcelDebugResponseDTO;
import exotic.app.planta.model.produccion.dto.PlaneacionTerminadosDebugResponseDTO;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.TerminadoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaneacionExcelDebugService {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "FECHA", "PREFIJO", "FACTURA", "RETE FUENTE", "RETE IVA", "RETE ICA",
            "RETE OTROS", "DESCUENTO ENC.", "IDENT. CLIENTE", "NOMBRE CLIENTE",
            "CODIGO", "DESCRIPCION", "CANTIDAD VENDIDA", "VALOR SIN IVA", "VALOR IVA",
            "VALOR TOTAL", "COSTO", "DESCUENTO", "GANANCIA", "% GANANCIA", "%IVA",
            "GANANCIA BRUTA", "% GANANCIA BRUTA", "VENDEDOR", "DEPARTAMENTO",
            "MUNICIPIO", "UBICACION", "OBSERVACION", "CLASE CLIENTE", "ZONA",
            "FECHACAD", "LOTE", "TIPO PAGO", "DIRECCION", "TELEFONO", "CELULAR",
            "CONTACTO", "UBICACION", "CANTIDAD INVENTARIO", "OBRA/SEDE", "OBSERVACION",
            "NOTA OCULTA", "CUENTA", "SEGUIMIENTO", "CATEGORIA", "GUIA ENVIO",
            "RANGO PRECIO", "FLETE", "ENVIO GRATIS"
    );

    private static final int COL_CODIGO_INDEX = 10;
    private static final int COL_CANTIDAD_VENDIDA_INDEX = 12;
    private static final int COL_VALOR_TOTAL_INDEX = 15;
    private static final int PRODUCT_SAMPLE_SIZE = 10;

    private final TerminadoRepo terminadoRepo;
    private final ObjectMapper objectMapper;

    public PlaneacionExcelDebugResponseDTO inspectExcel(
            MultipartFile file,
            String username,
            List<String> clientErrors,
            String clientExpectedHeadersVersion
    ) {
        String debugId = buildDebugId("PED");
        log.info(
                "[{}] Inicio diagnostico Excel planeacion. user={}, fileName={}, sizeBytes={}, contentType={}, clientExpectedHeadersVersion={}",
                debugId,
                username,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null,
                file != null ? file.getContentType() : null,
                clientExpectedHeadersVersion
        );

        if (clientErrors != null && !clientErrors.isEmpty()) {
            log.info("[{}] Errores reportados por frontend ({}): {}", debugId, clientErrors.size(), clientErrors);
        }

        if (file == null || file.isEmpty()) {
            log.warn("[{}] Archivo no enviado o vacio.", debugId);
            return new PlaneacionExcelDebugResponseDTO(
                    debugId,
                    "No se recibio archivo para diagnostico.",
                    0,
                    null,
                    EXPECTED_HEADERS.size()
            );
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            DataFormatter formatter = new DataFormatter();
            int sheetCount = workbook.getNumberOfSheets();
            logWorkbookSheets(debugId, workbook);

            Sheet primarySheet = detectPrimarySheet(workbook, formatter);
            String primarySheetName = primarySheet != null ? primarySheet.getSheetName() : null;

            if (primarySheet == null) {
                log.warn("[{}] No se detecto hoja principal para diagnostico.", debugId);
                return new PlaneacionExcelDebugResponseDTO(
                        debugId,
                        "No se encontro una hoja principal con contenido.",
                        sheetCount,
                        null,
                        EXPECTED_HEADERS.size()
                );
            }

            int mismatchCount = logSheetStructure(debugId, primarySheet, formatter);

            return new PlaneacionExcelDebugResponseDTO(
                    debugId,
                    "Diagnostico tecnico generado correctamente.",
                    sheetCount,
                    primarySheetName,
                    mismatchCount
            );
        } catch (Exception e) {
            log.error("[{}] Error diagnosticando estructura del Excel de planeacion: {}", debugId, e.getMessage(), e);
            return new PlaneacionExcelDebugResponseDTO(
                    debugId,
                    "Ocurrio un error al inspeccionar el archivo. Revise planeacion_excel_debug.log.",
                    0,
                    null,
                    EXPECTED_HEADERS.size()
            );
        }
    }

    public PlaneacionTerminadosDebugResponseDTO inspectTerminadosAssociation(
            MultipartFile file,
            String username,
            String clientContext,
            String clientExpectedHeadersVersion
    ) {
        String debugId = buildDebugId("PTD");
        log.info(
                "[{}] Inicio diagnostico asociacion terminados. user={}, fileName={}, sizeBytes={}, contentType={}, clientExpectedHeadersVersion={}",
                debugId,
                username,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null,
                file != null ? file.getContentType() : null,
                clientExpectedHeadersVersion
        );

        Map<String, Object> parsedClientContext = parseClientContext(debugId, clientContext);

        if (file == null || file.isEmpty()) {
            log.warn("[{}] Archivo no enviado o vacio para diagnostico de asociacion.", debugId);
            return new PlaneacionTerminadosDebugResponseDTO(
                    debugId,
                    "No se recibio archivo para diagnostico.",
                    0,
                    null,
                    0,
                    0,
                    0
            );
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            DataFormatter formatter = new DataFormatter();
            int sheetCount = workbook.getNumberOfSheets();
            logWorkbookSheets(debugId, workbook);

            Sheet primarySheet = detectPrimarySheet(workbook, formatter);
            String primarySheetName = primarySheet != null ? primarySheet.getSheetName() : null;

            if (primarySheet == null) {
                log.warn("[{}] No se detecto hoja principal para diagnostico de asociacion.", debugId);
                return new PlaneacionTerminadosDebugResponseDTO(
                        debugId,
                        "No se encontro una hoja principal con contenido.",
                        sheetCount,
                        null,
                        0,
                        0,
                        0
                );
            }

            logSheetStructure(debugId, primarySheet, formatter);

            ExtractionResult extractionResult = extractSalesRows(primarySheet, formatter);
            List<FilaInfVentasDTO> filasUnificadas = unifyRows(extractionResult.rowsWithCode());
            List<String> inputCodes = filasUnificadas.stream()
                    .map(FilaInfVentasDTO::getCodigo)
                    .toList();

            log.info(
                    "[{}] Resumen Excel step1: totalRowsRead={}, rowsWithCodigo={}, filasUnificadas={}, inputCodeCount={}",
                    debugId,
                    extractionResult.totalRowsRead(),
                    extractionResult.rowsWithCodeCount(),
                    filasUnificadas.size(),
                    inputCodes.size()
            );
            log.info("[{}] Codigos unificados del Excel: {}", debugId, inputCodes);

            List<String> allProductIds = terminadoRepo.findAllProductoIdsOrderByProductoIdAsc();
            logAvailableTerminadosSummary(debugId, allProductIds);

            List<Terminado> matchedTerminados = inputCodes.isEmpty()
                    ? List.of()
                    : terminadoRepo.findByProductoIdIn(inputCodes);
            Map<String, Terminado> matchedMap = matchedTerminados.stream()
                    .collect(Collectors.toMap(Terminado::getProductoId, Function.identity()));

            List<String> matchedCodes = inputCodes.stream()
                    .filter(matchedMap::containsKey)
                    .toList();
            List<String> unmatchedCodes = inputCodes.stream()
                    .filter(code -> !matchedMap.containsKey(code))
                    .toList();

            log.info(
                    "[{}] Resultado asociacion: matchedCodeCount={}, unmatchedCodeCount={}, matchedCodes={}, unmatchedCodes={}",
                    debugId,
                    matchedCodes.size(),
                    unmatchedCodes.size(),
                    matchedCodes,
                    unmatchedCodes
            );
            logMatchedTerminados(debugId, matchedTerminados);
            logNormalizationHints(debugId, inputCodes, allProductIds);

            return new PlaneacionTerminadosDebugResponseDTO(
                    debugId,
                    "Diagnostico tecnico generado correctamente.",
                    sheetCount,
                    primarySheetName,
                    inputCodes.size(),
                    matchedCodes.size(),
                    unmatchedCodes.size()
            );
        } catch (Exception e) {
            log.error("[{}] Error diagnosticando asociacion de terminados: {}", debugId, e.getMessage(), e);
            return new PlaneacionTerminadosDebugResponseDTO(
                    debugId,
                    "Ocurrio un error al inspeccionar el archivo. Revise planeacion_excel_debug.log.",
                    0,
                    null,
                    0,
                    0,
                    0
            );
        }
    }

    private void logWorkbookSheets(String debugId, Workbook workbook) {
        log.info("[{}] Workbook cargado correctamente. sheetCount={}", debugId, workbook.getNumberOfSheets());
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            log.info(
                    "[{}] Sheet[{}] name='{}', physicalRows={}, lastRowNum={}",
                    debugId,
                    i,
                    sheet.getSheetName(),
                    sheet.getPhysicalNumberOfRows(),
                    sheet.getLastRowNum()
            );
        }
    }

    private int logSheetStructure(String debugId, Sheet primarySheet, DataFormatter formatter) {
        String primarySheetName = primarySheet.getSheetName();
        Row headerRow = primarySheet.getRow(0);
        int lastCellNum = headerRow != null
                ? Math.max(headerRow.getLastCellNum(), (short) EXPECTED_HEADERS.size())
                : EXPECTED_HEADERS.size();

        log.info(
                "[{}] Hoja principal detectada='{}', physicalRows={}, lastRowNum={}, headerLastCellNum={}",
                debugId,
                primarySheetName,
                primarySheet.getPhysicalNumberOfRows(),
                primarySheet.getLastRowNum(),
                lastCellNum
        );

        List<String> rawHeaders = extractRowValues(headerRow, lastCellNum, formatter);
        List<String> normalizedHeaders = rawHeaders.stream().map(this::normalizeHeader).toList();

        log.info("[{}] Headers raw hoja '{}': {}", debugId, primarySheetName, formatIndexedValues(rawHeaders));
        log.info("[{}] Headers normalizados hoja '{}': {}", debugId, primarySheetName, formatIndexedValues(normalizedHeaders));
        log.info("[{}] Headers esperados: {}", debugId, formatIndexedValues(EXPECTED_HEADERS));

        int mismatchCount = 0;
        int compareLength = Math.max(EXPECTED_HEADERS.size(), normalizedHeaders.size());
        for (int i = 0; i < compareLength; i++) {
            String expected = i < EXPECTED_HEADERS.size() ? EXPECTED_HEADERS.get(i) : "(sin header esperado)";
            String actual = i < normalizedHeaders.size() ? normalizedHeaders.get(i) : "(sin header detectado)";
            if (!Objects.equals(expected, actual)) {
                mismatchCount++;
                log.warn("[{}] Header mismatch col {}: expected='{}', actual='{}'", debugId, i + 1, expected, actual);
            }
        }

        logColumnPresence(debugId, normalizedHeaders);
        logShiftCandidates(debugId, normalizedHeaders);
        logSampleRows(debugId, primarySheet, formatter, Math.max(lastCellNum, EXPECTED_HEADERS.size()));
        return mismatchCount;
    }

    private ExtractionResult extractSalesRows(Sheet sheet, DataFormatter formatter) {
        List<FilaInfVentasDTO> rowsWithCode = new ArrayList<>();
        int totalRowsRead = 0;

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            totalRowsRead++;
            String firstCell = extractCellValue(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter)
                    .trim()
                    .toUpperCase(Locale.ROOT);
            if ("TOTALES".equals(firstCell)) {
                continue;
            }

            String codigo = extractCellValue(row.getCell(COL_CODIGO_INDEX, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter)
                    .trim();
            if (codigo.isBlank()) {
                continue;
            }

            double cantidadVendida = extractNumericCellValue(
                    row.getCell(COL_CANTIDAD_VENDIDA_INDEX, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL),
                    formatter
            );
            double valorTotal = extractNumericCellValue(
                    row.getCell(COL_VALOR_TOTAL_INDEX, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL),
                    formatter
            );

            rowsWithCode.add(new FilaInfVentasDTO(codigo, cantidadVendida, valorTotal));
        }

        return new ExtractionResult(totalRowsRead, rowsWithCode.size(), rowsWithCode);
    }

    private List<FilaInfVentasDTO> unifyRows(List<FilaInfVentasDTO> rows) {
        Map<String, FilaInfVentasDTO> grouped = new LinkedHashMap<>();

        for (FilaInfVentasDTO row : rows) {
            FilaInfVentasDTO existing = grouped.get(row.getCodigo());
            if (existing == null) {
                grouped.put(row.getCodigo(), new FilaInfVentasDTO(row.getCodigo(), row.getCantidadVendida(), row.getValorTotal()));
            } else {
                existing.setCantidadVendida(existing.getCantidadVendida() + row.getCantidadVendida());
                existing.setValorTotal(existing.getValorTotal() + row.getValorTotal());
            }
        }

        return new ArrayList<>(grouped.values());
    }

    private Map<String, Object> parseClientContext(String debugId, String clientContext) {
        if (clientContext == null || clientContext.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    clientContext,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            log.info("[{}] Client context reportado por frontend: {}", debugId, parsed);
            return parsed;
        } catch (Exception e) {
            log.warn("[{}] No se pudo parsear clientContext. raw={}", debugId, clientContext);
            return Map.of("rawClientContext", clientContext);
        }
    }

    private void logAvailableTerminadosSummary(String debugId, List<String> allProductIds) {
        List<String> firstIds = allProductIds.stream().limit(PRODUCT_SAMPLE_SIZE).toList();
        List<String> lastIds = allProductIds.stream()
                .skip(Math.max(0, allProductIds.size() - PRODUCT_SAMPLE_SIZE))
                .toList();

        log.info(
                "[{}] Resumen terminados disponibles: totalTerminados={}, firstIds={}, lastIds={}",
                debugId,
                allProductIds.size(),
                firstIds,
                lastIds
        );
    }

    private void logMatchedTerminados(String debugId, List<Terminado> matchedTerminados) {
        List<String> sample = matchedTerminados.stream()
                .limit(PRODUCT_SAMPLE_SIZE)
                .map(terminado -> String.format(
                        Locale.ROOT,
                        "{productoId='%s', nombre='%s', status=%d, categoria='%s'}",
                        terminado.getProductoId(),
                        terminado.getNombre(),
                        terminado.getStatus(),
                        terminado.getCategoria() != null ? terminado.getCategoria().getCategoriaNombre() : null
                ))
                .toList();

        log.info("[{}] Terminados encontrados muestra ({}): {}", debugId, sample.size(), sample);
    }

    private void logNormalizationHints(String debugId, List<String> inputCodes, List<String> allProductIds) {
        Set<String> exactIds = new LinkedHashSet<>(allProductIds);
        Map<String, String> trimmedLookup = allProductIds.stream()
                .collect(Collectors.toMap(id -> id.trim(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, String> upperLookup = allProductIds.stream()
                .collect(Collectors.toMap(id -> id.trim().toUpperCase(Locale.ROOT), Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<String> trimHints = new ArrayList<>();
        List<String> upperHints = new ArrayList<>();

        for (String inputCode : inputCodes) {
            if (exactIds.contains(inputCode)) {
                continue;
            }

            String trimmedMatch = trimmedLookup.get(inputCode.trim());
            if (trimmedMatch != null) {
                trimHints.add("'" + inputCode + "' -> '" + trimmedMatch + "'");
                continue;
            }

            String upperMatch = upperLookup.get(inputCode.trim().toUpperCase(Locale.ROOT));
            if (upperMatch != null) {
                upperHints.add("'" + inputCode + "' -> '" + upperMatch + "'");
            }
        }

        log.info("[{}] Pistas de normalizacion por trim: {}", debugId, trimHints);
        log.info("[{}] Pistas de normalizacion por uppercase: {}", debugId, upperHints);
    }

    private void logColumnPresence(String debugId, List<String> normalizedHeaders) {
        Set<String> actualSet = new LinkedHashSet<>();
        for (String header : normalizedHeaders) {
            if (header != null && !header.isBlank()) {
                actualSet.add(header);
            }
        }

        List<String> missing = EXPECTED_HEADERS.stream()
                .filter(expected -> !actualSet.contains(expected))
                .toList();

        List<String> unexpected = actualSet.stream()
                .filter(actual -> !EXPECTED_HEADERS.contains(actual))
                .toList();

        log.info("[{}] Headers faltantes: {}", debugId, missing);
        log.info("[{}] Headers no esperados: {}", debugId, unexpected);
    }

    private void logShiftCandidates(String debugId, List<String> normalizedHeaders) {
        for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
            String expected = EXPECTED_HEADERS.get(i);
            int actualIndex = normalizedHeaders.indexOf(expected);
            if (actualIndex >= 0 && actualIndex != i) {
                log.warn(
                        "[{}] Header reordenado detectado: '{}' expectedCol={}, actualCol={}",
                        debugId,
                        expected,
                        i + 1,
                        actualIndex + 1
                );
            }
        }
    }

    private void logSampleRows(String debugId, Sheet sheet, DataFormatter formatter, int width) {
        int printed = 0;
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum() && printed < 5; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row, width, formatter)) {
                continue;
            }

            List<String> values = extractRowValues(row, width, formatter);
            log.info("[{}] Sample row {}: {}", debugId, rowIndex + 1, formatIndexedValues(values));
            printed++;
        }
    }

    private boolean isRowEmpty(Row row, int width, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < width; i++) {
            if (!extractCellValue(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Sheet detectPrimarySheet(Workbook workbook, DataFormatter formatter) {
        Sheet bestSheet = null;
        int bestScore = -1;

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            int score = 0;
            int maxRows = Math.min(sheet.getLastRowNum(), 4);
            for (int rowIndex = 0; rowIndex <= maxRows; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                for (int cellIndex = 0; cellIndex < Math.max(row.getLastCellNum(), 0); cellIndex++) {
                    String value = extractCellValue(row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter);
                    if (!value.isBlank()) {
                        score++;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSheet = sheet;
            }
        }

        return bestSheet;
    }

    private List<String> extractRowValues(Row row, int width, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            Cell cell = row != null ? row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) : null;
            values.add(extractCellValue(cell, formatter));
        }
        return values;
    }

    private String extractCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private double extractNumericCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return 0;
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }

        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (IllegalStateException ignored) {
                // Fall back to formatted parsing.
            }
        }

        String raw = formatter.formatCellValue(cell).replace(",", "").trim();
        if (raw.isBlank()) {
            return 0;
        }

        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String formatIndexedValues(List<String> values) {
        List<String> formatted = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            formatted.add((i + 1) + "='" + values.get(i) + "'");
        }
        return formatted.toString();
    }

    private String buildDebugId(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record ExtractionResult(
            int totalRowsRead,
            int rowsWithCodeCount,
            List<FilaInfVentasDTO> rowsWithCode
    ) {
    }
}
