package exotic.app.planta.config.initializers;

import exotic.app.planta.model.compras.ContactoProveedor;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.producto.MaterialRepo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class CargaMasiva {

    private final MaterialRepo materialRepo;
    private final ProveedorRepo proveedorRepo;

    // Assuming the excel files are in the "data/carga_masiva" folder relative to the working directory.
    private final String MATERIAS_PRIMAS_FILE = "data/carga_masiva/materias_primas.xlsx";
    private final String MATERIALES_EMPAQUE_FILE = "data/carga_masiva/materiales_empaque.xlsx";
    private final String PROVEEDORES_FILE = "data/carga_masiva/proveedores.xlsx";

    public CargaMasiva(MaterialRepo materialRepo, ProveedorRepo proveedorRepo) {
        this.materialRepo = materialRepo;
        this.proveedorRepo = proveedorRepo;
    }

    public void executeCargaMasiva() {
        CargaMateriasPrimas();
        CargaMaterialesEmpaque();
        CargaMasivaProveedores();
    }

    /**
     * Bulk load for materias primas (tipoMaterial = 1)
     */
    private void CargaMateriasPrimas(){
        readAndSaveMaterials(MATERIAS_PRIMAS_FILE, 1);
    }

    /**
     * Bulk load for materiales de empaque (tipoMaterial = 2)
     */
    private void CargaMaterialesEmpaque(){
        readAndSaveMaterials(MATERIALES_EMPAQUE_FILE, 2);
    }

    /**
     * Bulk load for proveedores
     * Only loads if the proveedores table is empty
     */
    private void CargaMasivaProveedores() {
        // Only proceed if the proveedores table is empty
        if (proveedorRepo.count() > 0) {
            System.out.println("Skipping proveedores bulk load as the table is not empty");
            return;
        }

        try (FileInputStream fis = new FileInputStream(new File(PROVEEDORES_FILE));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = 0;
            int processedCount = 0;

            for (Row row : sheet) {
                if (rowCount == 0) {
                    rowCount++; // skip header row
                    continue;
                }

                // Column mapping based on the Excel file:
                // 0: RAZON SOCIAL
                // 1: TIPO IDENTIFICACION
                // 2: NUMERO IDENTIFICACION
                // 3: CATEGORIA
                // 4: DIRECCION
                // 5: CIUDAD
                // 6: DEPARTAMENTO
                // 7: NUMERO CONTACTO
                // 8: PERSONA DE CONTACTO
                // 9: CORREO

                // Read identification number (required)
                Cell idCell = row.getCell(2);
                if (idCell == null || idCell.getCellType() == CellType.BLANK) {
                    rowCount++;
                    continue; // Skip if identification number is missing
                }

                String id = "";
                if (idCell.getCellType() == CellType.NUMERIC) {
                    // Convert numeric to string without decimal places
                    id = String.format("%.0f", idCell.getNumericCellValue());
                } else {
                    id = idCell.getStringCellValue().trim();
                }

                if (id.isEmpty()) {
                    rowCount++;
                    continue; // Skip if identification number is empty
                }

                // Read contact information (at least one email or phone is required)
                Cell phoneCell = row.getCell(7); // NUMERO CONTACTO
                Cell emailCell = row.getCell(9); // CORREO

                String phone = "";
                if (phoneCell != null && phoneCell.getCellType() != CellType.BLANK) {
                    if (phoneCell.getCellType() == CellType.NUMERIC) {
                        phone = String.format("%.0f", phoneCell.getNumericCellValue());
                    } else {
                        phone = phoneCell.getStringCellValue().trim();
                    }
                }

                String email = "";
                if (emailCell != null && emailCell.getCellType() != CellType.BLANK) {
                    email = emailCell.getStringCellValue().trim();
                }

                if (email.isEmpty() && phone.isEmpty()) {
                    rowCount++;
                    continue; // Skip if both email and phone are missing
                }

                // Read other cells
                Cell nombreCell = row.getCell(0); // RAZON SOCIAL
                Cell tipoIdCell = row.getCell(1); // TIPO IDENTIFICACION
                Cell categoriaCell = row.getCell(3); // CATEGORIA
                Cell direccionCell = row.getCell(4); // DIRECCION
                Cell ciudadCell = row.getCell(5); // CIUDAD
                Cell departamentoCell = row.getCell(6); // DEPARTAMENTO
                Cell personaContactoCell = row.getCell(8); // PERSONA DE CONTACTO

                // Create Proveedor object
                Proveedor proveedor = new Proveedor();
                proveedor.setId(id);

                // Set nombre
                if (nombreCell != null && nombreCell.getCellType() != CellType.BLANK) {
                    proveedor.setNombre(nombreCell.getStringCellValue().trim());
                } else {
                    proveedor.setNombre("");
                }

                // Set tipo identificacion
                int tipoId = 1; // Default to NIT
                if (tipoIdCell != null && tipoIdCell.getCellType() != CellType.BLANK) {
                    String tipoIdStr = tipoIdCell.getStringCellValue().trim().toUpperCase();
                    tipoId = tipoIdStr.contains("CC") ? 0 : 1;
                }
                proveedor.setTipoIdentificacion(tipoId);

                // Set direccion
                if (direccionCell != null && direccionCell.getCellType() != CellType.BLANK) {
                    proveedor.setDireccion(direccionCell.getStringCellValue().trim());
                } else {
                    proveedor.setDireccion("");
                }

                // Set ciudad
                if (ciudadCell != null && ciudadCell.getCellType() != CellType.BLANK) {
                    proveedor.setCiudad(ciudadCell.getStringCellValue().trim());
                } else {
                    proveedor.setCiudad("");
                }

                // Set departamento
                if (departamentoCell != null && departamentoCell.getCellType() != CellType.BLANK) {
                    proveedor.setDepartamento(departamentoCell.getStringCellValue().trim());
                } else {
                    proveedor.setDepartamento("");
                }

                // Consolidar phone y email en un único ContactoProveedor
                List<ContactoProveedor> contactos = new ArrayList<>();

                if (!phone.isEmpty() || !email.isEmpty()) {
                    ContactoProveedor contacto = new ContactoProveedor();

                    String nombreContacto = "";
                    if (personaContactoCell != null && personaContactoCell.getCellType() != CellType.BLANK) {
                        nombreContacto = personaContactoCell.getStringCellValue().trim();
                    }

                    contacto.setFullName(nombreContacto);
                    contacto.setCargo("");
                    contacto.setCel(phone);
                    contacto.setEmail(email);
                    contacto.setProveedor(proveedor);

                    contactos.add(contacto);
                }

                proveedor.setContactos(contactos);

                // Set default values for other fields
                proveedor.setRegimenTributario(0); // Default to Regimen comun
                proveedor.setUrl("");
                proveedor.setObservacion("");
                proveedor.setCondicionPago("0"); // Default to credito

                // Set categorias
                int[] categorias = new int[1]; // Default to one category
                categorias[0] = 0; // Default to Servicios Operativos

                if (categoriaCell != null && categoriaCell.getCellType() != CellType.BLANK) {
                    String categoriaStr = categoriaCell.getStringCellValue().trim().toLowerCase();

                    if (categoriaStr.contains("materia prima") || categoriaStr.contains("materias primas")) {
                        categorias[0] = 1; // Materias Primas
                    } else if (categoriaStr.contains("material de empaque") || categoriaStr.contains("materiales de empaque")) {
                        categorias[0] = 2; // Materiales de Empaque
                    } else if (categoriaStr.contains("servicios administrativos")) {
                        categorias[0] = 3; // Servicios Administrativos
                    } else if (categoriaStr.contains("equipos")) {
                        categorias[0] = 4; // Equipos y Otros Servicios
                    } else if (categoriaStr.contains("servicios operativos")) {
                        categorias[0] = 0; // Servicios Operativos
                    }
                }

                proveedor.setCategorias(categorias);

                // Save the proveedor
                try {
                    proveedorRepo.save(proveedor);
                    processedCount++;
                } catch (Exception e) {
                    System.err.println("Error saving proveedor with ID " + id + ": " + e.getMessage());
                }

                rowCount++;
            }

            System.out.println("Successfully processed " + processedCount + " proveedores out of " + (rowCount - 1) + " rows.");

        } catch (Exception e) {
            System.err.println("Error reading file " + PROVEEDORES_FILE + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void readAndSaveMaterials(String filePath, int tipoMaterial) {
        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            // Assuming the first row is a header: NOMBRE, UNIDAD DE MEDIDA, CODIGO
            int rowCount = 0;
            // Use a common timestamp for all records in this batch if required
            LocalDateTime timestamp = LocalDateTime.now();
            for (Row row : sheet) {
                if (rowCount == 0) {
                    rowCount++; // skip header row
                    continue;
                }
                // Read cells: NOMBRE, UNIDAD DE MEDIDA, CODIGO
                Cell nombreCell = row.getCell(0);
                Cell unidadCell = row.getCell(1);
                Cell codigoCell = row.getCell(2);
                if (nombreCell == null || unidadCell == null || codigoCell == null) {
                    continue; // skip if any required cell is missing
                }
                String nombre = nombreCell.getStringCellValue().trim();
                String unidad = unidadCell.getStringCellValue().trim();
                String codigo;

                if (codigoCell.getCellType() == CellType.NUMERIC) {
                    // Si es un valor numérico, convertirlo a String sin forzar a entero
                    codigo = String.valueOf(codigoCell.getNumericCellValue()).replaceAll("\\.0$", "");
                } else {
                    // Si es un valor de texto, obtenerlo directamente
                    codigo = codigoCell.getStringCellValue().trim();
                }

                Material material = new Material();
                material.setProductoId(codigo);
                material.setNombre(nombre);
                material.setObservaciones("");
                material.setCosto(0);
                material.setCantidadUnidad(1);
                material.setTipoUnidades(convertUnidad(unidad));
                material.setTipoMaterial(tipoMaterial);
                material.setIvaPercentual(0.0);

                materialRepo.save(material);
                rowCount++;
            }
        } catch (Exception e) {
            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Converts the unit of measure string to the corresponding string for tipoUnidades.
     * Supported units: "KG", "L", "U", "G".
     */
    private String convertUnidad(String unidad) {
        if (unidad.equalsIgnoreCase("KG")) {
            return "KG";
        } else if (unidad.equalsIgnoreCase("UND")) {
            return "U";
        } else if (unidad.equalsIgnoreCase("L")) {
            return "L";
        } else if (unidad.equalsIgnoreCase("G") ||
                unidad.equalsIgnoreCase("GRAMO") ||
                unidad.equalsIgnoreCase("GRAMOS")) {
            return "G";
        }
        // default to "KG" if the unit is not recognized
        return "KG";
    }
}
