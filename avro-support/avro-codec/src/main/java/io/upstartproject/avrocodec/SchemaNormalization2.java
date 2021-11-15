package io.upstartproject.avrocodec;

import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.data.Json;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An alternative to avro's standard {@link SchemaNormalization}, providing a more comprehensive {@link #resolvingFingerprint64}
 * to better manage compatibility for schema-evolution.
 */
public class SchemaNormalization2 {
  /**
   * Almost identical to {@link org.apache.avro.SchemaNormalization#toParsingForm(Schema)}, but includes additional
   * schema elements which are relevant to compatibility: "default" and "aliases".
   * <p/>
   * - [ORDER] Order of appearance of fields in canonicalized schema JSON is as follows:<br/>
   *           name, type, fields, symbols, items, values, size, DEFAULT, ALIASES
   * <p/>
   * See https://issues.apache.org/jira/browse/AVRO-2258
   */
  public static String toResolvingForm(Schema s) {
    return build(new HashMap<>(), s, new StringBuilder()).toString();
  }

  public static long resolvingFingerprint64(Schema s) {
    return SchemaNormalization.fingerprint64(toResolvingForm(s).getBytes(StandardCharsets.UTF_8));
  }

  private static StringBuilder build(Map<String,String> env, Schema s, StringBuilder o) {
    boolean firstTime = true;
    Schema.Type st = s.getType();
    switch (st) {
      default: // boolean, bytes, double, float, int, long, null, string
        return o.append('"').append(st.getName()).append('"');

      case UNION:
        o.append('[');
        for (Schema b: s.getTypes()) {
          if (! firstTime) o.append(','); else firstTime = false;
          build(env, b, o);
        }
        return o.append(']');

      case ARRAY:  case MAP:
        o.append("{\"type\":\"").append(st.getName()).append("\"");
        if (st == Schema.Type.ARRAY)
          build(env, s.getElementType(), o.append(",\"items\":"));
        else build(env, s.getValueType(), o.append(",\"values\":"));
        return o.append("}");

      case ENUM: case FIXED: case RECORD:
        String name = s.getFullName();
        if (env.get(name) != null) return o.append(env.get(name));
        String qname = "\""+name+"\"";
        env.put(name, qname);
        o.append("{\"name\":").append(qname);
        o.append(",\"type\":\"").append(st.getName()).append("\"");
        if (st == Schema.Type.ENUM) {
          o.append(",\"symbols\":[");
          for (String enumSymbol: s.getEnumSymbols()) {
            if (! firstTime) o.append(','); else firstTime = false;
            o.append('"').append(enumSymbol).append('"');
          }
          o.append("]");
        } else if (st == Schema.Type.FIXED) {
          o.append(",\"size\":").append(Integer.toString(s.getFixedSize()));
        } else { // st == Schema.Type.RECORD
          o.append(",\"fields\":[");
          for (Schema.Field f: s.getFields()) {
            if (! firstTime) o.append(','); else firstTime = false;
            o.append("{\"name\":\"").append(f.name()).append("\"");
            build(env, f.schema(), o.append(",\"type\":"));
            // THIS DIFFERS FROM avro parsing-form (SchemaNormalization): include defaults and aliases
            addCompatibilityProperties(f, o);
            o.append("}");
          }
          o.append("]");
        }
        return o.append("}");
    }
  }

  private static void addCompatibilityProperties(Schema.Field f, StringBuilder o) {
    // include default-value
    if (f.defaultVal() != null) o.append(",\"default\":").append(Json.toString(f.defaultVal()));

    // include aliases
    // TODO: aliases are sorted here, but could be further canonicalized to use FQ name where appropriate
    Set<String> aliases = f.aliases();
    switch (aliases.size()) {
      case 0: break;
      case 1: o.append(",\"aliases\":[\"").append(aliases.iterator().next()).append("\"]"); break;
      default:
        o.append(",\"aliases\":").append(aliases.stream().sorted().collect(Collectors.joining("\",\"", "[\"", "\"]")));
        break;
    }
  }
}
