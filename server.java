import java.io.*;
import java.net.*;
import java.lang.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Statement;
import java.math.BigInteger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.security.MessageDigest;



public class server {
	public static void main(String args[]) throws Exception {
		ServerSocket sock = new ServerSocket(3001);
		int tCount = 0;
		LinkedBlockingQueue<SQLUpdate> q = new LinkedBlockingQueue<SQLUpdate>(40); //40 things can be in queue, that way we don't have too much concurrency
		LinkedBlockingQueue<LoginRequest> lq = new LinkedBlockingQueue<LoginRequest>(40);
		Thread sqlThread = new Thread(new SQLHandler("jdbc:MySql://localhost/devicefinder_development", args[0], args[1], q));
		sqlThread.start();

		Thread loginThread = new Thread(new SQLLogin("jdbc:MySql://localhost/devicefinder_development", args[0], args[1], lq));
		loginThread.start();


		while (true) {

			Socket mySock = sock.accept();
			new Thread(new ServerHandler(mySock, tCount, q, lq)).start();
			tCount++;
		}
	}
}

class ServerHandler implements Runnable {
	Socket mSock;
	int connectionNumber;
	LinkedBlockingQueue<SQLUpdate> q;
	LinkedBlockingQueue<LoginRequest> lq;
	MessageDigest md;
	public ServerHandler(Socket mySocket, int n, LinkedBlockingQueue<SQLUpdate> queue, LinkedBlockingQueue<LoginRequest> lqueue) {
		mSock = mySocket;
		connectionNumber = n;
		q = queue;
		lq = lqueue;
		try {md = MessageDigest.getInstance("SHA-1");} catch (Exception e) {}
	}

