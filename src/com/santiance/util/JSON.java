package com.santiance.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Parses text into JSON objects.
 * 
 * @author Kristopher Ives <kristopher.ives@gmail.com>
 */
public abstract class JSON {
	public abstract String toString(String indentChar, String currentIndent);
	
	@Override public String toString() {
		return toString("\t", "");
	}
	
	public boolean isString() {
		return (this instanceof StringValue);
	}
	
	public String asString() {
		return toString();
	}
	
	public boolean isNumber() {
		return (this instanceof NumberValue);
	}
	
	public int asInt() {
		if (isNumber()) {
			NumberValue number = (NumberValue)this;
			
			return (Integer)number.value;
		} else if (isString()) {
			try {
				return Integer.parseInt(asString());
			} catch (Exception e) {
				return 0;
			}
		}
		
		return 0;
	}
	
	public boolean isArray() {
		return (this instanceof ArrayValue);
	}
	
	public boolean isMap() {
		return (this instanceof MapValue);
	}
	
	public MapValue asMap() {
		if (this instanceof MapValue) {
			return (MapValue)this;
		}
		
		return null;
	}
	
	/** Parse a JSON value from a String */
	public static JSON parse(String src, int pos) throws ParseException {
		final ParseContext c = new StringParseContext(src, pos);
		
		return parse(c);
	}
	
	/** Parse a JSON value from a text file */
	public static JSON parse(File f) throws IOException, ParseException {
		return parse(new FileReader(f));
	}
	
	/** Parse a JSON value from a text stream */
	public static JSON parse(Reader reader) throws ParseException {
		return parse(new ReaderParseContext(reader));
	}
	
	/** Parse a JSON value from an arbitrary source */
	public static JSON parse(ParseContext c) throws ParseException {
		while (c.hasMoreSkippingSpaces()) {
			char ch = c.peek();
			
			if (Character.isDigit(ch)) {
				return parseNumber(c);
			}
			
			switch (ch) {
			case '{': return parseMap(c);
			case '[': return parseArray(c);
			case '"': return parseString(c);
			case 'n': return parseNull(c);
			default:
				unexpected(c);
			}
		}
		
		return null;
	}
	
	/** Parse a <code>null</code> literal */
	public static JSON.NullValue parseNull(ParseContext c) throws ParseException {
		expectSkippingSpaces(c, "null");
		
		return new NullValue();
	}
	
	/** Parse a int or float */
	public static JSON.NumberValue parseNumber(ParseContext c) throws ParseException {
		boolean hasDecimal = false;
		//int start;
		String text;
		
		if (!c.hasMoreSkippingSpaces()) {
			throw new ParseException("Expecting digit, not '" + c.peek() + "'", c.pos);
		}
		
		//start = c.pos;
		c.mark();
		
		loop: while (c.hasMore()) {
			if (Character.isDigit(c.peek())) {
				c.consume();
				continue;
			}
			
			//if (Character.isWhitespace(c.peek())) {
			//	c.consume();
			//	break;
			//}
			
			switch (c.peek()) {
			case '.':
				if (hasDecimal) {
					throw new ParseException("Malformed decimal number", c.pos);
				}
				
				hasDecimal = true;
				c.consume();
				break;
			default:
				break loop;
			//	unexpected(c);
			}
		}
		
		text = c.sweep();//c.src.substring(start, c.pos);
		
		if (text.isEmpty()) {
			throw new ParseException("Expected digit", c.pos);
		}
		
		if (hasDecimal) {
			return new NumberValue(Double.parseDouble(text));
		}
		
		return new NumberValue(Integer.parseInt(text));
	}
	
	/** Parse a String value */
	public static JSON.StringValue parseString(ParseContext c) throws ParseException {
		return new JSON.StringValue(parseStringValue(c));
	}
	
	/** Parse a string value without wrapping it as a StringValue */
	private static String parseStringValue(ParseContext c) throws ParseException {
		boolean isEscaped = false;
		//int start;
		
		expectSkippingSpaces(c, '"');
		//start = c.pos;
		c.mark();
		
		while (c.hasMore()) {
			switch (c.peek()) {
			case '\\':
				isEscaped = true;
				c.consume();
				break;
			case '"':
				if (!isEscaped) {
					String value = c.sweep();
					c.consume();
					return value;
				}
				
				c.consume();
				isEscaped = false;
				break;
			default:
				c.consume();
				break;
			}
		}
		
		throw new ParseException("Expected '\"' not end of data", c.pos);
	}
	
