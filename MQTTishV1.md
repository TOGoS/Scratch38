# MQTTish format, v1

A [HashFormat](./HashFormat) for representing MQTT-like event streams
where each event is defined by a topic and a message.

- URI  :: http://ns.nuke24.net/X-2024/Formats/MQTTishV1
- UUID :: 2292a278-c066-52e4-93da-c0b89107a5a1

## Example

```
#format http://ns.nuke24.net/X-2024/Formats/MQTTishV1

# A comment line.  Does not represent a message in the stream.
# Blank lines are also ignored.

some/topic	Hello, world!

some/other/topic
	A longer, multi-line message.
	Second line!

some/other/topic	A multiline message
	that starts on the same line as the topic.

/some/global/topic	The leading slash can be used
	to indicate a 'global' topic, whereas topics without a leading
	slash are considered relative to some implied root.
```

## Description

To definitively mark a stream as being in this format,
the first line should be `#format http://ns.nuke24.net/X-2024/Formats/MQTTishV1`
or some other alias, e.g. `#format urn:uuid:2292a278-c066-52e4-93da-c0b89107a5a1`.

Each line is either blank, effectively blank (by being a comment),
a #directive (like '#format'), or a topic/message line.
Topic/message lines contain a topic (which may be blank to indicate a continuation),
optionally followed by a tab and payload.

Non-empty lines with empty topic, i.e. those that start with a tab,
are considered continuations of the previous line's payload,
including the newline character of the previous line.
Payloads containing newlines can be represented by replacing each newline with newline + tab character.
This is similar to how header lines are represented in [TEF](https://github.com/TOGoS/TEF).

Only the first tab on the line is treated this way.  Any other tabs are considered
part of the content.

All messages in MQTTish stream whose topic names do not start with a forward slash
are implied to be namespaced within some namespace that is implied by the context.
For example if the implied namespace is "jimbob", then the line

```
favorites/color	red
```

is mapped to `jimbob/favorites/color`

When a topic name starts with a slash, the slash is not considered to be literally
part of the topic name, but indicates that the remainder of the topic name
should be interpreted as global (to some other implied-by-the-context namespace).

So, assuming that same "jimbob" namespace, the following two lines would be equivalent:

```
favorites/color	red
/jimbob/favorites/color	red
```


## Out of scope

This format does *cannot* represent:
- Timestamps of messages
- Topic names that start with "/" or "#" or contain tabs or newlines.

If you need to represent arbitrary topics/payloads, use a different format.
