/*
 * (C) Copyright IBM Corp. 2001, 2003
 * (C) Ian Rogers/The University of Manchester 2007
 * (C) Ian Rogers 2010
 */
#include <assert.h>
#include <ctype.h>
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>              /* errno */
#include "jburg.h"
#ifdef __GNUC__
#include <unistd.h>             /* getcwd().  We use the non-gnu interface
                                 * (ugh).  */
#endif

static const char *prefix = ""; /* prefix for any Java symbols */
static const char *arch = ""; /* arch name for packages & directories */
static int ntnumber = 0;
static Nonterm start = 0;
static Term terms;
static Nonterm nts;
static Rule rules;
static int nrules;

static struct block {
  struct block *link;
} *memlist;                     /* list of allocated blocks */

#ifdef __GNUC__
static char *stringf(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
#else
static char *stringf(const char *fmt, ...);
#endif

/* Specially formatted output. */
static void print(const char *fmt, ...);
static const char *cwd(void);
static void ckreach(Nonterm p);
static void emitcase(Term p);
static void emitclosure(Nonterm nts_);
static void emitcost(Tree t, const char *v);
static void emitdefs(Nonterm nts_);
static void emitheader(void);
static void emitkids(Rule rules_, int nrules_);
static void emitnts(Rule rules_, int nrules_);
static void emitrecord(const char *pre, Rule r, const char *c, int cost);
static void emitrule(Nonterm nts_);
static void emitlabel(Term terms_);
static void emitstring(Rule rules_);
static void emitstruct(Nonterm nts_);
static void emitterms(Term terms_);
static void emittest(Tree t, const char *v, const char *suffix);
static void readPacked(Nonterm term_);
static void verify_chars(int ret, size_t bufsz);

static Rule sortrulesbykids(Rule rules_, int nrules_);
static char * computekids(Tree t, const char *v, char *bp, size_t *bufszp, int *ip);
static void emitsortedtounsortedmap(Rule rules_);

static int oneterminal = 0;     /* boolean */

#if !defined(__GNUC__)
/* Provide for non GCC compilers */
int snprintf(char* s, size_t n, const char* format, ...) {
  va_list ap;
  int r;
  va_start(ap, format);
  r = vsnprintf(s,n,format,ap);
  va_end(ap);
  return r;
}
#endif

/* Name of program */
static const char *Me;

/* Symbol table */
struct entry {
  union {
    const char *name;
    struct term t;
    struct nonterm nt;
  } sym;
  struct entry *link;
} *table[211];
#define HASHSIZE (sizeof table/sizeof table[0])

int main(int argc, char *argv[])
{
  int c, i;
  Nonterm p;

  Me = argv[0];
  for (i = 1; i < argc; i++) {
    if (strncmp(argv[i], "-p", 2) == 0 && argv[i][2]) {
      prefix = &argv[i][2];
    } else if (strncmp(argv[i], "-p", 2) == 0 && i + 1 < argc) {
      prefix = argv[++i];
    } else if (strncmp(argv[i], "-a", 2) == 0 && argv[i][2]) {
      arch = &argv[i][2];
    } else if (strncmp(argv[i], "-a", 2) == 0 && i + 1 < argc) {
      arch = argv[++i];
    } else if (*argv[i] == '-' && argv[i][1]) {
      yyerror("usage: %s [-T | -p prefix] [-a arch] ... [ input [ output ] ] \n",
              argv[0]);
      exit(1);
    } else if (infp == NULL) {
      if (strcmp(argv[i], "-") == 0) {
        infp = stdin;
      } else if ((infp = fopen(argv[i], "r")) == NULL) {
        yyerror("%s: can't read `%s'\n", argv[0], argv[i]);
        exit(1);
      }
    } else if (outfp == NULL) {
      if (strcmp(argv[i], "-") == 0)
        outfp = stdout;
      else if ((outfp = fopen(argv[i], "w")) == NULL) {
        yyerror("%s: can't write `%s'\n", argv[0], argv[i]);
        exit(1);
      }
    } else {
      yyerror("I don't know what to do with the argument `%s'\n", argv[i]);
    }
  }
  if (infp == NULL)
    infp = stdin;
  if (outfp == NULL)
    outfp = stdout;
  yyparse();
  if (start)
    ckreach(start);
  rules = sortrulesbykids(rules, nrules);
  for (p = nts; p; p = p->link)
    if (!p->reached)
      yyerror("can't reach nonterminal `%s'\n", p->name);
  if (!nts->link)
    oneterminal = 1;
  {
    FILE *saveoutfp = outfp;
    const char *outfn = "BURS_Definitions.java";
    outfp = fopen(outfn,"w");
    if (outfp == NULL) {
      yyerror("%s: can't write `%s/%s'\n", argv[0], cwd(), outfn);
      exit(1);
    }
    emitdefs(nts);
    fclose(outfp);
    outfp = saveoutfp;
  }
  {
    FILE *saveoutfp = outfp;
    const char *outfn = "BURS_State.template";
    outfp = fopen(outfn,"w");
    if (outfp == NULL) {
      yyerror("%s: can't write `%s/%s'\n", argv[0], cwd(), outfn);
      exit(1);
    }
    emitstruct(nts);
    fclose(outfp);
    outfp = saveoutfp;
  }
  emitheader();
  /*emitdefs(nts);*/
  emitsortedtounsortedmap(rules);
  emitnts(rules, nrules);
  emitterms(terms);
  {
    FILE *saveoutfp = outfp;
    outfp = fopen("BURS_Debug.java","w");
    emitstring(rules);
    fclose(outfp);
    outfp = saveoutfp;
  }
  emitrule(nts);
  emitclosure(nts);
  if (start) {
    emitlabel(terms);
  }
  emitkids(rules, nrules);
  if (!feof(infp))
    while ((c = getc(infp)) != EOF)
      putc(c, outfp);
  if (outfp == stdout) {
    fflush(stdout);
  } else {
    fclose(outfp);
  }
  while (memlist) {   /* for purify */
    struct block *q = memlist->link;
    free(memlist);
    memlist = q;
  }
  return errcnt > 0;
}

static Rule sortrulesbykids(Rule rules_, int nrules_)
{
  int i,j;
  Rule r, *rc = alloc((nrules_ + 1 + 1)*sizeof *rc);
  char **str  = alloc((nrules_ + 1 + 1)*sizeof *str);
  int *kids_pattern = alloc((nrules_ + 1 + 1)*sizeof(int));
  //    int next_kids_pattern=0;
  for (i = 0, r = rules_; r; r = r->link) {
    j = 0;
    char buf[1024], *bp = buf;
    size_t bufsz = sizeof buf;
    *computekids(r->pattern, "p", bp, &bufsz, &j) = '\0';
    for (j = 0; str[j] && strcmp(str[j], buf); j++)
      ;
    if (str[j] == NULL) {
      str[j] = strcpy(alloc(strlen(buf) + 1), buf);
      //				next_kids_pattern++;
      //				kids_pattern[i] = next_kids_pattern;
    }
    kids_pattern[i] = j;
    i++;
  }
  /*
   int *kids = alloc((nrules_ + 1 + 1)*sizeof(int));
   int i=0, j;
   Rule r;
   for (r = rules_; r; r = r->link) {
       j = 0;
       char buf[1024], *bp = buf;
       size_t bufsz = sizeof buf;
       *computekids(r->pattern, "p", bp, &bufsz, &j) = '\0';
       kids[i] = j;
       i++;
   }
  */
  for (j=0; j < nrules_; j++) {
    i=0;
    Rule prev = NULL;
    for (r = rules_; r->link; r = r->link) {
      Rule next = r->link;
      //print("Examining:\n r=%R ern=%d kids=%d\n next=%R ern=%d kids=%d\n", r, r->sorted_ern, kids[i], next, next->sorted_ern, kids[i+1]);
      if(kids_pattern[i] > kids_pattern[i+1]) {
        //print("Swapping\n");
        int next_ern = next->sorted_ern;
        next->sorted_ern = r->sorted_ern;
        r->sorted_ern = next_ern;

        Rule next_next = next->link;
        r->link = next_next;
        next->link = r;
        r = next;
        next = r->link;
        if (prev == NULL) {
          rules_ = r;
        }
        else {
          prev->link = r;
        }

        int old_kids = kids_pattern[i];
        kids_pattern[i] = kids_pattern[i+1];
        kids_pattern[i+1] = old_kids;
      }
      prev = r;
      i++;
      //print("After:\n r=%R ern=%d kids=%d\n next=%R ern=%d kids=%d\n", r, r->sorted_ern, kids[i-1], next, next->sorted_ern, kids[i]);
    }
  }
  return rules_;
}

/* print - formatted output */
static void print(const char *fmt, ...)
{
  va_list ap;

  va_start(ap, fmt);
  for ( ; *fmt; fmt++)
    if (*fmt == '%')
      switch (*++fmt) {
      case 'd':
        fprintf(outfp, "%d", va_arg(ap, int));
        break;
      case 's':
        print(va_arg(ap, char *));
        break;
      case 'P':
        fprintf(outfp, "%s_", prefix);
        break;
      case 'T': {
        Tree t = va_arg(ap, Tree);
        print("%S", t->op);
        if (t->left && t->right)
          print("(%T,%T)", t->left, t->right);
        else if (t->left)
          print("(%T)", t->left);
        break;
      }
      case 'R': {
        Rule r = va_arg(ap, Rule);
        print("%S: %T", r->lhs, r->pattern);
        break;
      }
      case 'S': {
        Term t = va_arg(ap, Term);
        fputs(t->name, outfp);
        break;
      }
      case '1':
      case '2':
      case '3':
      case '4':
      case '5': {
        int n = *fmt - '0';
        while (n-- > 0) {
          putc(' ', outfp);
          putc(' ', outfp);
        }
        break;
      }
      default:
        putc(*fmt, outfp);
        break;
      }
    else
      putc(*fmt, outfp);
  va_end(ap);
}

/* Utility to pack the write to a BURS tree node */
static void writePacked(Nonterm term_, int value)
{
  int shift = term_->bit_offset;
  int x= ((1 << term_->number_bits) - 1) << shift;
  char temp[256];
  snprintf(temp, sizeof temp, "p.writePacked(%d, 0x%X, 0x%X);",
           term_->word_number, ~x, (value << shift));
  if (oneterminal)
    print("p.writePacked(0, -1, %d); // p.%S = %d", value, term_, value);
  else
    print("%s // p.%S = %d", temp, term_, value);
}

/* Utility to unpack the read to a BURS tree node */
static void readPacked(Nonterm term_)
{
  char temp[256];
  int shift = term_->bit_offset;
  int x= (1 << term_->number_bits) - 1;
  snprintf(temp, sizeof temp, "readPacked(%d, %d, 0x%X)",term_->word_number,shift,x);
  print("%s",temp);
}

/* alloc - allocate nbytes or issue fatal error */
void * alloc(size_t nbytes)
{
  struct block *p = calloc(1, sizeof *p + nbytes);

  if (p == NULL) {
    yyerror("out of memory\n");
    exit(1);
  }
  p->link = memlist;
  memlist = p;
  return p + 1;
}

/* stringf - format and save a string */
static char * stringf(const char *fmt, ...)
{
  va_list ap;
  char buf[1024];
  int r;
  size_t nwrote;

  va_start(ap, fmt);
  r = vsnprintf(buf, sizeof buf, fmt, ap);
  assert(r > 0);
  nwrote = r;
  assert(nwrote < sizeof buf);
  va_end(ap);
  return strcpy(alloc(nwrote + 1), buf);
}

/* hash - return hash number for str */
static unsigned hash(const char *str)
{
  unsigned h = 0;

  while (*str)
    h = (h<<1) + *str++;
  return h;
}

/* lookup - lookup symbol name */
static void * lookup(const char *name)
{
  struct entry *p = table[hash(name)%HASHSIZE];

  for ( ; p; p = p->link)
    if (strcmp(name, p->sym.name) == 0)
      return &p->sym;
  return 0;
}

/* install - install symbol name */
static void * install(const char *name)
{
  struct entry *p = alloc(sizeof *p);
  int i = (int) hash(name) % HASHSIZE;

  p->sym.name = name;
  p->link = table[i];
  table[i] = p;
  return &p->sym;
}

/* nonterm - create a new terminal id, if necessary */
Nonterm nonterm(const char *id)
{
  Nonterm p = lookup(id), *q = &nts;

  if (p && p->kind == NONTERMINAL)
    return p;
  if (p && p->kind == TERMINAL)
    yyerror("`%s' is a terminal\n", id);
  p = install(id);
  p->kind = NONTERMINAL;
  p->number = ++ntnumber;
  if (p->number == 1)
    start = p;
  while (*q && (*q)->number < p->number)
    q = &(*q)->link;
  assert(*q == 0 || (*q)->number != p->number);
  p->link = *q;
  *q = p;
  return p;
}

/* term - create a new terminal id with external symbol number esn */
Term term(const char *id, int esn)
{
  Term p = lookup(id), *q = &terms;

  if (p)
    yyerror("redefinition of terminal `%s'\n", id);
  else
    p = install(id);
  p->kind = TERMINAL;
  p->esn = esn;
  p->arity = -1;
  while (*q && (*q)->esn < p->esn)
    q = &(*q)->link;
  if (*q && (*q)->esn == p->esn)
    yyerror("duplicate external symbol number `%s=%d'\n",
            p->name, p->esn);
  p->link = *q;
  *q = p;
  return p;
}

/* tree - create & initialize a tree node with the given fields */
Tree tree(const char *id, Tree left, Tree right)
{
  Tree t = alloc(sizeof *t);
  Term p = lookup(id);
  int arity = 0;

  if (left && right)
    arity = 2;
  else if (left)
    arity = 1;
  if (p == NULL && arity > 0) {
    yyerror("undefined terminal `%s'\n", id);
    p = term(id, -1);
  } else if (p == NULL && arity == 0)
    p = (Term)nonterm(id);
  else if (p && p->kind == NONTERMINAL && arity > 0) {
    yyerror("`%s' is a nonterminal\n", id);
    p = term(id, -1);
  }
  if (p->kind == TERMINAL && p->arity == -1)
    p->arity = arity;
  if (p->kind == TERMINAL && arity != p->arity)
    yyerror("inconsistent arity for terminal `%s'\n", id);
  t->op = p;
  t->nterms = p->kind == TERMINAL;
  if ((t->left = left) != NULL)
    t->nterms += left->nterms;
  if ((t->right = right) != NULL)
    t->nterms += right->nterms;
  return t;
}

/* rule - create & initialize a rule with the given fields */
Rule rule(const char *id, Tree pattern, const char *template, const char *code)
{
  Rule r = alloc(sizeof *r), *q;
  Term p = pattern->op;
  char *end;

  r->lhs = nonterm(id);
  r->packed = ++r->lhs->lhscount;
  for (q = &r->lhs->rules; *q; q = &(*q)->decode)
    ;
  *q = r;
  r->pattern = pattern;
  r->ern = ++nrules;
  r->sorted_ern = r->ern; // until we sort the rules
  r->template = template;
  r->code = code;
  r->cost = strtol(code, &end, 10);
  if (*end) {
    r->cost = -1;
    r->code = stringf("%s", code);
  }
  if (p->kind == TERMINAL) {
    for (q = &p->rules; *q; q = &(*q)->next)
      ;
    *q = r;
  } else if (pattern->left == NULL && pattern->right == NULL) {
    Nonterm p_ = pattern->op;
    r->chain = p_->chain;
    p_->chain = r;
    if (r->cost == -1)
      yyerror("illegal nonconstant cost `%s'\n", code);
  }
  for (q = &rules; *q; q = &(*q)->link)
    ;
  r->link = *q;
  *q = r;
  return r;
}

static void emitsortedtounsortedmap(Rule rules_)
{
  Rule r;
  print("%1/** Sorted rule number to unsorted rule number map */\n");
  print("%1private static int[] unsortedErnMap = {\n"
        "%20, /* 0 - no rule */\n");
  for (r = rules_; r->link; r = r->link) {
    print ("%2%d, /* %d - %R */\n", r->ern, r->sorted_ern, r);
  }
  print("%1};\n\n");
}


/* emitrecord - emit code that tests for a winning match of rule r */
static void emitrecord(const char *pre, Rule r, const char *c, int cost)
{
    print("%sif(BURS.DEBUG) trace(p, %d, %s + %d, p.getCost(%d) /* %S */);\n",
          pre, r->sorted_ern, c, cost, r->lhs->number, r->lhs);

  print("%sif (", pre);
  if (cost != 0)
    print("%s + %d < p.getCost(%d) /* %S */) {\n",c,cost,r->lhs->number,r->lhs);
  else
    print("%s < p.getCost(%d) /* %S */) {\n",c,r->lhs->number,r->lhs);
  if (oneterminal) {
    if (cost != 0)
      print("%s%1p.setCost(%d /* %S */, (%s + %d));\n",pre,r->lhs->number,r->lhs,c,cost);
    else
      print("%s%1p.setCost(%d /* %S */, (%s));\n",pre,r->lhs->number,r->lhs,c);
  } else if (cost != 0)
    print("%s%1p.setCost(%d /* %S */, (char)(%s + %d));\n",pre,r->lhs->number,r->lhs,c,cost);
  else
    print("%s%1p.setCost(%d /* %S */, (char)(%s));\n",pre,r->lhs->number,r->lhs,c);
  /*print("%s%1p.%S = %d;\n",pre, r->lhs, r->packed); */
  print("%s%1",pre);
  writePacked(r->lhs,r->packed);
  print("\n");
  if (r->lhs->chain) {
    if (cost != 0)
      print("%s%1closure_%S(p, %s + %d);\n", pre, r->lhs, c, cost);
    else
      print("%s%1closure_%S(p, %s);\n", pre, r->lhs, c);
  }
  print("%s}\n", pre);
}

/* reach - mark all nonterminals in tree t as reachable */
static void reach(Tree t)
{
  Nonterm p = t->op;

  if (p->kind == NONTERMINAL)
    if (!p->reached)
      ckreach(p);
  if (t->left)
    reach(t->left);
  if (t->right)
    reach(t->right);
}

/* ckreach - mark all nonterminals reachable from p */
static void ckreach(Nonterm p)
{
  Rule r;

  p->reached = 1;
  for (r = p->rules; r; r = r->decode)
    reach(r->pattern);
}


/* emitclosure - emit the closure functions */
static void emitclosure(Nonterm nts_)
{
  Nonterm p;
  /*
    for (p = nts_; p; p = p->link)
    if (p->chain)
    print("static void closure_%S (BURS_TreeNode, int);\n", p);
    print("\n");
  */
  for (p = nts_; p; p = p->link)
    if (p->chain) {
      Rule r;
      print("%1/**\n");
      print("%1 * Create closure for %S\n",p);
      print("%1 * @param p the node\n");
      print("%1 * @param c the cost\n");
      print("%1 */\n");
      print("%1@Inline\n"
            "%1private static void closure_%S(AbstractBURS_TreeNode p, int c) {\n",p);
      /*print("%1%PTreeNode p = STATE(a);\n"); */
      for (r = p->chain; r; r = r->chain)
        emitrecord("    ", r, "c", r->cost);
      print("%1}\n\n");
    }
}

/* emitcost - emit cost computation for tree t */
static void emitcost(Tree t, const char *v)
{
  Nonterm p = t->op;

  if (p->kind == TERMINAL) {
    if (t->left)
      emitcost(t->left,  stringf("%s.getChild1()", v));
    if (t->right)
      emitcost(t->right, stringf("%s.getChild2()", v));
  } else
    print("STATE(%s).getCost(%d /* %S */) + ", v, p->number, p);
}

/* emitdefs - emit nonterminal defines and data structures */
static void emitdefs(Nonterm nts_)
{
  Nonterm p;

  print("package org.jikesrvm.compilers.opt.lir2mir.%s; \n", arch);
  print("final class BURS_Definitions  {\n");
  for (p = nts_; p; p = p->link) {
    print("%1/** Unique value for non-terminal %S (%d) */\n", p, p->number);
    print("%1static final byte %S_NT  \t= %d;\n", p, p->number);
  }
  print("\n");
#if 0
  print("static char *ntname[] = {\n%10,\n");
  for (p = nts_; p; p = p->link)
    print("%1\"%S\",\n", p);
  print("%10\n};\n\n");
#endif
  print("}\n");
}

/* emitheader - emit initial definitions */
static void emitheader(void)
{
//    time_t timer = time(NULL);
  /* obsolete, now part of burg.template
     print("// program generated file, do not edit\n\n");
     print("import java.io.*;\n");
     print("\npublic class BURS_STATE implements Operators, BURS_Definitions, BaselineConstants {\n\n");
     print("#include \"burg.template\"\n");
  */
  /*
    print("static void fatal(String a, String b, int c) {\n");
    print("%1throw new OptimizingCompilerException(\"BURS ERROR in '\"+a+\"':\"+b+\" \"+c);\n");
    print("}\n\n");
  */
}

/* computekids - compute paths to kids in tree t */
static char * computekids(Tree t, const char *v, char *bp, size_t *bufszp, int *ip)
{
  Term p = t->op;

  if (p->kind == NONTERMINAL) {
    int ret = snprintf(bp, *bufszp,
                       "%%4if (kidnumber == %d) {\n"
                       "%%5return %s;\n"
                       "%%4}\n", (*ip)++, v);
    size_t nwrote;
    verify_chars(ret, *bufszp);
    nwrote = ret;
    *bufszp -= nwrote;
    bp += nwrote;
  } else if (p->arity > 0) {
    bp = computekids(t->left, stringf("%s.getChild1()", v), bp, bufszp, ip);
    if (p->arity == 2)
      bp = computekids(t->right, stringf("%s.getChild2()", v), bp, bufszp, ip);
  }
  return bp;
}

static char * computeMarkkids(Tree t, const char *v, char *bp, size_t *bufszp, int *ip)
{
  Term p = t->op;

  if (p->kind == NONTERMINAL) {
    int ret;
    size_t nwrote;
    if (oneterminal)
      ret = snprintf(bp, *bufszp, "%%3mark(%s, (byte)1);\n", v);
    else
      ret = snprintf(bp, *bufszp, "%%3mark(%s, ntsrule[%d]);\n", v, *ip);
    verify_chars(ret, *bufszp);
    nwrote = ret;

    ++*ip;

    bp += nwrote;
    *bufszp -= nwrote;
  } else if (p->arity > 0) {
    bp = computeMarkkids(t->left, stringf("%s.getChild1()", v), bp, bufszp, ip);
    if (p->arity == 2)
      bp = computeMarkkids(t->right, stringf("%s.getChild2()", v), bp, bufszp, ip);
  }
  return bp;
}


/* emitkids - emit _kids */
static void emitkids(Rule rules_, int nrules_)
{
  int i;
  Rule r, *rc = alloc((nrules_ + 1 + 1)*sizeof *rc);
  char **str  = alloc((nrules_ + 1 + 1)*sizeof *str);

  for (i = 0, r = rules_; r; r = r->link) {
    int j = 0;
    char buf[1024], *bp = buf;
    size_t bufsz = sizeof buf;
    *computekids(r->pattern, "p", bp, &bufsz, &j) = '\0';
    for (j = 0; str[j] && strcmp(str[j], buf); j++)
      ;
    if (str[j] == NULL)
      str[j] = strcpy(alloc(strlen(buf) + 1), buf);
    r->kids = rc[j];
    rc[j] = r;
  }
  print("%1/**\n"
        "%1 * Give leaf child corresponding to external rule and child number.\n"
        "%1 * e.g. .\n"
        "%1 *\n"
        "%1 * @param p tree node to get child for\n"
        "%1 * @param eruleno external rule number\n"
        "%1 * @param kidnumber the child to return\n"
        "%1 * @return the requested child\n"
        "%1 */\n"
        "%1private static AbstractBURS_TreeNode kids(AbstractBURS_TreeNode p, int eruleno, int kidnumber)  { \n"
        "%2if (BURS.DEBUG) {\n");

  /* not needed as JAVA has null exception
     print("%1if (p==null)\n%2fatal(\"kids\",\"Null tree\", 0);\n");
     print("%1if (kids==null)\n%2fatal(\"kids\", \"Null kids\", 0);\n");
  */
  print("%3switch (eruleno) {\n");
  for (i = 0; (r = rc[i]) != NULL; i++) {
    /* Next line triggers a bogus "Will never be executed" warning under
       g++ versions 3.3 and 3.3.1, with any optimization level. */
    for ( ; r; r = r->kids)
      print("%3case %d: // %R\n", r->sorted_ern, r);
    print("%s%4break;\n", str[i]);
  }
  print("%3}\n");
  print("%3throw new OptimizingCompilerException(\"BURS\",\"Bad rule number \",\n"
        "%4Integer.toString(eruleno));\n");
  print("%2} else {\n"
        "%3return null;\n"
        "%2}\n");
  print("%1}\n\n");

  for (i=0; i < nrules_+1; i++) {
    str[i] = NULL;
    rc[i]  = NULL;
  }

  for (i = 0, r = rules_; r; r = r->link) {
    int j = 0;
    char buf[1024], *bp = buf;
    size_t bufsz = sizeof buf;
    *bp = '\0';
    *computeMarkkids(r->pattern, "p", bp, &bufsz, &j) = '\0';
    for (j = 0; str[j] && strcmp(str[j], buf); j++)
      ;
    if (str[j] == NULL)
      str[j] = strcpy(alloc(strlen(buf) + 1), buf);
    r->kids = rc[j];
    rc[j] = r;
  }
  print("%1/**\n"
        "%1 * @param p node whose kids will be marked\n"
        "%1 * @param eruleno rule number\n"
        "%1 */\n"
        "%1private static void mark_kids(AbstractBURS_TreeNode p, int eruleno)\n"
        "%1{\n");
  if (!oneterminal)
    print("%2byte[] ntsrule = nts[eruleno];\n");
  //print("%2switch (eruleno) {\n");
  for (i = 0; (r = rc[i]) != NULL; i++) {
// for ( ; r; r = r->kids)
//     print("%2case %d: // %R\n", r->sorted_ern, r);
// print("%s%3break;\n", str[i]);
    for ( ; r; r = r->kids) {
      print("%2// %d: %R\n", r->sorted_ern, r);
    }
    r = rc[i];
    if (i == 0) {
      print("%2if (eruleno <= %d) {\n"
            "%3if (VM.VerifyAssertions) VM._assert(eruleno > 0);\n"
            "%s"
            "%2}\n", r->sorted_ern, str[i]);
    }
    else if (rc[i+1] == NULL) {
      print("%2else {\n"
            "%3if (VM.VerifyAssertions) VM._assert(eruleno <= %d);\n"
            "%s"
            "%2}\n", r->sorted_ern, str[i]);
    }
    else {
      print("%2else if (eruleno <= %d) {\n"
            "%s"
            "%2}\n", r->sorted_ern, str[i]);
    }
  }
  //print("%2}\n");
  print("%1}\n\n");
}

/* emitlabel - emit label function */
static void emitlabel(Term terms_)
{
//    int i;
  Term p;
//    Nonterm ntsc;

  if (!nts->link) {
    oneterminal = 1;
  }

  /* Emit master case statement */
  print("%1/**\n");
  print("%1/** Recursively labels the tree/\n");
  print("%1 * @param p node to label\n");
  print("%1 */\n");
  print("%1public static void label(AbstractBURS_TreeNode p) {\n");
  print("%2switch (p.getOpcode()) {\n");
  for (p = terms_; p; p = p->link) {
    if (p->arity != -1) {
      print("%2case %S_opcode:\n",p);
      print("%3label_%S(p);\n",p);
      print("%3break;\n");
    }
  }
  print("%2default:\n");
  print("%3throw new OptimizingCompilerException(\"BURS\",\"terminal not in grammar:\",\n"
        "%4p.toString());\n");
  print("%2}\n"
        "%1}\n\n");

  /* Emit a function for each opcode */
  for (p = terms_; p; p = p->link) {
    emitcase(p);
  }
}

/* emitcase - emit one case in function state */
static void emitcase(Term p)
{
  Rule r;

  if (p->arity == -1) return;
  print("%1/**\n");
  print("%1 * Labels %S tree node\n", p);
  print("%1 * @param p node to label\n");
  print("%1 */\n");
  print("%1private static void label_%S(AbstractBURS_TreeNode p) {\n", p);
  print("%2p.initCost();\n");

  switch (p->arity) {
  case 0:
    break;
  case 1:
    print("%2AbstractBURS_TreeNode lchild;\n");
    print("%2lchild = p.getChild1();\n");
    print("%2label(lchild);\n");
    print("%2int c;\n");
    break;
  case 2:
    print("%2AbstractBURS_TreeNode lchild, rchild;\n");
    print("%2lchild = p.getChild1();\n");
    print("%2rchild = p.getChild2();\n");
    print("%2label(lchild);\n");
    print("%2label(rchild);\n");
    print("%2int c;\n");
    break;
  default:
    assert(0);
  }
  for (r = p->rules; r; r = r->next) {
    const char *indent = "    ";
    int close_parentheses = 0;
    switch (p->arity) {
    case 0:
    case -1:
      print("%2// %R\n", r);
      if (r->cost == -1) {
        print("%2c = %s;\n", r->code);
        emitrecord("    ", r, "c", 0);
      } else
        emitrecord("    ", r, r->code, 0);
      break;
    case 1:
      if (r->pattern->nterms > 1) {
        print("%2if ( // %R\n", r);
        emittest(r->pattern->left, "lchild", "  ");
        print("%2) {\n");
        indent = "      ";
        close_parentheses = 1;
      } else
        print("%2// %R\n", r);

      print("%sc = ", indent);
      emitcost(r->pattern->left, "lchild");
      print("%s;\n", r->code);
      emitrecord(indent, r, "c", 0);
      if (close_parentheses)
        print("%2}\n");
      break;
    case 2:
      if (r->pattern->nterms > 1) {
        print("%2if ( // %R\n", r);
        emittest(r->pattern->left,  "lchild",
                 r->pattern->right->nterms ? " && " : "  ");
        emittest(r->pattern->right, "rchild", "  ");
        print("%2) {\n");
        indent = "      ";
        close_parentheses = 1;
      } else
        print("%2// %R\n", r);
      print("%sc = ", indent);
      emitcost(r->pattern->left,  "lchild");
      emitcost(r->pattern->right, "rchild");
      print("%s;\n", r->code);
      emitrecord(indent, r, "c", 0);
      if (close_parentheses)
        print("%2}\n");
      break;
    default:
      assert(0);
    }

  }
  print("%1}\n\n");
}

/* computents - fill in bp with _nts vector for tree t */
static char * computents(Tree t, char *bp, size_t *bufszp)
{
  if (t) {
    Nonterm p = t->op;
    if (p->kind == NONTERMINAL) {
      int ret = snprintf(bp, *bufszp, "%s_NT, ", p->name);
      size_t nwrote;

      verify_chars(ret, *bufszp);
      nwrote = ret;
      bp += nwrote;
      *bufszp -= nwrote;
    } else {
      bp = computents(t->left,  bp, bufszp),
      bp = computents(t->right, bp, bufszp);
    }
  }
  return bp;
}

/* emitnts - emit _nts ragged array */
static void emitnts(Rule rules_, int nrules_)
{
  Rule r;
  int i, j, *nts_ = alloc((nrules_ + 1)*sizeof *nts_);
  char **str = alloc((nrules_ + 1)*sizeof *str);
  if (oneterminal) {
    printf("\n\n private static final byte[][] nts={};\n\n");
    return;
  }
  // Iterate over rules
  for (i = 0, r = rules_; r; r = r->link) {
    // Generate string of non-terminal leaves
    char buf[1024];
    size_t bufsz = sizeof buf;
    *computents(r->pattern, buf, &bufsz) = '\0';
    // Have we seen this pattern before?
    for (j = 0; str[j] && strcmp(str[j], buf); j++)
      ;
    if (str[j] == NULL) {
      // We haven't seen these leaves before create ragged array
      print("%1/** Ragged array for non-terminal leaves of %s */\n", buf);
      print("%1private static final byte[] nts_%d = { %s };\n", j, buf);
      str[j] = strcpy(alloc(strlen(buf) + 1), buf);
    }
    nts_[i++] = j;
  }
  print("\n%1/** Map non-terminal to non-terminal leaves */\n");
  print("%1private static final byte[][] nts = {\n");
  for (i = j = 0, r = rules_; r; r = r->link) {
    for ( ; j < r->sorted_ern; j++)
      print("%2null, /* %d */\n", j);
    if (nts_[i] > 9) {
      print("%2nts_%d, // %d - %R \n", nts_[i++], j++, r);
    } else {
      print("%2nts_%d,  // %d - %R \n", nts_[i++], j++, r);
    }
  }
  print("%1};\n\n");
}

/* emitrule - emit decoding vectors and _rule */
static void emitrule(Nonterm nts_)
{
  Nonterm p;
  int i, j;
  if (nts_->link) {
    print("%1/**\n"
          "%1 * Decoding table. Translate the target non-terminal and minimal cost covering state encoding\n"
          "%1 * non-terminal into the rule that produces the non-terminal.\n"
          "%1 * The first index is the non-terminal that we wish to produce.\n"
          "%1 * The second index is the state non-terminal associated with covering a tree\n"
          "%1 * with minimal cost and is computed by jburg based on the non-terminal to be produced.\n"
          "%1 * The value in the array is the rule number\n"
          "%1 */\n"
          "%1private static final char[][] decode = {\n"
          "%2null, // [0][0]\n");
    i = 1;
    for (p = nts_; p; p = p->link) {
      Rule r;
      print("%2{ // %S_NT\n"
            "%30, // [%d][0]\n", p, i);
      j = 1;
      for (r = p->rules; r; r = r->decode)
        print("%3%d, // [%d][%d] - %R\n", r->sorted_ern, i, j++, r);
      print("%2},\n");
      i++;
    }
    print("%1};\n\n");
  }
}

/* emitstring - emit arrays of templates, instruction flags, and rules */
static void emitstring(Rule rules_)
{
  Rule r;
//    int k;
//    Term p;

  print("package org.jikesrvm.compilers.opt.lir2mir.%s; \n", arch);
  print("public class BURS_Debug {\n");

#if 0
  print("static final String opname[] = {\n");
  for (k = 0, p = terms; p; p = p->link) {
    for ( ; k < p->esn; k++)
      print("%1null,    \t// %d\n", k);
    print("%1\"%S\",    \t// %d\n", p,k++);
  }
  print("};\n\n");
#endif

#if 0
  print("static String templates[] = {\n");
  print("/* 0 */%10,\n");
  for (r = rules_; r; r = r->link)
    print("/* %d */%1\"%s\",%1/* %R */\n", r->sorted_ern, r->template, r);
  print("};\n");
  print("\nstatic char isinstruction[] = {\n");
  print("/* 0 */%10,\n");
  for (r = rules_; r; r = r->link) {
    int len = strlen(r->template);
    print("/* %d */%1%d,%1/* %s */\n", r->sorted_ern,
          len >= 2 && r->template[len-2] == '\\' && r->template[len-1] == 'n',
          r->template);
  }
  print("};\n");
#endif
  print("%1/** For a given rule number the string version of the rule it corresponds to */\n"
        "%1public static final String[] string = {\n"
        "%2/* 0 */ null,\n");
  for (r = rules_; r; r = r->link)
    print("%2/* %d */\"%R\",\n", r->sorted_ern, r);
  print("%1};\n\n}\n");
}

/* emitstruct - emit the definition of the state structure */
static void emitstruct(Nonterm nts_)
{
  Nonterm ntsc;
  int bit_offset, word_number, i;
  print("// program generated file, do not edit\n\n");
  print("%1// cost for each non-terminal\n");
  for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
    int n = 1, m = ntsc->lhscount;
    while ((m >>= 1) != 0)
      n++;
    if (oneterminal)
      print("%1private int ");
    else
      print("%1private char ");
    print("cost_%S;\n", ntsc);
  }
  /*
    print("%1short cost[] = new short[%d];\n",ntnumber_+1);
  */
  print("\n%1// rule for each non-terminal\n");
  bit_offset = 0;
  word_number = 0;
  print("%1private int word0;\n");
  for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
    int n = 1, m = ntsc->lhscount; //, k;
    while ((m >>= 1) != 0)
      n++;
    ntsc->number_bits = n;
    if ((bit_offset/32) != ((bit_offset+n)/32)) {
      word_number++;
      print("%1private int word%d;\n",word_number);
      bit_offset  = 0;
    }
    ntsc->word_number= word_number;
    ntsc->bit_offset = bit_offset;
    bit_offset += n;
    /*
      if (n > 7)
      print("%1short ");
      else
      print("%1byte ");
    */
    print("%1   // %S; word:%d offset:%d, bits:%d, %d rules);\n",
          ntsc,ntsc->word_number,ntsc->bit_offset,ntsc->number_bits,ntsc->lhscount);
  }
  if (oneterminal)
    print("\n%1public final int getCost(int goalNT) {\n");
  else
    print("\n%1public final char getCost(int goalNT) {\n");
  if (nts_->link) {
    print("%2switch(goalNT) {\n");
    for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
      int n = 1, m = ntsc->lhscount;
      while ((m >>= 1) != 0)
        n++;
      if (ntsc->link)
        print("%2case %S_NT:  ",ntsc);
      else
        print("%2default:     ");
      print("  return cost_%S;\n",ntsc);
    }
    print("%2}\n%1}\n");
  } else { /* only one terminal */
    print("%2return cost_%S;\n%1}\n",nts_);
  }

  if (oneterminal)
    print("\n%1public final void setCost(int goalNT, int cost) {\n");
  else
    print("\n%1public final void setCost(int goalNT, char cost) {\n");
  if (nts_->link) {
    print("%2switch(goalNT) {\n");
    for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
      int n = 1, m = ntsc->lhscount;
      while ((m >>= 1) != 0)
        n++;
      if (ntsc->link)
        print("%2case %S_NT:  ",ntsc);
      else
        print("%2default:     ");
        print("  cost_%S = cost; break;\n",ntsc);
    }
    print("%2}\n%1}\n");
  } else { /* only one terminal */
    print("%2cost_%S = cost;\n%1}\n",nts_);
  }

  word_number = 0;
  print("\n%1@Override");
  print("\n%1public final void initCost() {\n");
  for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
    int n = 1, m = ntsc->lhscount;
    if (ntsc->word_number > word_number) word_number = ntsc->word_number;
    while ((m >>= 1) != 0)
      n++;
    print("%2cost_%S = \n",ntsc);
  }
  print("%2      0x7fff;\n");
  for (i=0; i<= word_number; i++) {
    print("%2word%d = 0;\n",i);
  }
  print("\n%1}\n");


  print("\n%1@Override");
  print("\n%1public final void writePacked(int word, int mask, int shiftedValue) {\n");
  print("\n%2switch(word) {\n");
  for (i=0; i<= word_number; i++) {
    print("%2case %d: word%d = (word%d & mask) | shiftedValue; break;\n",i,i,i);
  }
  print("\n%2default: OptimizingCompilerException.UNREACHABLE();");
  print("\n%2}\n");
  print("\n%1}\n");

  print("\n%1@Override");
  print("\n%1public final int readPacked(int word, int shift, int mask) {\n");
  print("\n%2switch(word) {\n");
  for (i=0; i<= word_number; i++) {
    print("%2case %d: return (word%d >>> shift) & mask;\n",i,i);
  }
  print("\n%2default: OptimizingCompilerException.UNREACHABLE(); return -1;");
  print("\n%2}\n");
  print("\n%1}\n");

  print("\n%1/**\n"
        "%1 * Get the BURS rule number associated with this tree node for a given non-terminal\n"
        "%1 *\n"
        "%1 * @param goalNT the non-terminal we want to know the rule for (e.g. stm_NT)\n"
        "%1 * @return the rule number\n"
        "%1 */\n"
        "%1@Inline\n"
        "%1public final int rule(int goalNT) {\n");
  if (nts_->link) {
    print("%2int stateNT;\n");
    print("%2switch(goalNT) {\n");
    for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
      int n = 1, m = ntsc->lhscount;
      while ((m >>= 1) != 0)
        n++;
      if (ntsc->link)
        print("%2case %S_NT:\n"
              "%3stateNT = ",ntsc);
      else
        print("%2default: // %S_NT\n"
              "%3stateNT = ", ntsc);
      readPacked(ntsc);
      print(";\n"
            "%3break;\n");
    }
    print("%2}\n");
    print("%2return BURS_STATE.decode(goalNT, stateNT);\n");
  } else {
    print("\n%2return  word0;\n",nts_);
  }
  print("%1}\n");
  print("}\n\n");
}

