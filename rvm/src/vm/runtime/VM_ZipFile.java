/*
 * (C) Copyright IBM Corp. 2001
 */

// $Id$

/*
 * @author Maria Butrico
 * @author Julian Dolby
 * @author Tracy Ferguson 
 */

import java.io.IOException;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.NoSuchElementException;
import java.util.zip.*;

/**
  * Implements zip file reading for RVM, replacing OTI
  * native code
  */
public class VM_ZipFile implements ZipConstants {

// class properties

// missing fields in ZipConstants
// size of ...
private static final int SIZELFH = 30;	// local file header
private static final int SIZECDS = 46;	// central dir struct
private static final int SIZECDR = 22;	// end of central dir record

// offset in the local fiel header of ...
private static final int LFHEXTLN = 28;

// offset in the central directory structure of ...
private static final int CDSCOMPMETHOD = 10;	// compresssion method
private static final int CDSMODTIME = 12;	// file mod. time
private static final int CDSMODDATE = 14;	// file mod. date
private static final int CDSCRC = 16;		// crc-32
private static final int CDSCSIZE = 20;         // compressed size
private static final int CDSUSIZE = 24;		// uncompressed size
private static final int CDSFNL = 28;		// filename length
private static final int CDSEXTLN = 30;		// extra field length
private static final int CDSFCOMNTLEN = 32;	// file comment length
private static final int CDSOFFSETLFH = 42;	// offset local file header
private static final int CDSFNAME = 46;		// filename

// offset in the end of central dir record of ...
private static final int ECDRTOTAL = 10;	// total central dir entries
private static final int ECDROFFSET = 16;	// offset central dir


private static final boolean traceZip = false;


// instance properties
private String zipFilename;
private ZipHash index;
private int numberEntries;
private RandomAccessFile zipFileRAF;


// class methods
/**
  * @param	zipFilename	the file name of the zip file
  *
  * @exception	ZipException	zip format error
  * @exception	IOException	io error
  */
public VM_ZipFile(String zipFileName) 
	throws IOException {
	zipFilename = zipFileName;
	index = new ZipHash();
	numberEntries = 0;
	readZipFile();
}  // end constructor


/**
  * Returns the zip entry which contains
  * data from the corresponding entry in the zip file,
  * or null if the entry was not found
  *
  * @param	entryName
  *
  * @return	The ZipEntry, or null if the
  *		entry is not found in the file, or if the file was
  *		close.
  *
  * Note that according to the java documentation we should throw an
  * exception of the file was close, but the exception is not in the
  * method signature.
  */
public ZipEntry getEntryImpl(String entryName) {
	if (traceZip) VM.sysWrite("VM_ZipFile.getEntryImpl(" + entryName + ")\n" );
	return (ZipEntry) index.get(entryName);
}  // end getEntryImpl


/**
  * returns the set of entries in the zip file.
  *
  * @return	enumeration of ZipEntry in the zip file
  */
public Enumeration getEntries() {
	return index.elements();
}  //  end getEntries


/**
  * Reads the directory of a zip file and constructs its index
  *
  * @exception	ZipException	zip format error
  * @exception	IOException	io error
  */
private void readZipFile() throws IOException {

	byte buffer[];

	/* open the file and read the "end of central directory
	   header.  This should start at some fixed offset from
	   the end of the file * unless there is a zip file comment *.
	   This code does not handle that case.  From the end header
	   the offset and length of the central directory are
	   extracted.
	*/

	if (traceZip) VM.sysWrite("ZipFileJnp.readZipFile: reading file name " + zipFilename + "\n");
	zipFileRAF = new RandomAccessFile(zipFilename, "r");
	long fileLength = zipFileRAF.length();
	
	buffer = new byte[4096];

	if (traceZip) VM.sysWrite("ZipFileJnp.readZipFile: before reading end of central dir header file name " + zipFilename + " file length " + fileLength + "\n");

	zipFileRAF.seek(fileLength - SIZECDR);

	int bytesInBuffer = zipFileRAF.read(buffer); 

	if (bytesInBuffer != SIZECDR) {
		for (int i = 0; i < bytesInBuffer; i++ )
		  VM.sysWrite(i + " " + Integer.toHexString((((int) buffer[i]) & 0xff)) + "\n");
		fail("corrupted zip file -- central dir header ");
	}

	long signature = getLong4(buffer, 0);
	
	if (signature != ZIPCentralDirEnd) {
		for (int i = 0; i < SIZECDR; i++ )
		  VM.sysWrite(i + " " + buffer[i] + " " +
		              Integer.toHexString((((int) buffer[i]) & 0xff))
			      + "\n");
		fail("incorrect signature -- central dir header");
	}

	numberEntries = getInt2(buffer, ECDRTOTAL);
	long directorySize = getLong4(buffer,
				       ZIPDataDescriptorSize);
	long directoryOffset = getLong4(buffer, ECDROFFSET);


	/* Read the "central directory struture".  This structure
	   contains an entry for each file in the zip file and a 
	   file name.  Looping on all the enries in the directory
	   a hash table is constructed
	*/

	// trash and reallocate the buffer
	buffer = null;
	buffer = new byte[(int) directorySize];

	if (traceZip)
          VM.sysWrite("ZipFileJnp.readZipFile: before reading central directory structures" +
                                " dir size " + directorySize +
                                " dir offset " + directoryOffset + "\n");

	zipFileRAF.seek(directoryOffset);

	bytesInBuffer = zipFileRAF.read(buffer);
	if (bytesInBuffer != buffer.length) {
	  for (int i = 0; i < bytesInBuffer; i++ )
	    VM.sysWrite(i + " " + Integer.toHexString((((int) buffer[i]) & 0xff)) + "\n");
	  fail("corrupted zip file");
	}

	if (traceZip) VM.sysWrite("ZipFileJnp.readZipFile: before loop over central directory\n");

	int bo = 0;
	boolean done = false;

	while (! done) {

	  if ((bytesInBuffer - bo) < 4)
	    fail("Central Directory Structure is too short");

	  signature = getLong4(buffer, bo);
	  if (signature == ZIPCentralHeader) {

		/* Verify the local file header: it must have the
		   correct size; code ignores the version needed
		   field.
		*/
		
		if ((bytesInBuffer - bo) < ZIPCentralDirSize) {
		  for (int i = bo; i < bytesInBuffer; i++ )
		    VM.sysWrite(i + " " +
		 Integer.toHexString((((int) buffer[i]) & 0xff)) + "\n");
		  fail("Central Directory Structure is too short");
		}

		if ( traceZip &&
                     ( (buffer[bo+ZIPDataDescriptorFlag] != 0x00) ||
		       (buffer[bo+ZIPDataDescriptorFlag+1] != 0x00) ) )
		  {
		    VM.sysWrite("Flag ");
                    VM.sysWriteHex(((int) buffer[bo+ZIPDataDescriptorFlag]) & 0xff);
                    VM.sysWrite("\n"); 
		  }

		int compressionMethod = getInt2(buffer, bo+CDSCOMPMETHOD);
                if ( traceZip && compressionMethod != 0)
		{
                  VM.sysWrite("Compress Method ");
                  VM.sysWriteHex(compressionMethod);
                  VM.sysWrite("\n");
		}
		int filenameLength = getInt2(buffer,
				bo+CDSFNL);
		int extraFieldLength = getInt2(buffer,
				bo+CDSEXTLN);
		int fileCommentLength = getInt2(buffer,
				bo+CDSFCOMNTLEN);


		if (VM.VerifyAssertions) VM.assert(fileCommentLength == 0);

		int remainingBytes = bytesInBuffer - bo;
		String filename = new String(buffer,
				 	bo+CDSFNAME,
					filenameLength);

		long filesize = getLong4(buffer,
					  bo+CDSUSIZE);

		// use the non public constructor with all the fields
		ZipEntry zipEntry = new ZipEntry(filename,
				 null,
				 null,
				 (long) getInt2(buffer, bo+CDSMODTIME),
				 filesize, filesize,
				 getLong4(buffer, bo+CDSCRC),
				 compressionMethod,
				 (long) getInt2(buffer, bo+CDSMODDATE),
				 getLong4(buffer, bo+CDSOFFSETLFH) );

		// add the zip entry to the cached hashtable
		index.put(filename, zipEntry);

		//if (traceZip) VM.sysWrite("**" + filename + "**\n");

		// get the next entry in the buffer
		long nextHeaderOffset =
			SIZECDS +
			filenameLength +
			extraFieldLength +
			fileCommentLength;
		bo += nextHeaderOffset;

		if (bytesInBuffer == bo) done = true;

	    } else if (signature == ZIPCentralDirEnd) { // end of central directory 
		done = true;		//exit loop
	    } else {
		for (int i = bo; i < bo+ZIPCentralDirSize; i++ )
		  VM.sysWrite(i + " " + Integer.toHexString((((int) buffer[i]) & 0xff)) + "\n");
		fail("Incorrect signature");
	    }
	}  // end while loop


	if (traceZip) VM.sysWrite("ZipFileJnp.readZipFile: leaving\n");

}  // end method readZipFile


public void closeZipImpl() throws IOException {
	zipFileRAF.close();
	zipFileRAF = null;
}  


/**
 * Reads four bytes from the buffer and constructs a long from the value read
 *
 * @param	buffer	a byte buffer with at least 4 bytes
 * @param	position	position in the buffer 
 *
 * @return	long value corresponding to bytes read
 *
 * NOTE: it is assumed the bytes in the buffer are such the least
 * significant bytes has the lower address, i. e., buffer is little
 * endian
 */
private long getLong4(byte buffer[], int position) {
	long i;

	i =  ((int) buffer[position]) & 0xff;
	i |= (( ((int) buffer[position+1]) & 0xff) << 8);
	i |= (( ((int) buffer[position+2]) & 0xff) << 16);
	i |= (( ((int) buffer[position+3]) & 0xff) << 24);

	return i;
}  //  end method getLong4


/**
 * Reads two bytes from the buffer and constructs an int from the value read
 *
 * @param	buffer	a byte buffer with at least 4 bytes
 * @param	position	position in the buffer 
 *
 * @return	int value corresponding to bytes read
 *
 * NOTE: it is assumed the bytes in the buffer are such the least
 * significant bytes has the lower address, i. e., buffer is little
 * endian
 */
private int getInt2(byte buffer[], int position) {
	int i;

	i = (  ((int) buffer[position]) & 0xff) +
	    ( (((int) buffer[position+1]) & 0xff) << 8);

	return i;
} // end method getInt2


/**
  * 
  * 
  * @param	buffer	a byte array where the zip file content will be read
  * @param	entry	the zip entry that should be read
  *
  * @exception	IOExcpetion	io error
  * @exception	IllegalStateException	zip file is closed
  *
todo excetpion
  */
public void inflateEntryImpl(byte outputBuffer[], ZipEntry entry) 
	throws IOException {
	
	if (traceZip) VM.sysWrite("VM_ZipFile.inflateEntryImpl(" + entry + ")\n");

	if (null == zipFileRAF)
		throw new IllegalStateException();

	long offset = entry.getOffset();

	// read the local file header
	byte localFileHeader[] = new byte[SIZELFH];
	zipFileRAF.seek(offset);
	zipFileRAF.read(localFileHeader);

	// extract the extra field length
	int extraFieldLength = getInt2(localFileHeader,
					LFHEXTLN);

	long fileOffset = offset +
			  SIZELFH +
			  entry.getName().length() +
			  extraFieldLength;

	zipFileRAF.seek(fileOffset);
 
	if (entry.getMethod() == DEFLATED)
        {
	  byte[] inputBuffer = new byte[(int) entry.getCompressedSize()];
	  zipFileRAF.read(inputBuffer);
	  VM_InflateZip d = new VM_InflateZip(inputBuffer);
	  d.inflate(outputBuffer);
	}
        else if (entry.getMethod() == STORED)
        {
	  zipFileRAF.read(outputBuffer);
	}
	else
	  throw new IOException("Compression Method is not supported");

	if (traceZip) VM.sysWrite("VM_ZipFile.inflateEntryImpl exiting \n");
}  // end method inflateEntryImpl


private void fail(String message)
   throws ZipException
   {
   VM.sysWrite("ZIP FAILURE ");
   VM.sysWrite(message);
   VM.sysFail("java.util.zip.VM_ZipFile: " + zipFilename + ": " + message);
   throw new ZipException(zipFilename + ": " + message);
   }



private final class ZipHash
{

ZipMapEntry elementData[];

ZipHash() {
  elementData = new ZipMapEntry [1200];
}

public void put ( String key, ZipEntry value )
{
        if (key != null && value != null) {
                int index = (key.hashCode() & 0x7FFFFFFF) % elementData.length;
                ZipMapEntry entry = elementData[index];
                while (entry != null && entry.key != key && !entry.key.equals(key))
                        entry = entry.next;
                if (entry == null) {
                        entry = new ZipMapEntry(key, value);
                        entry.next = elementData[index];
                        elementData[index] = entry;
			return;
                }
                Object result = entry.value;
                entry.value = value;
        } else throw new NullPointerException();                     
}

public Object get ( String key )
{
        int index = (key.hashCode() & 0x7FFFFFFF) % elementData.length;
        ZipMapEntry entry = elementData[index];
        while (entry != null) {
                if (entry.key == key || entry.key.equals(key)) return entry.value;
                entry = entry.next;
        }
        return null;             
}

public Enumeration elements() {
        return new ZipEnum(elementData);
}   

}  // end class ZipHash


private static final class ZipEnum implements Enumeration {
        ZipMapEntry array[];
        int start, end;
        ZipMapEntry entry;

ZipEnum(ZipMapEntry[] entries ) {
        array = entries;
        start = 0;
        end = array.length;
}
public boolean hasMoreElements() {
        if (entry != null) return true;
        while (start < end) {
                if (array[start] == null) start++;
                else return true;
        }
        return false;
}
public Object nextElement() {
        if (hasMoreElements()) {
                if (entry == null) entry = array[start++];
                Object result = entry.value;
                entry = entry.next;
                return result;
        } else throw new NoSuchElementException();
}

}  // end class ZipEnum


private static final class ZipMapEntry {
String 		key;
ZipEntry 	value;
ZipMapEntry	next;

public ZipMapEntry( String key, ZipEntry value )
{
	this.key = key;
	this.value = value;
}
}  // end class ZipMapEntry
}  // end class VM_ZipFile


