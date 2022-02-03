package org.phoebus.applications.alarm.logging.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;

import org.phoebus.applications.alarm.logging.ui.SearchClause.Negation;
import org.phoebus.applications.alarm.logging.ui.SearchClause.QueryNotSupported;


public class SearchClauseTest {
    private SearchClause testClause;
    private QueryParser parser = new QueryParser("defaultField", new KeywordAnalyzer());;


    @Test
    public void simpleQueryString() throws ParseException {
        String fieldName = "field1";
        String term = "term";
        testClause = new SearchClause(Negation.IS, fieldName, term);


        BooleanQuery outerQuery = (BooleanQuery) parser.parse(testClause.toQueryString());
        assertEquals(outerQuery.clauses().size(), 1);
        assertEquals(outerQuery.clauses().get(0).getOccur(), BooleanClause.Occur.MUST);

        TermQuery innerQuery = (TermQuery) outerQuery.clauses().get(0).getQuery();
        assertEquals(innerQuery.getTerm().field(), fieldName);
        assertEquals(innerQuery.getTerm().text(), term);
    }

    @Test
    public void queryWithEscaping() throws ParseException {
        String fieldName = "field with space";
        String term = "wildcard*literal with space";

        testClause = new SearchClause(Negation.IS_NOT, fieldName, term, true);

        BooleanQuery outerQuery = (BooleanQuery) parser.parse(testClause.toQueryString());
        assertEquals(outerQuery.clauses().size(), 1);
        assertEquals(outerQuery.clauses().get(0).getOccur(), BooleanClause.Occur.MUST_NOT);

        TermQuery innerQuery = (TermQuery) outerQuery.clauses().get(0).getQuery();
        assertEquals(innerQuery.getTerm().field(), fieldName);
        assertEquals(innerQuery.getTerm().text(), term);
    }

    @Test
    public void empty() throws ParseException, QueryNotSupported {
        List<SearchClause> clauseList = SearchClause.parseQueryString("");
        assertTrue(clauseList.isEmpty());

        clauseList = new ArrayList<SearchClause>();
        String query = SearchClause.listToQuery(clauseList);

        assertTrue("Query is no empty, but: " + query, query.isEmpty());
    }

    @Test
    public void wildcardAsPrefix() throws ParseException {
        String fieldName = "field1";
        String prefix = "wildcard";
        String term = prefix + "*";

        testClause = new SearchClause(Negation.IS_NOT, fieldName, term, false);

        BooleanQuery outerQuery = (BooleanQuery) parser.parse(testClause.toQueryString());
        assertEquals(outerQuery.clauses().size(), 1);
        assertEquals(outerQuery.clauses().get(0).getOccur(), BooleanClause.Occur.MUST_NOT);

        PrefixQuery innerQuery = (PrefixQuery) outerQuery.clauses().get(0).getQuery();
        assertEquals(innerQuery.getField(), fieldName);
        assertEquals(innerQuery.getPrefix().field(), fieldName);
        assertEquals(innerQuery.getPrefix().text(), prefix);
    }

    @Test
    public void equalsMethod() {
        SearchClause clause1 = new SearchClause();
        SearchClause clause2 = new SearchClause();

        assertEquals(clause1, clause2);

        clause1 = new SearchClause(Negation.IS_NOT, "some field", "some term", true);
        clause2 = new SearchClause(Negation.IS_NOT, "some field", "some term", true);

        assertEquals(clause1, clause2);

        clause1.setFieldName("blubb");
        assertNotEquals(clause1, clause2);
    }

    @Test
    public void fromLuceneTerm() throws QueryNotSupported {
        final String fieldName = "testField";
        final String term = "testTerm";

        var luceneClause = new BooleanClause(new TermQuery(new Term(fieldName, term)), BooleanClause.Occur.MUST);

        var refClause = new SearchClause(Negation.IS, fieldName, term, true);
        var actualClause = SearchClause.fromLuceneClause(luceneClause);

        assertEquals(refClause, actualClause);
    }

    @Test
    public void fromLuceneRange() throws QueryNotSupported {
        final String fieldName = "testField";
        final String from = "*";
        final String to = "10";

        var range = new TermRangeQuery(fieldName, new BytesRef(from), new BytesRef(to), true, true);
        var luceneClause = new BooleanClause(range, BooleanClause.Occur.MUST);

        var refClause = new SearchClause(Negation.IS, fieldName, from, to, false);
        var actualClause = SearchClause.fromLuceneClause(luceneClause);

        assertEquals(refClause, actualClause);
    }

    @Test
    public void fromLucenePrefix() throws QueryNotSupported {
        final String fieldName = "testField";
        final String prefixStr = "something";
        final String wildcardStr = prefixStr + "*";

        var refClause = new SearchClause(Negation.IS, fieldName, wildcardStr, false);

        var prefixQ = new PrefixQuery(new Term(fieldName, prefixStr));
        var luceneClause = new BooleanClause(prefixQ, BooleanClause.Occur.MUST);
        var actualClause = SearchClause.fromLuceneClause(luceneClause);
        assertEquals(refClause, actualClause);

        var wildcardQ = new WildcardQuery(new Term(fieldName, wildcardStr));
        luceneClause = new BooleanClause(wildcardQ, BooleanClause.Occur.MUST);
        actualClause = SearchClause.fromLuceneClause(luceneClause);
        assertEquals(refClause, actualClause);

        refClause = new SearchClause(Negation.IS, fieldName, prefixStr + "*", true);
        assertNotEquals(refClause, actualClause);
    }
    @Test
    public void parsingErrors() throws Exception{
        assertThrows(ParseException.class,
        () -> {SearchClause.parseQueryString("+(field: something");});

        assertThrows(QueryNotSupported.class,
        () -> {SearchClause.parseQueryString("-(+field: something)");});

        assertThrows(QueryNotSupported.class,
        () -> {SearchClause.parseQueryString("#(field: something)");});

        assertThrows(QueryNotSupported.class,
            () -> {SearchClause.parseQueryString("(field: something) (field: something else)");});
    }

    @Test
    public void queryStringAndBack() throws ParseException, QueryNotSupported {
        var refList = new ArrayList<SearchClause>();
        var is = Negation.IS;
        refList.add(new SearchClause(is, "datefield", "2021-05-17 00:00:00.000", "2021-06-18 00:00:00.000", false));
        refList.add(new SearchClause(is, "field1", "MAJOR"));
        refList.add(new SearchClause(is, "field1", "MINOR"));
        refList.add(new SearchClause(is, "field2", "prod:*"));
        refList.add(new SearchClause(is, "field2", "test:*"));

        final String queryString = SearchClause.listToQuery(refList);

        var reconvertedList = SearchClause.parseQueryString(queryString);

        reconvertedList.sort(new Comparator<SearchClause>() {
            public int compare(SearchClause one, SearchClause two){
                int compVal = one.getFieldName().compareTo(two.getFieldName());
                if (compVal == 0) {
                    compVal = one.getTerm1().compareTo(two.getTerm1());
                }
                if (compVal == 0) {
                    compVal = one.getTerm2().compareTo(two.getTerm2());
                }
                return compVal;
            }
        });;

        assertEquals(refList.size(), reconvertedList.size());
        for (int i = 0; i < refList.size(); i++) {
            assertEquals(refList.get(i), reconvertedList.get(i));
        }
    }
}
