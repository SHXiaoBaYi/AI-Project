import java.sql.*;

public class QueryMenus {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = args[0];
        String user = args[1];
        String pass = args[2];
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("""
                     SELECT menu_id, menu_name, parent_id, menu_type, path, sort_order, visible, status, is_active
                     FROM sys_menu
                     WHERE menu_id IN (100,130,132,178,182,187,192,196,200,205,209)
                        OR parent_id IN (130,132,209)
                     ORDER BY parent_id, sort_order, menu_id
                     """)) {
            while (rs.next()) {
                System.out.printf("%s\t%s\tparent=%s\ttype=%s\tpath=%s\tsort=%s\tv=%s\ts=%s\ta=%s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9));
            }
        }
    }
}