	/** Parse an ArrayValue ( eg: <code>[1,2,3,...]</code> ) */
	public static JSON.ArrayValue parseArray(ParseContext c) throws ParseException {
		final LinkedList<JSON> values = new LinkedList<JSON>();
		
		expectSkippingSpaces(c, '[');
		
		while (c.hasMoreSkippingSpaces()) {
			values.add(parse(c));
			
			if (!c.hasMoreSkippingSpaces()) {
				throw new ParseException("Expecting ',' or ']' but ran out of data", c.pos);
			}
			
			switch (c.peek()) {
			case ',':
				c.consume();
				break;
			case ']':
				c.consume();
				return new ArrayValue(values);
			default:
				unexpected(c);
			}
		}
		
		throw new ParseException("Expected ']' but ran out of data", c.pos);
	}
	
	/** Parse a JSON object mapping (eg: <code>{"foo": "bar", ... }</code> ) */
	public static JSON.MapValue parseMap(ParseContext c) throws ParseException {
		MapValue map = new MapValue();
		
		expectSkippingSpaces(c, '{');
		
		while (c.hasMoreSkippingSpaces()) {
			map.put(parseMapEntry(c));
			
			if (!c.hasMoreSkippingSpaces()) {
				throw new ParseException("Expected ',' or '}' but ran out of data", c.getPosition());
			}
			
			switch (c.peek()) {
			case ',':
				c.consume();
				break;
			case '}':
				c.consume();
				return map;
			default:
				unexpected(c);
			}
		}
		
		throw new ParseException("Expected '}' before end of data", c.pos);
	}
	
	/** Parse a JSON key/value mapping (eg: <code>"foo":"bar"</code>) */
	public static JSON.MapValue.Entry parseMapEntry(ParseContext c) throws ParseException {
		String key;
		JSON value;
		
		key = parseStringValue(c);
		expectSkippingSpaces(c, ':');
		value = parse(c);
		
		return new JSON.MapValue.Entry(key, value);
	}
	
	/* ----- ----- JSON Value Type Classes ------ ----- */
	
	public static class StringValue extends JSON {
		public String value;
		
		public StringValue(String value) {
			this.value = value;
		}
		
		public String toString(String indentChar, String currentIndent) {
			return "\"" + this.value + "\"";
		}
		
		public boolean equals(Object b) {
			if (b instanceof String) {
				return this.value.equals((String)b);
			} else if (b instanceof StringValue) {
				return this.value.equals( ((StringValue)b).value );
			}
			
			return super.equals(b);
		}
	}
	
	public static class NumberValue extends JSON {
		Object value;
		
		public NumberValue(int value) {
			this.value = value;
		}
		
		public NumberValue(double value) {
			this.value = value;
		}
		
		public String toString(String indentChar, String currentIndent) {
			if (isInt()) {
				return String.valueOf((Integer)value);
			} else if (isFloat()) {
				return String.valueOf((Float)value);
			}
			
			return "0";
		}
		
		public boolean isInt() {
			return (value instanceof Integer);
		}
		
		public boolean isFloat() {
			return (value instanceof Double) || (value instanceof Float);
		}
	}
	
	public static class NullValue extends JSON {
		public String toString(String indentChar, String currentIndent) {
			return "null";
		}
	}
	
	public static class MapValue extends JSON implements Iterable<MapValue.Entry> {
		final Map<String, Entry> table = new HashMap<String, Entry>();
		final LinkedList<Entry> list = new LinkedList<Entry>();
		
		public static class Entry {
			public String key;
			public JSON value;
			
			public Entry(String key, JSON value) {
				this.key = key;
				this.value = value;
			}
		}
		
		public String toString(String indentChar, String currentIndent) {
			final StringBuilder buf = new StringBuilder("{\n");
			final String tab = currentIndent.concat(indentChar);
			int i = 0;
			
			for (MapValue.Entry entry : this) {
				if (i > 0) {
					buf.append(",\n");
				}
				
				buf.append(tab).append('"').append(entry.key).append("\": ");
				buf.append(entry.value.toString(indentChar, tab));
				
				i++;
			}
			
			if (i > 0) {
				buf.append("\n");
			}
			
			return buf.append(currentIndent).append("}\n").toString();
		}
		
		public Iterator<MapValue.Entry> iterator() {
			return list.iterator();
		}
		
		public void put(Entry entry) {
			Entry existing = table.get(entry.key);
			
			if (existing != null) {
				list.remove(existing);
			}
			
			table.put(entry.key, entry);
			list.add(entry);
		}
		
		public JSON get(String key) {
			Entry e = table.get(key);
			
			if (e == null) {
				return null;
			}
			
			return e.value;
		}
	}
	
	/** A JSON array of values ( eg; <code>[1, 2, 3, ... ]</code> ) */
	public static class ArrayValue extends JSON implements Iterable<JSON> {
		protected final List<JSON> values;
		
		public ArrayValue(JSON[] values) {
			this.values = Arrays.asList(values);
		}
		