	public void run() {
		BufferedReader in;
		DataOutputStream sockout;
		DataInputStream din = null;
		try {
			sockout = new DataOutputStream(mSock.getOutputStream());
			din = new DataInputStream(mSock.getInputStream());
		} catch (Exception e) {
			in = null;
			sockout = null;
			e.printStackTrace();
			try {mSock.close();} catch(Exception e2) {}
			return;
		}
		String s = null;
		try {
			int datalen = din.readInt();
			byte[] databytes = new byte[datalen];
			din.readFully(databytes);
			s = new String(databytes);
		} catch (Exception e) {}
		if (s == null) {
			System.out.println("socket was closed on other end");
			try { mSock.close(); } catch (Exception e) {} //socket needs to be closed
			return;
		}
		String[] myTs = s.split(" ");

		int choice = Integer.parseInt(myTs[0]);

		if (choice == 0) { //user login
			if (myTs.length != 6) {
				try { mSock.close(); } catch(Exception e) {}
				return;
			}
			Encryptor enc = new Encryptor(myTs[2], myTs[1]);
			enc.genSecret();
			BigInteger key = enc.getKey(myTs[3]);
			BigInteger calculated = enc.firstRound(); //calculate first public calculation to send back to phone
			String userEmail = myTs[4];
			int deviceID = Integer.parseInt(myTs[5]);
			LoginRequest lr = new LoginRequest(deviceID, userEmail);
			try {
				synchronized(lr) {
					lq.put(lr);
					lr.wait();
				}
				if (!lr.success || lr.hashpw == null) {
					mSock.close();
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();

			}

			String salt = lr.hashpw.split("[$]")[3].substring(0, 22);
			try {
				byte[] dataString = ("" + calculated + " " + salt).getBytes("UTF-8"); // wrote to phone
				int datalen = dataString.length;
				sockout.writeInt(datalen);
				sockout.write(dataString, 0, datalen);
			} catch (Exception e) {
				e.printStackTrace();
				try {
					mSock.close();
				} catch (Exception e2) {
					return;
				}
				return;
			}
			int numbytes = 0;
			try {numbytes = din.readInt();} catch (Exception e) {}
			if (numbytes == 0) {
				try {mSock.close();} catch(Exception e) {}
				return;
			}
			byte[] loginAuth = new byte[numbytes];
			try {din.readFully(loginAuth);} catch (Exception e) { e.printStackTrace();}
			try {
				byte[] tempFinalKey = md.digest(key.toByteArray());
				byte[] finalKey = new byte[16];
				System.arraycopy(tempFinalKey, 0, finalKey, 0, 16);
				SecretKeySpec aeskey = new SecretKeySpec(finalKey, "AES");
				Cipher myCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
				myCipher.init(Cipher.DECRYPT_MODE, aeskey);
				byte[] dcrMsg = myCipher.doFinal(loginAuth);

				if (!(new String(dcrMsg).equals(lr.hashpw))) {
					sockout.writeInt(-1);
					mSock.close();
					return;
				}

				lr.type = 1;
				lr.authKey = key.toString();
				synchronized (lr) {
					lq.put(lr);
					lr.wait();
				}
				System.out.println("Device id: " + lr.deviceID);
				sockout.writeInt(lr.deviceID);
				mSock.close();
				return;
			} catch (Exception e) {
				e.printStackTrace();
				try {mSock.close();} catch (Exception e2) {}
				return;
			}

		}
		else if (choice == 1) { //location update
			/*String[] tokens = s.split("[ ]"); //order should be "[device id] [email] [lat:string] [long:string]"
			int id = Integer.parseInt(tokens[0]);
			String em = tokens[1];
			String lat = tokens[2];
			String lng = tokens[3];
			SQLUpdate upd = new SQLUpdate(id, em, lat, lng);
			try { //wait for sql thread to return device id for phone to save
				synchronized(upd) {
					q.put(upd);
					upd.wait();
				}
			} catch (Exception e) {
				System.out.println("Error putting to queue");
			}
			id = upd.phoneId;

			try {
				sockout.writeInt(id);
				mSock.close();
			} catch (Exception e) {
				System.out.println("Error writing to socket");
			}*/
			try {
				int devID = din.readInt();
				int datalen = din.readInt();
				byte[] encrMsg = new byte[datalen];
				din.readFully(encrMsg);
				SQLUpdate upd = new SQLUpdate();
				upd.phoneId = devID;
				synchronized(upd) {
					q.put(upd);
					upd.wait();
				}
				byte[] finalKey = new byte[16];
				System.arraycopy(md.digest(upd.key), 0, finalKey, 0, 16);
				SecretKeySpec aeskey = new SecretKeySpec(finalKey, "AES");
				Cipher myCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
				myCipher.init(Cipher.DECRYPT_MODE, aeskey);
				byte[] dcrMsg = myCipher.doFinal(encrMsg);
				String dcrMsgStr = new String(dcrMsg);
				String[] updTokens = dcrMsgStr.split(" ");
				if (!updTokens[0].equals("hello")){
					mSock.close();
					return;
				}
				upd.deviceName = updTokens[1];
				int i = 2;
				for (i = 2; i < updTokens.length - 3; i++) {
					upd.deviceName += " " + updTokens[i];
				}
				upd.type = 1;
				upd.lat = updTokens[i];
				upd.lng = updTokens[i + 1];
				upd.ip = updTokens[i + 2];
				System.out.println(upd.ip);
				mSock.close();
				q.put(upd);
				return;
			} catch (Exception e) {
				try {mSock.close();} catch(Exception e2) {}
				e.printStackTrace();
				if (md == null) {
					try {
						md = MessageDigest.getInstance("SHA-1");
					} catch (Exception e2) {
						e.printStackTrace();
					}
				}
				return;
			}

		}
		try {mSock.close();} catch (Exception e) {e.printStackTrace();}
		return;
	}
}

class SQLUpdate { //Object consumed to update a phone location (or create a new one if needed)
	public int phoneId;
	public String deviceName;
	public byte[] key;
	public String lng;
	public String lat;
	public int type;
	public String ip;
	boolean success;

