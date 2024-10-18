sub demo($text, $source, $eval=True) {
	say "";
	say $text;
	say "> $source";
	if $eval {
		say "= ", $source.EVAL;
	} else {
		 $source.EVAL;
	}
}

demo "Use 'say' to print shit", 'say "Hello, world!"', False;

demo
	 "Use .WHAT to find out the types of things.",
	 '(38/15 + 27/30).WHAT;';
