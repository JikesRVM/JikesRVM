/* HAVE_STRTOLD: Do we have a working strtold() function?  AIX 5.1 
   does not declare strtold() in <stdlib.h> unless 
   sizeof (long double) > sizeof (double).   Note that C '99 requires 
   that function to be present. */
#define HAVE_STRTOLD 1
