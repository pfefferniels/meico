package meico.mpm.elements.maps.data;

import nu.xom.Attribute;
import nu.xom.Element;

import java.util.ArrayList;

/**
 * this class is used to collect all relevant data to compute movements
 * @author Niels Pfeffer
 */
public class MovementData {
    public Element xml = null;
    public String xmlId = null;

    public double startDate = 0.0;

    public Double position = 0.0;
    public Double transitionTo = null;
    public Double endDate = null;
    public String controller = "sustain";

    public Double curvature = 0.4;
    public Double protraction = 0.0;

    private Double x1 = null;
    private Double x2 = null;

    /**
     * default constructor
     */
    public MovementData() {}

    /**
     * constructor from XML element parsing
     * @param xml MPM dynamics element
     */
    public MovementData(Element xml) {
        this.xml = xml;
        this.startDate = Double.parseDouble(xml.getAttributeValue("date"));

        Attribute position = xml.getAttribute("position");
        if (position != null) {
            this.position = Double.parseDouble(position.getValue());
        }

        Attribute transitionToAtt = xml.getAttribute("transition.to");
        if (transitionToAtt != null) {
            this.transitionTo = Double.parseDouble(transitionToAtt.getValue());
        }

        Attribute curvatureAtt = xml.getAttribute("curvature");
        if (curvatureAtt != null)
            this.curvature = Double.parseDouble(curvatureAtt.getValue());

        Attribute protractionAtt = xml.getAttribute("protraction");
        if (protractionAtt != null)
            this.protraction = Double.parseDouble(protractionAtt.getValue());

        Attribute id = xml.getAttribute("id", "http://www.w3.org/XML/1998/namespace");
        if (id != null)
            this.xmlId = id.getValue();

        Attribute controller = xml.getAttribute("controller", "http://www.w3.org/XML/1998/namespace");
        if (controller != null)
            this.xmlId = controller.getValue();
    }

    /**
     * create a copy of this object
     * @return
     */
    @Override
    public MovementData clone() {
        MovementData clone = new MovementData();
        clone.xml = (this.xml == null) ? null : this.xml.copy();
        clone.xmlId = this.xmlId;
        clone.startDate = this.startDate;
        clone.position = this.position;
        clone.transitionTo = this.transitionTo;
        clone.curvature = this.curvature;
        clone.protraction = this.protraction;
        clone.controller = this.controller;

        clone.x1 = this.x1;
        clone.x2 = this.x2;

        return clone;
    }

    /**
     * check whether this represents a constant dynamics instruction
     * @return
     */
    public boolean isConstantMovement() {
        return this.transitionTo == null;
    }

    /**
     * For continuous dynamics transitions the dynamics curve is constructed from a cubic, S-shaped Bézier curve (P0, P1, P2, P3): _/̅
     * This method derives the x-coordinates of the inner two control points from the values of curvature and protraction. All other coordinates are fixed.
     */
    private void computeInnerControlPointsXPositions() {
        if (this.curvature == null)
            this.curvature = 0.0;

        if (this.protraction == null)
            this.protraction = 0.0;

        if (this.protraction == 0.0) {
            this.x1 = this.curvature;
            this.x2 = 1.0 - this.curvature;
            return;
        }

        this.x1 = this.curvature + ((Math.abs(this.protraction) + this.protraction) / (2.0 * this.protraction) - (Math.abs(this.protraction) / this.protraction) * this.curvature) * protraction;
        this.x2 = 1.0 - this.curvature + ((this.protraction - Math.abs(this.protraction)) / (2.0 * this.protraction) + (Math.abs(this.protraction) / this.protraction) * this.curvature) * this.protraction;
    }

    /**
     * compute parameter t of the Bézier curve that corresponds to time position date
     * @param date time position
     * @return
     */
    private double getTForDate(double date) {
        if (date == this.startDate)
            return 0.0;

        if (date == this.endDate)
            return 1.0;

        if (this.x1 == null)    // ||(x2 == null)
            this.computeInnerControlPointsXPositions();

        // values that are often required
        double s = this.endDate - this.startDate;
        date = date - this.startDate;
        double u = (3.0 * this.x1) - (3.0 * this.x2) + 1.0;
        double v = (-6.0 * this.x1) + (3.0 * this.x2);
        double w = 3.0 * this.x1;

        // binary search for the t that is integer precise on the x-axis/time domain
        double t = 0.5;
        double diffX = ((((u * t) + v) * t + w) * t * s) - date;
        for (double tt = 0.25; Math.abs(diffX) >= 1.0; tt *= 0.5) { // while the difference in the x-domain is >= 1.0
            if (diffX > 0.0)                                        // if t is too small
                t -= tt;
            else                                                    // if t is too big
                t += tt;
            diffX = ((((u * t) + v) * t + w) * t * s) - date;       // compute difference
        }
        return t;
    }

    /**
     * compute the dynamics value at the given tick position
     * @param date
     * @return
     */
    public double getPositionAt(double date) {
        if (date <= this.startDate || (this.position == null)) {
            return this.position;
        }
        
        if (date >= this.endDate) {
            return this.transitionTo;
        }

        double t = this.getTForDate(date);
        return ((((3.0 - (2.0 * t)) * t * t) * (this.transitionTo - this.position)) + this.position);
    }

    /**
     * this method works directly with the parameter t to specify a point on the Bézier curve
     * and returns a tuplet [date, volume]
     * @param t
     * @return
     */
    private double[] getDatePosition(double t) {
        if (this.transitionTo == null)
            return new double[] { this.startDate, this.position };

        double[] result = new double[2];

        double x1_3 = 3.0 * this.x1;
        double x2_3 = 3.0 * this.x2;
        double u = x1_3 - x2_3 + 1.0;
        double v = (-6.0 * this.x1) + x2_3;
        
        double frameStart = this.startDate;
        double frameLength = this.endDate - this.startDate;

        result[0] = ((((u * t) + v) * t + x1_3) * t * frameLength) + frameStart;
        result[1] = ((((3.0 - (2.0 * t)) * t * t) * (this.transitionTo - this.position)) + this.position);

        return result;
    }

    /**
     * This method generates a list of [date, volume] tuplets that can be rendered into a sequence of channelVolume events.
     * @param maxStepSize this sets the maximum volume step size between two adjacent tuplets
     * @return
     */
    public ArrayList<double[]> getMovementSegment(double maxStepSize) {
        if (this.x1 == null)    // ||(x2 == null)
            this.computeInnerControlPointsXPositions();

        ArrayList<Double> ts = new ArrayList<>();
        ts.add(0.0);
        ts.add(1.0);

        ArrayList<double[]> series = new ArrayList<>();
        series.add(this.getDatePosition(0.0));                  // we start with the first value
        series.add(this.getDatePosition(1.0));                  // and end up with the last value

        // generate further tuplets in-between each two adjacent tuplets as long as their value difference is greater than maxStepSize; this here is basically a depth-first algorithm
        for (int i = 0; i < ts.size() - 1; ++i) {
            while (Math.abs(series.get(i+1)[1] - series.get(i)[1]) > maxStepSize) {
                double t = (ts.get(i) + ts.get(i+1)) * 0.5;
                ts.add(i+1, t);
                series.add(i+1, this.getDatePosition(t));
            }
        }

        double[] beginning = { this.startDate, this.position };
        series.add(0, beginning);

        if (this.transitionTo != null) {
            double[] end = { this.endDate, this.transitionTo };
            series.add(end);
        }

        for (double[] tuple : series) {
            tuple[1] *= 127;
        }

        return series;
    }
}
