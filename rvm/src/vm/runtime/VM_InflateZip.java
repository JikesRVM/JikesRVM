import java.util.zip.*;

/**
  *
  * This is a conversion of the file 'unzip.cpp' found in the Jikes source.
  * It will be used in conjuction with RVM's ZipFileJpn to perform the
  * inflate algorithm needed to decompress its zip files.  

  * VM_InflateZip.java implements the algorithm discussed within RFC 1951, 
  * DEFLATE Compress Data Format Specification version 1.3, written by 
  * L. Peter Deutsch of Aladdin Enterprise in May 1996, and available from
  * http://info.internet.isi.edut:80/in-notes/rfc/files/rfc1951.txt
  *
  * The implementation is a variation and port to Java of the algorithm
  * used in Jikes.  Reproduced below is the header/copyright notice from
  * the Jikes code. 

// NOTE: Jikes incorporates compression code from the Info-ZIP
// group. There are no extra charges or costs due to the use of
// this code, and the original compression sources are freely
// available from http://www.cdrom/com/pub/infozip/ or
// ftp://ftp.cdrom.com/pub/infozip/ on the Internet.
// The sole use by Jikes of this compression code is contained in the
// files unzip.h and unzip.cpp, which are based on Info-ZIP's inflate.c and
// associated header files.

// You can do whatever you like with this source file, though I would
// prefer that if you modify it and redistribute it that you include
// comments to that effect with your name and the date.  Thank you.
// The abbreviated History list below includes the work of the
// following:
// M. Adler, G. Roelofs, J-l. Failly, J. Bush, C. Ghisler, A. Verheijen,
// P. Kienitz, C. Spieler, S. Maxwell, J. Altman
// Only the first and last entries from the original inflate.c are
// reproduced here.
//

//
// History:
// vers    date          who           what
// ----  ---------  --------------  ------------------------------------
//  a    ~~ Feb 92  M. Adler        used full (large, one-step) lookup table
//  ...
//  c16  20 Apr 97  J. Altman       added memzero(v[]) in huft_build()
//
  *
  *
  *
  *  @author T. Ferguson 
  */


public class VM_InflateZip {
  /* order of the code lengths in the input stream for dynamic tables */
  private static final int BORDER[] =  {
    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
  };

  /* copy lengths for literal codes 257..285 (length alphabet) */
  private static final int CPLENS[] =  {
    3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 
        59, 67, 83, 99, 115, 131, 163, 195, 227, 258, 0, 0
  };

  /* extra bits for literal codes 257..285 */
  private static final int CPLEXT[] =  {
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 
        4, 4, 5, 5, 5, 5, 0, 99, 99,
  };

  /* copy offset for distance codes 0..29 (distance alphabet) */
  private static final int CPDIST[] =  {
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 
        513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 
        24577
  };

  /* extra bits for distance codes */
  private static final int CPDEXT[] =  {
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 
        10, 10, 11, 11, 12, 12, 13, 13
  };
  private static final int MASK_BITS[] =  {
    0x0000, 0x0001, 0x0003, 0x0007, 0x000f, 0x001f, 0x003f, 0x007f, 0x00ff,
    0x01ff, 0x03ff, 0x07ff, 0x0fff, 0x1fff, 0x3fff, 0x7fff, 0xffff
  };

  /*Bits in base tables*/

  private static final int lbits = 9; /*bits in base lit/len lookup table*/
  private static final int dbits = 6; /*bits in base distance lookup table*/

  private static final int BMAX = 16; /*Max bit length of any code*/
  private static final int N_MAX = 288; /*Max number of codes in any set*/

  private static final boolean PKZIP_BUG_WORKAROUND	= true;
  private static final boolean TraceTableCreation	= false;
  private static final boolean TraceInflation		= false;
  private static final boolean TraceDynamic		= false;

  /*Instance variables*/
  private BitBucket bitBucket;
  private int slidePosition;
  private byte[] slideBuffer;
  private int Gtl;
  private int Gtd;

  /**
   * put your documentation comment here
   * @param   byte Inputbuffer[]
   */
  public VM_InflateZip (byte InputBuffer[]) {
    bitBucket = new BitBucket(InputBuffer);
    slidePosition = 0;
 }

