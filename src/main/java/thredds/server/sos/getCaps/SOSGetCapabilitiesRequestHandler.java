package thredds.server.sos.getCaps;

import java.io.IOException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.DOMException;
import thredds.server.sos.getObs.ObservationOffering;
import thredds.server.sos.getObs.SOSObservationOffering;
import thredds.server.sos.util.DiscreteSamplingGeometryUtil;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ft.ProfileFeature;
import ucar.nc2.ft.ProfileFeatureCollection;
import ucar.nc2.ft.StationProfileFeature;
import ucar.nc2.ft.StationProfileFeatureCollection;
import ucar.nc2.ft.StationTimeSeriesFeature;
import ucar.nc2.ft.StationTimeSeriesFeatureCollection;
import ucar.unidata.geoloc.Station;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.joda.time.Chronology;
import org.joda.time.chrono.ISOChronology;
import thredds.server.sos.CDMClasses.Grid;
import thredds.server.sos.CDMClasses.Profile;
import thredds.server.sos.CDMClasses.TimeSeries;
import thredds.server.sos.CDMClasses.TimeSeriesProfile;
import thredds.server.sos.CDMClasses.Trajectory;
import thredds.server.sos.service.SOSBaseRequestHandler;
import thredds.server.sos.service.StationData;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.units.DateFormatter;

/**
 *
 * @author Abird
 */
public class SOSGetCapabilitiesRequestHandler extends SOSBaseRequestHandler {

    private final static String TEMPLATE = "templates/sosGetCapabilities.xml";
    private final String threddsURI;
    private final String format = "text/xml; subtype=\"om/1.0.0\"";
    Chronology chrono = ISOChronology.getInstance();
    DateFormatter dateFormatter = new DateFormatter();

    public SOSGetCapabilitiesRequestHandler(NetcdfDataset netCDFDataset, String threddsURI) throws IOException {
        super(netCDFDataset);
        this.threddsURI = threddsURI;
    }

    @Override
    public String getTemplateLocation() {
        return TEMPLATE;
    }

