all: SqlTest.java
	javac -classpath .:/usr/share/java/mysql.jar SqlTest.java

run:
	java -classpath .:/usr/share/java/mysql.jar server root 3jYyzw{p
server: server.java
	javac -classpath .:/usr/share/java/mysql.jar server.java
clean:
	rm *.class