  /**
   * put your documentation comment here
   * @param   byte[] outputBuffer
   */
  public void inflate (byte[] outputBuffer) throws ZipException {
    boolean done;
    slideBuffer = outputBuffer;

    /*Decompress until the last block*/
    done = false;
    int count = 0;
    while ((!done) && (count < 11)) {
      int lastBlock;
      int blockType;

      /*read in last block bit & block type*/
      lastBlock = bitBucket.getBits(1);
      if ((lastBlock & 1) == 1)
        done = true;
      blockType = bitBucket.getBits(2);

      /*inflate appropiate block type*/
      if (blockType == 2)
        inflateDynamic(); 
      else if (blockType == 0)
        inflateStored(); 
      else if (blockType == 1)
        inflateFixed(); 
      else 
        throw  new ZipException("invalid zip file");
      count++;
    }
  }


  /*Decompresses inflate type 0, which means no compression.  Simply stores as
   is, eight bits per byte.  The bytes are preceded by a count since there is 
   no longer an EOB
   @return		integer value
   0(successful; any other value is an error)
   */
  public void inflateStored () throws ZipException {
    int blockBytes;
    int blockLength;
    int blockLengthComplement;
    int count = 0;


    /* Byte Boundary */
    blockBytes = bitBucket.currentBits();
    bitBucket.dumpBits(blockBytes);

    /* get LEN & complement */
    blockLength = bitBucket.getBits(16);
    blockLengthComplement = bitBucket.getBits(16);

    /* check length and complement */
    if (blockLength != ~blockLengthComplement) 
      throw new ZipException("inflateStored: lengths are not complements");

    /* read and output the compressed data */
    while (blockLength-- > 0) {
      slideBuffer[slidePosition++] = (byte)bitBucket.getBits(8);
    }
  }


  /*Decompress an inflated type 1, which means fixed Huffman code block.  
   */
  public void inflateFixed () throws ZipException {
    int i;


    /*first time set up table for fixed block*/
    int lengthList[] = new int[288];
    for (i = 0; i < 144; i++)	lengthList[i] = 8;
    for (i = 144; i < 256; i++)	lengthList[i] = 9;
    for (i = 256; i < 280; i++)	lengthList[i] = 7;
    for (i = 280; i < 288; i++)	lengthList[i] = 8;
    Huft lengthBuild = new Huft(lengthList, lengthList.length, 257, CPLENS, CPLEXT, 7);
    if ( lengthBuild.isIncomplete())
      throw new ZipException("Incomplete table--1");

    lengthList = new int[30];
    for (i = 0; i < 30; i++)	lengthList[i] = 5;
    Huft distanceBuild = new Huft(lengthList, 30, 0, CPDIST, CPDEXT, 5);
    inflateCodes(lengthBuild, distanceBuild);
  }

  /*Decompresses the codes in the block.  
   @return		integer value
   0(sucessful; any other is an error
   */
  public int inflateCodes (Huft tl, Huft td) throws ZipException {
    int bl, bd;
    int ml;
    int md;
    

    bl = tl.getMaxLookupBits();
    bd = td.getMaxLookupBits();
    ml = MASK_BITS[bl];
    md = MASK_BITS[bd];

    while (true)
    {
      int b = bitBucket.getBits(bl, ml);
      int t = tl.getAnchor() + b;
      int e = tl.getE(t);

      if (e > 16)
      {
        do
        {
          if (99 == e) throw new ZipException("e==99");
          bitBucket.dumpBits(tl.getB(t));
          e -= 16;

          b = bitBucket.getBits2(e);
          t = tl.getNT(t) + b;
          e = tl.getE(t);
        } while ( e > 16 );
      }
      bitBucket.dumpBits(tl.getB(t));
     
      if (16 == e)	/* then it's a literal */
      {
        slideBuffer[slidePosition++] = (byte)tl.getNT(t);
	if (TraceInflation) System.out.println(" value read "+slideBuffer[slidePosition-1]);
      }
      else
      {
        int length, distance;
	int p = td.getAnchor();

        if (15 == e) break;

        b = bitBucket.getBits(e);
        length = tl.getNT(t) + b;
	if (TraceInflation) System.out.println("length "+length);

        b = bitBucket.getBits(bd, md);
	if (TraceInflation) System.out.print("distance read " + bd + " bits b="+b);
        p += b;
        e = td.getE(p);
	if (TraceInflation) System.out.println(" p "+p+" e "+e);

        if (e > 16)
        {
          do
          {
            if (99 == e) throw new ZipException("e==99");
            bitBucket.dumpBits(td.getB(p));
            e -= 16;
            b = bitBucket.getBits2(e);
	    if (TraceInflation) System.out.print("distance inner loop: read b="+b);
	    p = td.getNT(p) + b;
	    e = td.getE(p);
	    if (TraceInflation) System.out.println("  p="+p+"  e="+e);
          } while (e > 16);
        }
        bitBucket.dumpBits(td.getB(p));
        b = bitBucket.getBits(e);
        distance = td.getNT(p) + b;
        
	if (TraceInflation) System.out.println("distance: "+distance );
        distance = slidePosition - distance;
	if (TraceInflation) System.out.println("distance: "+distance );
        do
        {
          slideBuffer[slidePosition++] = slideBuffer[distance++];
	  if (TraceInflation) System.out.println(" value "+slideBuffer[slidePosition-1]);
	  length--;
        } while (length != 0);

      }  // end 16 != e 
    }  // end while true loop

    return  0;
  }

