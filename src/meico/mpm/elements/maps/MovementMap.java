package meico.mpm.elements.maps;

import meico.mei.Helper;
import meico.mpm.Mpm;
import meico.mpm.elements.maps.data.MovementData;
import meico.supplementary.KeyValue;
import nu.xom.Attribute;
import nu.xom.Element;

import java.util.ArrayList;

/**
 * This class interfaces MPM's movementMaps
 * @author Niels Pfeffer
 */
public class MovementMap extends GenericMap {
    /**
     * constructor, generates an empty movementMap
     * @throws Exception
     */
    private MovementMap() throws Exception {
        super("movementMap");
    }

    /**
     * constructor, generates an instance from xml code
     * @param xml
     * @throws Exception
     */
    private MovementMap(Element xml) throws Exception {
        super(xml);
    }

    /**
     * MovementMap factory
     * @return
     */
    public static MovementMap createMovementMap() {
        MovementMap d;
        try {
            d = new MovementMap();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return d;
    }

    /**
     * MovementMap factory
     * @param xml
     * @return
     */
    public static MovementMap createMovementMap(Element xml) {
        MovementMap d;
        try {
            d = new MovementMap(xml);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return d;
    }

    /**
     * set the data of this object, this parses the xml element and generates the according data structure
     * @param xml
     */
    protected void parseData(Element xml) throws Exception {
        super.parseData(xml);
        this.setType("movementMap");            // make sure this is really a "movementMap"
    }

    /**
     * add a movement element to the movementMap
     * @param date
     * @param volume a numeric or literal string, the latter should have a corresponding movementDef in the movementStyles/styleDef
     * @param transitionTo a numeric or literal string, the latter should have a corresponding movementDef in the movementStyles/styleDef
     * @param curvature
     * @param protraction
     * @param subNoteMovement
     * @param id
     * @return the index at which it has been inserted
     */
    public int addMovement(double date, String controller, double position, double transitionTo, String id) {
        Element e = new Element("movement", Mpm.MPM_NAMESPACE);
        e.addAttribute(new Attribute("date", Double.toString(date)));
        e.addAttribute(new Attribute("position", Double.toString(position)));
        e.addAttribute(new Attribute("transition.to", Double.toString(transitionTo)));
        e.addAttribute(new Attribute("controller", controller));
        e.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", id));

        KeyValue<Double, Element> kv = new KeyValue<>(date, e);
        return this.insertElement(kv, false);
    }

    /**
     * add a movement element to the movementMap
     * @param data xml data will be ignored (use addElement() instead to add an xml representation)
     * @return
     */
    public int addMovement(MovementData data) {
        Element e = new Element("movement", Mpm.MPM_NAMESPACE);
        e.addAttribute(new Attribute("date", Double.toString(data.startDate)));

        if (data.position != null)
            e.addAttribute(new Attribute("position", Double.toString(data.position)));

        if (data.transitionTo != null)
            e.addAttribute(new Attribute("transition.to", Double.toString(data.transitionTo)));

        if (data.curvature != null) {
            e.addAttribute(new Attribute("curvature", Double.toString(data.curvature)));
        }

        if (data.protraction != null) {
            e.addAttribute(new Attribute("protraction", Double.toString(data.protraction)));
        }

        if (data.xmlId != null)
            e.addAttribute(new Attribute("xml:id", "http://www.w3.org/XML/1998/namespace", data.xmlId));

        KeyValue<Double, Element> kv = new KeyValue<>(data.startDate, e);
        return this.insertElement(kv, false);
    }

    /**
     * collect all data that is needed to compute the movement at the specified date
     * @param date
     * @return
     */
    private MovementData getMovementDataAt(double date) {
        for (int i = this.getElementIndexBeforeAt(date); i >= 0; --i) {
            MovementData dd = this.getMovementDataOf(i);
            if (dd != null)
                return dd;
        }
        return null;
    }

    /**
     * this collects the movement data of a specific element of this movementMap, given via the index
     * @param index
     * @return the movement data or null if the indexed element is invalid, if the index is lower than 0 or the movementMap is empty default movement data is returned
     */
    public MovementData getMovementDataOf(int index) {
        if (this.elements.isEmpty() || (index < 0))
            return null;

        if (index >= this.elements.size())
            index = this.elements.size() - 1;

        Element e = this.elements.get(index).getValue();
        if (!e.getLocalName().equals("movement")) {
            return null;
        }

        MovementData movementData = new MovementData();

        movementData.startDate = this.elements.get(index).getKey();
        movementData.endDate = this.getEndDate(index);
        movementData.xml = e;

        Attribute att = Helper.getAttribute("xml:id", e);
        if (att != null)
            movementData.xmlId = att.getValue();

        att = Helper.getAttribute("position", e);
        if (att == null)
            movementData.position = this.getPreviousPosition(index);   
        else 
            movementData.position = Double.parseDouble(att.getValue());

        att = Helper.getAttribute("transition.to", e);
        if (att != null) {
            movementData.transitionTo = Double.parseDouble(att.getValue());
        }

        return movementData;
    }

    private double getPreviousPosition(int index) {
        // get the final position of the previous movement element
        double finalPosition = 0;
        for (int j = index - 1; j > 0; --j) {
            if (this.elements.get(j).getValue().getLocalName().equals("movement")) {
                finalPosition = Double.parseDouble(
                    this.elements.get(j).getValue().getAttribute("transition.to").getValue()
                );
                break;
            }
        }
        return finalPosition;
    }

    /**
     * On the basis of this movementMap, add the corresponding movement data to a MSM map called "positionMap".
     * 
     * @return the positionMap with movement data; this map should be added to the MSM part
     */
    public GenericMap renderMovementToMap() {
        GenericMap movementMap = GenericMap.createGenericMap("positionMap");

        int mapIndex = 0;
        for (int movementIndex = 0; movementIndex < this.size(); ++movementIndex) {
            MovementData md = this.getMovementDataOf(movementIndex);
            if (md == null)
                continue;

            if (movementMap != null && movementIndex < (this.size() - 1) && md.startDate >= 0) {
                MovementMap.generateMovement(md, movementMap);
            }
        }

        return movementMap;
    }

    public static GenericMap renderMovementToMap(MovementMap movementMap) {
        if (movementMap == null) return null;
        return movementMap.renderMovementToMap();
    }


    /**
     * a helper method for the implementation of movements.
     * It generates a series position events and adds them to
     * the specified channelVolumeMap,
     * the basis for this is the specified MovementData object
     * @param movementData
     * @param channelVolumeMap
     */
    private static void generateMovement(MovementData movementData, GenericMap movementMap) {
        ArrayList<double[]> movementSegment = movementData.getMovementSegment(0.1);

        for (double[] event : movementSegment) {
            Element e = new Element("position", movementMap.getXml().getNamespaceURI());
            e.addAttribute(new Attribute("date", Double.toString(event[0])));
            e.addAttribute(new Attribute("value", Double.toString(event[1])));
            e.addAttribute(new Attribute("controller", movementData.controller));
            movementMap.addElement(e);
        }
    }

    /**
     * a helper method to retrieve the date at which the indexed dynamics instruction ends, which is either the date of the subsequent instruction or Double.MAX_VALUE
     * @param index index of the current dynamics instruction for which the endDate is needed
     * @return
     */
    private double getEndDate(int index) {
        // get the date of the subsequent dynamics element
        double endDate = Double.MAX_VALUE;
        for (int j = index+1; j < this.elements.size(); ++j) {
            if (this.elements.get(j).getValue().getLocalName().equals("movement")) {
                endDate = this.elements.get(j).getKey();
                break;
            }
        }
        return endDate;
    }
}