		public ArrayValue(List<JSON> list) {
			this.values = list;
		}
		
		public Iterator<JSON> iterator() {
			return values.iterator();
		}
		
		@Override public String toString(String indentChar, String currentIndent) {
			final StringBuilder buf = new StringBuilder("[\n");
			final String tab = currentIndent.concat(indentChar);
			int i = 0;
			
			for (JSON value : this) {
				if (i > 0) {
					buf.append(",\n");
				}
				
				buf.append(tab).append(value.toString(indentChar, tab));
				i++;
			}
			
			if (i > 0) {
				buf.append("\n");
			}
			
			return buf.append(currentIndent).append("]").toString();
		}
	}
	
	protected static class OutputContext {
		String indent;
	}
	
	/** Abstract context of the parser (doesn't seek) */
	protected static abstract class ParseContext {
		private int pos;
		private int tokenStart = -1;
		private StringBuilder token = new StringBuilder();
		
		protected ParseContext(int pos) {
			this.pos = pos;
		}
		
		protected ParseContext() {
			this.pos = 0;
		}
		
		/** Check if there are more characters in the soruce */
		public abstract boolean hasMore();
		
		/** Look at the current character in the source */
		public abstract char peek();
		
		/** Start capturing a token from the current position */
		public void mark() {
			this.tokenStart = getPosition();
			this.token.setLength(0);
		}
		
		/** Get text since calling mark() and ends the token capturing */
		public String sweep() {
			String token = this.token.toString();
			
			this.tokenStart = -1;
			this.token.setLength(0);
			
			return token;
		}
		
		public void consume() {
			if (tokenStart > 0) {
				this.token.append(peek());
			}
			
			this.pos++;
		}
		
		public int getPosition() {
			return pos;
		}
		
		public boolean hasMoreSkippingSpaces() {
			while (hasMore()) {
				if (Character.isWhitespace(peek())) {
					consume();
					continue;
				}
				
				return true;
			}
			
			return false;
		}
	}
	
	/** A context for when parsing from a String object */
	protected static class StringParseContext extends ParseContext {
		final protected String src;
		
		public StringParseContext(String src, int pos) {
			super(pos);
			
			this.src = src;
		}
		
		public boolean hasMore() {
			return getPosition() < src.length();
		}
		
		public char peek() {
			return src.charAt(getPosition());
		}
	}
	
	/** A context for reading from Reader streams */
	public static class ReaderParseContext extends ParseContext {
		final BufferedReader reader;
		Character next = null;
		
		public ReaderParseContext(Reader reader) {
			if (reader instanceof BufferedReader) {
				this.reader = (BufferedReader)reader;
			} else {
				this.reader = new BufferedReader(reader);
			}
		}
		
		public void consume() {
			super.consume();
			next = null;
		}
		
		public boolean hasMore() {
			if (next != null) {
				return true;
			}
			
			return read() != null;
		}
		
		public char peek() {
			if (next == null) {
				throw new IllegalStateException("Tried to peek() without hasMore() data");
			}
			
			return next;
		}
		
		private Character read() {
			int c ;
			
			try {
				c = reader.read();
			} catch (IOException e) {
				next = null;
				return null;
			}
			
			if (c == -1) {
				next = null;
				return null;
			}
			
			return next = (char)c;
		}
		/*
		private char unbuffer() {
			char c = this.next;
			this.next = null;
			return c;
		}
		*/
	}
	
	/* ----- ----- Parse Helpers ----- ----- */
	
	private static void unexpected(ParseContext c) throws ParseException {
		throw new ParseException("Unexpected '" + c.peek() + "'", c.pos);
	}
	
	private static void expectSkippingSpaces(ParseContext c, char ch) throws ParseException {
		if (!c.hasMoreSkippingSpaces()) {
			throw new ParseException("Expected '" + ch + "' but ran out of data", c.pos);
		}
		
		expect(c, ch);
	}
	
	private static void expectSkippingSpaces(ParseContext c, String str) throws ParseException {
		int pos = 0;
		
		expectSkippingSpaces(c, str.charAt(pos++));
		
		while (pos < str.length()) {
			expect(c, str.charAt(pos));
		}
	}
	
	private static void expect(ParseContext c, char ch) throws ParseException {
		if (!c.hasMore()) {
			throw new ParseException("Expected '" + ch + "' but ran out of data", c.pos);
		}
		
		if (c.peek() != ch) {
			expected(c, ch);
		}
		
		c.consume();
	}
	
	private static void expected(ParseContext c, char good, char bad) throws ParseException {
		throw new ParseException("Expected '" + good + "' not '" + bad + "'", c.pos);
	}
	
	private static void expected(ParseContext c, char good) throws ParseException {
		expected(c, good, c.peek());
	}
}
