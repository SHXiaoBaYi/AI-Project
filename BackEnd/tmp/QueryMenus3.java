import java.sql.*;

public class QueryMenus3 {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(args[0], args[1], args[2]);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("""
                     SELECT menu_id, menu_name, parent_id, menu_type, path, perms, is_active
                     FROM sys_menu
                     WHERE path IN ('geo/review-kb','geo/gen-kb','geo/sell-point','geo/media','geo/style-pack','geo/skeleton')
                        OR perms LIKE 'geo:reviewKb%'
                        OR perms LIKE 'geo:skeleton%'
                        OR menu_id BETWEEN 310 AND 330
                     ORDER BY menu_id
                     """)) {
            while (rs.next()) {
                System.out.printf("%s\t%s\tp=%s\t%s\t%s\t%s\ta=%s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
            }
        }
    }
}
