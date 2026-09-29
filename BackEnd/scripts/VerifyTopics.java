import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public class VerifyTopics {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com:3306/base_admin"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true&allowPublicKeyRetrieval=true";
        String user = System.getenv().getOrDefault("RDS_USERNAME", "xiaobayi0099");
        String pass = System.getenv().getOrDefault("RDS_PASSWORD", "Xby@123456");
        StringBuilder out = new StringBuilder();
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement()) {
            out.append("=== geo_topic ===\n");
            try (ResultSet rs = st.executeQuery("""
                    SELECT t.id, t.topic_name, t.is_active,
                      (SELECT COUNT(1) FROM geo_monitor_daily d WHERE d.topic_id = t.id) daily_cnt,
                      (SELECT COUNT(1) FROM geo_content_placement p WHERE p.topic_id = t.id) place_cnt,
                      (SELECT COUNT(1) FROM geo_year_target y WHERE y.topic_id = t.id) year_cnt,
                      (SELECT COUNT(1) FROM geo_board_period_stat b WHERE b.topic_id = t.id) board_cnt
                    FROM geo_topic t ORDER BY t.id
                    """)) {
                while (rs.next()) {
                    out.append(String.format(Locale.ROOT, "%d\t%s\tactive=%d\tdaily=%d\tplace=%d\tyear=%d\tboard=%d%n",
                            rs.getLong("id"), rs.getString("topic_name"), rs.getInt("is_active"),
                            rs.getLong("daily_cnt"), rs.getLong("place_cnt"),
                            rs.getLong("year_cnt"), rs.getLong("board_cnt")));
                }
            }
            out.append("\n=== orphan daily (topic_id not in geo_topic) ===\n");
            try (ResultSet rs = st.executeQuery("""
                    SELECT COUNT(1) c FROM geo_monitor_daily d
                    LEFT JOIN geo_topic t ON t.id = d.topic_id WHERE t.id IS NULL
                    """)) {
                rs.next();
                out.append("count=").append(rs.getLong(1)).append('\n');
            }
            out.append("\n=== orphan placement ===\n");
            try (ResultSet rs = st.executeQuery("""
                    SELECT COUNT(1) c FROM geo_content_placement p
                    LEFT JOIN geo_topic t ON t.id = p.topic_id
                    WHERE p.topic_id IS NOT NULL AND t.id IS NULL
                    """)) {
                rs.next();
                out.append("count=").append(rs.getLong(1)).append('\n');
            }
            out.append("\n=== placement topic_name mismatch ===\n");
            try (ResultSet rs = st.executeQuery("""
                    SELECT p.id, p.topic_id, p.topic_name, t.topic_name AS real_name
                    FROM geo_content_placement p
                    JOIN geo_topic t ON t.id = p.topic_id
                    WHERE p.topic_name <> t.topic_name
                    LIMIT 20
                    """)) {
                int n = 0;
                while (rs.next()) {
                    n++;
                    out.append(rs.getLong("id")).append('\t')
                            .append(rs.getLong("topic_id")).append('\t')
                            .append(rs.getString("topic_name")).append(" => ")
                            .append(rs.getString("real_name")).append('\n');
                }
                if (n == 0) out.append("(none)\n");
            }
        }
        Path path = Path.of("topic-verify.txt");
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        System.out.println("wrote " + path.toAbsolutePath());
    }
}
