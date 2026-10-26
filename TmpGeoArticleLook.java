import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class TmpGeoArticleLook {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:mysql://127.0.0.1:13306/base_admin?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
    StringBuilder out = new StringBuilder();
    try (Connection c = DriverManager.getConnection(url, "xiaobayi0099", "Xby@123456")) {
      out.append("=== placement 1605 ===\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery(
               "SELECT id, topic_name, target_question, create_time, update_time FROM geo_content_placement WHERE id=1605")) {
        while (rs.next()) {
          out.append("id=").append(rs.getLong(1))
              .append(" topic=").append(rs.getString(2))
              .append(" q=").append(rs.getString(3))
              .append(" ct=").append(rs.getString(4))
              .append(" ut=").append(rs.getString(5)).append('\n');
        }
      }

      out.append("\n=== items for 1605 ===\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery(
               """
               SELECT id, title, content_title, content_status, create_by, create_time, content_updated_at,
                      CHAR_LENGTH(IFNULL(content_body,'')) blen
               FROM geo_content_placement_item WHERE placement_id=1605 ORDER BY id DESC
               """)) {
        while (rs.next()) {
          out.append("item=").append(rs.getLong("id"))
              .append(" title=").append(rs.getString("title"))
              .append(" content_title=").append(rs.getString("content_title"))
              .append(" status=").append(rs.getString("content_status"))
              .append(" by=").append(rs.getString("create_by"))
              .append(" blen=").append(rs.getLong("blen"))
              .append(" updated=").append(rs.getString("content_updated_at"))
              .append('\n');
        }
      }

      out.append("\n=== dump bodies ===\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery(
               "SELECT id, title, content_title, content_body FROM geo_content_placement_item WHERE placement_id=1605 ORDER BY id DESC")) {
        int k = 0;
        while (rs.next()) {
          k++;
          String body = rs.getString("content_body");
          Path f = Path.of("tmp-geo-1605-" + k + "-id" + rs.getLong("id") + ".html");
          String meta = "id=" + rs.getLong("id")
              + "\ntitle=" + rs.getString("title")
              + "\ncontent_title=" + rs.getString("content_title")
              + "\n\n";
          Files.writeString(f, meta + (body == null ? "" : body), StandardCharsets.UTF_8);
          out.append("wrote ").append(f).append(" len=").append(body == null ? 0 : body.length()).append('\n');
        }
      }

      out.append("\n=== reviews for 1605 ===\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery(
               """
               SELECT id, round_no, status, content_title, CHAR_LENGTH(IFNULL(content_body,'')) blen, create_time
               FROM geo_content_review WHERE placement_id=1605 ORDER BY id DESC
               """)) {
        while (rs.next()) {
          out.append("review=").append(rs.getLong(1))
              .append(" round=").append(rs.getInt(2))
              .append(" status=").append(rs.getString(3))
              .append(" title=").append(rs.getString(4))
              .append(" blen=").append(rs.getLong(5))
              .append(" ct=").append(rs.getString(6)).append('\n');
        }
      }

      out.append("\n=== trace 6 full stages ===\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery("SELECT stages_json FROM geo_article_gen_trace WHERE id=6")) {
        if (rs.next()) out.append(rs.getString(1)).append('\n');
      }

      // sell points that would be recalled for this topic - sample domains
      out.append("\n=== sell points sample (enabled) count by domain ===\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery(
               """
               SELECT d.name domain_name, COUNT(*) cnt
               FROM geo_sell_point p
               LEFT JOIN geo_sell_domain d ON d.id=p.domain_id
               WHERE p.enabled=1 OR p.is_active=1
               GROUP BY d.name
               ORDER BY cnt DESC
               LIMIT 20
               """)) {
        while (rs.next()) {
          out.append(rs.getString(1)).append("=").append(rs.getLong(2)).append('\n');
        }
      }
    } catch (SQLException e) {
      out.append("ERR ").append(e.getMessage()).append('\n');
      // retry sell without wrong columns
      try (Connection c = DriverManager.getConnection(url, "xiaobayi0099", "Xby@123456")) {
        out.append("\n=== sell_point columns ===\n");
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW COLUMNS FROM geo_sell_point")) {
          while (rs.next()) out.append(rs.getString(1)).append(' ').append(rs.getString(2)).append('\n');
        }
      }
    }
    Files.writeString(Path.of("tmp-geo-look-out.txt"), out.toString(), StandardCharsets.UTF_8);
    System.out.println("done bytes=" + out.length());
  }
}