/* emitterms - emit terminal data structures */
static void emitterms(Term terms_)
{
  Term p;
  int k;

  /* no need for this
     for (k = 0, p = terms_; p; p = p->link) {
     for ( ; k < p->esn; k++) ;
     print("static final char _%S = (char)%d;\n", p, k++);
     }
     print("\n");
  */

  print("%1/* private static final byte arity[] = {\n");
  for (k = 0, p = terms_; p; p = p->link) {
    for ( ; k < p->esn; k++)
      print("%20, // %d\n", k);
    if (p->arity < 0) {
      print("%2%d, // %d - %S\n", p->arity, k++, p);
    } else {
      print("%2%d,  // %d - %S\n", p->arity, k++, p);
    }
  }
  print("%1};*/\n\n");
}

/* emittest - emit clause for testing a match */
static void emittest(Tree t, const char *v, const char *suffix)
{
  Term p = t->op;

  if (p->kind == TERMINAL) {
    print("%3%s.getOpcode() == %S_opcode%s\n", v, p,
          t->nterms > 1 ? " && " : suffix);
    if (t->left)
      emittest(t->left, stringf("%s.getChild1()",  v),
               t->right && t->right->nterms ? " && " : suffix);
    if (t->right)
      emittest(t->right, stringf("%s.getChild2()", v), suffix);
  }
}

char * xstrdup(const char *src)
{
  size_t len = strlen(src);
  char *ret = alloc(len + 1);
  strcpy(ret, src);
  return ret;
}

static void verify_chars(int ret, size_t bufsz)
{
  // ret < 0 for compatibility with old glibc versions.
  size_t nwrote;
  assert(ret >= 0);
  nwrote = ret;
  if (nwrote >= bufsz) {
    fprintf(stderr, "Needed %d chars of space; had only %zu\n",
            ret + 1, (unsigned long) nwrote);
    /* skip on */
  }
}

/* We use the non-gnu interface.  Return a pointer to allocated memory.  (In
   this program we're going to throw it away anyway.) */
static const char * cwd(void)
{
  char *buf;
  size_t bufsz = 512;

  for (;;) {
    char *ret;
    buf = alloc(bufsz);
    ret = getcwd(buf, bufsz);
    if (ret)
      return ret;
    if (errno == ERANGE) {
      /* aiee, we can't free in this setup here. */
      bufsz *= 2;
      continue;           /* allocate a double-sized buf! */
    }
    assert(errno);        /* Or the C library is messed up! */
    yyerror("%s: getcwd failed: %s\n", Me, strerror(errno));
    return xstrdup("./");
  }
}
