# 2024-10-30
# Let's play Regexes

# Note that this regex will match non-empty lines with or without trailing \n,
# but will not match a zero-width 'blank line' after the last \n:
my regex line { \N + \n ? | \N * \n }

my $some-input = "Hello, world!\nThis is a second line\n\nThis line has no trailing \\n char";

say "Extract lines from a string:";

my @lines = $some-input.comb(&line);

for @lines -> $line { say "  Line: {$line.trim()}"; };

# say "Extract lines from standard input, a.k.a. \$*IN:";
# 
# for $*IN.comb(&line) -> $line { say "  Line: {$line.trim()}"; }
