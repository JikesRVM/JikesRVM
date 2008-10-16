/*
 * (C) Copyright IBM Corp. 2001
 */
#ifndef BURG_INCLUDED
#define BURG_INCLUDED

/* iburg.c: */
extern void *alloc(size_t nbytes);

typedef enum { TERMINAL=1, NONTERMINAL } Kind;
typedef struct rule *Rule;
typedef struct term *Term;
struct term {		/* terminals: */
	char *name;		/* terminal name */
	Kind kind;		/* TERMINAL */
	int esn;		/* external symbol number */
	int arity;		/* operator arity */
	Term link;		/* next terminal in esn order */
	Rule rules;		/* rules whose pattern starts with term */
};

typedef struct nonterm *Nonterm;
struct nonterm {	/* nonterminals: */
	const char *name;	/* nonterminal name */
	Kind kind;		/* NONTERMINAL */
	int number;		/* identifying number */
        int word_number;        /* MAURICIO */
        int bit_offset;         /* MAURICIO */
        int number_bits;        /* MAURICIO */
	int lhscount;		/* # times nt appears in a rule lhs */
	int reached;		/* 1 iff reached from start nonterminal */
	Rule rules;		/* rules w/nonterminal on lhs */
	Rule chain;		/* chain rules w/nonterminal on rhs */
	Nonterm link;		/* next terminal in number order */
};
extern Nonterm nonterm(const char *id);
extern Term term(const char *id, int esn);

typedef struct tree *Tree;
struct tree {		/* tree patterns: */
	void *op;		/* a terminal or nonterminal */
	Tree left, right;	/* operands */
	int nterms;		/* number of terminal nodes in this tree */
};
extern Tree tree(const char *op, Tree left, Tree right);

struct rule {		/* rules: */
	Nonterm lhs;		/* lefthand side nonterminal */
	Tree pattern;		/* rule pattern */
	int ern;		/* external rule number */
	int packed;		/* packed external rule number */
	int cost;		/* cost, if a constant */
	const char *code;	/* cost, if an expression */
	const char *template;	/* assembler template */
	Rule link;		/* next rule in ern order */
	Rule next;		/* next rule with same pattern root */
	Rule chain;		/* next chain rule with same rhs */
	Rule decode;		/* next rule with same lhs */
	Rule kids;		/* next rule with same _kids pattern */
};
extern Rule rule(const char *id, Tree pattern, const char *template, const char *code);

/* gram.y: */
#ifdef __GNUC__
extern void yyerror(const char *fmt, ...) __attribute__((format(printf,1,2)));
extern void yywarn(const char *fmt, ...)  __attribute__((format(printf,1,2)));
#else
extern void yyerror(const char *fmt, ...);
extern void yywarn(const char *fmt, ...);
#endif
extern int yyparse(void);
extern int errcnt;
extern char *xstrdup(const char *src);
extern FILE *infp;
extern FILE *outfp;

#endif
