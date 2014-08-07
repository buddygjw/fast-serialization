package org.nustaq.konfigkaiser;

import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTConfiguration;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * <p/>
 * Date: 20.12.13
 * Time: 12:03
 *
 * Created by ruedi on 07.08.2014.
 */
public class KKSerializer {
    protected KKCharOutput out;
    protected KKTypeMapper mapper;
    protected boolean pretty = true;
    protected boolean writeNull = false;
    protected FSTConfiguration conf;

    public KKSerializer(KKCharOutput out, KKTypeMapper mapper, FSTConfiguration conf) {
        this.out = out;
        this.mapper = mapper;
        this.conf = conf;
    }

    public void writeObject(Object o) throws Exception {
        writeObjectInternal(o,0);
    }

    protected void writeObjectInternal(Object o, int indent) throws Exception {
        if ( o == null ) {
            out.writeString("null");
            return;
        }
        if ( o instanceof Character) {
            if ( indent >= 0 ) writeIndent(indent);
            writeString(o.toString());
            if ( indent >= 0 ) writeln();
        } else if ( o instanceof Number || o instanceof Boolean ) {
            if ( indent >= 0 ) writeIndent(indent);
            writeString(o.toString());
            if ( indent >= 0 ) writeln();
        } else if ( o instanceof String ) {
            if ( indent >= 0 ) writeIndent(indent);
            writeString((String) o);
            if ( indent >= 0 ) writeln();
        } else if ( o instanceof Map) {
            Map map = (Map) o;
            writeIndent(indent);
            out.writeString("{");
            writeln();
            for (Iterator iterator = map.keySet().iterator(); iterator.hasNext(); ) {
                Object next = iterator.next();
                Object value = map.get(next);
                boolean valueSingleLine = isSingleLine(null, value);
                boolean keySingleLine = isSingleLine(null, next);
                if (keySingleLine) {
                    writeIndent(indent + 1);
                    writeObjectInternal(next, -1);
                    out.writeString(" : ");
                    if ( !valueSingleLine)
                        writeln();
                } else {
                    writeObjectInternal(next, indent + 1);
                    writeIndent(indent + 1);
                    out.writeString(":");
                    if ( !valueSingleLine)
                        writeln();
                }
                if ( valueSingleLine ) {
                    out.writeChar(' ');
                    writeObjectInternal(value, -1);
                    writeln();
                } else {
                    writeObjectInternal(value, indent + 1);
                }
            }
            writeIndent(indent);
            out.writeChar('}');
            writeln();
        } else if ( o instanceof Collection) {
            Collection coll = (Collection) o;
            writeIndent(indent);
            out.writeString("{");
            writeln();
            for (Iterator iterator = coll.iterator(); iterator.hasNext(); ) {
                Object next = iterator.next();
                writeObjectInternal(next, indent + 1);
            }
            writeIndent(indent);
            out.writeChar('}');
            writeln();
        } else if ( o.getClass().isArray()) {
//            String stringForType = mapper.getStringForType(o.getClass());
//            out.writeString(stringForType+" {");
            writeIndent(indent);
            out.writeString("{ ");
            int len = Array.getLength(o);
            boolean lastWasSL = false;
            for ( int ii=0; ii < len; ii++ ) {
                Object val = Array.get(o,ii);
//            val = mapper.coerceWriting(val);
                if ( isSingleLine(null,val) ) {
                    writeObjectInternal(val, -1 );
                    out.writeChar(' ');
                    lastWasSL = true;
                } else {
                    if ( ii == 0)
                        writeln();
                    writeObjectInternal(val, indent + 2);
                    lastWasSL = false;
                }
            }
            if ( ! lastWasSL )
                writeIndent(indent);
            out.writeChar('}');
            writeln();
        } else {
            String stringForType = mapper.getStringForType(o.getClass());

            writeIndent(indent);
            out.writeString(stringForType + " {");
            writeln();

            FSTClazzInfo clInfo = conf.getCLInfoRegistry().getCLInfo(o.getClass());
            FSTClazzInfo.FSTFieldInfo[] fieldInfo = clInfo.getFieldInfo();

            for (int i = 0; i < fieldInfo.length; i++) {
                FSTClazzInfo.FSTFieldInfo fstFieldInfo = fieldInfo[i];
                Object fieldValue = fstFieldInfo.getField().get(o);
  //              fieldValue = mapper.coerceWriting(fieldValue);
                if ( isNullValue(fieldValue) || writeNull ) {
                    writeIndent(indent + 1);
                    out.writeString(fstFieldInfo.getField().getName());
                    out.writeChar(':');
                    if (isSingleLine(fstFieldInfo, fieldValue)) {
                        out.writeString(" ");
                        writeObjectInternal(fieldValue, 0);
                    } else {
                        writeln();
                        writeObjectInternal(fieldValue, indent + 2);
                    }
                }
            }
            writeIndent(indent);
            out.writeChar('}');
            writeln();
        }
    }