  /*Decompress an inflate type 2, which means dynamic Huffman code, block
   @rerurn		integer value 
   0(successful; any other is an error
   */
  public int inflateDynamic () throws ZipException {
    int nl, nd, nb;
    int lastLength;
    int lengthTableMask;
    int length2Get;
    int tableLengthLookUp;
    int tableDistanceLookUp;
    int bbl[] = new int[19];

    nl = 257 + bitBucket.getBits(5);	/* number of literal/length codes */
    nd = 1 + bitBucket.getBits(5);	/* number of distance codes */
    nb = 4 + bitBucket.getBits(4);	/* number of bit length codes */

    if (TraceDynamic)
      System.out.println ("inflateDyn: number of literal length codes nl=" + nl +
			"\n     number of distace codes nd=" + nd +
			"\n     number of bit length codes nb=" + nb +
			"\n\n" );

    if (PKZIP_BUG_WORKAROUND)
    {
      if ( nl > 288 || nd > 32 )
        throw new ZipException ("inflateDynamic");
    }
    else
    {
      if ( nl > 286 || nd > 30 )
        throw new ZipException ("inflateDynamic");
    }


    /*read in bit-length-code lengths*/
    for ( int j = 0; j < nb; j++) {
      int t;
      t = BORDER[j];
      bbl[t] = bitBucket.getBits(3);
    }
    for ( int j = nb; j < 19; j++) {
      int t;
      t = BORDER[j];
      bbl[t] = 0;
    }
   
    int ll[] = new int[nl];
    int dis[] = new int[nd];
    if (TraceDynamic) arrayDisplay(bbl, 19, "bit length codes array\n","bbl[");

    /*build decoding table for trees--single level, 7 bit lookup*/
    Huft single = new Huft(bbl, 19, 19, null, null, 7);
    if (single.isIncomplete())
      throw new ZipException("Dynamic: Incomplete table--1");

    /* read and decompress code, length and distance codes lengths */
    tableLengthLookUp = single.getMaxLookupBits();
    lengthTableMask = MASK_BITS[tableLengthLookUp];
    int i = 0;
    int d = 0;
    lastLength = 0;

    length2Get = nl + nd;
    while (i < length2Get) {
      int k;
      int b = bitBucket.getBits(tableLengthLookUp, lengthTableMask);

      int td = single.getAnchor() + b;
      k = single.getB(td);
      bitBucket.dumpBits(k);
      k = single.getNT(td);

      if ( k < 16) {
	if ( i < nl )
          ll[i++] = k;	// length of code in bits (0..15)
        else
        {
	  dis[d++] = k;
	  i++;
        }
        lastLength = k;			// save last length
      }

      else if (k == 16) {		// repeat last length 3 to 6 times
	int j = 3 + bitBucket.getBits(2);

        if (i + j > length2Get) throw new ZipException("Dynamic: Incomplete Table--2");
        while (j-- > 0)
	  if ( i < nl)
	    ll[i++] = lastLength;
	  else
	  {
	    dis[d++] = lastLength;
	    i++;
	  }
      }

      else if (k == 17) {		// 3 to 10 zero length codes
        int j = 3 + bitBucket.getBits(3);

        if (i + j > length2Get) throw new ZipException("Dynamic: Incomplete Table--3");
        while (j-- > 0)
	  if ( i < nl)
	    ll[i++] = 0;
	  else
	  {
	    dis[d++] = 0;
	    i++;
	  }

        lastLength = 0;
      }

      else {
        int j = 11 + bitBucket.getBits(7);
        if ((i + j) > length2Get)
	  throw new ZipException("Dynamic: Incomplete Table--4");
        while (j-- > 0) ll[i++] = 0;
        lastLength = 0;
      } 
    }

    if (TraceDynamic) arrayDisplay(ll, nl, "literal length code lengths array\n","ll[");

    //build the decoding tables for literal/length & distance codes
    Huft literal = new Huft(ll, nl, 257, CPLENS, CPLEXT, lbits);
    if (literal.isIncomplete())
      throw new ZipException("Dynamic: Incomplete table--5");

    if (TraceDynamic) arrayDisplay(dis,"distances code lengths array\n","dis[");

    Huft distance = new Huft( dis, nd, 0, CPDIST, CPDEXT, dbits);
    if (distance.isIncomplete())
      throw new ZipException("Dynamic: Incomplete table--6");

    if (inflateCodes(literal,distance) != 0)
      throw new ZipException("Dynamic: Incomplete Table--7");
     
    return  0;
  }


