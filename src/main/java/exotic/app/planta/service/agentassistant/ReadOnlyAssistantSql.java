package exotic.app.planta.service.agentassistant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A complete, deliberately small SELECT grammar. Model text is never executed. */
public final class ReadOnlyAssistantSql {
    public static final int MAX_ROWS = 200;
    private static final Set<String> COMMON = Set.of("producto_id", "nombre", "tipo_producto",
            "tipo_unidades", "inventareable", "stock_minimo", "fecha_creacion");
    private static final Set<String> MATERIAL = Set.of("tipo_material", "punto_reorden", "consumo_directo");
    private static final Set<String> FUNCTIONS = Set.of("count", "sum", "avg", "min", "max", "lower", "upper");

    private ReadOnlyAssistantSql() { }

    public static CompiledQuery compile(String proposal) {
        return new Parser(proposal).parse();
    }

    public static String catalog() {
        return """
                Tablas virtuales autorizadas (no existen otras para este asistente):
                asistente_productos: producto_id(texto), nombre(texto), tipo_producto(M/S/T),
                  tipo_unidades(KG/G/L/ML/U), inventareable(booleano), stock_minimo(número), fecha_creacion(fecha).
                asistente_materiales: las mismas columnas, más tipo_material(1 materia prima, 2 empaque),
                  punto_reorden(número; -1 indica ignorar alerta), consumo_directo(booleano).
                No hay existencias actuales ni órdenes en este catálogo. No inferir faltantes de stock_minimo.
                No sumar cantidades de unidades distintas; agrupar por tipo_unidades.
                SQL admitido: un SELECT sobre UNA tabla virtual, columnas explícitas, AS alias,
                WHERE con comparaciones, AND/OR/NOT, paréntesis, LIKE/ILIKE, IN y IS NULL;
                GROUP BY, ORDER BY y LIMIT. COUNT(*), COUNT(DISTINCT columna), SUM/AVG/MIN/MAX/LOWER/UPPER.
                Identificadores sin comillas, literales de texto con comillas simples. Máximo 200 filas.
                No usar SELECT *, WITH, JOIN, subconsultas, UNION, casts, esquemas, comentarios ni otras funciones.
                """;
    }

    public record CompiledQuery(String sql, List<Object> parameters, int rowLimit, String preview) {
        public CompiledQuery { parameters = List.copyOf(parameters); }
    }

    private enum Kind { WORD, NUMBER, STRING, SYMBOL, END }
    private record Token(Kind kind, String value) { }

    private static IllegalArgumentException rejected() {
        return new IllegalArgumentException("La propuesta no pertenece al catálogo y a la gramática de lectura permitidos.");
    }

