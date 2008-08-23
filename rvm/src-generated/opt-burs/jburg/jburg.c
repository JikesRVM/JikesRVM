/*
 * (C) Copyright IBM Corp. 2001, 2003
 */
#include <assert.h>
#include <ctype.h>
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
// #include <time.h>
#include <errno.h>              /* errno */
#include "jburg.h"
#ifdef __GNUC__
#include <unistd.h>             /* getcwd().  We use the non-gnu interface
                                 * (ugh).  */
#endif

static const char *prefix = ""; /* prefix for any Java symbols */
static const char *arch = ""; /* arch name for packages & directories */
static int Tflag = 0;
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
static void emitclosure(Nonterm nts_);
static void emitcost(Tree t, const char *v);
static void emitdefs(Nonterm nts_);
static void emitheader(void);
static void emitkids(Rule rules_, int nrules_);
static void emitnts(Rule rules_, int nrules_);
static void emitrecalc(const char *pre, Term root, Term kid);
static void emitrecord(const char *pre, Rule r, const char *c, int cost);
static void emitrule(Nonterm nts_);
static void emitlabel(Term terms_);
static void emitstring(Rule rules_);
static void emitstruct(Nonterm nts_);
static void emitterms(Term terms_);
static void emittest(Tree t, const char *v, const char *suffix);
static void readPacked(Nonterm term_);
static void verify_chars(int ret, size_t bufsz);


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

static
void
writePacked(Nonterm term_, int value)
{
    int shift = term_->bit_offset;
    int x= ((1 << term_->number_bits) - 1) << shift;
    char temp[256];
    snprintf(temp, sizeof temp, "p.word%d = (p.word%d & 0x%X) | 0x%X;",
            term_->word_number, term_->word_number, ~x, (value << shift));
    if (oneterminal)
        print("p.word0 = %d; // p.%S = %d", value, term_, value);
    else
        print("%s // p.%S = %d", temp, term_, value);
}

static void
readPacked(Nonterm term_)
{
    char temp[256];
    int shift = term_->bit_offset;
    int x= (1 << term_->number_bits) - 1;
    if (shift != 0)
        snprintf(temp, sizeof temp, "((word%d >>> %d) & 0x%X)",term_->word_number,shift,x);
    else
        snprintf(temp, sizeof temp, "(word%d & 0x%X)",term_->word_number,x);
    print("%s",temp);
}

static const char *Me;

int
main(int argc, char *argv[])
{
    int c, i;
    Nonterm p;

    Me = argv[0];
    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-T") == 0) {
            Tflag = 1;
        } else if (strncmp(argv[i], "-p", 2) == 0 && argv[i][2]) {
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
    while (memlist) {   /* for purify */
        struct block *q = memlist->link;
        free(memlist);
        memlist = q;
    }
    return errcnt > 0;
}

/* alloc - allocate nbytes or issue fatal error */
void *
alloc(size_t nbytes)
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
static char *
stringf(const char *fmt, ...)
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

struct entry {
    union {
        const char *name;
        struct term t;
        struct nonterm nt;
    } sym;
    struct entry *link;
} *table[211];
#define HASHSIZE (sizeof table/sizeof table[0])

/* hash - return hash number for str */
static unsigned
hash(const char *str)
{
    unsigned h = 0;

    while (*str)
        h = (h<<1) + *str++;
    return h;
}

/* lookup - lookup symbol name */
static void *
lookup(const char *name)
{
    struct entry *p = table[hash(name)%HASHSIZE];

    for ( ; p; p = p->link)
        if (strcmp(name, p->sym.name) == 0)
            return &p->sym;
    return 0;
}

/* install - install symbol name */
static void *
install(const char *name)
{
    struct entry *p = alloc(sizeof *p);
    int i = (int) hash(name) % HASHSIZE;

    p->sym.name = name;
    p->link = table[i];
    table[i] = p;
    return &p->sym;
}

/* nonterm - create a new terminal id, if necessary */
Nonterm
nonterm(const char *id)
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
Term
term(const char *id, int esn)
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
Tree
tree(const char *id, Tree left, Tree right)
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
Rule
rule(const char *id, Tree pattern, const char *template, const char *code)
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


/* print - formatted output */
static void
print(const char *fmt, ...)
{
    va_list ap;

    va_start(ap, fmt);
    for ( ; *fmt; fmt++)
        if (*fmt == '%')
            switch (*++fmt) {
            case 'd': fprintf(outfp, "%d", va_arg(ap, int)); break;
            case 's': fputs(va_arg(ap, char *), outfp); break;
            case 'P': fprintf(outfp, "%s_", prefix); break;
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
                fputs(t->name, outfp); break;
            }
            case '1': case '2': case '3': case '4': case '5': {
                int n = *fmt - '0';
                while (n-- > 0)
                    putc('\t', outfp);
                break;
            }
            default: putc(*fmt, outfp); break;
            }
        else
            putc(*fmt, outfp);
    va_end(ap);
}

