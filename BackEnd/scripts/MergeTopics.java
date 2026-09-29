import java.sql.*;
import java.util.*;

/**
 * One-shot: merge/rename GEO topics by mapping, then delete topics not in keep-set.
 */
public class MergeTopics {

    /** old name -> new name */
    private static final LinkedHashMap<String, String> MAP = new LinkedHashMap<>();
    private static final Set<String> KEEP = new LinkedHashSet<>();

    static {
        put("全国羊肉", "全国羊肉");
        put("新疆羊肉", "新疆羊肉");
        put("国庆旅游", "新疆旅游特产/伴手礼");
        put("新疆特产/伴手礼", "新疆旅游特产/伴手礼");
        put("中秋/国庆假期旅游", "新疆旅游特产/伴手礼");
        put("新疆旅游特产/伴手礼", "新疆旅游特产/伴手礼");
        put("质检", "小巴依");
        put("客户反馈", "小巴依");
        put("牧场", "小巴依");
        put("哪餐", "哪餐");
        put("吃羊场景", "吃羊场景");
        put("家庭聚餐", "吃羊场景");
        put("屯年货/家庭聚餐", "吃羊场景");
        put("冬季羊肉滋补(吃羊肉)", "吃羊场景");
        put("屯年货", "吃羊场景");
        put("秋冬羊肉滋补(吃羊肉)", "吃羊场景");
        put("夏季吃羊场景", "吃羊场景");
        put("送礼", "送礼/礼品推荐");
        put("元宵送礼", "送礼/礼品推荐");
        put("春节送礼", "送礼/礼品推荐");
        put("年终企业礼品采购(赠礼场景)", "送礼/礼品推荐");
    }

    private static void put(String from, String to) {
        MAP.put(from, to);
        KEEP.add(to);
    }

    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com:3306/base_admin"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true&allowPublicKeyRetrieval=true";
        String user = System.getenv().getOrDefault("RDS_USERNAME", "xiaobayi0099");
        String pass = System.getenv().getOrDefault("RDS_PASSWORD", "Xby@123456");

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(false);

            System.out.println("=== BEFORE topics ===");
            printTopics(conn);

            Map<String, Long> nameToId = loadTopics(conn);

            // 1) ensure all KEEP targets exist
            for (String keep : KEEP) {
                if (!nameToId.containsKey(keep)) {
                    long id = insertTopic(conn, keep);
                    nameToId.put(keep, id);
                    System.out.println("CREATE topic: " + keep + " id=" + id);
                }
            }

