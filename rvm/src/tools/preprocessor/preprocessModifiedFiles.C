/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
// Preprocess source files that are new or have changed.
//
// The timestamp of each input file is compared with that of the corresponding
// file in the output directory. If the output file doesn't exist, or is older 
// than the input file, then the input file is copied to the output directory, 
// applying any preprocessor directives specified on the command line.
//
// Invocation parameters:
//    - zero or more preprocessor directives of the form "-D<name>=1"
//    - name of directory to receive output files
//    - names of zero or more input files
//
// Return values: 0 - no files changed
//                1 - some files changed
//        otherwise - error
//
// The following preprocessor directives are recognized in source files:
//    //-#if    <name>
//    //-#elif  <name>
//    //-#else  <optional-comment>
//    //-#endif <optional-comment>
//
// @author Derek Lieber
// @date 13 Oct 1999
//
#include <stdio.h>
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

#define DEBUG 0
#define SHOW_PROGRESS(STR) if (trace) { printf(STR); fflush(stdout); }

char *Me; // name to appear in error messages

// Limits.
//
#define MAXLINE       4000 /* longest input line we can handle */
#define MAXNESTING    100  /* maximum #if nesting we can handle */
#define MAXDIRECTIVES 100  /* maximum number of -D<name> directives we can handle */

// Preprocessor directives that have been set via "-D<name>".
//
char *DirectiveName[MAXDIRECTIVES];   // <name>'s
char *DirectiveValue[MAXDIRECTIVES];  // values
int   Directives;                    // number thereof

// Source file currently being processed.
//
char package[MAXLINE];       // package name if it exists
char *SourceName;            // file name
int   SourceLine;            // current line number therein
int   Nesting;               // number of unclosed #if's
int   TrueSeen[MAXNESTING];  // has any block evaluated VV_TRUE at current nesting level?
int   Value[MAXNESTING];     // has current block at current nesting level evaluated VV_TRUE?
int   Unmatched[MAXNESTING]; // line number of currently active #if, #elif, or #else at each level
int   PassLines;             // VV_TRUE --> pass lines through, VV_FALSE --> don't

char *PutIntoPackage = NULL;

// Forward references.
//
int  preprocess(char *srcFile, char *destinationFile);
void reviseState();
void printState(FILE *fout, char *directive, char *line);
int  scan(char *srcFile, char *line, int *value);
int  eval(char *p);
int evalReplace(char *cursor);

// Types of tokens returned by scan().
//
#define TT_TEXT         0
#define TT_IF           1
#define TT_ELIF         2
#define TT_ELSE         3
#define TT_ENDIF        4
#define TT_REPLACE      5
#define TT_UNRECOGNIZED 6

// Values of tokens returned by scan().
// For some cases, an index into DirectiveValue is returned.
#define VV_FALSE 0
#define VV_TRUE  1

