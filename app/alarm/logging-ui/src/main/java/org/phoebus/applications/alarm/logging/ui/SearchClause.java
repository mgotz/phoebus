package org.phoebus.applications.alarm.logging.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;


public class SearchClause {

    private Negation occur;
    private String fieldName;
    private String term1;
    private String term2;
    private Boolean wildcardLiteral;

    public SearchClause() {
        this("");
    }

    public SearchClause(String field){
        this(Negation.IS, field);
    }

    public SearchClause(Negation occur, String field){
        this(occur, field, "*");
    }

    public SearchClause(Negation occur, String field, String term){
        this(occur, field, term, "", false);
    }

    public SearchClause(Negation occur, String field, String term, Boolean wcAreLiteral){
        this(occur, field, term, "", wcAreLiteral);
    }

    public SearchClause(Negation occur, String field, String from, String to, Boolean wcAreLiteral){
        this.occur = occur;
        this.fieldName = field;
        this.term1 = from;
        this.term2 = to;
        this.wildcardLiteral = wcAreLiteral;
    }

    public void setFieldName(String fn) {
        fieldName = fn;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setOccur(Negation oc) {
        occur = oc;
    }

    public Negation getOccur(){
        return occur;
    }

    public void setTerm1(String t) {
        term1 = t;
    }

    public String getTerm1() {
        return term1;
    }

    public void setTerm2(String t) {
        term2 = t;
    }

    public String getTerm2() {
        return term2;
    }

    public void setWildcardLiteral(Boolean treatWildcardLiteral) {
        wildcardLiteral = treatWildcardLiteral;
    }

    public Boolean getWildcardLiteral() {
        return wildcardLiteral;
    }

    @Override
    public String toString() {
        return toQueryString();
    }

    public String toQueryString(){

        String condition = escape(term1, wildcardLiteral);
        if (!term2.isEmpty()) {
            condition = "[\"" + condition + "\" TO \"" + escape(term2, wildcardLiteral) + "\"]";
        }

        return occur.toQueryString() + escape(fieldName, true) + ":" + condition;
    }

    public static enum Negation {
        IS("is", BooleanClause.Occur.MUST),
        IS_NOT("is not", BooleanClause.Occur.MUST_NOT);

        private String prettyStr;
        private BooleanClause.Occur occur;

        Negation(String str, BooleanClause.Occur occur){
            this.prettyStr = str;
            this.occur = occur;
        }


        private static final Map<BooleanClause.Occur, Negation> BY_OCCUR = new HashMap<>();

        static {
            for (Negation n: values()) {
                BY_OCCUR.put(n.occur, n);
            }
        }

        public static Optional<Negation> fromOccur(BooleanClause.Occur occur){
            return Optional.ofNullable(BY_OCCUR.get(occur));
        }

        public String toQueryString() {
            return occur.toString();
        }

        @Override
        public String toString() {
            return prettyStr;
        }
    }

    public static String listToQuery(List<SearchClause> clauseList) {

        if (clauseList.isEmpty()) {
            return "";
        }

        // collect all the clauses referencing the same field together under the
        // same key in a map
        var nameMap = new HashMap<String,List<SearchClause>>();
        String fieldName;
        for (SearchClause clause : clauseList) {
            fieldName = clause.getFieldName();
            if (nameMap.containsKey(fieldName)) {
                nameMap.get(fieldName).add(clause);
            }
            else {
                nameMap.put(fieldName, new ArrayList<SearchClause>(List.of(clause)));
            }
        }

        // nested stream().map().collect() construct:
        // inner part uses the list of clauses to join all clauses for the same
        // fieldName with an OR. Thus only one condition must be true per field.
        // outer part joins the inner clauses with a MUST. Thus for each field, where
        // a clause was given, one MUST match.
        final String queryString = nameMap.values().stream().map(
            cList -> cList.stream()
                .map(clause -> clause.toQueryString())
                .collect(Collectors.joining(") (","(",")"))
            )
            .collect(Collectors.joining(") +(","+(",")"));

        return queryString;

    }

    private static final Set<Character> wildcards = Set.of('*', '?');
    private static final Set<Character> luceneSpecial =
        Set.of('\\', '+', '-', '!', '(', ')', ':', '^', '[', ']', '\"', '{',  '}', '~', '|', '&', '/', ' ');


    private static String escape(String s, Set<Character> toEscape) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (toEscape.contains(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String escape(String s, Boolean escapeWildcards, Set<Character> extras) {
        Set<Character> toEscape = new HashSet<Character>(luceneSpecial);

        if (escapeWildcards){
            toEscape.addAll(wildcards);
        }

        toEscape.addAll(extras);

        return escape(s, toEscape);
    }

    public static String escape(String s, Boolean escapeWildcards) {
        return escape(s, escapeWildcards, new HashSet<Character>());
    }

    public static SearchClause fromLuceneClause(BooleanClause luceneClause) throws QueryNotSupported {
        Negation occur;
        try {
            occur = Negation.fromOccur(luceneClause.getOccur()).get();
        }
        catch (NoSuchElementException e) {
            throw new QueryNotSupported("unsupported occur value: "+luceneClause.getOccur().name());
        }

        String fieldName;
        String term1;
        String term2;
        Boolean wildcardLiteral;
        Query query = luceneClause.getQuery();

        if (query instanceof TermQuery) {
            var q = (TermQuery) query;
            fieldName = q.getTerm().field();
            term1 = q.getTerm().text();
            term2 = "";
            wildcardLiteral = true;
        }
        else if (query instanceof WildcardQuery) {
            var q = (WildcardQuery) query;
            fieldName = q.getTerm().field();
            term1 = q.getTerm().text();
            term2 = "";
            wildcardLiteral = false;
        }
        else if (query instanceof PrefixQuery) {
            var q = (PrefixQuery) query;
            fieldName = q.getField();
            term1 = q.getPrefix().text() + "*";
            term2 = "";
            wildcardLiteral = false;
        }
        else if (query instanceof TermRangeQuery) {
            var q = (TermRangeQuery) query;
            fieldName = q.getField();
            term1 = q.getLowerTerm().utf8ToString();
            term2 = q.getUpperTerm().utf8ToString();
            wildcardLiteral = false;
        }
        else {
            throw new QueryNotSupported("unsupported query type: " + query.getClass().getName());
        }

        return new SearchClause(occur, fieldName, term1, term2, wildcardLiteral);
    }

    public static List<SearchClause> parseQueryString(String queryString) throws ParseException, QueryNotSupported {
        return parseQueryString(queryString, "defaultField");
    }

    public static List<SearchClause> parseQueryString(String queryString, String defaultField) throws ParseException, QueryNotSupported {
        var clauseList = new ArrayList<SearchClause>();

        if (queryString.isBlank()) {
            return clauseList;
        }

        var parser = new QueryParser(defaultField, new KeywordAnalyzer());
        parser.setAllowLeadingWildcard(true);
        Query query = parser.parse(queryString);

        if (query instanceof BooleanQuery) {
            List<BooleanClause> outerClauses  = ((BooleanQuery) query).clauses();
            for (BooleanClause luceneClause : outerClauses) {
                if (luceneClause.getQuery() instanceof BooleanQuery) {
                    assertQueryFormat(luceneClause.getOccur() == BooleanClause.Occur.MUST,
                                      "For nested bool queries the first level only supports MUST");
                    clauseList.addAll(unpackInnerBool((BooleanQuery) luceneClause.getQuery()));
                }
                else {
                    clauseList.add(fromLuceneClause(luceneClause));
                }
            }
        }
        else {
            SearchClause singleClause = fromLuceneClause(new BooleanClause(query, BooleanClause.Occur.MUST));
            clauseList.add(singleClause);
        }
        return clauseList;
    }

    static List<SearchClause> unpackInnerBool(BooleanQuery luceneQuery) throws QueryNotSupported {
        List<SearchClause> searchClauseList = new ArrayList<SearchClause>();

        for (BooleanClause luceneClause : luceneQuery) {
            if (luceneClause.getQuery() instanceof BooleanQuery) {
                assertQueryFormat(luceneClause.getOccur() == BooleanClause.Occur.SHOULD,
                                "For nested bool require OR for the second level"
                                + "offending clause: " + luceneClause.getQuery().toString());
                List<BooleanClause> innerClauses = ((BooleanQuery) luceneClause.getQuery()).clauses();
                assertQueryFormat(innerClauses.size() == 1,
                                  "Inner bool query must have one clause");
                searchClauseList.add(fromLuceneClause(innerClauses.get(0)));
            }
            else {
                searchClauseList.add(fromLuceneClause(luceneClause));
            }

        }

        if (!searchClauseList.isEmpty()) {
            final String fieldName = searchClauseList.get(0).getFieldName();
            for (SearchClause searchClause : searchClauseList) {
                assertQueryFormat(searchClause.getFieldName().equals(fieldName),
                                  "Nested clauses must all apply to the same field, found: "
                                   + searchClause.getFieldName() + " and " + fieldName);
            }
        }

        return searchClauseList;
    }

    static void assertQueryFormat(Boolean cond, String msg) throws QueryNotSupported {
        if (!cond) {
            throw new QueryNotSupported(msg);
        }
    }

    public static class QueryNotSupported extends Exception {
        public QueryNotSupported(String errorMessage) {
            super(errorMessage);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null && !(obj instanceof SearchClause)) {
            return false;
        }
        else {
            var other = (SearchClause) obj;
            return this.toQueryString().equals(other.toQueryString());
        }
    }
}
