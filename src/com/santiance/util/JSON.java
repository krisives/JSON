package com.santiance.util;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JSON {	
	public static Value parse(String src, int pos) throws ParseException {
		final ParseContext c = new ParseContext(src, pos);
		
		return parse(c);
	}
	
	public static Value parse(ParseContext c) throws ParseException {
		while (c.hasMoreSkippingSpaces()) {
			switch (c.peek()) {
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
	
	public static JSON.MapValue parseMap(ParseContext c) throws ParseException {
		MapValue map = new MapValue();
		
		expectSkippingSpaces(c, '{');
		
		while (c.hasMoreSkippingSpaces()) {
			map.put(parseMapEntry(c));
			
			if (!c.hasMoreSkippingSpaces()) {
				throw new ParseException("Expected ',' or '}' but ran out of data", c.pos);
			}
			
			switch (c.peek()) {
			case ',':
				c.consume(1);
				break;
			case '}':
				c.consume(1);
				return map;
			default:
				unexpected(c);
			}
		}
		
		throw new ParseException("Expected '}' before end of data", c.pos);
	}
	
	public static JSON.MapValue.Entry parseMapEntry(ParseContext c) throws ParseException {
		String key;
		JSON.Value value;
		
		key = parseStringValue(c);
		expectSkippingSpaces(c, ':');
		value = parse(c);
		
		return new JSON.MapValue.Entry(key, value);
	}
	
	public static JSON.ArrayValue parseArray(ParseContext c) throws ParseException {
		final LinkedList<JSON.Value> values = new LinkedList<JSON.Value>();
		
		expectSkippingSpaces(c, '[');
		
		while (c.hasMoreSkippingSpaces()) {
			values.add(parse(c));
			
			if (!c.hasMoreSkippingSpaces()) {
				throw new ParseException("Expecting ',' or ']' but ran out of data", c.pos);
			}
			
			switch (c.peek()) {
			case ',':
				c.consume(1);
				break;
			case ']':
				c.consume(1);
				return new ArrayValue(values);
			default:
				unexpected(c);
			}
		}
		
		throw new ParseException("Expected ']' but ran out of data", c.pos);
	}
	
	public static JSON.NullValue parseNull(ParseContext c) throws ParseException {
		expectSkippingSpaces(c, "null");
		
		return new NullValue();
	}
	
	public static JSON.NumberValue parseNumber(ParseContext c) throws ParseException {
		boolean hasDecimal = false;
		int start;
		String text;
		
		if (!c.hasMoreSkippingSpaces()) {
			throw new ParseException("Expecting digit, not '" + c.peek() + "'", c.pos);
		}
		
		start = c.pos;
		
		while (c.hasMore()) {
			if (Character.isDigit(c.peek())) {
				c.consume(1);
				continue;
			}
			
			if (Character.isWhitespace(c.peek())) {
				break;
			}
			
			switch (c.peek()) {
			case '.':
				if (hasDecimal) {
					throw new ParseException("Malformed decimal number", c.pos);
				}
				
				break;
			default:
				unexpected(c);
			}
		}
		
		text = c.src.substring(start, c.pos);
		
		if (text.isEmpty()) {
			throw new ParseException("Expected digit", c.pos);
		}
		
		if (hasDecimal) {
			return new NumberValue(Double.parseDouble(text));
		}
		
		return new NumberValue(Integer.parseInt(text));
	}
	
	public static JSON.StringValue parseString(ParseContext c) throws ParseException {
		return new JSON.StringValue(parseStringValue(c));
	}
	
	public static String parseStringValue(ParseContext c) throws ParseException {
		boolean isEscaped = false;
		int start;
		
		expectSkippingSpaces(c, '"');
		start = c.pos;
		
		while (c.hasMore()) {
			switch (c.peek()) {
			case '\\':
				isEscaped = true;
				c.consume(1);
				break;
			case '"':
				c.consume(1);
				
				if (!isEscaped) {
					return c.src.substring(start, c.pos);
				}
				
				isEscaped = false;
				break;
			default:
				c.consume(1);
				break;
			}
		}
		
		throw new ParseException("Expected '\"' not end of data", c.pos);
	}
	
	/* ----- ----- JSON Value Type Classes ------ ----- */
	
	public abstract static class Value {
		public boolean isString() {
			return (this instanceof StringValue);
		}
		
		public boolean isNumber() {
			return (this instanceof NumberValue);
		}
		
		public boolean isArray() {
			return (this instanceof ArrayValue);
		}
		
		public boolean isMap() {
			return (this instanceof MapValue);
		}
	}
	
	public static class StringValue extends Value {
		public String value;
		
		public StringValue(String value) {
			this.value = value;
		}
	}
	
	public static class NumberValue extends Value {
		Object value;
		
		public NumberValue(int value) {
			this.value = value;
		}
		
		public NumberValue(double value) {
			this.value = value;
		}
		
		public boolean isInt() {
			return (value instanceof Integer);
		}
		
		public boolean isFloat() {
			return (value instanceof Double) || (value instanceof Float);
		}
	}
	
	public static class NullValue extends Value {
		
	}
	
	public static class MapValue extends Value implements Iterable<MapValue.Entry> {
		final Map<String, Entry> table = new HashMap<String, Entry>();
		final LinkedList<Entry> list = new LinkedList<Entry>();
		
		public static class Entry {
			public String key;
			public JSON.Value value;
			
			public Entry(String key, JSON.Value value) {
				this.key = key;
				this.value = value;
			}
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
	}
	
	public static class ArrayValue extends Value {
		final JSON.Value[] values;
		
		public ArrayValue(JSON.Value[] values) {
			this.values = values;
		}
		
		public ArrayValue(List<JSON.Value> list) {
			this.values = new JSON.Value[list.size()];
			list.toArray(this.values);
		}
	}
	
	protected static class ParseContext {
		protected String src;
		protected int pos;
		
		public ParseContext(String src, int pos) {
			this.src = src;
			this.pos = pos;
		}
		
		public boolean hasMore() {
			return pos < src.length();
		}
		
		public boolean hasMoreSkippingSpaces() {
			while (hasMore()) {
				if (Character.isWhitespace(src.charAt(pos))) {
					consume(1);
					continue;
				}
				
				return true;
			}
			
			return false;
		}
		
		public char peek() {
			return src.charAt(pos);
		}
		
		public void consume(int len) {
			pos += len;
		}
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
		
		c.consume(1);
	}
	
	private static void expected(ParseContext c, char good, char bad) throws ParseException {
		throw new ParseException("Expected '" + good + "' not '" + bad + "'", c.pos);
	}
	
	private static void expected(ParseContext c, char good) throws ParseException {
		expected(c, good, c.peek());
	}
}
