// (C) Copyright IBM Corp. 2001, 2003
//
// $Id$

// Read the documentation for how this works:

#define _GNU_SOURCE 1		// so we get vsnprintf() guaranteed on gnu libc.
#define _XOPEN_SOURCE 500	// the most extended version.  GNU explicitly
				// tests for this being equal to 500.  Fun, eh?
#include <stdio.h>
char *Me; // name to appear in error messages

const char short_help_msg[] = ""
"Usage: %s [--help]\n"
"        [ --disable-modification-exit-status ] [--trace]\n"
"        [ -D<name>[ =1 | =0 | =<string-value> ] ]... \n"
"	 [ -- ] <output directory> [ <input file> ]...\n";

      
const char long_help_msg[] = ""
"   Preprocess source files that are new or have changed.\n"
"\n"
"   The timestamp of each input file is compared with that\n"
"   of the corresponding file in the <output directory>.  If the\n"
"   output file doesn't exist, or is older than the input file,\n"
"   then the input file is copied to the <output directory>, \n"
"   with preprocessing.\n"
"\n"
"   Invocation parameters:\n"
"      - zero or more preprocessor directives of the form \"-D<name>=1\", of the\n"
"        equivalent shorthand form \"-D<name>\", of the form \"-D<name>=0\",\n"
"	 and/or of the form \"-D<name>=<string-value>\".\n"
"      - name of directory to receive output files\n"
"      - names of zero or more input files\n"
"      - other flags\n"
"\n"
"   Process exit status means:\n"
"           0 - no files changed\n"
"           1 - some files changed\n"
"       other - trouble\n"
"\n"
"   With --disable-modification-exit-status, the process will\n"
"   exit with status 0 even when some files changed.  Under\n"
"   --disable-modification-exit-status, non-zero exit status\n"
"   always means trouble.  This is handy inside Makefiles.\n"
"\n"
"\n"
"   --trace  The preprocessor prints a '.' for each file that did\n"
"	  not need to be changed and a '+' for each file that needed\n"
"	  preprocessing again.\n"
"\n"
"   --verbose, -v  The preprocessor prints a message for each file\n"
"         examined, and prints a summary at the end \n"
"\n"
"   --help, -h  Show this long help message and exit with status 0.\n"
"\n"
"   -D<name>=0 is a no-op; equivalent to never defining <name>.\n"
"\n"
"   -D<name>=1 and -D<name> are equivalent.\n"
"\n"
"   -D<name>=<any-string-value-but-0-or-1> will define a constant that is\n"
"       usable in a //-#value dirctive.\n"
"\n"
"   The following preprocessor directives are recognized\n"
"   in source files.  They must be the first non-whitespace characters\n"
"   on a line of input.\n"
"\n"
"      //-#if    <name>\n"
"	    It is not an error for <name> to be undefined.  Only checks\n"
"	    whether <name> is defined.\n"
"\n"
"	    \"//-#if\" also supports the constructs '!' (invert the sense of \n"
"	    the next test), '&&', and '||'.  '!' binds more tightly \n"
"	    than '&&' and '||' do.   '&&' and '||' are at the same precedence.\n"
"	    The preprocessor does not support parentheses in //-#if constructs\n"
"	    If you don't mix '&&' and '||' in the same line, you'll be OK.\n"
"\n"
"      //-#elif  <name>\n"
"            Takes the same arguments that //-#if does. \n"
"\n"
"      //-#else  <optional-comment>\n"
"\n"
"      //-#endif <optional-comment>\n"
"\n"
"      //-#value <preprocessor-symbol>\n"
"	    <-preprocessor-symbol> is the name of a constant defined on the\n"
"	    command line with -D; it will be replaced with the defined value.\n"
"\n"
"	    It is an error for <preprocessor-symbol> not to be defined.\n"
"\n"
"	    It is an error for <preprocessor-symbol> to have been defined with\n"
"	    -D<name>=1 or with -D<name>\n"
"\n"
"	   (This is an odd restriction, but is the way the code was written\n"
"           when I found it.  You're free to rewrite it if you want it to act\n"
"           just like the C preprocessor does.)\n"
"\n"
"     There is no equivalent to the C preprocessor's \"#define\" construct;\n"
"     all constants are defined on the command line with \"-D\".\n"
"\n"
"   @author Derek Lieber\n"
"   @date 13 Oct 1999\n"
"   @modified Steven Augart\n"
"   @date June, 2003\n";

