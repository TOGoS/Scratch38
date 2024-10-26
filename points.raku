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
}

my $point = Point2D.new(abscissa => 123, ordinate => 456);
say $point.fuck-it-up(9).with-abscissa(22);

