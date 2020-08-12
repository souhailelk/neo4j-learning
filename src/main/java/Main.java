import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;
import org.neo4j.gis.spatial.SimplePointLayer;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class Main {
    private static final Coordinate testOrigin = new Coordinate(13.0, 55.6);

    private static Coordinate[] makeCoordinateDataFromTextFile() throws IOException {
        CoordinateList data = new CoordinateList();
        try {
            BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/" + "NEO4J-SPATIAL.txt"));
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

    private static void FindingThingsCloseToOtherThings() throws IOException {
        // Initialize database
        File tmpDir = Files.createTempDirectory(null).toFile();
        GraphDatabaseService graph = new GraphDatabaseFactory().newEmbeddedDatabase(tmpDir);

        try(Transaction tx = graph.beginTx()) {
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

    public static void main(String[] argv) {
        try {
            FindingThingsCloseToOtherThings();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
