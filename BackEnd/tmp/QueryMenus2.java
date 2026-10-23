import java.sql.*;

public class QueryMenus2 {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(args[0], args[1], args[2]);
             Statement s = c.createStatement()) {
            System.out.println("=== 180-220 ===");
            try (ResultSet rs = s.executeQuery("""
                    SELECT menu_id, menu_name, parent_id, menu_type, path, perms, is_active
                    FROM sys_menu WHERE menu_id BETWEEN 180 AND 230
                    ORDER BY menu_id
                    """)) {
                while (rs.next()) {
                    System.out.printf("%s\t%s\tp=%s\t%s\t%s\t%s\ta=%s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
                }
            }
            System.out.println("=== max menu_id ===");
            try (ResultSet rs = s.executeQuery("SELECT MAX(menu_id) FROM sys_menu")) {
                rs.next();
                System.out.println(rs.getString(1));
            }
            System.out.println("=== children of 201/203/209 ===");
            try (ResultSet rs = s.executeQuery("""
                    SELECT menu_id, menu_name, parent_id, menu_type, path
                    FROM sys_menu WHERE parent_id IN (201,203,209,200) OR menu_id IN (200,201,203,205,209)
                    ORDER BY parent_id, menu_id
                    """)) {
                while (rs.next()) {
                    System.out.printf("%s\t%s\tp=%s\t%s\t%s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5));
                }
            }
        }
    }
}
