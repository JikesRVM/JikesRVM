package org.jikesrvm.energy;
import java.util.Random;

public class RandomSingleton {
	private static final Random INSTANCE = new Random();
	private RandomSingleton(){}

	public static Random getInstance() {
		return INSTANCE;
	}
}
