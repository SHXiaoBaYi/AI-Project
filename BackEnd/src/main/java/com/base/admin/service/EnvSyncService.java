package com.base.admin.service;

import com.base.admin.common.Constants;
import com.base.admin.config.EnvSyncProperties;
import com.base.admin.domain.vo.EnvSyncResultVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 仅测试环境：生产数据 → 测试；测试表结构 → 生产。
 * 数据同步不改表结构；结构同步只增不删（缺表建表、缺列加列）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvSyncService {

    private final EnvSyncProperties properties;

    @Value("${spring.datasource.url}")
    private String testJdbcUrl;

    @Value("${spring.datasource.username}")
    private String testUsername;

    @Value("${spring.datasource.password}")
    private String testPassword;

    public boolean featureEnabled() {
        return properties.isEnabled();
    }

    public boolean canUse(LoginUser user) {
        if (!featureEnabled() || user == null || !StringUtils.hasText(user.getUsername())) {
            return false;
        }
        return Constants.ENV_SYNC_USERNAME.equalsIgnoreCase(user.getUsername().trim());
    }

    public EnvSyncResultVO syncProdDataToTest() {
        ensureAllowed();
        long start = System.currentTimeMillis();
        List<String> details = new ArrayList<>();
        Path dumpFile = null;
        Path prodCnf = null;
        Path testCnf = null;
        try {
            EnvSyncProperties.RdsEndpoint prod = requireProd();
            String testHost = extractHost(testJdbcUrl);
            int testPort = extractPort(testJdbcUrl);
            String testDb = extractDatabase(testJdbcUrl);

            dumpFile = Files.createTempFile("prod_data_", ".sql");
            prodCnf = writeClientCnf(prod.getHost(), prod.getPort(), prod.getUsername(), prod.getPassword());
            testCnf = writeClientCnf(testHost, testPort, testUsername, testPassword);

            details.add("生产 dump(仅数据): " + prod.getHost() + "/" + prod.getDatabase());
            runProcess(List.of(
                    "mysqldump",
                    "--defaults-extra-file=" + prodCnf,
                    "--single-transaction",
                    "--no-create-info",
                    "--skip-triggers",
                    "--skip-routines",
                    "--complete-insert",
                    "--set-gtid-purged=OFF",
                    "--default-character-set=utf8mb4",
                    "--hex-blob",
                    prod.getDatabase()
            ), dumpFile, null, 30, TimeUnit.MINUTES);
            details.add("dump 大小: " + Files.size(dumpFile) + " bytes");

            details.add("清空测试库业务表数据(保留表结构)");
            int truncated = truncateAllTables(testHost, testPort, testDb, testUsername, testPassword);
            details.add("已 TRUNCATE 表数: " + truncated);

            details.add("导入测试库: " + testHost + "/" + testDb);
            runProcess(List.of(
                    "mysql",
                    "--defaults-extra-file=" + testCnf,
                    "--default-character-set=utf8mb4",
                    "--force",
                    testDb
            ), null, dumpFile, 30, TimeUnit.MINUTES);
            details.add("导入完成");

            return EnvSyncResultVO.builder()
                    .kind("DATA")
                    .message("生产数据已同步到测试（未改表结构）")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .details(details)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("syncProdDataToTest failed", e);
            throw new BusinessException("同步生产数据失败: " + rootMessage(e));
        } finally {
            deleteQuietly(dumpFile);
            deleteQuietly(prodCnf);
            deleteQuietly(testCnf);
        }
    }

    public EnvSyncResultVO syncTestSchemaToProd() {
        ensureAllowed();
        long start = System.currentTimeMillis();
        List<String> details = new ArrayList<>();
        try {
            EnvSyncProperties.RdsEndpoint prod = requireProd();
            String testHost = extractHost(testJdbcUrl);
            int testPort = extractPort(testJdbcUrl);
            String testDb = extractDatabase(testJdbcUrl);

            try (Connection testConn = open(testHost, testPort, testDb, testUsername, testPassword);
                 Connection prodConn = open(prod.getHost(), prod.getPort(), prod.getDatabase(),
                         prod.getUsername(), prod.getPassword())) {
                prodConn.setAutoCommit(true);
                List<String> testTables = listBaseTables(testConn, testDb);
                Set<String> prodTables = new LinkedHashSet<>(listBaseTables(prodConn, prod.getDatabase()));
                details.add("测试表数: " + testTables.size() + "，生产已有表: " + prodTables.size());

                int created = 0;
                int altered = 0;
                for (String table : testTables) {
                    if (!prodTables.contains(table)) {
                        String ddl = showCreateTable(testConn, table);
                        try (Statement st = prodConn.createStatement()) {
                            st.execute(ddl);
                        }
                        created++;
                        details.add("CREATE TABLE `" + table + "`");
                        continue;
                    }
                    List<ColumnMeta> testCols = listColumns(testConn, testDb, table);
                    Set<String> prodCols = listColumns(prodConn, prod.getDatabase(), table).stream()
                            .map(c -> c.name.toLowerCase(Locale.ROOT))
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    String prev = null;
                    for (ColumnMeta col : testCols) {
                        if (!prodCols.contains(col.name.toLowerCase(Locale.ROOT))) {
                            String alter = "ALTER TABLE `" + table + "` ADD COLUMN " + col.toAddDdl()
                                    + (prev != null ? " AFTER `" + prev + "`" : " FIRST");
                            try (Statement st = prodConn.createStatement()) {
                                st.execute(alter);
                            }
                            altered++;
                            details.add(alter);
                        }
                        prev = col.name;
                    }
                }
                details.add("新建表: " + created + "，新增列: " + altered);
                if (created == 0 && altered == 0) {
                    details.add("生产已包含测试侧全部表/列，无需变更");
                }
            }

            return EnvSyncResultVO.builder()
                    .kind("SCHEMA")
                    .message("测试表结构已推送到生产（只增不删）")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .details(details)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("syncTestSchemaToProd failed", e);
            throw new BusinessException("同步表结构到生产失败: " + rootMessage(e));
        }
    }

    private void ensureAllowed() {
        if (!featureEnabled()) {
            throw new BusinessException("当前环境未开放环境同步");
        }
        LoginUser user = SecurityUtils.getCurrentUser();
        if (!canUse(user)) {
            throw new BusinessException("仅账号 " + Constants.ENV_SYNC_USERNAME + " 可在测试环境执行同步");
        }
    }

    private EnvSyncProperties.RdsEndpoint requireProd() {
        EnvSyncProperties.RdsEndpoint prod = properties.getProd();
        if (prod == null || !StringUtils.hasText(prod.getHost()) || !StringUtils.hasText(prod.getUsername())
                || !StringUtils.hasText(prod.getPassword())) {
            throw new BusinessException("未配置生产库连接（xby.env-sync.prod）");
        }
        return prod;
    }

    private static Path writeClientCnf(String host, int port, String user, String pass) throws IOException {
        Path cnf = Files.createTempFile("mysql_client_", ".cnf");
        String body = """
                [client]
                host=%s
                port=%d
                user=%s
                password=%s
                """.formatted(host, port, user, pass.replace("\\", "\\\\"));
        Files.writeString(cnf, body, StandardCharsets.UTF_8);
        try {
            cnf.toFile().setReadable(false, false);
            cnf.toFile().setReadable(true, true);
            cnf.toFile().setWritable(true, true);
        } catch (Exception ignored) {
            // Windows 本地调试可能无 POSIX 权限
        }
        return cnf;
    }

    private static void runProcess(List<String> cmd, Path stdoutFile, Path stdinFile, long timeout, TimeUnit unit)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        if (stdoutFile != null) {
            pb.redirectOutput(stdoutFile.toFile());
        }
        if (stdinFile != null) {
            pb.redirectInput(stdinFile.toFile());
        }
        Process p = pb.start();
        String errTail = "";
        if (stdoutFile == null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                errTail = br.lines().collect(Collectors.joining("\n"));
            }
        }
        boolean finished = p.waitFor(timeout, unit);
        if (!finished) {
            p.destroyForcibly();
            throw new BusinessException("命令超时: " + cmd.get(0));
        }
        if (p.exitValue() != 0) {
            String logOut = stdoutFile != null ? Files.readString(stdoutFile, StandardCharsets.UTF_8) : errTail;
            String snippet = logOut.length() > 800 ? logOut.substring(logOut.length() - 800) : logOut;
            throw new BusinessException(cmd.get(0) + " 失败: " + snippet);
        }
    }

    private int truncateAllTables(String host, int port, String db, String user, String pass) throws Exception {
        try (Connection conn = open(host, port, db, user, pass);
             Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            List<String> tables = listBaseTables(conn, db);
            for (String t : tables) {
                st.execute("TRUNCATE TABLE `" + t + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS=1");
            return tables.size();
        }
    }

    private static Connection open(String host, int port, String db, String user, String pass) throws Exception {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=15000&socketTimeout=600000";
        return DriverManager.getConnection(url, user, pass);
    }

    private static List<String> listBaseTables(Connection conn, String schema) throws Exception {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT TABLE_NAME FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        }
        return tables;
    }

    private static String showCreateTable(Connection conn, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
            if (!rs.next()) {
                throw new BusinessException("无法读取建表语句: " + table);
            }
            return rs.getString(2);
        }
    }

    private static List<ColumnMeta> listColumns(Connection conn, String schema, String table) throws Exception {
        List<ColumnMeta> cols = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT,
                       GENERATION_EXPRESSION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(new ColumnMeta(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("COLUMN_TYPE"),
                            rs.getString("IS_NULLABLE"),
                            rs.getString("COLUMN_DEFAULT"),
                            rs.getString("EXTRA"),
                            rs.getString("COLUMN_COMMENT"),
                            rs.getString("GENERATION_EXPRESSION")));
                }
            }
        }
        return cols;
    }

    private static String extractHost(String jdbcUrl) {
        // jdbc:mysql://host:3306/db?...
        String s = jdbcUrl.substring(jdbcUrl.indexOf("://") + 3);
        int slash = s.indexOf('/');
        String hostPort = slash > 0 ? s.substring(0, slash) : s;
        int colon = hostPort.indexOf(':');
        return colon > 0 ? hostPort.substring(0, colon) : hostPort;
    }

    private static int extractPort(String jdbcUrl) {
        String s = jdbcUrl.substring(jdbcUrl.indexOf("://") + 3);
        int slash = s.indexOf('/');
        String hostPort = slash > 0 ? s.substring(0, slash) : s;
        int colon = hostPort.indexOf(':');
        if (colon < 0) {
            return 3306;
        }
        return Integer.parseInt(hostPort.substring(colon + 1));
    }

    private static String extractDatabase(String jdbcUrl) {
        String s = jdbcUrl.substring(jdbcUrl.indexOf("://") + 3);
        int slash = s.indexOf('/');
        if (slash < 0) {
            throw new BusinessException("无法解析测试库名");
        }
        String rest = s.substring(slash + 1);
        int q = rest.indexOf('?');
        return q > 0 ? rest.substring(0, q) : rest;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.toString() : t.getMessage();
    }

    private record ColumnMeta(
            String name,
            String columnType,
            String nullable,
            String columnDefault,
            String extra,
            String comment,
            String generationExpression) {

        String toAddDdl() {
            StringBuilder sb = new StringBuilder();
            sb.append('`').append(name).append("` ").append(columnType);
            String extraNorm = extra == null ? "" : extra.toLowerCase(Locale.ROOT);
            boolean generated = extraNorm.contains("generated") || StringUtils.hasText(generationExpression);
            if (generated && StringUtils.hasText(generationExpression)) {
                sb.append(" GENERATED ALWAYS AS (").append(generationExpression).append(")");
                if (extraNorm.contains("virtual")) {
                    sb.append(" VIRTUAL");
                } else {
                    sb.append(" STORED");
                }
            } else {
                if ("NO".equalsIgnoreCase(nullable)) {
                    sb.append(" NOT NULL");
                } else {
                    sb.append(" NULL");
                }
                if (columnDefault != null) {
                    sb.append(" DEFAULT ").append(formatDefault(columnDefault, columnType));
                }
                if (StringUtils.hasText(extra) && !extraNorm.contains("DEFAULT_GENERATED".toLowerCase(Locale.ROOT))) {
                    // keep auto_increment / on update
                    String cleaned = extra.replaceAll("(?i)DEFAULT_GENERATED", "").trim();
                    if (!cleaned.isEmpty()) {
                        sb.append(' ').append(cleaned);
                    }
                }
            }
            if (StringUtils.hasText(comment)) {
                sb.append(" COMMENT '").append(comment.replace("'", "''")).append('\'');
            }
            return sb.toString();
        }

        private static String formatDefault(String def, String type) {
            String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
            String d = def.trim();
            if ("current_timestamp".equalsIgnoreCase(d) || d.toUpperCase(Locale.ROOT).startsWith("CURRENT_TIMESTAMP")) {
                return d;
            }
            if (d.startsWith("'") || d.startsWith("b'") || d.startsWith("(")) {
                return d;
            }
            if (t.startsWith("int") || t.startsWith("bigint") || t.startsWith("decimal") || t.startsWith("double")
                    || t.startsWith("float") || t.startsWith("bit") || "null".equalsIgnoreCase(d)) {
                return d;
            }
            return "'" + d.replace("'", "''") + "'";
        }
    }
}
