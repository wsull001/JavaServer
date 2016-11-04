import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.*;

public class SqlTest {
	public static void main(String[] argv) {
		Connection con = null;
		Statement st = null;
		ResultSet rs = null;
		
		String url = "jdbc:MySql://localhost/devicefinder_development";
		String user = "root";
		String password = "3jYyzw{p";
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (Exception e) {
			System.out.println("This didn't work");
		}
		try {
			con = DriverManager.getConnection(url, user, password);
			st = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			rs = st.executeQuery("SELECT * FROM users");
			if (rs.next()) {
				String email = rs.getString("email");
				rs.updateString("email", "pepperoni@pizza.com");
				rs.updateRow();
				int id = rs.getInt("id");
				System.out.println(email + " " + id);
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				
				if (st != null) {
					st.close();
				}
				
				if (con != null) {
					con.close();
				}
			} catch (SQLException e) {
			}
				
		}
		
	}
}