#include <errno.h>
#include <string.h>     /* strcmp */
#include <libgen.h>     /* basename */
#include <unistd.h>     /* unlink */
#include <stdlib.h>     /* exit */
#include <sys/stat.h>   /* stat */
#if (defined __linux__) 
#include <limits.h> /* xxx_MAX */
#else
#include <sys/limits.h> /* xxx_MAX */
#endif
#include <assert.h>		// assert()
#include <ctype.h>		// isspace()
#include <stdarg.h>		// va_list, for snprintf()
#include <sys/types.h>
#include <sys/wait.h>

#ifndef DEBUG
#define DEBUG 0
#endif

#define SHOW_PROGRESS(STR) if (trace) { printf(STR); fflush(stdout); }

// Limits.
//
#define MAXLINE       4000 /* longest input line we can handle */
#define MAXNESTING    100  /* maximum #if nesting we can handle */
#define MAXDIRECTIVES 100  /* maximum number of -D<name> directives we can handle */

// Preprocessor directives that have been set via "-D<name>".
//
const char *DirectiveName[MAXDIRECTIVES];   // <name>'s
const char *DirectiveValue[MAXDIRECTIVES];  // values
int   Directives;                    // number thereof

// Source file currently being processed.
//
char package[MAXLINE];       // package name if it exists
const char *SourceName;            // file name
int   SourceLine;            // current line number therein
int   Nesting;               // number of unclosed #if's
int   TrueSeen[MAXNESTING];  // has any block evaluated VV_TRUE at current nesting level?
int   Value[MAXNESTING];     // has current block at current nesting level evaluated VV_TRUE?
int   Unmatched[MAXNESTING]; // line number of currently active #if, #elif, or #else at each level
bool   PassLines;             // if true, pass lines through.   false --> don't

bool exit_with_status_1_if_files_modified = true;

char *PutIntoPackage = NULL;

// Forward references.
//
int  preprocess(const char *srcFile, const char *destinationFile);
void reviseState();
void printState(FILE *fout, char *directive, char *line);
bool  eval(char *p);
int evalReplace(char *cursor);

// Types of tokens returned by scan().
//
enum scan_token {
	TT_TEXT         = 0,
	TT_IF           = 1,
	TT_ELIF         = 2,
	TT_ELSE         = 3,
	TT_ENDIF        = 4,
	TT_REPLACE      = 5,
	TT_UNRECOGNIZED = 6,
};
enum scan_token scan(const char *srcFile, char *line, int *value);


#define UNUSED_DECL_ARG __attribute__((__unused__))
#define UNUSED_DEF_ARG __attribute__((__unused__))
// The __signal__ attribute is only relevant on GCC on the AVR processor.
// We don't (yet) work on the AVR, so this code will probably never be
// #executed.,  
#ifdef __avr__
#define SIGNAL_ATTRIBUTE    __attribute__((__signal__))
#else
#define SIGNAL_ATTRIBUTE
#endif

//static void delete_on_trouble(int dummy_status UNUSED_DECL_ARG, void *dummy_arg UNUSED_DECL_ARG);
static void delete_on_trouble(UNUSED_DECL_ARG int dummy_status , UNUSED_DECL_ARG void *dummy_arg ) 
    SIGNAL_ATTRIBUTE;


// Values of tokens returned by scan() for IF and ELIF.
// For some cases, an index into DirectiveValue is returned.
#define VV_FALSE 0
#define VV_TRUE  1

/* These are like strcmp() and strncmp(), but with a more intuitive
   interface (at least, more intuitive for those of us who did not grow
   up using the FORTRAN Arithmetic IF!) */
inline bool 
strneql(const char *s, const char *t, size_t n) 
{
    return strncmp(s, t, n) == 0;
}


inline bool 
streql(const char *s, const char *t) 
{
    return strcmp(s, t) == 0;
}


/* snprintf(), but with our own built-in error checks. */
void xsnprintf(char *buf, size_t bufsize, const char *format, ...)
    __attribute__((__format__(__printf__, 3, 4)));