/* reach - mark all nonterminals in tree t as reachable */
static void
reach(Tree t)
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
static void
ckreach(Nonterm p)
{
    Rule r;

    p->reached = 1;
    for (r = p->rules; r; r = r->decode)
        reach(r->pattern);
}


/* emitcase - emit one case in function state */
static void
emitcase(Term p)
{
    Rule r;

    if (p->arity == -1) return;
    print("private void label_%S(BURS_TreeNode p) {\n", p);
    print("%1p.word0 = 0;\n");
    print("%1p.initCost();\n");

    switch (p->arity) {
    case 0:
        break;
    case 1:
        print("%1BURS_TreeNode lchild;\n");
        print("%1lchild = p.child1;\n");
        print("%1label(lchild);\n");
        print("%1int c;\n");
        break;
    case 2:
        print("%1BURS_TreeNode lchild, rchild;\n");
        print("%1lchild = p.child1;\n");
        print("%1rchild = p.child2;\n");
        print("%1label(lchild);\n");
        print("%1label(rchild);\n");
        print("%1int c;\n");
        break;
    default: assert(0);
    }
    for (r = p->rules; r; r = r->next) {
        const char *indent = "\t\0";
        switch (p->arity) {
        case 0: case -1:
            print("%1// %R\n", r);
            if (r->cost == -1) {
                print("%1c = %s;\n", r->code);
                emitrecord("\t", r, "c", 0);
            } else
                emitrecord("\t", r, r->code, 0);
            break;
        case 1:
            if (r->pattern->nterms > 1) {
                print("%1if (%1// %R\n", r);
                emittest(r->pattern->left, "lchild", " ");
                print("%1) {\n");
                indent = "\t\t";
            } else
                print("%1// %R\n", r);
            if (r->pattern->nterms == 2
                && r->pattern->left
                &&  r->pattern->right == NULL)
            {
                emitrecalc(indent, r->pattern->op, r->pattern->left->op);
            }

            print("%sc = ", indent);
            emitcost(r->pattern->left, "lchild");
            /* The next line triggers a bogus "will never be executed" warning
               in GCC 3.3 and GCC 3.3.1, with optimization level 1 or higher
               set. */
            print("%s;\n", r->code);
            emitrecord(indent, r, "c", 0);
            if (indent[1])
                print("%1}\n");
            break;
        case 2:
            if (r->pattern->nterms > 1) {
                print("%1if (%1// %R\n", r);
                emittest(r->pattern->left,  "lchild",
                         r->pattern->right->nterms ? " && " : " ");
                emittest(r->pattern->right, "rchild", " ");
                print("%1) {\n");
                indent = "\t\t";
            } else
                print("%1// %R\n", r);
            print("%sc = ", indent);
            emitcost(r->pattern->left,  "lchild");
            emitcost(r->pattern->right, "rchild");
            print("%s;\n", r->code);
            emitrecord(indent, r, "c", 0);
            if (indent[1])
                print("%1}\n");
            break;
        default: assert(0);
        }
    }
    print("}\n\n");
}

/* emitclosure - emit the closure functions */
static void
emitclosure(Nonterm nts_)
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
            print("static void closure_%S(BURS_TreeNode p, int c) {\n",p);
            /*print("%1%PTreeNode p = STATE(a);\n"); */
            for (r = p->chain; r; r = r->chain)
                emitrecord("\t", r, "c", r->cost);
            print("}\n\n");
        }
}

/* emitcost - emit cost computation for tree t */
static void
emitcost(Tree t, const char *v)
{
    Nonterm p = t->op;

    if (p->kind == TERMINAL) {
        if (t->left)
            emitcost(t->left,  stringf("%s.child1",  v));
        if (t->right)
            emitcost(t->right, stringf("%s.child2", v));
    } else
        print("STATE(%s).cost_%S + ", v, p);
}

