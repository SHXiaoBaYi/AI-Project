import java.sql.*;
import java.util.*;

/** One-shot: wipe GEO topic master + all topic-linked business data. */
public class CleanTopicData {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com:3306/base_admin"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true&allowPublicKeyRetrieval=true";
        String user = System.getenv().getOrDefault("RDS_USERNAME", "xiaobayi0099");
        String pass = System.getenv().getOrDefault("RDS_PASSWORD", "Xby@123456");

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(false);
            Map<String, Long> before = counts(conn);
            System.out.println("=== BEFORE ===");
            before.forEach((k, v) -> System.out.println(k + "=" + v));

            // 1) tasks tied to content placements (topic-related biz)
            exec(conn, """
                    DELETE f FROM sys_task_file f
                    INNER JOIN sys_task t ON t.id = f.task_id
                    WHERE t.biz_type = 'geo_content_placement'
                    """);
            exec(conn, """
                    DELETE a FROM sys_task_assignee a
                    INNER JOIN sys_task t ON t.id = a.task_id
                    WHERE t.biz_type = 'geo_content_placement'
                    """);
            long taskN = exec(conn, "DELETE FROM sys_task WHERE biz_type = 'geo_content_placement'");

            // 2) placement tree
            long citeN = exec(conn, "DELETE FROM geo_content_placement_cite");
            long itemN = exec(conn, "DELETE FROM geo_content_placement_item");
            long placeN = exec(conn, "DELETE FROM geo_content_placement");

            // 3) daily / targets / board aggregates (all keyed by topic)
            long dailyN = exec(conn, "DELETE FROM geo_monitor_daily");
            long yearN = exec(conn, "DELETE FROM geo_year_target");
            long boardN = tableExists(conn, "geo_board_period_stat")
                    ? exec(conn, "DELETE FROM geo_board_period_stat") : 0;
            long contentStatN = tableExists(conn, "geo_content_period_stat")
                    ? exec(conn, "DELETE FROM geo_content_period_stat") : 0;

            // 4) topic master
            long topicN = exec(conn, "DELETE FROM geo_topic");

            // 5) clear topicIds inside user data-scope geo_config JSON
            long scopeN = clearGeoTopicIdsInScope(conn);

            conn.commit();

            System.out.println("=== DELETED ===");
            System.out.println("sys_task(geo_content_placement)=" + taskN);
            System.out.println("geo_content_placement_cite=" + citeN);
            System.out.println("geo_content_placement_item=" + itemN);
            System.out.println("geo_content_placement=" + placeN);
            System.out.println("geo_monitor_daily=" + dailyN);
            System.out.println("geo_year_target=" + yearN);
            System.out.println("geo_board_period_stat=" + boardN);
            System.out.println("geo_content_period_stat=" + contentStatN);
            System.out.println("geo_topic=" + topicN);
            System.out.println("sys_user_data_scope.geo_config.topicIds_cleared=" + scopeN);

            System.out.println("=== AFTER ===");
            counts(conn).forEach((k, v) -> System.out.println(k + "=" + v));
        }
    }

    private static Map<String, Long> counts(Connection conn) throws SQLException {
        LinkedHashMap<String, Long> m = new LinkedHashMap<>();
        String[] tables = {
                "geo_topic", "geo_monitor_daily", "geo_year_target",
                "geo_board_period_stat", "geo_content_period_stat",
                "geo_content_placement", "geo_content_placement_item", "geo_content_placement_cite"
        };
        for (String t : tables) {
            if (!tableExists(conn, t)) {
                m.put(t, -1L);
                continue;
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(1) FROM " + t)) {
                rs.next();
                m.put(t, rs.getLong(1));
            }
        }
        return m;
    }

    private static long exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate(sql);
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(1) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    private static long clearGeoTopicIdsInScope(Connection conn) throws SQLException {
        if (!tableExists(conn, "sys_user_data_scope")) {
            return 0;
        }
        long n = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, geo_config FROM sys_user_data_scope WHERE is_active = 1")) {
            List<Object[]> updates = new ArrayList<>();
            while (rs.next()) {
                long userId = rs.getLong("user_id");
                String json = rs.getString("geo_config");
                if (json == null || json.isBlank() || !json.contains("topicIds")) {
                    continue;
                }
                // naive clear: "topicIds":[1,2] -> "topicIds":[]
                String cleaned = json.replaceAll("\"topicIds\"\\s*:\\s*\\[[^\\]]*]", "\"topicIds\":[]");
                if (!cleaned.equals(json)) {
                    updates.add(new Object[]{cleaned, userId});
                }
            }
            try (PreparedStatement ups = conn.prepareStatement(
                    "UPDATE sys_user_data_scope SET geo_config = ? WHERE user_id = ?")) {
                for (Object[] u : updates) {
                    ups.setString(1, (String) u[0]);
                    ups.setLong(2, (Long) u[1]);
                    n += ups.executeUpdate();
                }
            }
        }
        return n;
    }
}
