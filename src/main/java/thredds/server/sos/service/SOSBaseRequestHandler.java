package thredds.server.sos.service;

import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Formatter;
import org.w3c.dom.Document;
import thredds.server.sos.util.DiscreteSamplingGeometryUtil;
import thredds.server.sos.util.XMLDomUtils;
import ucar.nc2.Variable;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.ft.FeatureCollection;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;
import ucar.nc2.ft.ProfileFeatureCollection;
import ucar.nc2.ft.StationProfileFeatureCollection;
import ucar.nc2.ft.StationTimeSeriesFeatureCollection;
import ucar.nc2.ft.grid.Grid;
import ucar.nc2.ft.point.standard.StandardProfileCollectionImpl;
import ucar.nc2.units.DateFormatter;

/**
 *
 * @author tkunicki
 */
public abstract class SOSBaseRequestHandler {

    private static final String STATION_GML_BASE = "urn:tds:station.sos:";
    private final NetcdfDataset netCDFDataset;
    private FeatureDataset featureDataset;
    @Deprecated
    private StationTimeSeriesFeatureCollection featureCollection;
    @Deprecated
    private StationProfileFeatureCollection featureCollectionProfileFeature;
    @Deprecated
    private ProfileFeatureCollection ProfileFeatureCollection;
    private String title;
    private String history;
    private String institution;
    private String source;
    private String description;
    private String featureOfInterestBaseQueryURL;
    protected Document document;
    private final static NumberFormat FORMAT_DEGREE;
    private final FeatureCollection CDMPointFeatureCollection;
    private GridDataset gridDataSet = null;

    static {
        FORMAT_DEGREE = NumberFormat.getNumberInstance();
        FORMAT_DEGREE.setMinimumFractionDigits(1);
        FORMAT_DEGREE.setMaximumFractionDigits(14);
    }
    private FeatureType dataFeatureType;

    public SOSBaseRequestHandler(NetcdfDataset netCDFDataset) throws IOException {
        this.netCDFDataset = netCDFDataset;

        featureDataset = FeatureDatasetFactoryManager.wrap(FeatureType.ANY, netCDFDataset, null, new Formatter(System.err));
        //change multi to single featureCollectionType - allowing for single variable - switch on feature Type
        //keep remove others

        featureCollection = DiscreteSamplingGeometryUtil.extractStationTimeSeriesFeatureCollection(featureDataset);

        featureCollectionProfileFeature = DiscreteSamplingGeometryUtil.extractStationProfileFeatureCollection(featureDataset);

        ProfileFeatureCollection = DiscreteSamplingGeometryUtil.extractStdProfileCollection(featureDataset);

        //try and get dataset
        CDMPointFeatureCollection = DiscreteSamplingGeometryUtil.extractFeatureDatasetCollection(featureDataset);

        //if its null try using grid?
        if (CDMPointFeatureCollection == null) {
            gridDataSet = DiscreteSamplingGeometryUtil.extractGridDatasetCollection(featureDataset);
            if (gridDataSet != null) {
                dataFeatureType = FeatureType.GRID;
            }
        } else {
            dataFeatureType = CDMPointFeatureCollection.getCollectionFeatureType();
        }

        parseGlobalAttributes();
        document = parseTemplateXML();
    }

    private void parseGlobalAttributes() {

        title = netCDFDataset.findAttValueIgnoreCase(null, "title", "Empty Title");
        history = netCDFDataset.findAttValueIgnoreCase(null, "history", "Empty History");
        institution = netCDFDataset.findAttValueIgnoreCase(null, "institution", "Empty Insitution");
        source = netCDFDataset.findAttValueIgnoreCase(null, "source", "Empty Source");
        description = netCDFDataset.findAttValueIgnoreCase(null, "description", "Empty Description");
        featureOfInterestBaseQueryURL = netCDFDataset.findAttValueIgnoreCase(null, "featureOfInterestBaseQueryURL", null);
    }

    public abstract String getTemplateLocation();

    protected final Document parseTemplateXML() {
        InputStream templateInputStream = null;
        try {
            templateInputStream = getClass().getClassLoader().getResourceAsStream(getTemplateLocation());
            return XMLDomUtils.getTemplateDom(templateInputStream);
        } finally {
            if (templateInputStream != null) {
                try {
                    templateInputStream.close();
                } catch (IOException e) {
                    // ignore, closing..
                }
            }
        }
    }

    public final Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public NetcdfDataset getNetCDFDataset() {
        return netCDFDataset;
    }

    public FeatureDataset getFeatureDataset() {
        return featureDataset;
    }

    public GridDataset getGridDataset() {
        return gridDataSet;
    }

    @Deprecated
    public StationTimeSeriesFeatureCollection getFeatureCollection() {
        return featureCollection;
    }

    @Deprecated
    public StationProfileFeatureCollection getFeatureProfileCollection() {
        return featureCollectionProfileFeature;
    }

    @Deprecated
    public ProfileFeatureCollection getProfileFeatureCollection() {
        return ProfileFeatureCollection;
    }

    /**
     * Gets the dataset currently in use
     * @return feature collection dataset
     */
    public FeatureCollection getFeatureTypeDataSet() {
        return CDMPointFeatureCollection;
    }

    /**
     * gets the feature type of the dataset in question
     * @return feature type of dataset
     */
    public FeatureType getDatasetFeatureType() {
        return dataFeatureType;
    }

    public String getTitle() {
        return title;
    }

    public String getHistory() {
        return history;
    }

    public String getInstitution() {
        return institution;
    }

    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }

    public String getGMLID(String stationName) {
        return stationName;
    }

    public String getGMLNameBase() {
        return STATION_GML_BASE;
    }

    public String getGMLName(String stationName) {
        return STATION_GML_BASE + stationName;
    }

    public String getLocation() {
        return netCDFDataset.getLocation();
    }

    public String getFeatureOfInterestBase() {
        if (featureOfInterestBaseQueryURL == null || featureOfInterestBaseQueryURL.contentEquals("null")) {
            return "";
        }
        return featureOfInterestBaseQueryURL;
    }

    public String getFeatureOfInterest(String stationName) {
        return featureOfInterestBaseQueryURL == null
                ? stationName
                : featureOfInterestBaseQueryURL + stationName;
    }

    public static String formatDegree(double degree) {
        return FORMAT_DEGREE.format(degree);
    }

    public static String formatDateTimeISO(Date date) {
        // assuming not thread safe...  true?
        return (new DateFormatter()).toDateTimeStringISO(date);
    }

    public void finished() {
        //added abird   
        if (featureCollection != null) {
            try {
                featureCollection.finish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //try { featureCollection.finish(); } catch (Exception e) { e.printStackTrace(); }
        try {
            featureDataset.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            netCDFDataset.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //tidy up set vars
        featureCollectionProfileFeature = null;
        ProfileFeatureCollection = null;
        title = null;
        history = null;;
        institution = null;;
        source = null;
        description = null;
        featureOfInterestBaseQueryURL = null;
        document = null;


    }
}
