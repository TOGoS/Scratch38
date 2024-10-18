# Think Perl 6 mentions `use Math::Trig`,
# but Raku doesn't know about it:
# use Math::Trig;



demo
	"Use 'my' to declare local variables.",
	'my $foo = 123; say $foo;',
	False;

say "";
say "Note that the previous example had to end with a semicolon;";
say "if eval sees one semicolon, it'll expect one at the end, too.";	

demo "Use 'say' to print shit", 'say "Hello, world!"', False;

demo
	 "Use .WHAT to find out the types of things.",
	 '(38/15 + 27/30).WHAT;';

# Function definitions can come after
# the point where they are called.

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

demo "x repeats text.",
	"'foo' x 5"
