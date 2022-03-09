package apoc.schema;

import apoc.util.Neo4jContainerExtension;
import apoc.util.TestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.driver.v1.Session;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static apoc.util.TestContainerUtil.cleanBuild;
import static apoc.util.TestContainerUtil.createEnterpriseDB;
import static apoc.util.TestContainerUtil.executeGradleTasks;
import static apoc.util.TestContainerUtil.testResult;
import static apoc.util.TestUtil.isCI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * @author as
 * @since 12.02.19
 */
public class MetaEnterpriseFeaturesTest {

    private static Neo4jContainerExtension neo4jContainer;
    private static Session session;

    @BeforeClass
    public static void beforeAll() {
        assumeFalse(isCI());
        executeGradleTasks("clean", "shadow");
        TestUtil.ignoreException(() -> {
            // We build the project, the artifact will be placed into ./build/libs
            neo4jContainer = createEnterpriseDB(!TestUtil.isCI());
            neo4jContainer.start();
        }, Exception.class);
        assumeNotNull(neo4jContainer);
        assumeTrue(neo4jContainer.isRunning());
        session = neo4jContainer.getSession();
    }

    @AfterClass
    public static void afterAll() {
        if (neo4jContainer != null && neo4jContainer.isRunning()) {
            session.close();
            neo4jContainer.close();
        }
        cleanBuild();
    }

    public static boolean hasRecordMatching(List<Map<String,Object>> records, Map<String,Object> record) {
        return hasRecordMatching(records, row -> {
            boolean okSoFar = true;

            for(String k : record.keySet()) {
                okSoFar = okSoFar && row.containsKey(k) &&
                        (row.get(k) == null ?
                                (record.get(k) == null) :
                                row.get(k).equals(record.get(k)));
            }

            return okSoFar;
        });
    }

    public static boolean hasRecordMatching(List<Map<String,Object>> records, Predicate<Map<String,Object>> predicate) {
        return records.stream().filter(predicate).count() > 0;
    }

    public static List<Map<String,Object>> gatherRecords(Iterator<Map<String,Object>> r) {
        List<Map<String,Object>> rows = new ArrayList<>();
        while(r.hasNext()) {
            Map<String,Object> row = r.next();
            rows.add(row);
        }
        return rows;
    }

    @Test
    public void testNodeTypePropertiesBasic() {
        session.writeTransaction(tx -> {
            tx.run("CREATE (:Foo { l: 1, s: 'foo', d: datetime(), ll: ['a', 'b'], dl: [2.0, 3.0] });");
            tx.success();
            return null;
        });
        session.writeTransaction(tx -> {
            tx.run("CREATE CONSTRAINT ON (f:Foo) ASSERT EXISTS (f.s);");
            tx.success();
            return null;
        });

        testResult(session, "CALL apoc.meta.nodeTypeProperties();", (r) -> {
            List<Map<String,Object>> records = gatherRecords(r);
            assertEquals(true, hasRecordMatching(records, m ->
                        m.get("nodeType").equals(":`Foo`") &&
                                ((List)m.get("nodeLabels")).get(0).equals("Foo") &&
                                m.get("propertyName").equals("s") &&
                                m.get("mandatory").equals(true)));

                    assertEquals(true, hasRecordMatching(records, m ->
                            m.get("propertyName").equals("s") &&
                                    ((List)m.get("propertyTypes")).get(0).equals("String")));

                    assertEquals(true, hasRecordMatching(records, m ->
                        m.get("nodeType").equals(":`Foo`") &&
                                ((List)m.get("nodeLabels")).get(0).equals("Foo") &&
                                m.get("propertyName").equals("dl") &&
                                m.get("mandatory").equals(false)));
                    assertEquals(5, records.size());
        });
    }
}