/* emitdefs - emit nonterminal defines and data structures */
static void
emitdefs(Nonterm nts_)
{
    Nonterm p;

    print("package org.jikesrvm.compilers.opt.lir2mir.%s; \n", arch);
    print("interface BURS_Definitions  {\n");
    for (p = nts_; p; p = p->link)
        print("%1byte %S_NT  \t= %d;\n", p, p->number);
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
static void
emitheader(void)
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
static char *
computekids(Tree t, const char *v, char *bp, size_t *bufszp, int *ip)
{
    Term p = t->op;

    if (p->kind == NONTERMINAL) {
        int ret = snprintf(bp, *bufszp, "\t\tif (kidnumber == %d)  return %s;\n", (*ip)++, v);
        size_t nwrote;
        verify_chars(ret, *bufszp);
        nwrote = ret;
        *bufszp -= nwrote;
        bp += nwrote;
    } else if (p->arity > 0) {
        bp = computekids(t->left, stringf("%s.child1", v), bp, bufszp, ip);
        if (p->arity == 2)
            bp = computekids(t->right, stringf("%s.child2", v), bp, bufszp, ip);
    }
    return bp;
}

static char *
computeMarkkids(Tree t, const char *v, char *bp, size_t *bufszp, int *ip)
{
    Term p = t->op;

    if (p->kind == NONTERMINAL) {
        int ret;
        size_t nwrote;
        if (oneterminal)
            ret = snprintf(bp, *bufszp, "\t\tmark(%s, (byte)1);\n", v);
        else
            ret = snprintf(bp, *bufszp, "\t\tmark(%s, ntsrule[%d]);\n", v, *ip);
        verify_chars(ret, *bufszp);
        nwrote = ret;

        ++*ip;

        bp += nwrote;
        *bufszp -= nwrote;
    } else if (p->arity > 0) {
        bp = computeMarkkids(t->left, stringf("%s.child1", v), bp, bufszp, ip);
        if (p->arity == 2)
            bp = computeMarkkids(t->right, stringf("%s.child2", v), bp, bufszp, ip);
    }
    return bp;
}


/* emitkids - emit _kids */
static void
emitkids(Rule rules_, int nrules_)
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
    print("static BURS_TreeNode kids(BURS_TreeNode p, int eruleno, int kidnumber)  { \n");
    print("%1if (BURS.DEBUG) {\n");
    /* not needed as JAVA has null exception
       print("%1if (p==null)\n%2fatal(\"kids\",\"Null tree\", 0);\n");
       print("%1if (kids==null)\n%2fatal(\"kids\", \"Null kids\", 0);\n");
    */
    print("%1switch (eruleno) {\n");
    for (i = 0; (r = rc[i]) != NULL; i++) {
        /* Next line triggers a bogus "Will never be executed" warning under
           g++ versions 3.3 and 3.3.1, with any optimization level. */
        for ( ; r; r = r->kids)
            print("%1case %d: // %R\n", r->ern, r);
        print("%s%2break;\n", str[i]);
    }
    print("%1}\n");
    print("%1throw new OptimizingCompilerException(\"BURS\",\"Bad rule number \",Integer.toString(eruleno));\n");
    print("%} else return null;\n");
    print("%}\n\n");

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
    print("static void mark_kids(BURS_TreeNode p, int eruleno)\n");
    print("%1 {\n");
    if (!oneterminal)
        print("%1byte[] ntsrule = nts[eruleno];\n");
    print("%1switch (eruleno) {\n");
    for (i = 0; (r = rc[i]) != NULL; i++) {
        /* Next line triggers a broken "Will never be executed" warning
         * under g++ version 3.3.1 with -O. */
        for ( ; r; r = r->kids)
            print("%1case %d: // %R\n", r->ern, r);
        print("%s%2break;\n", str[i]);
    }
    print("%1}\n");
    print("}\n\n");

}

/* emitlabel - emit label function */
static void
emitlabel(Term terms_)
{
//    int i;
    Term p;
//    Nonterm ntsc;

    if (!nts->link) {
        oneterminal = 1;
    }

    /* Emit a function for each opcode */
    for (p = terms_; p; p = p->link) {
        emitcase(p);
    }

    /* Emit master case statement */
    print("public void label(BURS_TreeNode p) {\n");
    print("%1p.initCost();\n");
    print("%1switch (p.getOpcode()) {\n");
    for (p = terms_; p; p = p->link) {
        if (p->arity != -1) {
            print("%1case %S_opcode: label_%S(p); break;\n", p, p);
        }
    }
    print("%1default:\n");
                 print("%2throw new OptimizingCompilerException(\"BURS\",\"terminal not in grammar:\",OperatorNames.operatorName[p.getOpcode()]);");
                 print("%1}\n}\n\n");
}

