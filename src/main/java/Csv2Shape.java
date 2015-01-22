import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by thk22 on 20/01/2015.
 */
public class Csv2Shape {
	public static void main(String[] args) throws Exception {
		File file = JFileDataStoreChooser.showOpenFile("csv", null);
		if (file == null) {
			return;
		}

		// Read in a FeatureCollection (CSV)
		final SimpleFeatureType TYPE = DataUtilities.createType("Location", "location:Point:srid=4326,name:String,number:Integer");

		DefaultFeatureCollection collection = new DefaultFeatureCollection("flam_cheltuk", TYPE);
		GeometryFactory geoFactory = JTSFactoryFinder.getGeometryFactory(null);

		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

		BufferedReader reader = new BufferedReader(new FileReader(file));
		try {
            /* First line of the data file is the header */
			String line = reader.readLine();
			System.out.println("Header: " + line);

			for (line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.trim().length() > 0) { // skip blank lines
					String tokens[] = line.split("\\,");

					double latitude = Double.parseDouble(tokens[0]);
					double longitude = Double.parseDouble(tokens[1]);
					String name = tokens[2].trim();
					int number = Integer.parseInt(tokens[3].trim());

					Point point = geoFactory.createPoint(new Coordinate(longitude, latitude));

					featureBuilder.add(point);
					featureBuilder.add(name);
					featureBuilder.add(number);

					SimpleFeature feature = featureBuilder.buildFeature(null);
					collection.add(feature);
				}
			}
		} finally {
			reader.close();
		}

		// Create shapefile from feature collection
		File newFile = getNewShapeFile(file);

		ShapefileDataStoreFactory sdsFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<String, Serializable>();
		params.put("url", newFile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore ds = (ShapefileDataStore)sdsFactory.createNewDataStore(params);
		ds.forceSchemaCRS(DefaultGeographicCRS.WGS84);

		// Write features to shapefile
		Transaction transaction = new DefaultTransaction("create");

		String typeName = ds.getTypeNames()[0];
		SimpleFeatureSource featureSource = ds.getFeatureSource(typeName);

		if (featureSource instanceof SimpleFeatureStore) {
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

			featureStore.setTransaction(transaction);
			try {
				featureStore.addFeatures(collection);
				transaction.commit();

			} catch (Exception problem) {
				problem.printStackTrace();
				transaction.rollback();

			} finally {
				transaction.close();
			}
			System.exit(0); // success!
		} else {
			System.out.println(typeName + " does not support read/write access");
			System.exit(1);
		}
	}

	private static File getNewShapeFile(File csvFile) {
		String path = csvFile.getAbsolutePath();
		String newPath = path.substring(0, path.length() - 4) + ".shp";

		JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
		chooser.setDialogTitle("Save shapefile");
		chooser.setSelectedFile(new File(newPath));

		int returnVal = chooser.showSaveDialog(null);

		if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
			// the user cancelled the dialog
			System.exit(0);
		}

		File newFile = chooser.getSelectedFile();
		if (newFile.equals(csvFile)) {
			System.out.println("Error: cannot replace " + csvFile);
			System.exit(0);
		}

		return newFile;
	}

}