  private void arrayDisplay(int[] array, String name, String beginning)
  {
    arrayDisplay (array, array.length, name, beginning);
  }


  private void arrayDisplay (int[] array, int al, String name, String beginning)
  {
    for (int ind = 0; ind < al; ind++)
      if (array[ind] != 0) System.out.println(beginning + ind + "]=" + array[ind]);
  }


  /**
   Replaces the NEEDBITS and DUMPBITS used in unzip.cpp.  This method
   gets the n bit(s) requested by call. 
   */
  final class BitBucket {
    private int bitBuffer;
    private int numberBitsNBuffer;
    private byte inputBuff[];
    private int nextInputByteIndex;
    /**
     * Constructor
     * @param     byte[] inputBuffer
     */
    BitBucket (byte[] inputBuffer) {
      bitBuffer = 0;
      numberBitsNBuffer = 0;
      inputBuff = inputBuffer;
      nextInputByteIndex = 0;
    }

    /**
     gets the n bit(s) requested by call, masks them and dumps them.
     @param		bitsWanted (number of bits needed)
     @return		bits 
     */
   final int getBits (int bitsWanted) throws ZipException {
      int bitReturn;

      bitReturn = getBits(bitsWanted, MASK_BITS[bitsWanted]);
      dumpBits(bitsWanted);

      return bitReturn; 
    }


    /**
     gets the n bit(s) requested by call & DO NOT dumpbits.
     and the mask is precomputed
     The mask param is the mask that is used is equal to the
     mask value referred to in the C++ program when return
     bit is used for manipulation
     @param             bitsWanted (number of bits needed)
     @param             mask (mask used to help determine return bit)
     @return            bits
     */
   final int getBits (int bitsWanted, int mask) throws ZipException {
      int bitReturn;
      byte inputByte;

      while (numberBitsNBuffer < bitsWanted) {
        if (nextInputByteIndex < inputBuff.length) {
          inputByte = inputBuff[nextInputByteIndex];
          nextInputByteIndex++;
        }
        else
	  throw new ZipException ("unexpected end of input stream");
        bitBuffer |= (((int)inputByte) & 0xff) << numberBitsNBuffer;
        numberBitsNBuffer += 8;
      }

      bitReturn = bitBuffer;
      return  (bitReturn & mask);
      
    }


    /*
     * mask but 
     * no dump
     */
    final int getBits2(int bitsWanted) throws ZipException
    {
      return getBits(bitsWanted, MASK_BITS[bitsWanted]); 
    }


    /**
     * @return bits currently in the bucket
     */
    final int currentBits () {
      return  bitBuffer;
    }

    /**
     * dumps n bit(s) from the bucket
     * @param bitsToDump
     */
    final void dumpBits (int bitsToDump) {

    bitBuffer >>>= bitsToDump;
    numberBitsNBuffer -= bitsToDump;
    }
  }             // end class BitBucket


  /*Builds a decoding table given a code length distribution for the 288 literal
    values*/
  class Huft {
    boolean incompleteTable;
    int maxLookupBits;

    int h_e[];		// number of extra bits or operation
    int h_b[];		// number of bits in this code or subcode
    int h_nt[];		// literal, length base, or distance base
    int h_size;
    int h_watermark;


