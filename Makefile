all: SqlTest.java
	javac -classpath .:/usr/share/java/mysql.jar SqlTest.java

run:
	java -classpath .:/usr/share/java/mysql.jar server team1 wheresmyphone1
server: server.java
	javac -classpath .:/usr/share/java/mysql.jar server.java
clean:
	rm *.class