int
main(int argc, char **argv)
   {
   Me = basename(*argv++); --argc;
   if (argc < 2)
      {
      fprintf(stderr, "%s: specify [-trace] [ -D<name>=1 ]... <output directory> [ <input file> ]...\n", Me);
      exit(2);
      }

   // gather arguments
   //
   int trace = 0;
   for (; **argv == '-'; ++argv, --argc)
      {
      char *arg   = *argv;
      if (!strcmp(arg, "-trace")) {
         trace = 1;
         continue;
      }

      if (!strncmp(arg, "-D", 2)) {

         char *name = new char[strlen(arg) + 1];
         char *src, *dst = name;
	 
	 for (src = arg + 2; *src && *src != '=';)
	   *dst++ = *src++;
         *dst = 0;
             
         // ignore "-D<name>=0"
         // accept "-D<name>" as "-D<name>=1"
         // accept "-D<name>=<str>" for any othre string
	 //
         if (*src) {
	   if (strcmp(src, "=0") == 0) 
	     continue;
	   else if (strcmp(src, "=1") == 0) {
	     DirectiveName[Directives] = name;
	     DirectiveValue[Directives] = NULL;
	     Directives++;
	     continue;
	   }
	   else if (*src == '=') {
	     DirectiveName[Directives] = name;
	     DirectiveValue[Directives] = src+1;
	     Directives++;
	     continue;
	   }
	 }

         if (*src && strcmp(src, "=1") != 0) {
	   fprintf(stderr, "%s: expected `=1' instead of `%s' in argument: %s\n", Me, src, arg);
	   exit(2);
	 }
        
         if (!*name) {
	   fprintf(stderr, "%s: <name> expected in argument: %s\n", Me, arg);
	   exit(2);
	 }
            
         continue;
      }
         
      if (strncmp(arg, "-package", 8) == 0) {
	PutIntoPackage = strdup( (arg[8]=='=')? (char*)arg+9: (char*)++argv );
	continue;
      }

      fprintf(stderr, "%s: unrecognized argument: %s\n", Me, arg);
      exit(2);
      }
   
   char *outputDirectory = *argv++; --argc;

   // check timestamps and preprocess new/changed files
   //
   int examined     = 0;
   int preprocessed = 0;
   while (argc != 0)
      {
      char *source = *argv++; --argc;
      char  destination[PATH_MAX + 1];
      sprintf(destination, "%s/%s", outputDirectory, basename(source));

      struct stat info;
      time_t      sourceTime;
      time_t      destinationTime;

      if (stat(source, &info))
         {
         fprintf(stderr, "%s: source file (%s) doesn't exist\n", Me, source);
         exit(2);
         }
      sourceTime = info.st_mtime;

      if (stat(destination, &info))
         info.st_mtime = 0;
      destinationTime = info.st_mtime;
      
      #if DEBUG
      if (sourceTime > destinationTime && destinationTime != 0)
         fprintf(stdout, "%s: \"%s\" changed since last time: reprocessing\n", Me, basename(source));
      #endif

      if (sourceTime > destinationTime)
         { // file is new or has changed
         
      // // make (previously preprocessed) output file writable
      // if (destinationTime != 0)
      //    chmod(destination, S_IREAD | S_IWRITE);
         
      if (!preprocess(source, destination)) {
	// trouble
	unlink(destination); // remove (partially generated) output file
	exit(2);             // treat as fatal error
      }

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
	sprintf(finalDir, "%s/%s", outputDirectory, package);
	sprintf(finalDstFile, "%s/%s", finalDir, basename(source));
	sprintf(command, "mkdir -p %s", finalDir);
	//fprintf(stderr, "%s\n", command);
	system(command);
	sprintf(command, "mv %s %s", destination, finalDstFile);
	//fprintf(stderr, "%s\n", command);
	system(command);
      }

      // // make output file non-writable to discourage editing that will get clobbered by future preprocessing runs
      // chmod(destination, S_IREAD);
         
         preprocessed += 1;
         SHOW_PROGRESS("+");
         }
      else
         SHOW_PROGRESS(".");
      examined += 1;
      }
   
   #if DEBUG
   fprintf(stdout, "\n%s: %d of %d files required preprocessing\n", Me, preprocessed, examined);
   #endif
// SHOW_PROGRESS("\n");
   exit(preprocessed ? 0 : 1);
   }

