/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Elias Naur 2006
 * 
 * $Id$
 */
import java.io.*;
import java.nio.*;
import java.nio.channels.*;

/**
 * @author Elias Naur
 */
public class TestFileChannel {
	public static void main(String[] args) {
		final int MAGIC_INT = 0xdeadbeef;
		final String TEST_FILE = "test.out";
		try {
			ByteBuffer buffer = ByteBuffer.allocateDirect(4);
			ByteChannel write_channel = new FileOutputStream(TEST_FILE).getChannel();
			buffer.putInt(MAGIC_INT);
			buffer.flip();
			write_channel.write(buffer);
			write_channel.close();
			ByteChannel read_channel = new FileInputStream(TEST_FILE).getChannel();
			buffer.clear();
			while (buffer.hasRemaining())
				read_channel.read(buffer);
			read_channel.close();
			buffer.flip();
			int file_int = buffer.getInt();
			if (file_int != MAGIC_INT)
				throw new Error("Wrote " + Integer.toHexString(MAGIC_INT) + " but read " + Integer.toHexString(file_int));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