    private boolean isSingleLine(FSTClazzInfo.FSTFieldInfo fstFieldInfo, Object fieldValue) {
        if ( fstFieldInfo == null ) {
            if ( fieldValue instanceof Class ) { // now that's dirty ..
                Class clz = (Class) fieldValue;
                return String.class.isAssignableFrom(clz) || Number.class.isAssignableFrom(clz) || clz.isPrimitive();
            }
            return fieldValue instanceof String || fieldValue == null || fieldValue instanceof Number;
        }
        if ( fstFieldInfo.isArray() && isSingleLine( null, fstFieldInfo.getType().getComponentType() ) ) {
            return true;
        }
        return fstFieldInfo.isPrimitive() || fieldValue instanceof String || fieldValue == null || fieldValue instanceof Number;
    }

    static Character zeroC = new Character((char) 0);
    private boolean isNullValue(Object fieldValue) {
        if (writeNull)
            return false;
        if ( fieldValue instanceof Number ) {
            return ((Number) fieldValue).doubleValue() != 0.0;
        }
        return fieldValue != null && !fieldValue.equals(zeroC) && !fieldValue.equals(Boolean.FALSE);
    }

    protected void writeln() {
        if ( pretty )
            out.writeChar('\n');
    }

    public void writeString(String string) {
        if (string==null) {
            out.writeString("null");
            return;
        }
        if (string.length() == 0) {
            out.writeString("\"\"");
            return;
        }

        char         b;
        char         c = 0;
        int          i;
        int          len = string.length();
        String       t;
        boolean ws = false;
        for ( int ii = 0; ii < string.length(); ii++ ) {
            if ( Character.isWhitespace(string.charAt(ii)) ) {
                ws = true;
                break;
            }
        }

        if ( ws )
            out.writeChar('\"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':  out.writeChar('\\'); out.writeChar(c); break;
                case '/':
                    if (b == '<') {
                        out.writeChar('\\');
                    }
                    out.writeChar(c);
                    break;
                case '\b': out.writeString("\\b"); break;
                case '\t': out.writeString("\\t"); break;
                case '\n': out.writeString("\\n"); break;
                case '\f': out.writeString("\\f"); break;
                case '\r': out.writeString("\\r"); break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') ||
                            (c >= '\u2000' && c < '\u2100')) {
                        t = "000" + Integer.toHexString(c);
                        out.writeString("\\u" + t.substring(t.length() - 4));
                    } else {
                        out.writeChar(c);
                    }
            }
        }
        if ( ws )
            out.writeChar('"');
    }

    protected void writeIndent(int indent) {
        if ( pretty ) {
            for (int i=0; i<indent*2;i++) {
                out.writeChar(' ');
            }
        }
    }

    public boolean isWriteNull() {
        return writeNull;
    }

    public void setWriteNull(boolean writeNull) {
        this.writeNull = writeNull;
    }
}
