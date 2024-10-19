=TITLE TOGoS Learns Perl6 aka Raku
=AUTHOR TOGoS

=comment This C<comment> won't show up in the documentation.

=head1 TOGoS Learns Perl 6 aka Raku

=para
U<Think Perl 6> mentions C<use Math::Trig>,
but Raku doesn't know about it, so the following doesn't work:
=begin code
use Math::Trig;
=end code
=para
This might be because I need to C<zef install> it.
=begin code
zef install Math::Trig
=end code
=para And maybe while you're at it,
=begin code
zef install Pod::To::HTML
=end code
=para so that you can
=code raku --doc=HTML helloworld.raku
=para
I had some trouble with this, something about C<curl>,
but eventually after trying to install C<Pod::To::HTML::Section> (whatever that is)
and C<Pod::To::Markdown>, C<Pod::To::HTML> ended up installed.


say-head "Use a variable";


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

=head2 Some Functions

sub trime(Str $text) {
	$text ~~ /^\n*(.*?)\s*$/;
	return $0;
}

#|<
	Indicate a bit of source code to be evaled,
	then eval it and, if $eval is True, print the result.
>
sub demo(Str $text, Str $source is copy, Bool $eval=True) {
	my $f-source = trime($source).indent(*);
	$f-source = $f-source.lines.map({ "> $_" }).join("\n");
	say "";
	say $text;
	say $f-source;
	if $eval {
		say "= ", $source.EVAL;
	} else {
		try $source.EVAL;
		with $! {
			say "Error: $!"
		}
	}
}

sub say-head($title) {
	say "";
	say "## $title";
	say "";
}

demo
	"Use `.WHY` to get at documentation.",
	'(&demo).WHY';

demo
	"x repeats text.",
	"'foo' x 5";

=head2 Now how 'bout them tables?

=begin table
 Material |   Drying | Nozzle temp |      Bed |  Print | Needs     
          | temp (C) |    temp (C) | temp (C) |  scale | glue?     
===================================================================
 PLA      |       55 |     190-210 |    50-60 | 1.0035 | sometimes,
  |  |  |  |  | especially in the winter.
-------------------------------------------------------------------
 PETG     |       60 |     220-245 |       80 |  1.006 | yes       
-------------------------------------------------------------------
 TPU      |       65 |         230 |       60 |   1.00 | no        
===================================================================
 Socks    |       40 |             |          |   0.96 | no
=end table

=begin pod
Note that, unlike in org-mode tables, pod6 table cells can span
multiple lines of text.  Source cells don't need to line up, but
it seems like bars must be separated by at least two spaces.

Pod will also interpret C<+> as a column separator,
so if you've got any plusses in there, escape them with a backslash.

=begin table
What you want to say | How you gotta say it
===========================================
\+                   | \\+
\|                   | \\|
\\+                  | \\\+
\                    | \
=end table

Backslash is kinda weird because it's an escape character,
but doesn't itself need to be escaped.

Anyhoo, back to some code that does stuff.
=end pod

sub do-twice($do) {
	$do();
	$do();
}

=begin pod
Note that in this implementation of do-twice,
the () are required to actually call the function.
=end pod



sub say-hey() { say "Hey there." }

do-twice(&say-hey);

=begin pod
Using C<&> instead of C<$> is 'more idiomatic'
and has the advantage that you can leave off
the sigil and the parentheses when calling it.
=end pod

sub do-twice-ampersandly(&do) {
	do();
	do;
}

sub say-howdy() { say "Howdy!" }

do-twice-ampersandly(&say-howdy);

do-twice-ampersandly(sub { say "Jerk butts" });

=begin pod
You can create anonymous subroutines like:
=begin code
sub { say "Hi there" }
=end code
or
=begin code
sub ($whom) { say "Hi there, $whom" }
=end code
but B<not> like
=begin code
sub($whom) { say "Hi there, $whom" }
=end code
C<sub> needs to have a space after it or the parser gets confused.
=end pod

sub compose(&a, &b) {
	sub ($x) { b(a($x)) }
}

sub double($t) {
	"$t and $t";
}

say &compose.raku;

# This should quadruple Fred, I think:
demo
	'Call that compose function.',
	'compose(&double, &double)("Fred")';


=begin pod
=head2 Refinement types

(At least that's what I think these are called,
based on some CoRecursive episode.)

Raku lets you define types in terms of constraints,
which is something I like.

=begin code
subset Three-letter of Str where .chars == 3;
my Three-letter $acronym = "FOF";
=end code
=end pod

demo
	'Define a type using a runtime check',
	Q[
		subset Three-letter of Str where .chars == 3;
	], False;

=begin comment
Note that that type definition sticks around for later evals.
=end comment

demo
	'Cram something into a Three-letter variable',
	Q[
		my Three-letter $acronym = "FOF";
		say "Look, a TLA: '$acronym'";
	], False;

demo
	'Try to cram four letters into a three-letter acronym',
	Q[
		my Three-letter $acronym = "FAFF";
		say "Look, a TLA: '$acronym'";
	], False;


=begin pod
=head2 given

C<given "some value" { ... }> results in the code within the braces
being run with C<$_> set to C<"some value">

Source: L<https://docs.raku.org/language/control#given>
=end pod

say-head "Now let's try out `given`";
	
given "Bob Ross\nGray and floofy" {
	.lines.map({"  > a line: $_"}).join("\n").say
}

=begin pod
=head2 gather/take

Raku offers some other interesting control structures,
like L<gather/take|https://docs.raku.org/language/control#gather/take>
and L<supply/emit|https://docs.raku.org/language/control#wupply/emit>.
I'm not yet sure how those are different.
=end pod

say-head "supply";

my $supply = supply {
    .emit for "foo", 42, .5;
}
$supply.tap: {
    say "received {.^name} ($_)";
}