	public SQLUpdate() {
		phoneId = 0;
		deviceName = null;
		lng = null;
		lat = null;
		key = null;
		type = 0;
		success = false;
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
				/*if (upd != null) {
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
						} else { //no entry for the current device id... you're gonna have to create a new one
							st.executeUpdate("insert into devices (deviceNum, created_at, updated_at) VALUES ('1', '2016-10-23 18:25:07', '2016-10-23 18:25:07')");
							rs = st.executeQuery("Select * from devices ORDER BY id DESC LIMIT 1;");
							if (rs.next()){ //inserted successfully
								upd.phoneId = rs.getInt("id");
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
							} else {
								upd.phoneId = -1; //error inserting into table maybe?
							}
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
				upd.notify();*/
				if (upd != null) {
					if (upd.type == 0) { //I need the decryption key
						try {
							rs = st.executeQuery("SELECT * FROM devices WHERE id LIKE " + upd.phoneId);
							if (rs.next()) { //device exists;
								BigInteger keyString = new BigInteger(rs.getString("myKey"));
								upd.success = true;
								upd.key = keyString.toByteArray();
							}
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							try {
								if (rs != null) rs.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					} else if (upd.type == 1) {
						try {
							rs = st.executeQuery("SELECT * FROM devices WHERE id LIKE " + upd.phoneId);
							if (rs.next()) { //device exists
								rs.updateString("deviceName", upd.deviceName);
								rs.updateString("lat", upd.lat);
								rs.updateString("long", upd.lng);
								rs.updateString("ipauth", upd.ip);
								rs.updateTimestamp("updated_at", new Timestamp(new java.util.Date().getTime()));
								rs.updateRow();
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
						finally {
							try {
								if (rs != null) rs.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				}
				upd.notify();
			}
		}


	}
}


class LoginRequest {
	String email;
	String hashpw;
	String authKey;
	int deviceID;
	boolean success;
	int userID;
	int type;


	public LoginRequest(int d, String e) {
		deviceID = d;
		email = e;
		hashpw = null;
		authKey = null;
		success = false;
		userID = 0;
		type = 0;
	}
}

class SQLLogin implements Runnable {
	Connection con;
	Statement st;
	ResultSet rs;
	LinkedBlockingQueue<LoginRequest> q;

	String url;
	String user;
	String password;

	public SQLLogin(String ur, String us, String pw, LinkedBlockingQueue<LoginRequest> queue) {
		url = ur;
		user = us;
		password = pw;
		q = queue;
	}

	public void run() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection(url, user, password);
			st = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		while (true) {
			LoginRequest logInfo = null;

			try {
				logInfo = q.take();
			} catch (Exception e) {
				e.printStackTrace(); //something interrupted us
				System.exit(0);
			}
			if (logInfo == null) continue;
			synchronized (logInfo) {
				if (logInfo.type == 0) {
					try {
						rs = st.executeQuery("SELECT * FROM users WHERE email LIKE \"" + logInfo.email + "\";");
						if (rs.next()) {
							logInfo.hashpw = rs.getString("encrypted_password");
							logInfo.success = true;
							logInfo.userID = rs.getInt("id");
						}
						if (rs != null) rs.close();

					} catch (Exception e) {
						e.printStackTrace();
						System.exit(0);
					}
				} else {
					try {
						rs = st.executeQuery("SELECT * FROM devices WHERE id LIKE '" + logInfo.deviceID + "';");
						if (rs.next()) { //device exists
							rs.updateInt("user_id", logInfo.userID);
							rs.updateString("myKey", logInfo.authKey);
							rs.updateRow();
						}
						else { //need to create new device
							PreparedStatement pst = con.prepareStatement("insert into devices (deviceNum, created_at, updated_at) VALUES ('1', '2016-10-23 18:25:07', '2016-10-23 18:25:07')");
																		//"insert into devices (deviceNum, created_at, updated_at) VALUES ('1', '2016-10-23 18:25:07', '2016-10-23 18:25:07')"
																		//"INSERT INTO devices (user_id, myKey, lat, long, deviceName, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?);"
							pst.executeUpdate();
							if (pst != null) pst.close();
							if (rs != null) rs.close();
							rs = st.executeQuery("SELECT * FROM devices;");
							rs.last();
							rs.updateInt("user_id", logInfo.userID);
							rs.updateString("myKey", logInfo.authKey);
							rs.updateString("lat", "33");
							rs.updateString("long", "33");
							rs.updateString("deviceName", "my device");
							rs.updateTimestamp("created_at", new Timestamp(new java.util.Date().getTime()));
							rs.updateTimestamp("updated_at", new Timestamp(new java.util.Date().getTime()));
							rs.updateRow();
							logInfo.deviceID = rs.getInt("id");


							if (pst != null) {
								pst.close();
							}


						}
						if (rs != null) rs.close();

					} catch (Exception e) {
						System.out.println("login sql error");
						e.printStackTrace();
					}

				}
				logInfo.notify();
			}
		}
	}
}
