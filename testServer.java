import java.io.*;
import java.net.*;
import java.lang.*;

public class testServer {
	public static void main(String[] args) {
		try {
			ServerSocket ss = new ServerSocket(3005);
			Socket s = ss.accept();
			BufferedReader sin = new BufferedReader(new InputStreamReader(s.getInputStream()));
			String str = sin.readLine();
			System.out.println(str);
			s.close();
			ss.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}