// Preprocess a file.
// Taken:    name of file to be read
//           name of file to be written
//           place to store real file name in case this is not in unnamed package
// Returned: 1 --> success
//           0 --> failure
int
preprocess(char *srcFile, char *dstFile)
   {
   *package = 0;
   FILE *fin = fopen(srcFile, "r");
   if (!fin) {
     fprintf(stderr, "%s: can't find `%s'\n", Me, srcFile);
     return 0;
   }

   FILE *fout = fopen(dstFile, "w");
   if (!fout) {
     fprintf(stderr, "%s: can't create `%s'\n", Me, dstFile);
     return 0;
   }

   #if DEBUG
   for (int i = 0; i < Directives; ++i)
      fprintf(fout, "[%s=%d]\n", DirectiveName[i], VV_TRUE);
   #endif
      
   SourceName = srcFile;
   SourceLine = 0;
   Nesting = 0;
   reviseState();

   if (PutIntoPackage != NULL)
     fprintf(fout, "\npackage %s;\n\n", PutIntoPackage);

   for (;;)
      {
      char line[MAXLINE];
      if (!fgets(line, sizeof(line), fin))
         { 
         fclose(fin); 
         fclose(fout); 
         if (Nesting)
            {
            fprintf(stderr, "%s: missing #endif corresponding to line %d of `%s'\n", Me, Unmatched[Nesting], SourceName);
            return 0;
            }
         return 1;
         }
      SourceLine += 1;

      int value;
      switch (scan(srcFile, line, &value))
         {
         case TT_TEXT:
         #if DEBUG
         printState(fout, "TEXT ", line);
         #else
         fputs(PassLines ? line : "\n", fout);
         #endif
         continue;

         case TT_REPLACE:
         fputs(PassLines ? DirectiveValue[value] : "\n", fout);
         continue;

         case TT_IF:
         TrueSeen[Nesting] = Value[Nesting] = value;
         Unmatched[Nesting] = SourceLine;
         Nesting += 1;
         reviseState();
         #if DEBUG
         printState(fout, "IF   ", line);
         #else
         fputs("\n", fout);
         #endif
         continue;
         
         case TT_ELIF:
         if (Nesting == 0)
            {
            fprintf(stderr, "%s: #elif with no corresponding #if at line %d of `%s'\n", Me, SourceLine, SourceName);
            return 0;
            }
         Nesting -= 1;
         if (TrueSeen[Nesting]) Value[Nesting] = VV_FALSE;
         else                   TrueSeen[Nesting] = Value[Nesting] = value;
         Unmatched[Nesting] = SourceLine;
         Nesting += 1;
         reviseState();
         #if DEBUG
         printState(fout, "ELIF ", line);
         #else
         fputs("\n", fout);
         #endif
         continue;
         
         case TT_ELSE:
         if (Nesting == 0)
            {
            fprintf(stderr, "%s: #else with no corresponding #if at line %d of `%s'\n", Me, SourceLine, SourceName);
            return 0;
            }
         Nesting -= 1;
         if (TrueSeen[Nesting]) Value[Nesting] = VV_FALSE;
         else                   TrueSeen[Nesting] = Value[Nesting] = VV_TRUE;
         Unmatched[Nesting] = SourceLine;
         Nesting += 1;
         reviseState();
         #if DEBUG
         printState(fout, "ELSE ", line);
         #else
         fputs("\n", fout);
         #endif
         continue;
         
         case TT_ENDIF:
         if (Nesting == 0)
            {
            fprintf(stderr, "%s: #endif with no corresponding #if at line %d of `%s'\n", Me, SourceLine, SourceName);
            return 0;
            }
         Nesting -= 1;
         reviseState();
         #if DEBUG
         printState(fout, "ENDIF", line);
         #else
         fputs("\n", fout);
         #endif
         continue;
         
         case TT_UNRECOGNIZED:
         fprintf(stderr, "%s: unrecognized preprocessor directive at line %d of `%s'\n", Me, SourceLine, SourceName);
         return 0; 
         }
      }
   
   return 0; // not reached
   }

// Compute new preprocessor state after scanning a directive.
// Taken:    Value[]
//           Nesting
// Returned: PassLines
//
void reviseState() {

   PassLines = VV_TRUE;
   for (int i = 0; i < Nesting; ++i)
      PassLines &= Value[i];
}

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

