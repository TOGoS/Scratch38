my $whom = 'world'; "hello $whom!".say;


say "Use .WHAT to find out the types of things:";
my $code = 'say (38/15 + 27/30).WHAT;';
say "> $code";
$code.EVAL;
