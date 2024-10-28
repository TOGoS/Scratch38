=CREATED 2024-10-25
=begin pod

Basic object definition stuff, blah blah.

Following along with Think Perl, which is a bit overly basic so far.

=end pod

# Raku has classes
class Point2D {
	# By default, attributes are read-only.
	# follow the definition with 'is rw' if you want them mutable.
	has Numeric $.abscissa;
	has Numeric $.ordinate;

	# Can have multiple methods with the same name,
	# so long as they are 'multi' and the signatures are different:
	multi method fuck-it-up {
		return self.fuck-it-up(-1);
	}
	multi method fuck-it-up( Numeric $ordiv ) {
		return Point2D.new(abscissa => self.abscissa + self.ordinate, ordinate => self.abscissa / $ordiv);
	}
	
	method with-abscissa(Numeric $abs) {
		# :name(value) seems an alternative syntax to name => value
		return Point2D.new(:abscissa($abs), ordinate => self.ordinate);
	}
	method with-ordinate(Numeric $ord) {
		# :name(value) seems an alternative syntax to name => value
		return Point2D.new(:abscissa(self.abscissa), ordinate => $ord);
	}

	method components {
		return self.abscissa, self.ordinate;
	}
}

# $thing is rw makes $thing an alias to the passed-in variable,
# so that we can get its name.
# I wonder if there's a way to do that without making it rw.
multi sub talk-about( $thing is rw ) {
	talk-about($thing, $thing.VAR.name);
}
multi sub talk-about( $thing, Str $name ) {
	say "Value of {$name} is {$thing.raku}, a {$thing.WHAT.raku}"
}

my $point = Point2D.new(abscissa => 123, ordinate => 456);
talk-about($point);
talk-about($point.fuck-it-up(9).with-abscissa(22), 'fucked up $point');


role Tuple {
	method components { ... }
}

say 'Now I\'ll make a Point2D, $pointy, that `does Tuple`.';
say 'This only works because Point2D happened to define `components`.';
my $pointy = $point;
$pointy does Tuple;

talk-about($pointy);

talk-about($point, '$point (which is aliased by $pointy)');


say "";
say "Now for some delegation.";

class NamedPoint2D does Tuple {
	has Str $.name;
	has Point2D $.point handles <components>
}

my $named-point = NamedPoint2D.new(name => 'Fred', point => $point);
# Can use colon instead of parentheses to indicate a function call:
#   foo: bar, baz
# is the same as:
#   foo(bar, baz)
talk-about: $named-point;
say '$named-point will delegate calls to `.components`.';
say '$named-point\'s components are ', $named-point.components.raku;
