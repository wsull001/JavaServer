import java.math.BigInteger;
import java.security.SecureRandom;


public class Encryptor {
	BigInteger q, p;
	BigInteger secret;
	BigInteger key;
	public Encryptor() {
		SecureRandom rnd = new SecureRandom();
		p = BigInteger.probablePrime(512, rnd);
		q = BigInteger.probablePrime(512, rnd);
	}

	public Encryptor(String sq, String sp) {
		q = new BigInteger(sq);
		p = new BigInteger(sp);
	}

	public BigInteger getQ() {
		return q;
	}

	public BigInteger getP() {
		return p;
	}

	public void genSecret() {
		SecureRandom rnd = new SecureRandom();
		do {
			secret = new BigInteger(512, rnd);
		} while (secret.signum() != 1);
	}

	public BigInteger firstRound() {
		 return q.modPow(secret, p);
	}

	public BigInteger getKey(String v) {
		BigInteger temp = new BigInteger(v);
		return temp.modPow(secret, p);
	}
}