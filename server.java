import java.io.*;
import java.net.*;
import java.lang.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;



public class server {
	public static void main(String args[]) throws Exception {
		ServerSocket sock = new ServerSocket(3001);
		Thread[] T = new Thread[10];
		int tCount = 0;
		boolean[] threads = new boolean[10];
		LinkedBlockingQueue<SQLUpdate> q = new LinkedBlockingQueue<SQLUpdate>(40); //40 things can be in queu
		Thread sqlThread = new Thread(new SQLHandler("jdbc:MySql://localhost/devicefinder_development", args[0], args[1], q));
		sqlThread.start();
		for (int i = 0; i < 10; i++) {
			threads[i] = false; // no threads
		}

		
		
		while (true) {
			if (tCount < 10) {
				Socket mySock = sock.accept();
				int i;
				for (i = 0; i < 10; i++) {
					if (!threads[i]) {
						threads[i] = true;
						break;
					}
				}
				T[i] = new Thread(new ServerHandler(mySock, i, q));
				T[i].start(); //start thread handler
				tCount++; //increment thread count
			} else {
				int i;
				for (i = 0; i < 10; i++) {
					if (T[i].getState() == Thread.State.TERMINATED) {
						T[i].join(); //clean up if works like process
						threads[i] = false; //free up a connection
						tCount--; //decrement count
						System.out.println("Connection " + i + " terminated");
					}
				}
			}
		}
	}
}

class ServerHandler implements Runnable {
	Socket mSock;
	int connectionNumber;
	LinkedBlockingQueue<SQLUpdate> q;
	public ServerHandler(Socket mySocket, int n, LinkedBlockingQueue<SQLUpdate> queue) {
		mSock = mySocket;
		connectionNumber = n;
		q = queue;
	}
	
	public void run() {
		BufferedReader in;
		try {
			
			in = new BufferedReader(new InputStreamReader(mSock.getInputStream()));
		} catch (Exception e) {
			in = null;
		}
		String s = null;
		try { s = in.readLine(); } catch (Exception e) {}
		if (s == null) {
			try { mSock.close(); } catch (Exception e) {} //socket needs to be closed
			return;
		}

		String[] tokens = s.split("[ ]"); //order should be "[device id] [email] [lat:string] [long:string]"
		int id = Integer.parseInt(tokens[0]);
		String em = tokens[1];
		String lat = tokens[2];
		String lng = tokens[3];
		if (id == 0) {
			try {
				Class.forName("com.mysql.jdbc.Driver");
				Connection cn = DriverManager.getConnection("jdbc:MySql://localhost/devicefinder_development", "root", "3jYyzw{p");
				Statement st = cn.createStatement();
				//st.executeQuery("INSERT INTO devices (deviceNum) VALUES ('1');");
				st.executeUpdate("insert into devices (deviceNum, created_at, updated_at) VALUES ('1', '2016-10-23 18:25:07', '2016-10-23 18:25:07')");
				ResultSet rs = st.executeQuery("select * from devices;");
				if (rs.last()) id = rs.getInt("id");
				if (rs != null) rs.close();
				if (st != null) st.close();
				if (cn != null) cn.close();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}
		
		SQLUpdate upd = new SQLUpdate(id, em, lat, lng);
		try {
			synchronized(upd) {
				q.put(upd);
				upd.wait();
			}
			
		} catch (Exception e) {
			System.out.println("Error putting to queue");
		}
		System.out.println("We woke up successfully");
		id = upd.phoneId;

		try {
			DataOutputStream sockout = new DataOutputStream(mSock.getOutputStream());
			sockout.writeInt(id);
			mSock.close();
		} catch (Exception e) {
			System.out.println("Error writing to socket");
		}
		return;
	}
}

class SQLUpdate { //Object consumed to update a phone location (or create a new one if needed)
	public int phoneId;
	public String email;
	public String lng;
	public String lat;

	public SQLUpdate(int id, String em, String la, String ln) {
		phoneId = id;
		email = em;
		lng = ln;
		lat = la;
	}
}

class SQLHandler implements Runnable {

	Connection con = null;
	Statement st = null;
	ResultSet rs = null;
	LinkedBlockingQueue<SQLUpdate> q = null;

	String url;
	String user;
	String password;

	public SQLHandler(String u, String use, String pass, LinkedBlockingQueue<SQLUpdate> queue) {
		url = u;
		user = use;
		password = pass;
		q = queue;
	}
	
	public void run() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection(url, user, password);
			st = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		} catch (Exception e) {
			System.out.println("Connection error");
			System.exit(0);
		}

		//now let's loop
		while (true) {
			SQLUpdate upd = null;
			try {
				upd = q.take();
			} catch (Exception e) {
				System.out.println("Error, interrupted");
				System.exit(0);
			}
			synchronized(upd) {
				if (upd != null) {
					try {
						rs = st.executeQuery("SELECT * FROM devices where id like " + upd.phoneId);
						if (rs.next()) {
							rs.updateString("lat", upd.lat);
							rs.updateString("long", upd.lng);
							if (upd.email != null) {
								Statement s2 = con.createStatement();
								ResultSet rs2 = s2.executeQuery("SELECT * FROM users where email like \"" + upd.email + "\";");
								if (rs2.next()) {
									rs.updateInt("user_id", rs2.getInt("id"));
								}
								if (rs2 != null) rs2.close();
								if (s2 != null) s2.close();

							}
							rs.updateRow();
						}
					} catch (Exception e) {
						System.out.print("Server Crashed");
					} finally {
						try {
							if (rs != null) rs.close();
						} catch (Exception e) {

						}
					}
				}
				upd.notify();
			}
		}


	}
}