import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;
import org.neo4j.gis.spatial.SimplePointLayer;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.gis.spatial.osm.OSMImporter;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final Coordinate testOrigin = new Coordinate(13.0, 55.6);

    private static Coordinate[] makeCoordinateDataFromTextFile() throws IOException {
        CoordinateList data = new CoordinateList();
        try {
            BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/NEO4J-SPATIAL.txt"));
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                int col = 0;
                for (String character : line.split("")) {
                    if (col > 0 && !character.matches("\\s")) {
                        Coordinate coordinate = new Coordinate(Main.testOrigin.x + (double) col / 100.0, Main.testOrigin.y - (double) row / 100.0);
                        data.add(coordinate);
                    }
                    col++;
                }
                row++;
            }
        } catch (IOException e) {
            throw new IOException("Input data for string test invalid: " + e.getMessage());
        }
        return data.toCoordinateArray();
    }

    private static void FindingThingsCloseToOtherThings(File dbDir) throws IOException {
        // Initialize database
        File tmpDir = dbDir == null ? Files.createTempDirectory(null).toFile() : dbDir;
        GraphDatabaseService graph = new GraphDatabaseFactory().newEmbeddedDatabase(tmpDir);

        try (Transaction tx = graph.beginTx()) {
            SpatialDatabaseService db = new SpatialDatabaseService(graph);
            SimplePointLayer layer = db.createSimplePointLayer("neo-text");
            for (Coordinate coordinate :
                    makeCoordinateDataFromTextFile()) {
                layer.add(coordinate);
            }
            // Search for nearby locations
            Coordinate myPosition = new Coordinate(13.76, 55.56);
            List<GeoPipeFlow> results =
                    layer.findClosestPointsTo(myPosition, 10.0);
            System.out.println(results.size());
            Coordinate closest = results.get(0).getGeometry().getCoordinate();
            System.out.println(closest.x + " " + closest.y + " " + closest.z);
            tx.success();
        }
        graph.shutdown();
        tmpDir.deleteOnExit();
    }

    static final Map<String, String> LARGE_CONFIG = new HashMap<>();

    static {
        LARGE_CONFIG.put(GraphDatabaseSettings.pagecache_memory.name(), "100M");
        LARGE_CONFIG.put(GraphDatabaseSettings.batch_inserter_batch_size.name(), "2");
        LARGE_CONFIG.put(GraphDatabaseSettings.dump_configuration.name(), "true");
    }

    private static void LoadOsmToNeo4j(File dbDir) throws IOException, XMLStreamException {
        File tmpDir = dbDir == null ? Files.createTempDirectory(null).toFile() : dbDir;
        String dataset = "src/main/resources/copenhaggen.osm";
        OSMImporter importer = new OSMImporter(dataset);
        importer.setCharset(StandardCharsets.UTF_8);

        GraphDatabaseService graph = new GraphDatabaseFactory().newEmbeddedDatabase(tmpDir);
        graph.shutdown();
        BatchInserter batchInserter = BatchInserters.inserter(tmpDir, LARGE_CONFIG);
        importer.importFile(batchInserter, dataset, false);
        batchInserter.shutdown();
        graph = new GraphDatabaseFactory().newEmbeddedDatabase(tmpDir);
        importer.reIndex(graph);
        graph.shutdown();
        tmpDir.deleteOnExit();
    }

    public static void main(String[] argv) {
        try {
            FindingThingsCloseToOtherThings(null);
            LoadOsmToNeo4j(new File("C:\\Users\\elkai\\Desktop\\xx"));
        } catch (IOException | XMLStreamException e) {
            e.printStackTrace();
        }
    }
}