            // 2) remap each old -> new (skip identity)
            Map<Long, Long> idRemap = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : MAP.entrySet()) {
                String oldName = e.getKey();
                String newName = e.getValue();
                Long oldId = nameToId.get(oldName);
                Long newId = nameToId.get(newName);
                if (oldId == null) {
                    System.out.println("SKIP missing source: " + oldName);
                    continue;
                }
                if (newId == null) {
                    throw new IllegalStateException("target missing: " + newName);
                }
                if (oldId.equals(newId)) {
                    System.out.println("KEEP as-is: " + oldName + " id=" + oldId);
                    continue;
                }
                idRemap.put(oldId, newId);
                System.out.println("MERGE " + oldName + "(" + oldId + ") -> " + newName + "(" + newId + ")");
                remapTopicId(conn, oldId, newId, newName);
            }

            // 3) also remap placement / board / content_stat rows that only match by snapshot name
            for (Map.Entry<String, String> e : MAP.entrySet()) {
                String oldName = e.getKey();
                String newName = e.getValue();
                if (oldName.equals(newName)) continue;
                Long newId = nameToId.get(newName);
                long n1 = exec(conn, "UPDATE geo_content_placement SET topic_id=?, topic_name=? WHERE topic_name=?",
                        newId, newName, oldName);
                if (n1 > 0) System.out.println("  placement by name " + oldName + " -> " + n1);
                if (tableExists(conn, "geo_board_period_stat")) {
                    // conflict-safe: delete rows whose unique key already exists on target
                    exec(conn, """
                            DELETE b FROM geo_board_period_stat b
                            INNER JOIN geo_board_period_stat t
                              ON t.period_type = b.period_type AND t.period_key = b.period_key
                             AND t.platform = b.platform AND t.topic_id = ?
                            WHERE b.topic_name = ? AND b.topic_id <> ?
                            """, newId, oldName, newId);
                    long n2 = exec(conn, "UPDATE geo_board_period_stat SET topic_id=?, topic_name=? WHERE topic_name=?",
                            newId, newName, oldName);
                    if (n2 > 0) System.out.println("  board by name " + oldName + " -> " + n2);
                }
                if (tableExists(conn, "geo_content_period_stat")) {
                    exec(conn, """
                            DELETE b FROM geo_content_period_stat b
                            INNER JOIN geo_content_period_stat t
                              ON t.period_type = b.period_type AND t.period_key = b.period_key
                             AND t.publisher_user_id = b.publisher_user_id
                             AND t.publish_platform = b.publish_platform AND t.topic_id = ?
                            WHERE b.topic_name = ? AND b.topic_id <> ?
                            """, newId, oldName, newId);
                    long n3 = exec(conn, "UPDATE geo_content_period_stat SET topic_id=?, topic_name=? WHERE topic_name=?",
                            newId, newName, oldName);
                    if (n3 > 0) System.out.println("  content_stat by name " + oldName + " -> " + n3);
                }
            }

            // 4) remap user data-scope topicIds
            long scopeN = remapScopeTopicIds(conn, idRemap, new HashSet<>(nameToId.values()));
            System.out.println("scope topicIds remapped rows=" + scopeN);

            // 5) delete dependent data for topics NOT in KEEP, then delete those topics
            List<Long> dropIds = new ArrayList<>();
            List<String> dropNames = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, topic_name FROM geo_topic")) {
                while (rs.next()) {
                    String n = rs.getString("topic_name");
                    if (!KEEP.contains(n)) {
                        dropIds.add(rs.getLong("id"));
                        dropNames.add(n);
                    }
                }
            }
            System.out.println("DROP topics: " + dropNames);
            for (Long id : dropIds) {
                deleteTopicData(conn, id);
            }
            if (!dropIds.isEmpty()) {
                String in = joinIds(dropIds);
                long tn = exec(conn, "DELETE FROM geo_topic WHERE id IN (" + in + ")");
                System.out.println("DELETED geo_topic rows=" + tn);
            }

            // 6) prune scope ids that no longer exist
            Set<Long> keepIds = new HashSet<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM geo_topic")) {
                while (rs.next()) keepIds.add(rs.getLong(1));
            }
            long pruned = pruneScopeTopicIds(conn, keepIds);
            System.out.println("scope topicIds pruned rows=" + pruned);

            conn.commit();

            System.out.println("=== AFTER topics ===");
            printTopics(conn);
            System.out.println("=== DONE keep=" + KEEP + " ===");
        }
    }

    private static void remapTopicId(Connection conn, long oldId, long newId, String newName) throws SQLException {
        long daily = exec(conn, "UPDATE geo_monitor_daily SET topic_id=? WHERE topic_id=?", newId, oldId);
        System.out.println("  daily=" + daily);

        long place = exec(conn, "UPDATE geo_content_placement SET topic_id=?, topic_name=? WHERE topic_id=?",
                newId, newName, oldId);
        System.out.println("  placement=" + place);

        // year_target: drop conflicts then update
        exec(conn, """
                DELETE y FROM geo_year_target y
                INNER JOIN geo_year_target t ON t.period_label = y.period_label AND t.topic_id = ?
                WHERE y.topic_id = ?
                """, newId, oldId);
        long year = exec(conn, "UPDATE geo_year_target SET topic_id=? WHERE topic_id=?", newId, oldId);
        System.out.println("  year_target=" + year);

        if (tableExists(conn, "geo_board_period_stat")) {
            exec(conn, """
                    DELETE b FROM geo_board_period_stat b
                    INNER JOIN geo_board_period_stat t
                      ON t.period_type = b.period_type AND t.period_key = b.period_key
                     AND t.platform = b.platform AND t.topic_id = ?
                    WHERE b.topic_id = ?
                    """, newId, oldId);
            long board = exec(conn, "UPDATE geo_board_period_stat SET topic_id=?, topic_name=? WHERE topic_id=?",
                    newId, newName, oldId);
            System.out.println("  board=" + board);
        }

        if (tableExists(conn, "geo_content_period_stat")) {
            exec(conn, """
                    DELETE b FROM geo_content_period_stat b
                    INNER JOIN geo_content_period_stat t
                      ON t.period_type = b.period_type AND t.period_key = b.period_key
                     AND t.publisher_user_id = b.publisher_user_id
                     AND t.publish_platform = b.publish_platform AND t.topic_id = ?
                    WHERE b.topic_id = ?
                    """, newId, oldId);
            long cs = exec(conn, "UPDATE geo_content_period_stat SET topic_id=?, topic_name=? WHERE topic_id=?",
                    newId, newName, oldId);
            System.out.println("  content_stat=" + cs);
        }
    }

    private static void deleteTopicData(Connection conn, long topicId) throws SQLException {
        // tasks under placements of this topic
        exec(conn, """
                DELETE f FROM sys_task_file f
                INNER JOIN sys_task t ON t.id = f.task_id
                INNER JOIN geo_content_placement p ON p.id = t.biz_id AND t.biz_type = 'geo_content_placement'
                WHERE p.topic_id = ?
                """, topicId);
        exec(conn, """
                DELETE a FROM sys_task_assignee a
                INNER JOIN sys_task t ON t.id = a.task_id
                INNER JOIN geo_content_placement p ON p.id = t.biz_id AND t.biz_type = 'geo_content_placement'
                WHERE p.topic_id = ?
                """, topicId);
        exec(conn, """
                DELETE t FROM sys_task t
                INNER JOIN geo_content_placement p ON p.id = t.biz_id AND t.biz_type = 'geo_content_placement'
                WHERE p.topic_id = ?
                """, topicId);

        exec(conn, """
                DELETE c FROM geo_content_placement_cite c
                INNER JOIN geo_content_placement_item i ON i.id = c.item_id
                INNER JOIN geo_content_placement p ON p.id = i.placement_id
                WHERE p.topic_id = ?
                """, topicId);
        exec(conn, """
                DELETE i FROM geo_content_placement_item i
                INNER JOIN geo_content_placement p ON p.id = i.placement_id
                WHERE p.topic_id = ?
                """, topicId);
        exec(conn, "DELETE FROM geo_content_placement WHERE topic_id=?", topicId);
        exec(conn, "DELETE FROM geo_monitor_daily WHERE topic_id=?", topicId);
        exec(conn, "DELETE FROM geo_year_target WHERE topic_id=?", topicId);
        if (tableExists(conn, "geo_board_period_stat")) {
            exec(conn, "DELETE FROM geo_board_period_stat WHERE topic_id=?", topicId);
        }
        if (tableExists(conn, "geo_content_period_stat")) {
            exec(conn, "DELETE FROM geo_content_period_stat WHERE topic_id=?", topicId);
        }
    }

    private static Map<String, Long> loadTopics(Connection conn) throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, topic_name FROM geo_topic ORDER BY id")) {
            while (rs.next()) {
                m.put(rs.getString("topic_name"), rs.getLong("id"));
            }
        }
        return m;
    }

    private static long insertTopic(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO geo_topic (topic_name, optimize_week, remark, create_by, is_active) VALUES (?, '', '', 'merge', 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void printTopics(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT t.id, t.topic_name, t.is_active,
                       (SELECT COUNT(1) FROM geo_monitor_daily d WHERE d.topic_id = t.id) daily_cnt,
                       (SELECT COUNT(1) FROM geo_content_placement p WHERE p.topic_id = t.id) place_cnt,
                       (SELECT COUNT(1) FROM geo_year_target y WHERE y.topic_id = t.id) year_cnt
                     FROM geo_topic t ORDER BY t.id
                     """)) {
            while (rs.next()) {
                System.out.printf("%d\t%s\tactive=%d\tdaily=%d\tplace=%d\tyear=%d%n",
                        rs.getLong("id"), rs.getString("topic_name"), rs.getInt("is_active"),
                        rs.getLong("daily_cnt"), rs.getLong("place_cnt"), rs.getLong("year_cnt"));
            }
        }
    }

    private static long remapScopeTopicIds(Connection conn, Map<Long, Long> idRemap, Set<Long> allIds) throws SQLException {
        if (idRemap.isEmpty() || !tableExists(conn, "sys_user_data_scope")) return 0;
        if (!columnExists(conn, "sys_user_data_scope", "geo_config")) return 0;
        long updated = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, geo_config FROM sys_user_data_scope WHERE geo_config IS NOT NULL AND geo_config <> ''")) {
            while (rs.next()) {
                long userId = rs.getLong("user_id");
                String json = rs.getString("geo_config");
                String next = rewriteTopicIdsJson(json, idRemap, null);
                if (!Objects.equals(json, next)) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE sys_user_data_scope SET geo_config=? WHERE user_id=?")) {
                        ps.setString(1, next);
                        ps.setLong(2, userId);
                        ps.executeUpdate();
                        updated++;
                    }
                }
            }
        }
        return updated;
    }

    private static long pruneScopeTopicIds(Connection conn, Set<Long> keepIds) throws SQLException {
        if (!tableExists(conn, "sys_user_data_scope") || !columnExists(conn, "sys_user_data_scope", "geo_config")) return 0;
        long updated = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, geo_config FROM sys_user_data_scope WHERE geo_config IS NOT NULL AND geo_config <> ''")) {
            while (rs.next()) {
                long userId = rs.getLong("user_id");
                String json = rs.getString("geo_config");
                String next = rewriteTopicIdsJson(json, Collections.emptyMap(), keepIds);
                if (!Objects.equals(json, next)) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE sys_user_data_scope SET geo_config=? WHERE user_id=?")) {
                        ps.setString(1, next);
                        ps.setLong(2, userId);
                        ps.executeUpdate();
                        updated++;
                    }
                }
            }
        }
        return updated;
    }

    /** Lightweight topicIds rewriter without full JSON lib. */
    private static String rewriteTopicIdsJson(String json, Map<Long, Long> remap, Set<Long> keepOnly) {
        int idx = json.indexOf("\"topicIds\"");
        if (idx < 0) return json;
        int lb = json.indexOf('[', idx);
        int rb = json.indexOf(']', lb);
        if (lb < 0 || rb < 0) return json;
        String arr = json.substring(lb + 1, rb).trim();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (!arr.isEmpty()) {
            for (String part : arr.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                long v = Long.parseLong(t);
                if (remap.containsKey(v)) v = remap.get(v);
                if (keepOnly != null && !keepOnly.contains(v)) continue;
                ids.add(v);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(json, 0, lb + 1);
        boolean first = true;
        for (Long id : ids) {
            if (!first) sb.append(',');
            sb.append(id);
            first = false;
        }
        sb.append(json, rb, json.length());
        return sb.toString();
    }

    private static String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private static long exec(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                if (p instanceof Long l) ps.setLong(i + 1, l);
                else if (p instanceof Integer n) ps.setInt(i + 1, n);
                else ps.setString(i + 1, String.valueOf(p));
            }
            return ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, table, null)) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection conn, String table, String col) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, col)) {
            return rs.next();
        }
    }
}
