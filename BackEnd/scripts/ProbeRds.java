import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** 探测生产/测试 RDS 外网连通性（一次性脚本，勿提交密钥到业务代码）。 */
public class ProbeRds {
  public static void main(String[] args) throws Exception {
    String user = System.getenv().getOrDefault("RDS_USER", "xiaobayi0099");
    String pass = System.getenv().getOrDefault("RDS_PASS", "Xby@123456");
    String[] hosts = {
      "prod|" + System.getenv().getOrDefault("PROD_RDS_HOST", "rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com"),
      "test|" + System.getenv().getOrDefault("TEST_RDS_HOST", "rm-bp15f2a3k5b93293kso.mysql.rds.aliyuncs.com")
    };
    Class.forName("com.mysql.cj.jdbc.Driver");
    for (String item : hosts) {
      String[] p = item.split("\\|", 2);
      String label = p[0];
      String host = p[1];
      String url = "jdbc:mysql://" + host + ":3306/?useSSL=true&allowPublicKeyRetrieval=true&connectTimeout=8000&socketTimeout=8000";
      System.out.println("==> " + label + " " + host);
      try (Connection c = DriverManager.getConnection(url, user, pass);
           Statement st = c.createStatement();
           ResultSet rs = st.executeQuery("SELECT VERSION(), DATABASE()")) {
        rs.next();
        System.out.println("    OK version=" + rs.getString(1));
        try (ResultSet dbs = st.executeQuery("SHOW DATABASES LIKE 'base_admin'")) {
          System.out.println(dbs.next() ? "    base_admin EXISTS" : "    base_admin MISSING");
        }
        try (ResultSet tables = st.executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='base_admin'")) {
          if (tables.next()) {
            System.out.println("    tables=" + tables.getInt(1));
          }
        }
      } catch (Exception e) {
        System.out.println("    FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
  }
}
