import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

public class PeekJdFiles {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        StringBuilder out = new StringBuilder();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            List<Path> files = new ArrayList<>();
            ds.forEach(files::add);
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path f : files) {
                String name = f.getFileName().toString();
                out.append("\n======== FILE: ").append(name).append(" ========\n");
                if (name.toLowerCase().endsWith(".csv")) {
                    peekCsv(f, out);
                } else if (name.toLowerCase().endsWith(".xlsx")) {
                    peekXlsx(f, out);
                }
            }
        }
        Files.writeString(Path.of(args[1]), out.toString(), Charset.forName("UTF-8"));
        System.out.println("wrote " + args[1]);
    }

    static void peekCsv(Path f, StringBuilder out) throws IOException {
        Charset[] tryCs = {Charset.forName("GBK"), Charset.forName("UTF-8")};
        for (Charset cs : tryCs) {
            List<String> lines = Files.readAllLines(f, cs);
            if (lines.isEmpty()) continue;
            String h = lines.get(0);
            if (h.contains("?") && cs.name().equals("UTF-8")) continue;
            out.append("encoding=").append(cs.name()).append(" rows=").append(lines.size()).append('\n');
            int lim = Math.min(3, lines.size());
            for (int i = 0; i < lim; i++) {
                out.append("L").append(i + 1).append(": ").append(lines.get(i)).append('\n');
            }
            return;
        }
        out.append("FAILED csv decode\n");
    }

    static void peekXlsx(Path f, StringBuilder out) throws Exception {
        try (InputStream in = Files.newInputStream(f); Workbook wb = new XSSFWorkbook(in)) {
            out.append("sheets=").append(wb.getNumberOfSheets()).append('\n');
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                out.append("--- sheet[").append(s).append("] ").append(sheet.getSheetName())
                        .append(" lastRow=").append(sheet.getLastRowNum()).append(" ---\n");
                DataFormatter fmt = new DataFormatter();
                int max = Math.min(sheet.getLastRowNum(), 8);
                for (int r = 0; r <= max; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        out.append("R").append(r).append(": <empty>\n");
                        continue;
                    }
                    List<String> cells = new ArrayList<>();
                    int last = row.getLastCellNum();
                    for (int c = 0; c < last; c++) {
                        cells.add(fmt.formatCellValue(row.getCell(c)));
                    }
                    out.append("R").append(r).append(": ").append(String.join(" | ", cells)).append('\n');
                }
            }
        }
    }
}
