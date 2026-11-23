package com.asalkartechworks.himmat.catalog.imports;

import com.asalkartechworks.himmat.catalog.model.ImportMode;
import com.asalkartechworks.himmat.catalog.model.ImportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.import.enabled", havingValue = "true", matchIfMissing = false)
public class ProductImportRunner implements CommandLineRunner {

    private final ProductImportService importService;
    private final Environment env;

    @Override
    public void run(String... args) throws Exception {
        log.info("ProductImportRunner invoked with app.import.enabled=true");

        String filePath = env.getProperty("app.import.file");
        if (filePath == null || filePath.isBlank()) {
            log.error("Missing required parameter: --app.import.file=<path-to-xlsx>");
            System.err.println("ERROR: Missing required parameter: --app.import.file=<path-to-xlsx>");
            System.exit(1);
            return;
        }

        String modeStr = env.getProperty("app.import.mode", "DRY_RUN");
        ImportMode mode;
        try {
            mode = ImportMode.valueOf(modeStr.trim().toUpperCase());
        } catch (Exception e) {
            log.error("Invalid import mode: {}. Expected DRY_RUN, CREATE_ONLY, or UPDATE", modeStr);
            System.err.println("ERROR: Invalid import mode: " + modeStr + ". Expected DRY_RUN, CREATE_ONLY, or UPDATE");
            System.exit(1);
            return;
        }

        String operator = env.getProperty("app.import.operator", "system:developer-cli");
        String brand = env.getProperty("app.import.brand", "Finolex");
        String sheet = env.getProperty("app.import.sheet", "Price List");

        ProductImportConfig config = ProductImportConfig.builder()
                .brandName(brand)
                .sheetName(sheet)
                .build();

        File file = new File(filePath);
        if (!file.exists()) {
            log.error("Import file not found: {}", file.getAbsolutePath());
            System.err.println("ERROR: Import file not found: " + file.getAbsolutePath());
            System.exit(1);
            return;
        }

        try {
            ProductImportResult result = importService.runImport(file, mode, operator, config);
            System.out.println(result.humanReadableSummary());

            if (result.getStatus() == ImportStatus.FAILED || result.getStatus() == ImportStatus.CONFLICT) {
                log.warn("Product import ended with errors/conflicts. Exiting with code 1.");
                System.exit(1);
            } else {
                log.info("Product import completed successfully. Exiting with code 0.");
                System.exit(0);
            }
        } catch (Exception e) {
            log.error("Fatal error during product import", e);
            System.err.println("FATAL: " + e.getMessage());
            System.exit(1);
        }
    }
}