    public void parseServiceIdentification() {
        NodeList nodeLst = document.getElementsByTagName("ows:ServiceIdentification");

        for (int s = 0; s < nodeLst.getLength(); s++) {

            Node fstNode = nodeLst.item(s);
            if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

                //looks at the one node
                Element fstElmnt = (Element) fstNode;
                //looks at title
                NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("ows:Title");
                Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
                NodeList fstNm = fstNmElmnt.getChildNodes();
                fstNm.item(0).setNodeValue(getTitle());

                //looks at the adstract
                NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("ows:Abstract");
                Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
                NodeList lstNm = lstNmElmnt.getChildNodes();
                if (getHistory() == null) {
                    lstNm.item(0).setNodeValue("");
                } else {
                    lstNm.item(0).setNodeValue(getHistory());
                }
            }

        }
    }

    private NodeList getXMLNode(Element fstElmnt, String xmlLocation) {
        NodeList tagNameNodeList = fstElmnt.getElementsByTagName(xmlLocation);
        Element fstNmElmnt1 = (Element) tagNameNodeList.item(0);
        NodeList fstNm1 = fstNmElmnt1.getChildNodes();
        return fstNm1;
    }

    private void checkEndDateElementNode(ObservationOffering offering, Element obsOfferingTimeEndEl) throws DOMException {
        //check the string to see if it either needs attribute of element
        if ((offering.getObservationTimeEnd().isEmpty()) || (offering.getObservationTimeEnd().length() < 2) || (offering.getObservationTimeEnd().contentEquals(""))) {
            obsOfferingTimeEndEl.setAttribute("indeterminatePosition", "unknown");
        } else {
            obsOfferingTimeEndEl.appendChild(document.createTextNode(offering.getObservationTimeEnd()));
        }
    }

    @Deprecated
    private void ifProfileCollection(ProfileFeatureCollection profileCollection, String profileID, List<String> observedPropertyList) throws IOException {
        PointFeatureIterator pp = null;
        //profiles act like stations at present
        while (profileCollection.hasNext()) {
            ProfileFeature pFeature = profileCollection.next();

            //scan through the data and get the profile id number
            pp = pFeature.getPointFeatureIterator(-1);
            while (pp.hasNext()) {
                PointFeature pointFeature = pp.next();
                profileID = StationData.getProfileIDFromProfile(pointFeature);
                //System.out.println(profileID);
                break;
            }

            //attributes
            SOSObservationOffering newOffering = new SOSObservationOffering();

            newOffering.setObservationStationLowerCorner(Double.toString(pFeature.getLatLon().getLatitude()), Double.toString(pFeature.getLatLon().getLongitude()));
            newOffering.setObservationStationUpperCorner(Double.toString(pFeature.getLatLon().getLatitude()), Double.toString(pFeature.getLatLon().getLongitude()));

            pFeature.calcBounds();

            //check the data
            if (pFeature.getDateRange() != null) {
                newOffering.setObservationTimeBegin(pFeature.getDateRange().getStart().toDateTimeStringISO());
                newOffering.setObservationTimeEnd(pFeature.getDateRange().getEnd().toDateTimeStringISO());
            } //find the dates out!
            else {
                System.out.println("no dates yet");
            }


            newOffering.setObservationStationDescription(pFeature.getCollectionFeatureType().toString());
            if (profileID != null) {
                newOffering.setObservationStationID("PROFILE_" + profileID);
                newOffering.setObservationProcedureLink(getGMLName("PROFILE_" + profileID));
                newOffering.setObservationName(getGMLName(profileID));
                newOffering.setObservationFeatureOfInterest(getFeatureOfInterest("PROFILE_" + profileID));
            } else {
                newOffering.setObservationFeatureOfInterest(getFeatureOfInterest(pFeature.getName()));
                newOffering.setObservationStationID(getGMLID(pFeature.getName()));
                newOffering.setObservationProcedureLink(getGMLName((pFeature.getName())));
                newOffering.setObservationFeatureOfInterest(getFeatureOfInterest(pFeature.getName()));
            }
            newOffering.setObservationSrsName("EPSG:4326");  // TODO?  
            newOffering.setObservationObserveredList(observedPropertyList);
            newOffering.setObservationFormat(format);
            addObsOfferingToDoc(newOffering);
        }

        if (profileCollection.isMultipleNested() == false) {
            System.out.println("not nested");
        } else {
            System.out.println("nested");
        }
    }

    @Deprecated
    private void ifTimeSeriesFeatureCollection(StationTimeSeriesFeatureCollection featureCollection, List<String> observedPropertyList) throws IOException {
        String stationName = null;
        String stationLat = null;
        String stationLon = null;
        SOSObservationOffering newOffering = null;
        StationTimeSeriesFeature feature = null;

        List<Station> stationList = featureCollection.getStations();
        for (int i = 0; i < stationList.size(); i++) {
            feature = featureCollection.getStationFeature(stationList.get(i));
            stationName = stationList.get(i).getName();
            stationLat = formatDegree(stationList.get(i).getLatitude());
            stationLon = formatDegree(stationList.get(i).getLongitude());
            newOffering = new SOSObservationOffering();
            newOffering.setObservationStationID(getGMLID(stationName));
            newOffering.setObservationStationLowerCorner(stationLat, stationLon);
            newOffering.setObservationStationUpperCorner(stationLat, stationLon);

            // Code that causes slow issues
            /*
            if (stationList.size() < 75) {
            feature.calcBounds();
            newOffering.setObservationTimeBegin(feature.getDateRange().getStart().toDateTimeStringISO());
            newOffering.setObservationTimeEnd(feature.getDateRange().getEnd().toDateTimeStringISO());
            }
             * 
             */

            try {
                feature.calcBounds();
                newOffering.setObservationTimeBegin(feature.getDateRange().getStart().toDateTimeStringISO());
                newOffering.setObservationTimeEnd(feature.getDateRange().getEnd().toDateTimeStringISO());
            } catch (Exception e) {
            }



            //END of slow issue code

            newOffering.setObservationStationDescription(feature.getDescription());
            newOffering.setObservationName(getGMLName((stationName)));
            newOffering.setObservationSrsName("EPSG:4326");  // TODO? 
            newOffering.setObservationProcedureLink(getGMLName((stationName)));
            newOffering.setObservationObserveredList(observedPropertyList);
            newOffering.setObservationFeatureOfInterest(getFeatureOfInterest(stationName));
            newOffering.setObservationFormat(format);
            addObsOfferingToDoc(newOffering);
        }
    }

    @Deprecated
    private void ifTimeSeriesProfileCollection(StationProfileFeatureCollection featureCollection1, List<String> observedPropertyList) throws IOException {
        StationProfileFeature stationProfileFeature = null;
        SOSObservationOffering newOffering = null;
        DateFormatter timePeriodFormatter = new DateFormatter();
        for (Station station : featureCollection1.getStations()) {
            String stationName = station.getName();
            String stationLat = formatDegree(station.getLatitude());
            String stationLon = formatDegree(station.getLongitude());
            newOffering = new SOSObservationOffering();
            newOffering.setObservationStationID(getGMLID(stationName));
            newOffering.setObservationStationLowerCorner(stationLat, stationLon);
            newOffering.setObservationStationUpperCorner(stationLat, stationLon);
            StationProfileFeature feature = featureCollection1.getStationProfileFeature(station);
            //feature.calcBounds();
            stationProfileFeature = getFeatureProfileCollection().getStationProfileFeature(station);
            List<Date> times = stationProfileFeature.getTimes();
            newOffering.setObservationTimeBegin(timePeriodFormatter.toDateTimeStringISO(times.get(0)));
            newOffering.setObservationTimeEnd(timePeriodFormatter.toDateTimeStringISO(times.get(times.size() - 1)));
            newOffering.setObservationStationDescription(feature.getDescription());
            newOffering.setObservationName(getGMLName((stationName)));
            newOffering.setObservationSrsName("EPSG:4326");
            newOffering.setObservationProcedureLink(getGMLName((stationName)));
            newOffering.setObservationObserveredList(observedPropertyList);
            newOffering.setObservationFeatureOfInterest(getFeatureOfInterest(stationName));
            newOffering.setObservationFormat(format);
            addObsOfferingToDoc(newOffering);
        }
    }

    private void setProviderName(Element fstElmnt, String xmlLocation) throws DOMException {
        //get the node named be the string
        NodeList fstNm1 = getXMLNode(fstElmnt, xmlLocation);
        if (getSource() == null) {
            fstNm1.item(0).setNodeValue("");
        } else {
            fstNm1.item(0).setNodeValue(getSource());
        }
    }

    private void setProviderSite(Element fstElmnt, String xmlLocation) throws DOMException {
        NodeList fstNm1 = getXMLNode(fstElmnt, xmlLocation);
        if (getInstitution() == null) {
            fstNm1.item(0).setNodeValue("");
        } else {
            fstNm1.item(0).setNodeValue(getInstitution());
        }
    }

    //
    //TODO ADD THESE TO MOCK GET CAPS RESULT!!!!!!
    //
    public String getInvividualNameSP() {
        return "";
    }

    public String getPositionNameSP() {
        return "";
    }

    public String getPhoneNoSP() {
        return "";
    }

    public String getHTTPGetAddress() {
        return threddsURI;
    }

    //
    //TODO ADD THESE TO MOCK GET CAPS RESULT!!!!!!
    //
    public void parseServiceDescription() {
        //get service provider node list
        NodeList serviceProviderNodeList = document.getElementsByTagName("ows:ServiceProvider");
        //get the first node in the the list matching the above name
        Node fstNode = serviceProviderNodeList.item(0);
        //create an element from the node
        Element fstElmnt = (Element) fstNode;

        setProviderName(fstElmnt, "ows:ProviderName");
        setProviderSite(fstElmnt, "ows:ProviderSite");
        setServiceContractName(fstElmnt, "ows:IndividualName");
        setServiceContractPositionName(fstElmnt, "ows:PositionName");
        setServiceContractPhoneNumber(fstElmnt, "ows:Voice");

    }

    private void setServiceContractName(Element fstElmnt, String xmlLocation) {
        NodeList fstNm1 = getXMLNode(fstElmnt, xmlLocation);
        fstNm1.item(0).setNodeValue(getInvividualNameSP());
    }

    private void setServiceContractPositionName(Element fstElmnt, String xmlLocation) {
        NodeList fstNm1 = getXMLNode(fstElmnt, xmlLocation);
        fstNm1.item(0).setNodeValue(getPositionNameSP());
    }

    private void setServiceContractPhoneNumber(Element fstElmnt, String xmlLocation) {
        NodeList fstNm1 = getXMLNode(fstElmnt, xmlLocation);
        fstNm1.item(0).setNodeValue(getPhoneNoSP());
    }

    public void parseOperationsMetaData() {
        //get operations meta data
        NodeList operationsNodeList = document.getElementsByTagName("ows:OperationsMetadata");
        //set get capabilities meta data
        for (int s = 0; s < operationsNodeList.getLength(); s++) {

            Node fstNode = operationsNodeList.item(s);
            if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

                //looks at the one node
                Element fstElmnt = (Element) fstNode;
                //looks at title
                NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("ows:Operation");

                for (int jj = 0; jj < fstNmElmntLst.getLength(); jj++) {

                    Element fstNmElmnt = (Element) fstNmElmntLst.item(jj);
                    //String c = fstNmElmnt.getAttribute("name");
                    //System.out.println("Name: "  + fstNmElmnt.getAttribute("name"));

                    if (fstNmElmnt.getAttribute("name").contentEquals("GetCapabilities")) {
                        setGetCapabilitiesOperationsMetaData(fstNmElmnt);
                    } else if (fstNmElmnt.getAttribute("name").contentEquals("GetObservation")) {
                        setGetCapabilitiesOperationsMetaData(fstNmElmnt);
                    } else if (fstNmElmnt.getAttribute("name").contentEquals("DescribeSensor")) {
                        setGetCapabilitiesOperationsMetaData(fstNmElmnt);
                    }
                }
            }

        }
    }

    public void setGetCapabilitiesOperationsMetaData(Element fstNmElmnt) {
        //set get capabilities GET request link
        NodeList fstNm1 = fstNmElmnt.getElementsByTagName("ows:Get");
        Element fstNmElmnt1 = (Element) fstNm1.item(0);
        fstNmElmnt1.setAttribute("xlink:href", threddsURI);

        //set get capabilities Post request link
        NodeList fstNm12 = fstNmElmnt.getElementsByTagName("ows:Post");
        Element fstNmElmnt12 = (Element) fstNm12.item(0);
        fstNmElmnt12.setAttribute("xlink:href", threddsURI);
    }

    /**
     * parses the observation list object and add the observations to the node
     */
    public void parseObservationList() throws IOException {
        List<VariableSimpleIF> variableList = null;
        List<String> observedPropertyList = null;

        if (getDatasetFeatureType() != FeatureType.GRID) {
            variableList = DiscreteSamplingGeometryUtil.getDataVariables(getFeatureDataset());
            observedPropertyList = new ArrayList<String>(variableList.size());
            List<String> observedPropertyUnitList = new ArrayList<String>(variableList.size());

            for (VariableSimpleIF variable : variableList) {
                observedPropertyList.add(variable.getShortName()); // TODO ? getName() instead?
                observedPropertyUnitList.add(variable.getUnitsString());
            }
        }


        /*
        //if the stationTimeSeriesFeature is null
        StationTimeSeriesFeatureCollection featureCollection = getFeatureCollection();
        //***************************************        
        //PROFILE
        //profiles differ depending on type
        ProfileFeatureCollection profileCollection = getProfileFeatureCollection();
        String profileID = null;

        if (profileCollection != null) {
            //ifProfileCollection(profileCollection, profileID, observedPropertyList);
        }
        //***************************************
        //added abird
        //TIMESERIESPROFILE
        StationProfileFeatureCollection featureCollection1 = getFeatureProfileCollection();

        if (featureCollection1 != null) {
            //ifTimeSeriesProfileCollection(featureCollection1, observedPropertyList);
        }

        //***************************************
        //TIMESERIES
        if (featureCollection != null) {
            //ifTimeSeriesFeatureCollection(featureCollection, observedPropertyList);
        }
         * 
         */

        //***************************************
            // use CDM to get, getCaps;
        if (getDatasetFeatureType() == FeatureType.TRAJECTORY) {
            try {
                this.document = Trajectory.getCapsResponse(getFeatureTypeDataSet(), getDocument(), getFeatureOfInterestBase(), getGMLNameBase(), format, observedPropertyList);
            } catch (Exception e) {
            }
            
        } else if (getDatasetFeatureType() == FeatureType.STATION) {            
            this.document = TimeSeries.getCapsResponse((StationTimeSeriesFeatureCollection)getFeatureTypeDataSet(),getDocument(),getFeatureOfInterestBase(),getGMLNameBase(),format,observedPropertyList);
            
        } else if (getDatasetFeatureType() == FeatureType.STATION_PROFILE) {           
            this.document = TimeSeriesProfile.getCapsResponse((StationProfileFeatureCollection)getFeatureTypeDataSet(),getDocument(),getFeatureOfInterestBase(),getGMLNameBase(),format,observedPropertyList);
            
        } else if (getDatasetFeatureType() == FeatureType.PROFILE) {            
            this.document = Profile.getCapsResponse((ProfileFeatureCollection)getFeatureTypeDataSet(),getDocument(),getFeatureOfInterestBase(),getGMLNameBase(),format,observedPropertyList);
            
            
        } else if (getDatasetFeatureType() == FeatureType.GRID) {            
            this.document = Grid.getCapsResponse(getGridDataset(), getDocument(), getGMLNameBase(), format);
        }



    }

    public void addObsOfferingToDoc(ObservationOffering offering) {

        NodeList obsOfferingList = document.getElementsByTagName("ObservationOfferingList");
        Element obsOfferEl = (Element) obsOfferingList.item(0);
        obsOfferEl.appendChild(constructObsOfferingNodes(offering));
        offering = null;
    }

    public Element constructObsOfferingNodes(ObservationOffering offering) {
        //Create the observation offering
        Element obsOfferingEl = document.createElement("ObservationOffering");
        //add the station ID to the created element
        obsOfferingEl.setAttribute("gml:id", offering.getObservationStationID());

        //create the description and add the offering info
        Element obsOfferingDescripEl = document.createElement("gml:description");
        obsOfferingDescripEl.appendChild(document.createTextNode(offering.getObservationStationDescription()));

        //create the obs name and add it to the element
        Element obsOfferingNameEl = document.createElement("gml:name");
        obsOfferingNameEl.appendChild(document.createTextNode(offering.getObservationName()));

        //create the source name el and add data
        Element obsOfferingSrsNameEl = document.createElement("gml:srsName");
        obsOfferingSrsNameEl.appendChild(document.createTextNode(offering.getObservationSrsName()));

        //create bounded area node
        Element obsOfferingBoundedByEl = document.createElement("gml:boundedBy");
        // create the envelope node and add attribute srs name
        Element obsOfferingEnvelopeEl = document.createElement("gml:Envelope");
        obsOfferingEnvelopeEl.setAttribute("srsName", offering.getObservationSrsName());
        //create the lower coner node
        Element obsOfferinglowerCornerEl = document.createElement("gml:lowerCorner");
        obsOfferinglowerCornerEl.appendChild(document.createTextNode(offering.getObservationStationLowerCorner()));
        //create the upper corner node
        Element obsOfferingUpperCornerEl = document.createElement("gml:upperCorner");
        obsOfferingUpperCornerEl.appendChild(document.createTextNode(offering.getObservationStationUpperCorner()));

        //add the upper and lower to the envelope node
        obsOfferingEnvelopeEl.appendChild(obsOfferinglowerCornerEl);
        obsOfferingEnvelopeEl.appendChild(obsOfferingUpperCornerEl);
        //add the envelope node to the bounded by node
        obsOfferingBoundedByEl.appendChild(obsOfferingEnvelopeEl);

        //create time node
        Element obsOfferingTimeEl = document.createElement("time");
        //create time period node
        Element obsOfferingTimePeriodEl = document.createElement("gml:TimePeriod");
        //create begin position node
        Element obsOfferingTimeBeginEl = document.createElement("gml:beginPosition");
        obsOfferingTimeBeginEl.appendChild(document.createTextNode(offering.getObservationTimeBegin()));
        //create end position node
        Element obsOfferingTimeEndEl = document.createElement("gml:endPosition");
        checkEndDateElementNode(offering, obsOfferingTimeEndEl);

        //add time begin to time period
        obsOfferingTimePeriodEl.appendChild(obsOfferingTimeBeginEl);
        //add time end to time period
        obsOfferingTimePeriodEl.appendChild(obsOfferingTimeEndEl);
        //add time period to time
        obsOfferingTimeEl.appendChild(obsOfferingTimePeriodEl);

        //create procedure node and add element
        Element obsOfferingProcedureEl = document.createElement("procedure");
        obsOfferingProcedureEl.setAttribute("xlink:href", offering.getObservationProcedureLink());

        //create feature of interest node and add element
        Element obsOfferingFeatureOfInterestEl = document.createElement("featureOfInterest");
        obsOfferingFeatureOfInterestEl.setAttribute("xlink:href", offering.getObservationFeatureOfInterest());

        //create response format
        Element obsOfferingFormatEl = document.createElement("responseFormat");
        obsOfferingFormatEl.appendChild(document.createTextNode(offering.getObservationFormat()));

        //create response model
        Element obsOfferingModelEl = document.createElement("responseModel");
        obsOfferingModelEl.appendChild(document.createTextNode(offering.getObservationModel()));

        //create response model
        Element obsOfferingModeEl = document.createElement("responseMode");
        obsOfferingModeEl.appendChild(document.createTextNode(offering.getObservationResponseMode()));

        //add the new elements to the XML doc
        obsOfferingEl.appendChild(obsOfferingDescripEl);
        obsOfferingEl.appendChild(obsOfferingNameEl);
        obsOfferingEl.appendChild(obsOfferingSrsNameEl);
        obsOfferingEl.appendChild(obsOfferingBoundedByEl);
        obsOfferingEl.appendChild(obsOfferingTimeEl);
        obsOfferingEl.appendChild(obsOfferingProcedureEl);

        //create obs property node and add element
        for (int i = 0; i < offering.getObservationObserveredList().size(); i++) {
            Element obsOfferingObsPropertyEll = document.createElement("observedProperty");
            obsOfferingObsPropertyEll.setAttribute("xlink:href", (String) offering.getObservationObserveredList().get(i));
            obsOfferingEl.appendChild(obsOfferingObsPropertyEll);
        }

        obsOfferingEl.appendChild(obsOfferingFeatureOfInterestEl);
        obsOfferingEl.appendChild(obsOfferingFormatEl);
        obsOfferingEl.appendChild(obsOfferingModelEl);
        obsOfferingEl.appendChild(obsOfferingModeEl);
        return obsOfferingEl;
    }
}