// Scan for a preprocessor directive.
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
//
int scan(char *srcFile, char *line, int *value) {

   // skip whitespace
   //
   char *p;
   for (p = line;;++p)
      {
      if (*p == 0)    return TT_TEXT;
      if (*p == ' ')  continue;
      if (*p == '\t') continue;
      break;
      }

   // look for "package [c.]*;"
   //
   if (strncmp(p, "package ", 8) == 0) {
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
   if (p[0] != '/') return TT_TEXT;
   if (p[1] != '/') return TT_TEXT;
   if (p[2] != '-') return TT_TEXT;
   if (p[3] != '#') return TT_TEXT;

   // look for "value "
   //
   if (p[4] == 'v' && p[5] == 'a' && p[6] == 'l' && p[7] == 'u' && p[8] == 'e' && p[9] == ' ')
      {
      *value = evalReplace(&p[10]);
      return TT_REPLACE;
      }

   // look for "if "
   //
   if (p[4] == 'i' && p[5] == 'f' && p[6] == ' ')
      {
      *value = eval(&p[6]);
      return TT_IF;
      }
     
   // look for "elif"
   //
   if (p[4] == 'e' && p[5] == 'l' && p[6] == 'i' && p[7] == 'f' && p[8] == ' ')
      {
      *value = eval(&p[8]);
      return TT_ELIF;
      }
     
   // look for "else"
   //
   if (p[4] == 'e' && p[5] == 'l' && p[6] == 's' && p[7] == 'e')
      {
      return TT_ELSE;
      }
     
   // look for "endif"
   //
   if (p[4] == 'e' && p[5] == 'n' && p[6] == 'd' && p[7] == 'i' && p[8] == 'f')
      return TT_ENDIF;
     
   return TT_UNRECOGNIZED;
   }

// Evaluate <name> appearing in an `if' or `elif' directive.
// Taken:    `//-#if <conditional>'
//                  ^cursor
//    or:    `//-#elif <conditional>'
//                    ^cursor
//		where conditional is name <&& name> <|| name>
//			(!name toggles the sense)
// Returned:  VV_TRUE  --> <conditional> is true
//            VV_FALSE --> <conditional> is false
//
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

int getBoolean(char **c) {

   char *cursor;
   cursor = *c;
   while (*cursor == ' ' || *cursor == '\t')	// skip white space
      ++cursor;
   *c = cursor;
   return (cursor[0] == '|' && cursor[1] == '|') ||
	  (cursor[0] == '&' && cursor[1] == '&');
}

int evalReplace(char *cursor) {
  char *origCursor = cursor;
  char *name = getToken( &cursor );
  int len = cursor - name;
  for (int i = 0; i < Directives; ++i) {
    char *directiveName = DirectiveName[i];
    if (strlen(directiveName) == len &&
	strncmp(name, directiveName, len) == 0) {
      if (DirectiveValue[i] == NULL)
	fprintf(stderr, "%s: value used on non-value directive '%s' at line %d of `%s'\n", 
		Me, directiveName, SourceLine, SourceName);
      return i;
    }
  }
  fprintf(stderr, "%s: Unknown value directive '%s' at line %d of `%s'\n", 
	  Me, name, SourceLine, SourceName);
  exit(1);
}

int eval(char *cursor) {

  int match;
  while ( 1 ) {
      match = 0;
      char *name = getToken( &cursor );
      int toggle = 0;
      if ( name[0] == '!' ) {
	toggle = 1;
	name++;
      }
      int len = cursor - name;
      if (len == 0)
	{
         fprintf(stderr, "%s: missing <name> in preprocessor directive at line %d of `%s'\n", 
		 Me, SourceLine, SourceName);
         exit(1);
	}
      for (int i = 0; i < Directives; ++i)
	{
	  char *directiveName = DirectiveName[i];
	  if (strlen(directiveName) == len &&
	      memcmp(directiveName, name, len) == 0) {
	    match = 1;
	    break;
	  }
	}
      if ( toggle ) match = !match;
      if ( !getBoolean( &cursor ) ) break;
      if ( cursor[0] == '|' ) {
	if ( match ) return VV_TRUE;
      } else {
	if ( !match ) return VV_FALSE;
      }
      cursor += 2;
  }
  return match ? VV_TRUE : VV_FALSE;
}