// void xsnprintf(char *buf, size_t bufsize, const char *format, ...);

void xsystem(const char *command);

const char *DeleteOnTrouble = NULL; // delete this file on trouble.

/** GNU C++ (at least in 3.3) requires the __unused__ to be present here. */
static void 
delete_on_trouble(UNUSED_DEF_ARG int dummy_status, UNUSED_DEF_ARG void *  dummy_arg )
{
    if (DeleteOnTrouble) {
	// We're already in trouble, so might as well delete, no?
	int err = unlink(DeleteOnTrouble);
	if (err && errno != ENOENT) {
	    fprintf(stderr, "%s: More Trouble -- unable to clean up by deleting  ", Me);
	    perror(DeleteOnTrouble);
	}
	DeleteOnTrouble = NULL;
    }
}


void
shorthelp(FILE *out)
{
    fprintf(out,  short_help_msg, Me);
    fprintf(out, "%s:   Use \"%s --help\" for more information.\n", Me, Me);
}





int
main(int argc, char **argv) 
{
   Me = basename(*argv++); --argc;

   if (on_exit(&delete_on_trouble, (void *) NULL)) {
       perror("Unable to register delete_on_trouble() via on_exit.  This should never happen.\n");
       exit(2);
   }

   // gather arguments
   //
   int trace = 0;
   int verbose = 0;
   for (; **argv == '-'; ++argv, --argc) {
      char *arg   = *argv;

      if (streql(arg, "--")) {
	  ++argv;
	  break;
      }
      
      if (strneql(arg, "--", 2)) {
	  ++arg;       /* treat double-dash and single-dash the same way. */
      }
	  
      if (streql(arg, "-help") || streql(arg, "-h")) {
//	  shorthelp(stdout);
	  printf(short_help_msg, Me);
	  fputs(long_help_msg, stdout);
	  exit(0);
      }
      
      if (streql(arg, "-disable-modification-exit-status")) {
	  exit_with_status_1_if_files_modified = false;
	  continue;
      }
      
      if (streql(arg, "-trace")) {
         trace = 1;
         continue;
      }

      if (streql(arg, "-verbose") || streql(arg, "-v")) {
         trace = 1;
         continue;
      }
	  
      if (strneql(arg, "-D", 2)) {

         char *name = new char[strlen(arg) + 1];
	 char *dst = name;

         const char *src;
	 for (src = arg + 2; *src && *src != '='; )
	   *dst++ = *src++;
         *dst = '\0';
             
         // ignore "-D<name>=0"
         // accept "-D<name>" as "-D<name>=1"
         // accept "-D<name>=<str>" for any other string
	 //
         if (*name == '\0') {
	     fprintf(stderr, "%s: The -D<name>=<value> flag needs at least a <name>! Got argument: %s\n", Me, arg);
	     shorthelp(stderr);
	     exit(2);
	 }
            
	 // -D<name>=0
	 if (streql(src, "=0"))
	     continue;		// ignore it (!)

	 // -D<name> with no =<value> is a special case.  
	 // Treat it as if -D<name>=1.
	 // -D<name>=1 is also a special case.
	 if (*src == '\0' || streql(src, "=1")) {
	     DirectiveName[Directives] = name;
	     DirectiveValue[Directives] = NULL;
	     if (++Directives >= MAXDIRECTIVES) {
		 fprintf(stderr, "\
%s: Too many (%d) -D directives; recompile with a larger value of MAXDIRECTIVES.\n",
			 Me, Directives);
		 exit(3);
	     }
	     continue;
	 } 

	 // Must be -D<name>=<value>
	 assert(*src == '=');	// This replaces a former "if" that seems to me
				// to represent a condition that must always
				// be true.  I'll see if I'm wrong. 
				// --steven augart 
	 DirectiveName[Directives] = name;
	 DirectiveValue[Directives] = src+1;
	 if (++Directives >= MAXDIRECTIVES) {
	     fprintf(stderr, "%s: Too many (%d) -D directives; recompile with a larger value of MAXDIRECTIVES.\n", Me, Directives);
	     exit(3);
	 }
	 continue;
      }
         
      if (strneql(arg, "-package", 8)) {
	PutIntoPackage = strdup( (arg[8]=='=')? (char*)arg+9: (char*)++argv );
	continue;
      }

      fprintf(stderr, "%s: unrecognized option: %s\n", Me, arg);
      shorthelp(stderr);
      exit(2);
   }
   
   if (! *argv) {
       shorthelp(stderr);
       exit(2);
   }



   char *outputDirectory = *argv++; --argc;

   // check timestamps and preprocess new/changed files
   //
   int examined     = 0;
   int preprocessed = 0;
   while (argc != 0) {
      char *source = *argv++; --argc;
      char  destination[PATH_MAX + 1];
      xsnprintf(destination, sizeof destination, "%s/%s", outputDirectory, basename(source));

      struct stat info;
      time_t      sourceTime;
      time_t      destinationTime;

      if (stat(source, &info) < 0) {
	  fprintf(stderr, "%s: a source file (%s) doesn't exist.  Aborting.\n",
		  Me, source);
	  exit(2);
      }
      sourceTime = info.st_mtime;

      if (stat(destination, &info) < 0) {
	  destinationTime = 0;
      } else {
	  destinationTime = info.st_mtime;
      }
      
      if (sourceTime > destinationTime) {

	  if (verbose) {
	      if (destinationTime == 0) {
		  fprintf(stdout, "%s: \"%s\" has never been processed: processing\n", Me, basename(source));
	      } else {
		  fprintf(stdout, "%s: \"%s\" changed since last time: reprocessing\n", Me, basename(source));
	      }
	  }
	  
	  // file is new or has changed
         
	  // // make (previously preprocessed) output file writable
	  // if (destinationTime != 0)
	  //    chmod(destination, S_IREAD | S_IWRITE);
	  
	  if (preprocess(source, DeleteOnTrouble = destination) < 0) {
	      exit(2);             // treat as fatal error
	  }
	  DeleteOnTrouble = NULL;
	  

	  // Move file to the right subdirectory if it is part of a package
	  //
	  if (*package != 0) {
	      // Should do error-checking of package
	      char command[PATH_MAX + 100];
	      char finalDir[PATH_MAX + 1];
	      char finalDstFile[PATH_MAX + 1];
	      // Convert "." in package to "/"
	      char *cur = package;
	      while ((cur = strchr(cur, '.')) != NULL) {
		  *cur = '/';
	      }
	      xsnprintf(finalDir, sizeof finalDir, "%s/%s", outputDirectory, package);
	      xsnprintf(finalDstFile, sizeof finalDstFile, "%s/%s", finalDir, basename(source));
	      xsnprintf(command, sizeof command, "mkdir -p %s", finalDir);
	      //fprintf(stderr, "%s\n", command);
	      xsystem(command);
	      xsnprintf(command, sizeof command, "mv -f %s %s", destination, finalDstFile);
	      //fprintf(stderr, "%s\n", command);
	      xsystem(command);
	  }

	  // // make output file non-writable to discourage editing that will get clobbered by future preprocessing runs
	  // chmod(destination, S_IREAD);
         
	  ++preprocessed;
	  SHOW_PROGRESS("+");
      }
      else
	  SHOW_PROGRESS(".");
      ++examined;
   }
   
   if (verbose)
       fprintf(stdout, "\n%s: %d of %d files required preprocessing\n", Me, preprocessed, examined);
// SHOW_PROGRESS("\n");
   if (exit_with_status_1_if_files_modified && preprocessed)
       exit(1);
   exit(0);
}


