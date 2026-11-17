# Java Style

Try to apply these formatting rules to new code.
Do not reformat existing code unless it is being changed for other reasons,
for consistency with other lexically-nearby changes,
or because TOGoS specifically asked for reformatting.

Ideally formatting would be done mechanically by a formatting tool,
which as of this writing I don't yet have in place.

## Formatting

- Use tabs for indentation in Java source files.
- Use spaces only for alignment after tabs if needed.
- Preserve indentation on blank lines inside blocks; a blank line should still carry the block's indent.
- The repository config is in [.editorconfig](./.editorconfig).
- Run `./scripts/format-java-indent.py` on Java files before committing formatting changes.

### Indentation

Structures that easily fit on one line should be put on one line.
'Easily' depends on context, where the choice should be about 70%
a reflection of the complexity of the code and 30% actual character count.

For example:
- Methods, especially constructors, that take more than one argument may give each argument its own line
  'for clarity'
- Trivial getters should usually be reduced to a single line,
  since that line line contains a lot of redundant information.

There is no perfect way to format throws clauses;
just try to avoid excessive indentation.

```
class Foo<T>
implements Foobricator
{
	protected final String name;
	protected final T thingy;
	
	public Foo(
		// Note ONE additional level of indent here, not two.
		String name,
		T thingy
	) {
		this.name = name;
		this.thingy = thingy;
	}
	
	public T getThingy(T thingy) { return this.thingy; }

	public void doSomethingThatMightThrowBar(
		int bar
	) throws CraptacularException { // throws may be 'cuddled' after a multi-line parameter list
		,,,
	}
	
	public void doSomethingThatMightThrowFoo()
	throws CraptacularException // throws on its own line when not 'cuddled'
	{
		,,,
	}
}
```

One tab per indent.  This applies to regular code blocks, parameter lists, and array contents.