/* computents - fill in bp with _nts vector for tree t */
static char *
computents(Tree t, char *bp, size_t *bufszp)
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
static void
emitnts(Rule rules_, int nrules_)
{
    Rule r;
    int i, j, *nts_ = alloc((nrules_ + 1)*sizeof *nts_);
    char **str = alloc((nrules_ + 1)*sizeof *str);
    if (oneterminal) {
        printf("\n\n private static final byte[][] nts={};\n\n");
        return;
    }
    for (i = 0, r = rules_; r; r = r->link) {
        char buf[1024];
        size_t bufsz = sizeof buf;
        *computents(r->pattern, buf, &bufsz) = '\0';
        for (j = 0; str[j] && strcmp(str[j], buf); j++)
            ;
        if (str[j] == NULL) {
            print(" private static final byte[] nts_%d = { %s };\n", j, buf);
            str[j] = strcpy(alloc(strlen(buf) + 1), buf);
        }
        nts_[i++] = j;
    }
    print("\nprivate static final byte[][] nts = {\n");
    for (i = j = 0, r = rules_; r; r = r->link) {
        for ( ; j < r->ern; j++)
            print("%1null,%1/* %d */\n", j);
        print("%1nts_%d,%1// %d \n", nts_[i++], j++);
    }
    print("};\n\n");
}

/* emitrecalc - emit code that tests for recalculation of INDIR?(VREGP) */
static void
emitrecalc(const char *pre, Term root, Term kid)
{
    if (root->kind == TERMINAL && strncmp(root->name, "INDIR", 5) == 0
        &&   kid->kind == TERMINAL &&  strcmp(kid->name,  "VREGP"   ) == 0) {
        Nonterm p;
        print("%sif (mayrecalc(a)) {\n", pre);
        print("%s%1BURS_State q = a->syms[RX]->u.t.cse->x.state;\n", pre);
        for (p = nts; p; p = p->link) {
            print("%s%1if (q->cost_%S == 0) {\n", pre, p);
            print("%s%2p.cost_%S = 0;\n", pre, p);
            print("%s%2p.%S = q.%S;\n", pre, p, p);
            print("%s%1}\n", pre);
        }
        print("%s}\n", pre);
    }
}

/* emitrecord - emit code that tests for a winning match of rule r */
static void
emitrecord(const char *pre, Rule r, const char *c, int cost)
{
    if (Tflag) {
        print("%strace(a, %d, %s + %d, p.cost_%S);\n",
              pre, r->ern, c, cost, r->lhs);
    }
    print("%sif (", pre);
    if (cost != 0)
        print("%s + %d < p.cost_%S) {\n",c,cost,r->lhs);
    else
        print("%s < p.cost_%S) {\n",c,r->lhs);
    if (oneterminal) {
        if (cost != 0)
            print("%s%1p.cost_%S = (%s + %d);\n",pre,r->lhs,c,cost);
        else
            print("%s%1p.cost_%S = (%s);\n",pre,r->lhs,c);

    } else
        if (cost != 0)
            print("%s%1p.cost_%S = (char)(%s + %d);\n",pre,r->lhs,c,cost);
        else
            print("%s%1p.cost_%S = (char)(%s);\n",pre,r->lhs,c);
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

/* emitrule - emit decoding vectors and _rule */
static void
emitrule(Nonterm nts_)
{
    Nonterm p;
    if (nts_->link) {
        print("static final char[][] decode = {null,\n");
        for (p = nts_; p; p = p->link) {
            Rule r;
            print("%1{// %S_NT\n%10,\n", p);
            for (r = p->rules; r; r = r->decode)
                print("%1%d,\n", r->ern);
            print("},\n");
        }
        print("};\n\n");
    }


#if 0
    print("static short rule(BURS_TreeNode state, byte goalnt) {\n");
/* no need: Java has null pointer and array bounds exception
   print("%1if (goalnt < 1 || goalnt > %d)\n",ntnumber);
   print("%2fatal(\"rule\", \"Bad goal nonterminal \", goalnt);\n");
   print("%1if (state == null)\n%2return 0;\n");
*/
    print("%1int statent = state.getStatement(goalnt);\n");
    /*
      print("%1switch (goalnt) {\n");
      for (p = nts_; p; p = p->link) {
      print("%1case %S_NT: \tstatent = state.%S; \tbreak;\n",p,p);
      }
      print("%1default:\n");
      print("%2throw new OptimizingCompilerException(\"Bad nonterminal \"+goalnt);\n");
    */
    /*
      print("%2fatal(\"rule\", \"Bad goal nonterminal \", goalnt);\n");
      print("%2return 0;\n");
    */
    /*
      print("%1}\n");
    */
    print("%1return decode[goalnt][statent];\n");
    print("}\n\n");
#endif
}

/* emitstring - emit arrays of templates, instruction flags, and rules */
static void
emitstring(Rule rules_)
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
        print("/* %d */%1\"%s\",%1/* %R */\n", r->ern, r->template, r);
    print("};\n");
    print("\nstatic char isinstruction[] = {\n");
    print("/* 0 */%10,\n");
    for (r = rules_; r; r = r->link) {
        int len = strlen(r->template);
        print("/* %d */%1%d,%1/* %s */\n", r->ern,
              len >= 2 && r->template[len-2] == '\\' && r->template[len-1] == 'n',
              r->template);
    }
    print("};\n");
#endif
    print("public static final String[] string = {\n");
    print("%1null,     \t// 0\n");
    for (r = rules_; r; r = r->link)
        print("%1\"%R\",  \t// %d\n", r,r->ern);
    print("};\n\n}\n");
}

