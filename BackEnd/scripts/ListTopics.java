import java.sql.*;

public class ListTopics {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com:3306/base_admin"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true&allowPublicKeyRetrieval=true";
        String user = System.getenv().getOrDefault("RDS_USERNAME", "xiaobayi0099");
        String pass = System.getenv().getOrDefault("RDS_PASSWORD", "Xby@123456");
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT t.id, t.topic_name, t.is_active,
                       (SELECT COUNT(1) FROM geo_monitor_daily d WHERE d.topic_id = t.id) daily_cnt,
                       (SELECT COUNT(1) FROM geo_content_placement p WHERE p.topic_id = t.id) place_cnt,
                       (SELECT COUNT(1) FROM geo_year_target y WHERE y.topic_id = t.id) year_cnt
                     FROM geo_topic t
                     ORDER BY t.id
                     """)) {
            while (rs.next()) {
                System.out.printf("%d\t%s\tactive=%d\tdaily=%d\tplace=%d\tyear=%d%n",
                        rs.getLong("id"), rs.getString("topic_name"), rs.getInt("is_active"),
                        rs.getLong("daily_cnt"), rs.getLong("place_cnt"), rs.getLong("year_cnt"));
            }
        }
    }
}