    /**
     * put your documentation comment here
     * @param     int b[]
     * @param     int n
     * @param     int s
     * @param     int m
     */
    Huft (int b[], int n, int s, int d[], int e[], int m) throws ZipException
    {
      int a;
      int el;
      int g;
      int j;
      int k;
      int p;
      int xp;
      int y;
      int u[] = new int[BMAX];
      
      maxLookupBits = m;

      el = n > 256 ? b[256] : BMAX;

      /* count the number of codes for each code length assuming that
         all entries in the length input table, b, are less or equal
         to BMAX */
      int c[] = new int[BMAX + 1];
      for (int ii = 0; ii < c.length; ii++) c[ii] = 0;
      for (int ii = 0; ii < n; ii++) c[b[ii]]++;

      /* now each c arrays entry at position i counts the number of i's
         in the length input table, b.  Having the count for each code length
	 enables us to determine the codes themselves */

      if (TraceTableCreation)
	arrayDisplay(c,"C array \n","c[");

      /*null input -- all zero length codes*/
      if (c[0] == n) {
        maxLookupBits = 0;
        return;
      }

      /* given the way in which c, the number of codes for each code length, was
         constructed, there exit an i between 1 and BMAX, such that c[i] is not
         0.  Find the smallest and largest such i, and call them k and g,
         respectively.  These are the minimun and maximun code length.
      */

      for (k = 1; k <= BMAX && c[k] == 0; k++);
      if (maxLookupBits < k) maxLookupBits = k;

      for (g = BMAX; g > 0 && c[g] == 0; g--);
      if (maxLookupBits > g) maxLookupBits = g;

      /*Adjust last length count*/
      {
      int jj = k;
      for (y = 1 << jj; jj < g; jj++, y <<= 1)
      {
        y -= c[jj];
        if (y < 0) throw new ZipException("uhm");
      }

      y -= c[g];
      if (y < 0) throw  new ZipException("y too small for c[g]");
      c[g] += y;
      }

      if (TraceTableCreation)
	arrayDisplay(c,"C array \n","c[");

      if (TraceTableCreation)
	System.out.println("\nmaxLookupBits=" + maxLookupBits + "  k=" + k + " g=" + g );

      /*generate starting offsets into the value table for each length*/
      int x[] = new int[BMAX + 1];
      for (int ii = 0; ii < x.length; ii++) x[ii] = 0;
      p = 1;
      xp = 2;
      j = 0;
      for (int ii = g-1; ii > 0; --ii) {
        j += c[p++];
        x[xp++] = j;
      }

      if (TraceTableCreation)
	arrayDisplay(x,"X array\n","x[");

      /*Make a table of values in order of bit lengths*/
      int v[] = new int[N_MAX];
      for (int ii = 0; ii < v.length; ii++) v[ii] = 0;
      for (int i = 0; i < n; i++) {
        int jj;
        jj = b[i];
        if (jj != 0) {
          int ii = x[jj]++;
          v[ii] = i;
        }
      }
      n = x[g];

      if (TraceTableCreation)
	arrayDisplay(v,"V array\n","v[");

      /* Generate the Huffman codes and for each, make the table entries */
      x[0] = 0;		/* first Huffman code is zero */
      int i = 0;

      p = 0;		/* grab values in bit order */
      int h = 0;	/* no tables yet--level 0*/ 
      int w = 0;	/* no bits decoded yet */
      int z = 0;
      int t = -1;
      int q = -1;
      int lx[] = new int[BMAX + 1];
      lx[0] = 0;

      h_size = 1024;
      h_e = new int[h_size];
      h_b = new int[h_size];
      h_nt = new int[h_size];
      h_watermark = 0;

      for ( int ii = 0; ii < h_nt.length; ii++ ) h_nt[ii] = -1;

      /* go through the bit lengths (k already is bits in shortest code) */
      for (; k <= g; k++) {
        a = c[k];

        if (TraceTableCreation)
	  System.out.println("top of loop on k, k=" + k + "  a=" + a );

        while (a > 0) {
	  a--;

          int f;

          if (TraceTableCreation)
		System.out.println("top of loop on a, a=" + a + "  h=" + h );

          /* here i is the Huffman code of length k bits for value v[h] */

          /* make tables up to required level */
          while (k > w + lx[h]) {

            if (TraceTableCreation)
		System.out.println("top of k loop, k="+k+"  h=" + h );

            w += lx[h++];

            /* add bits already decoded */

            /* compute minimum size table less than or equal to *m bits */
            z = g - w;
            if (z > maxLookupBits) z = maxLookupBits; /* upper limit */

            j = k - w;
            f = 1 << j;
            if (f > a + 1) 
            /* try a k-w bit table */
            {
              /* too few codes for k-w bit table */
              f -= a + 1;

              /* deduct codes from patterns left */
              xp = k;
              while (++j < z) 
              /* try smaller tables up to z bits */
              {
                f <<= 1;
                if (f <= c[++xp])
                  break;

                /* enough codes to use up j bits */
                f -= c[xp];
                /* else deduct codes from patterns */

              }
            }
            if (w + j > el && w < el)
              j = el - w;
            /* make EOB code end at table */

            z = 1 << j;		/* table entries for j-bit table */
            lx[h] = j;		/* set table size in stack */

            
	    // allocate z entries
            if (TraceTableCreation)
	      System.out.println("Allocating " + z + " entries\n");
            q = h_watermark;
            h_watermark += z;
            if ( h_watermark >= h_size )
            {
 	      int var1[] = h_e;
 	      int var2[] = h_b;
 	      int var3[] = h_nt;
	      int temp = h_size;

              h_size *= 2;

              h_e = new int[h_size];
              h_b = new int[h_size];
              h_nt = new int[h_size];

              for (int replace = 0; replace < temp; replace++) {
                 h_e[replace] = var1[replace];
                 h_b[replace] = var2[replace];
                 h_nt[replace] = var3[replace];
              }
            }

            u[h-1] = q;
	
	    // connect to last table if there is one
	    if ( h > 1 )
            {
	      int jj, bits;
	      x[h-1] = i;	// save pattern for backing up
	      bits = 16+j;	// bits in this table
	      j = ( i & (( 1<<w) - 1)) >> ( w - lx[h-1]);
	      jj = u[h-2] + j;
	      h_b[jj] = lx[h-1]; // bits to dump before this table
	      h_e[jj] = bits;	// bits in this table
	      h_nt[jj] = q;	// index of this table
	      if (TraceTableCreation)
		System.out.println("e[" + jj + "] = " + h_e[jj] +
			 "  b[" + jj + "] = " + h_b[jj] +
			 "  t[" + jj + "] = " + h_nt[jj] );
            }
          }  // loop on k>w+lx[h]


	  /* set up table entry in r */
          int r_e;
          int r_b;
          int r_nt;
	  r_nt = -1;
	  r_b = k - w;
	  if (p >= n)
	    r_e = 99;               /* out of values--invalid code */
	  else if (v[p] < s)
  	  {
	    r_e = (v[p] < 256 ? 16 : 15);  /* 256 is end-of-block code */
	    r_nt = v[p++];                /* simple code is just the value */
	  }
	  else
	  {
	    r_e = e[v[p] - s];   /* non-simple--look up in lists */
	    r_nt = d[v[p++] - s];
	  }

          /* fill code-like entries with r */
          f = 1 << (k - w);
          for (j = i >> w; j < z; j += f)
          {
	    int jj = q + j;
	    if (TraceTableCreation)
		System.out.println("e[" + jj + "] = " + r_e +
			 "  b[" + jj + "] = " + r_b + "  n[" + jj + "] = " + r_nt);
	    h_e[jj] = r_e;
	    h_b[jj] = r_b;
	    h_nt[jj] = r_nt;
          }

          /* backwards increment the k-bit code i */

          for (j = 1 << (k - 1); (j & i) != 0; j >>= 1)
            i ^= j;
          i ^= j;

          /* backup over finished tables */
          while ((i & ((1 << w) - 1)) != x[h-1])
          {
            h--;
            w -= lx[h];
          }

        }
      }

      /* return actual size of base table */
      maxLookupBits = lx[1];

      /* Return true (1) if we were given an incomplete table */
      if (y != 0 && g != 1){
        incompleteTable = true;
      }else
        incompleteTable = false;
    }  // end contructor


    boolean isIncomplete() { return incompleteTable; }
    int getMaxLookupBits() { return maxLookupBits; }
    int getAnchor() 	   { return 0; }
    int getE(int ind)	   { return h_e[ind]; }
    int getB(int ind)	   { return h_b[ind]; }
    int getNT(int ind)	   { return h_nt[ind]; }
    

  }             // HuftBuild class 
}               //VM_InflateZip class



