/*
 * (c) Copyright IBM Corp. 2000, 2001.
 */
package org.eclipse.jdt.internal.launching.jikesrvm;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;

/**
 * @author Jeffrey Palm
 */
public class SocketUtil {
	private static final Random fgRandom= new Random(System.currentTimeMillis());
	
	public static int findUnusedLocalPort(String host, int searchFrom, int searchTo) {
		for (int i= 0; i < 10; i++) {
			int port= getRandomPort(searchFrom, searchTo);
			try {
				new Socket(host, port);
			} catch (SocketException e) {
				return port;
			} catch (IOException e) {
			}
		}
		return -1;
	}

	private static int getRandomPort(int low, int high) {
		return (int)(fgRandom.nextFloat()*(high-low))+low;
	}
}
