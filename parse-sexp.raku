grammar S-Expression-Grammar {
	token bareword { <-[ \s \( \) \" ]>+ }
	token string { '"' [ <-[\"]> | \\ \" ]* '"' }
	rule sexp { <bareword> | <string> | '(' <sexp>* ')' }
	rule TOP { <sexp> }
}

my $source = "(foo \"bar\" (baz)\n quux)";

my $match = S-Expression-Grammar.parse($source);
if $match {
	say ~$match;
} else {
	say "No match!"
}
