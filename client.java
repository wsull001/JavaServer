import java.net.*;
import java.io.*;

public class client {
	
	public static void main(String argv[]) throws Exception {
		
		String msg = "This is a message";
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Enter id: ");
		msg = in.readLine();
		int id = Integer.parseInt(msg);
		System.out.println("Enter email: ");
		msg = in.readLine();
		String email = msg;
		System.out.println("Enter latitude: ");
		msg = in.readLine();
		String lat = msg;
		System.out.println("Enter longitude: ");
		msg = in.readLine();
		String lng = msg;
		msg = "" + id + " " + email + " " + lat + " " + lng;
		Socket clientSocket = new Socket("localhost", 3001);
		DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
		out.writeBytes(msg + "\n");
		DataInputStream sin = new DataInputStream(clientSocket.getInputStream());
		int var = sin.readInt();
		msg = "worked";
		System.out.println(var);
		
	}
}