// Preprocess a file.
// Takes:    name of file to be read
//           name of file to be written
// Returns: 0 --> success
//          -1 --> trouble
int
preprocess(const char *srcFile, const char *dstFile)
{
    const int Trouble = -1;
    const int OK = 0;
    
    *package = 0;
    FILE *fin = fopen(srcFile, "r");
    if (!fin) {
	fprintf(stderr, "%s: can't find `%s'\n", Me, srcFile);
	return Trouble;
    }

    FILE *fout = fopen(dstFile, "w");
    if (!fout) {
	fprintf(stderr, "%s: can't create `%s':", Me, dstFile);
	perror("");
	return Trouble;
    }

#if DEBUG
    for (int i = 0; i < Directives; ++i)
	fprintf(fout, "[%s=%s]\n", DirectiveName[i], DirectiveValue[i] ? : "1");
#endif

    SourceName = srcFile;
    SourceLine = 0;
    Nesting = 0;
    reviseState();

    if (PutIntoPackage != NULL)
	fprintf(fout, "\npackage %s;\n\n", PutIntoPackage);

    for (;;) {
	char line[MAXLINE];
	size_t linelen;

	if (!fgets(line, sizeof line, fin)) { 
	    if (feof(fin)) {
		if (fclose(fin)) {
		    fputs("Trouble while closing the input file ", stderr);
		    perror(SourceName);
		    return Trouble; // take advantage of the fact that the
				    // caller will abort.
		}; 

		if (ferror(fout)) {
		    fputs("Trouble while writing the file ", stderr);
		    perror(dstFile);
		    return Trouble;
		}

		if (fclose(fout)) {
		    fputs("Trouble while closing the file ", stderr);
		    perror(dstFile);
		    return Trouble;
		}

		if (Nesting) {
		    fprintf(stderr, "%s: No #endif matches line %d of `%s'\n", Me, Unmatched[Nesting], SourceName);
		    return Trouble;
		}
		return OK;
	    } else if (ferror(fin)) {
		fprintf(stderr, "%s: Trouble while reading the file \"%s\": ",
			Me, SourceName);
		perror("");
		// take advantage of the fact that the caller will abort.; no
		// need to close files.
		return Trouble;
	    } else {
		fprintf(stderr, "%s: Internal error: fgets() returned NULL, but"
			" neither feof() nor ferror() are true!\n"
			"%s: Aborting execution.\n", Me, Me);
		exit(13);
	    }
	}
	linelen = strlen(line);
	assert(linelen > 0);	// otherwise we'd have gotten feof().
	if (line[ linelen - 1 ] != '\n') {
	    fprintf(stderr, "%s: %s:%d: Line too long (over %d characters).\n", Me, srcFile, SourceLine, linelen);
	    return Trouble;
	}

	++SourceLine;
	

	int value;
	enum scan_token token_type;
	
	switch (token_type = scan(srcFile, line, &value)) {
	case TT_TEXT:
#if DEBUG
	    printState(fout, "TEXT ", line);
#endif
	    fputs(PassLines ? line : "\n", fout);
	    continue;

	case TT_REPLACE:
#if DEBUG
	    printState(fout, "REPLACE ", line);
#endif
	    fputs(PassLines ? DirectiveValue[value] : "\n", fout);
	    continue;

	case TT_IF:
	    TrueSeen[Nesting] = Value[Nesting] = value;
	    Unmatched[Nesting] = SourceLine;
	    ++Nesting;
	    reviseState();
#if DEBUG
	    printState(fout, "IF   ", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_ELIF:
	    if (Nesting == 0) {
		fprintf(stderr, "%s: #elif with no corresponding #if at line %d of `%s'\n", Me, SourceLine, SourceName);
		return Trouble;
	    }
	    --Nesting;
	    
	    if (TrueSeen[Nesting]) Value[Nesting] = VV_FALSE;
	    else                   TrueSeen[Nesting] = Value[Nesting] = value;
	    Unmatched[Nesting] = SourceLine;
	    ++Nesting;
	    
	    reviseState();
#if DEBUG
	    printState(fout, "ELIF ", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_ELSE:
	    if (Nesting == 0) {
		fprintf(stderr, "%s: #else with no corresponding #if at line %d of `%s'\n", Me, SourceLine, SourceName);
		return Trouble;
	    }
	    --Nesting;
	    if (TrueSeen[Nesting]) 
		Value[Nesting] = VV_FALSE;
	    else
		TrueSeen[Nesting] = Value[Nesting] = VV_TRUE;

	    Unmatched[Nesting] = SourceLine;
	    ++Nesting;
	    reviseState();
#if DEBUG
	    printState(fout, "ELSE ", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_ENDIF:
	    if (Nesting == 0) {
		fprintf(stderr, "%s: #endif with no corresponding #if at line %d of `%s'\n", Me, SourceLine, SourceName);
		return Trouble;
	    }
	    --Nesting;
	    reviseState();
#if DEBUG
	    printState(fout, "ENDIF", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_UNRECOGNIZED:
	    fprintf(stderr, "%s: %s:%d: unrecognized preprocessor directive: %s\n", Me, SourceName, SourceLine, line);
	    return Trouble; 

	default:
	    fprintf(stderr, "%s: %s:%d: Internal error: scan() should never return token type %d\n", Me, SourceName, SourceLine, token_type);
	    return Trouble;

	}
    }
   
    return 0; // not reached
}

// Compute new preprocessor state after scanning a directive.
// Taken:    Value[]
//           Nesting
// Returned: PassLines
//
void reviseState() 
{

   PassLines = true;
   for (int i = 0; i < Nesting; ++i)
      PassLines = PassLines && Value[i];
}

#if DEBUG
// Print preprocessor state (for debugging).
// Taken:    output file
//           current directive
//           current input line
// Returned: nothing
//
void
printState(FILE *fout, char *directive, char *line)
{
   fprintf(fout, "[stack=");

   int i;
   for (i = 0; i < Nesting; ++i) 
      fprintf(fout, "%d ", Value[i]);
   
   for (; i < 5; ++i)
      fprintf(fout, "..");
   
   fprintf(fout, "%s] %s %s", PassLines ? "pass" : "hide", directive, line);
}
#endif

// Scan for a preprocessor directive.  Also handles "package" declarations.
//
// Taken:    line to be scanned
//           place to put value of #if or #elif directive, if found
// Returned: TT_TEXT         --> found no                directive
//           TT_IF           --> found '//-#if <name>'   directive
//           TT_ELIF         --> found `//-#elif <name>' directive
//           TT_ELSE         --> found `//-#else'        directive
//           TT_ENDIF        --> found `//-#endif'       directive
//           TT_REPLACE      --> found `//-#value <name>' directive
//           TT_UNRECOGNIZED --> found unrecognized      directive
// In some cases, value is modified.

scan_token
scan(const char *srcFile, char *line, int *value) 
{

    // skip whitespace
    //
    char *p;
    for (p = line; *p && isspace(*p); ++p)
	;

   // look for "package [c.]*;"
   //
   if (strneql(p, "package ", 8)) {
     if (PutIntoPackage)
       fprintf(stderr, "WARNING: package declaration co-existing with specified package via -package");
     if (*package != 0)
       fprintf(stderr, "WARNING: multiple package declaration in file %s", srcFile);
     p += 8;
     char *tmp = package;
     while (*p != ';') {
       if (*p == '\n') fprintf(stderr, "%s: Ill-formed package declaration: %s", srcFile, line);
       *(tmp++) = *(p++);
     }
     *tmp = 0;
     return TT_TEXT;
   }

   // look for "//-#"
   //
   if (*p++ != '/') return TT_TEXT;
   if (*p++ != '/') return TT_TEXT;
   if (*p++ != '-') return TT_TEXT;
   if (*p++ != '#') return TT_TEXT;

   // look for "value "
   //
   if (strneql(p, "value", 5) && isspace(p[5])) {
       p +=6;
       while (isspace(*p))
	   ++p;
       
       *value = evalReplace(p);
       return TT_REPLACE;
   }

   // look for "if "
   //
   if (strneql(p, "if", 2) && isspace(p[2])) {
       p +=3;
       while (isspace(*p))
	   ++p;
       *value = eval(p);
       return TT_IF;
   }
     
   // look for "elif"
   //
   if (strneql(p, "elif", 4) && isspace(p[4])) {
       p += 5;
       while (isspace(*p))
	   ++p;

       *value = eval(p);
       return TT_ELIF;
   }
     
   // look for "else"
   //
   if (strneql(p, "else", 4)) {
       return TT_ELSE;
   }

   // look for "endif"
   //
   if (strneql(p, "endif", 5)) {
       return TT_ENDIF;
   }
     
   return TT_UNRECOGNIZED;
}

// Evaluate <name> appearing in an `if' or `elif' directive.
// Taken:    `//-#if <conditional>'
//                  ^cursor
//    or:    `//-#elif <conditional>'
//                    ^cursor
//		where conditional is name <&& name> <|| name>
//			(!name toggles the sense)
//  Returns the name of the token.
//    *c points to the cursor.
char *
getToken(char **c)
{
   char *start, *cursor;
   cursor = *c;
   while (*cursor == ' ' || *cursor == '\t')	// skip white space
      ++cursor;
   start = cursor;
   while (*cursor != ' ' && *cursor != '\t' && *cursor != '\n' && *cursor != '\v' && *cursor != '\r' && *cursor != '&' && *cursor != '|')
      ++cursor;
   *c = cursor;
   return start;
}


// Returned:  true  --> <conditional> is true
//            false --> <conditional> is false
//

bool getBoolean(char **c)
{
   char *cursor;
   cursor = *c;
   while (*cursor == ' ' || *cursor == '\t')	// skip white space
      ++cursor;
   *c = cursor;
   return (cursor[0] == '|' && cursor[1] == '|') ||
	  (cursor[0] == '&' && cursor[1] == '&');
}


/* Returns an index into DirectiveValue. */
int 
evalReplace(char *cursor) 
{
  char *name = getToken( &cursor );
  assert(cursor >= name);
  size_t len = cursor - name;
  for (int i = 0; i < Directives; ++i) {
    const char *directiveName = DirectiveName[i];
    if (strlen(directiveName) == len && strneql(name, directiveName, len)) {
      if (DirectiveValue[i] == NULL)
	fprintf(stderr, "%s: %s:%d: //-#value used on non-value (true/false) directive '%s'\n", 
		Me, SourceName, SourceLine, directiveName);
      return i;
    }
  }
  fprintf(stderr, "%s: %s:%d: Unknown value directive '%s'\n", 
	  Me, SourceName, SourceLine, name);
  exit(2);
}


bool
eval(char *cursor) 
{
  int match;
  while ( 1 ) {
      match = 0;
      char *name = getToken( &cursor );
      int toggle = 0;
      if ( name[0] == '!' ) {
	toggle = 1;
	name++;
      }
      assert(cursor >= name);
      size_t len = cursor - name;
      if (len == 0) {
	  fprintf(stderr, "%s: %s:%d missing <name> in preprocessor directive\n", 
		  Me, SourceName, SourceLine);
	  exit(2);
	}
      for (int i = 0; i < Directives; ++i)
	{
	  const char *directiveName = DirectiveName[i];
	  if (strlen(directiveName) == len &&
	      memcmp(directiveName, name, len) == 0) {
	      match = 1;
	      break;
}
	}
      if ( toggle ) match = !match;
      if ( !getBoolean( &cursor ) ) break;
      if ( cursor[0] == '|' ) {
	  if ( match ) 
	      return true;
	  
      } else {
	if ( !match ) 
	    return false;
      }
      cursor += 2;		// skip && or ||
  }
  return match ? true : false;
}




/* snprintf(), but with our own built-in error checks. */
void
xsnprintf(char *buf, size_t bufsize, const char *format, ...)
{
    va_list ap;
    va_start(ap, format);
    int n = vsnprintf(buf, bufsize, format, ap);
    va_end(ap);
    // Handle both the old and new GNU C library return values.
    if (n < 0 || n + 1 > (int) bufsize) {
	fprintf(stderr, "%s: xsnprintf(): Ran out of space in a fixed-size buffer while printing a message starting with: \"%s\"", Me, buf);
	exit(4);
    }
    /* All is well. */
}

void
xsystem(const char *command)
{
    int ret;
    ret = system(command);

    if (ret == 0)
	return;

    if (ret < 0) {
	fputs(Me, stderr);
	perror(": Trouble while trying to use system()");
	exit(5);
    }

    fprintf(stderr, "%s: Trouble while running system(\"%s\").\n", Me, command);
    fputs("     Aborting execution.\n", stderr);

    if (WIFEXITED(ret)) {
	if (WEXITSTATUS(ret) == 127) {
	    fputs("     The shell probably) could not be executed.\n", stderr);
	} else {
	    fprintf(stderr, "     The command exited with status %d\n", 
		    WEXITSTATUS(ret));
	}
    } else if (WIFSIGNALED(ret)) {
	fprintf(stderr, "     The command died because it got hit with signal #%d\n", WTERMSIG(ret));
    } else {
	fprintf(stderr, "    !!! The system() function failed for some reason that we do not understand; return status was %d.  You should never see this message.  Aborting execution.\n", ret);
	exit(13);
    }
    exit(5);
}

    
