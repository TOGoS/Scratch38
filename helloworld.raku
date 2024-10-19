=head1 TOGoS Learns Perl 6 aka Raku

=para
_Think Perl 6_ mentions `use Math::Trig`,
but Raku doesn't know about it, so the following doesn't work:
=begin code
use Math::Trig;
=end code



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

=para
Function definitions can come after
the point where they are called.

#|<
	Indicate a bit of source code to be evaled,
	then eval it and, if $eval is True, print the result.
>
sub demo(Str $text, Str $source, Bool $eval=True) {
	say "";
	say $text;
	say "> $source";
	if $eval {
		say "= ", $source.EVAL;
	} else {
		 $source.EVAL;
	}
}

demo
	"Use `.WHY` to get at documentation.",
	'(&demo).WHY';

demo
	"x repeats text.",
	"'foo' x 5"