    private static List<Token> tokenize(String source) {
        if (source == null || source.isBlank() || source.length() > 6000) throw rejected();
        List<Token> tokens = new ArrayList<>();
        int p = 0;
        while (p < source.length()) {
            char c = source.charAt(p);
            if (Character.isWhitespace(c)) { p++; continue; }
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_') {
                int start = p++;
                while (p < source.length() && (Character.isLetterOrDigit(source.charAt(p)) || source.charAt(p) == '_')) p++;
                String word = source.substring(start, p).toLowerCase(Locale.ROOT);
                if (!word.matches("[a-z_][a-z0-9_]{0,63}")) throw rejected();
                tokens.add(new Token(Kind.WORD, word));
            } else if (c == '\'') {
                p++;
                StringBuilder value = new StringBuilder();
                boolean closed = false;
                while (p < source.length()) {
                    char next = source.charAt(p++);
                    if (next == '\'') {
                        if (p < source.length() && source.charAt(p) == '\'') { value.append('\''); p++; }
                        else { closed = true; break; }
                    } else {
                        if (next == 0 || next == '\\') throw rejected();
                        value.append(next);
                    }
                    if (value.length() > 512) throw rejected();
                }
                if (!closed) throw rejected();
                tokens.add(new Token(Kind.STRING, value.toString()));
            } else if (c >= '0' && c <= '9' || c == '-' && p + 1 < source.length()
                    && source.charAt(p + 1) >= '0' && source.charAt(p + 1) <= '9') {
                int start = p++;
                while (p < source.length() && (source.charAt(p) >= '0' && source.charAt(p) <= '9' || source.charAt(p) == '.')) p++;
                String number = source.substring(start, p);
                if (!number.matches("-?[0-9]{1,18}(\\.[0-9]{1,6})?")) throw rejected();
                tokens.add(new Token(Kind.NUMBER, number));
            } else {
                if (",()*;=<>!".indexOf(c) < 0) throw rejected();
                String symbol = String.valueOf(c);
                p++;
                if (p < source.length() && "<>=!".indexOf(c) >= 0 && source.charAt(p) == '=') symbol += source.charAt(p++);
                else if (c == '<' && p < source.length() && source.charAt(p) == '>') symbol += source.charAt(p++);
                if (symbol.equals("!")) throw rejected();
                tokens.add(new Token(Kind.SYMBOL, symbol));
            }
            if (tokens.size() > 512) throw rejected();
        }
        tokens.add(new Token(Kind.END, ""));
        return tokens;
    }

    private static String quoted(String identifier) { return '"' + identifier + '"'; }

    private static final class Parser {
        private final String source;
        private final List<Token> tokens;
        private final List<Object> parameters = new ArrayList<>();
        private final Set<String> columns = new LinkedHashSet<>();
        private final Set<String> aliases = new LinkedHashSet<>();
        private int position;

        private Parser(String source) {
            tokens = tokenize(source);
            this.source = source.trim();
        }

        private CompiledQuery parse() {
            require("select");
            String distinct = take("distinct") ? "DISTINCT " : "";
            List<String> selection = new ArrayList<>();
            do {
                String expression = expression(0, false);
                if (take("as")) {
                    String alias = word();
                    if (alias.length() > 32 || !aliases.add(alias) || COMMON.contains(alias) || MATERIAL.contains(alias)) throw rejected();
                    expression += " AS " + quoted(alias);
                }
                selection.add(expression);
                if (selection.size() > 16) throw rejected();
            } while (take(","));
            require("from");
            String table = word();
            if (!Set.of("asistente_productos", "asistente_materiales").contains(table)) throw rejected();
            String where = take("where") ? " WHERE " + predicate(0) : "";
            String group = "";
            if (take("group")) { require("by"); group = " GROUP BY " + expressions(false); }
            String order = "";
            if (take("order")) {
                require("by");
                List<String> ordering = new ArrayList<>();
                do {
                    String expr = expression(0, true);
                    if (take("asc")) expr += " ASC";
                    else if (take("desc")) expr += " DESC";
                    ordering.add(expr);
                    if (ordering.size() > 16) throw rejected();
                } while (take(","));
                order = " ORDER BY " + String.join(", ", ordering);
            }
            int limit = MAX_ROWS;
            if (take("limit")) {
                Token token = next();
                if (token.kind != Kind.NUMBER || !token.value.matches("[0-9]{1,6}")) throw rejected();
                int requested = Integer.parseInt(token.value);
                if (requested < 1) throw rejected();
                limit = Math.min(requested, MAX_ROWS);
            }
            take(";");
            if (peek().kind != Kind.END) throw rejected();
            for (String column : columns) {
                if (!COMMON.contains(column) && !(table.equals("asistente_materiales") && MATERIAL.contains(column))) throw rejected();
            }
            String allowed = "producto_id, nombre, tipo_producto, tipo_unidades, inventareable, stock_minimo, fecha_creacion";
            if (table.equals("asistente_materiales")) allowed += ", tipo_material, punto_reorden, consumo_directo";
            String fixedFilter = table.equals("asistente_materiales") ? "tipo_producto = 'M'" : "tipo_producto IN ('M','S','T')";
            String sql = "WITH " + quoted(table) + " AS (SELECT " + allowed
                    + " FROM public.productos WHERE " + fixedFilter + ") SELECT " + distinct
                    + String.join(", ", selection) + " FROM " + quoted(table) + where + group + order
                    + " LIMIT " + (limit + 1);
            return new CompiledQuery(sql, parameters, limit, source);
        }

        private String expressions(boolean allowAlias) {
            List<String> result = new ArrayList<>();
            do {
                result.add(expression(0, allowAlias));
                if (result.size() > 16) throw rejected();
            } while (take(","));
            return String.join(", ", result);
        }

        private String expression(int depth, boolean allowAlias) {
            if (depth > 4) throw rejected();
            String name = word();
            if (take("(")) {
                if (!FUNCTIONS.contains(name)) throw rejected();
                boolean distinct = take("distinct");
                if (distinct && !name.equals("count")) throw rejected();
                String argument;
                if (take("*")) {
                    if (!name.equals("count") || distinct) throw rejected();
                    argument = "*";
                } else argument = expression(depth + 1, false);
                require(")");
                return "pg_catalog." + name + "(" + (distinct ? "DISTINCT " : "") + argument + ")";
            }
            if (!(allowAlias && aliases.contains(name))) columns.add(name);
            return quoted(name);
        }

        private String predicate(int depth) {
            if (depth > 16) throw rejected();
            String result = conjunction(depth + 1);
            while (take("or")) result = "(" + result + " OR " + conjunction(depth + 1) + ")";
            return result;
        }

        private String conjunction(int depth) {
            String result = condition(depth);
            while (take("and")) result = "(" + result + " AND " + condition(depth) + ")";
            return result;
        }

        private String condition(int depth) {
            if (depth > 16) throw rejected();
            if (take("not")) return "(NOT " + condition(depth + 1) + ")";
            if (take("(")) {
                String nested = predicate(depth + 1);
                require(")");
                return "(" + nested + ")";
            }
            String left = expression(0, false);
            if (take("is")) {
                boolean not = take("not");
                require("null");
                return left + (not ? " IS NOT NULL" : " IS NULL");
            }
            boolean not = take("not");
            if (take("in")) {
                require("(");
                List<String> values = new ArrayList<>();
                do {
                    values.add(literal());
                    if (values.size() > 50) throw rejected();
                } while (take(","));
                require(")");
                return left + (not ? " NOT IN (" : " IN (") + String.join(", ", values) + ")";
            }
            if (take("like")) return left + (not ? " NOT LIKE " : " LIKE ") + literal();
            if (take("ilike")) return left + (not ? " NOT ILIKE " : " ILIKE ") + literal();
            if (not) throw rejected();
            Token operatorToken = next();
            if (operatorToken.kind != Kind.SYMBOL) throw rejected();
            String operator = operatorToken.value;
            if (!Set.of("=", "<>", "!=", "<", ">", "<=", ">=").contains(operator)) throw rejected();
            String right = peek().kind == Kind.WORD && !Set.of("true", "false").contains(peek().value)
                    ? expression(0, false) : literal();
            return left + " " + operator + " " + right;
        }

        private String literal() {
            Token token = next();
            Object value = switch (token.kind) {
                case STRING -> token.value;
                case NUMBER -> new BigDecimal(token.value);
                case WORD -> {
                    if (!Set.of("true", "false").contains(token.value)) throw rejected();
                    yield Boolean.valueOf(token.value);
                }
                default -> throw rejected();
            };
            parameters.add(value);
            if (parameters.size() > 100) throw rejected();
            return "?";
        }

        private Token peek() { return tokens.get(position); }
        private Token next() { if (peek().kind == Kind.END) throw rejected(); return tokens.get(position++); }
        private boolean take(String value) {
            // A quoted literal can never act as grammar punctuation or a keyword.
            if ((peek().kind == Kind.WORD || peek().kind == Kind.SYMBOL) && peek().value.equals(value)) { position++; return true; }
            return false;
        }
        private void require(String value) { if (!take(value)) throw rejected(); }
        private String word() { Token token = next(); if (token.kind != Kind.WORD) throw rejected(); return token.value; }
    }
}