/* emitstruct - emit the definition of the state structure */
static void
emitstruct(Nonterm nts_)
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
            print("%1public int ");
        else
            print("%1public char ");
        print("cost_%S;\n", ntsc);
    }
    /*
      print("%1short cost[] = new short[%d];\n",ntnumber_+1);
    */
    print("\n%1// rule for each non-terminal\n");
    bit_offset = 0;
    word_number = 0;
    print("%1public int word0;\n");
    for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
        int n = 1, m = ntsc->lhscount; //, k;
        while ((m >>= 1) != 0)
            n++;
        ntsc->number_bits = n;
        if ((bit_offset/32) != ((bit_offset+n)/32)) {
            word_number++;
            print("%1public int word%d;\n",word_number);
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
        print("\n%1public int getCost(int goalNT) {\n");
    else
        print("\n%1public char getCost(int goalNT) {\n");
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

    word_number = 0;
    print("\n%1public void initCost() {\n");
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


    print("\n%1public int rule(int goalNT) {\n");
    if (nts_->link) {
        print("%2int statement = 0;\n");
        print("%2switch(goalNT) {\n");
        for (ntsc=nts_ ; ntsc; ntsc = ntsc->link) {
            int n = 1, m = ntsc->lhscount;
            while ((m >>= 1) != 0)
                n++;
            /* print("%2case %S_NT:  return (int)%S;\n",ntsc,ntsc); */
            if (ntsc->link)
                print("%2case %S_NT:  statement= ",ntsc);
            else
                print("%2default:     statement= ",ntsc);
            readPacked(ntsc);
            print("; break;// %S\n",ntsc);
        }
        print("%2}\n");
        print("%2return BURS_STATE.decode[goalNT][statement];\n");
    } else {
        print("\n%2return  word0;\n",nts_);
    }
    print("%1}\n");
    print("}\n\n");
}

/* emitterms - emit terminal data structures */
static void
emitterms(Term terms_)
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

    print("/*static final byte arity[] = {\n");
    for (k = 0, p = terms_; p; p = p->link) {
        for ( ; k < p->esn; k++)
            print("%10,%1// %d\n", k);
        /*
          print("%1%d,%1// %d=%S\n", p->arity < 0 ? 0 : p->arity, k++, p);
        */
        print("%1%d,%1// %d=%S\n", p->arity, k++, p);
    }
    print("};*/\n\n");
}

/* emittest - emit clause for testing a match */
static void
emittest(Tree t, const char *v, const char *suffix)
{
    Term p = t->op;

    if (p->kind == TERMINAL) {
        print("%2%s.getOpcode() == %S_opcode%s\n", v, p,
              t->nterms > 1 ? " && " : suffix);
        if (t->left)
            emittest(t->left, stringf("%s.child1",  v),
                     t->right && t->right->nterms ? " && " : suffix);
        if (t->right)
            emittest(t->right, stringf("%s.child2", v), suffix);
    }
}

char *
xstrdup(const char *src)
{
    size_t len = strlen(src);
    char *ret = alloc(len + 1);
    strcpy(ret, src);
    return ret;
}




static void
verify_chars(int ret, size_t bufsz)
{
    // ret < 0 for compatibility with old glibc versions.
    size_t nwrote;
    assert(ret >= 0);
    nwrote = ret;
    /* The %z modifier appears in ANSI C '99, for "size_t".
       We still have platforms (AIX 5.1, to be precise) which don't appear to
       support %z.  How ugly; we have to work around this. */
    if (nwrote >= bufsz) {
//      fprintf(stderr, "Needed %d chars of space; had only %zu\n",
        fprintf(stderr, "Needed %d chars of space; had only %lu\n",
                ret + 1, (unsigned long) nwrote);
        /* skip on */
    }
}

/* We use the non-gnu interface.  Return a pointer to allocated memory.  (In
   this program we're going to throw it away anyway.) */
static const char *
cwd(void)
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
